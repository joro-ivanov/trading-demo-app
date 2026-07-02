import { act, renderHook } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

// A controllable fake @stomp/stompjs Client, set up before the module under test is imported.
const stomp = vi.hoisted(() => {
  const subscriptions: Record<string, (message: { body: string }) => void> = {}
  const client = {
    onConnect: (() => undefined) as () => void,
    activate: vi.fn(),
    deactivate: vi.fn(() => Promise.resolve()),
    subscribe: vi.fn((destination: string, callback: (message: { body: string }) => void) => {
      subscriptions[destination] = callback
      return { unsubscribe: vi.fn() }
    }),
  }
  const ClientCtor = vi.fn((options: unknown) => {
    Object.assign(client, { options })
    return client
  })
  return { subscriptions, client, ClientCtor }
})

vi.mock('@stomp/stompjs', () => ({ Client: stomp.ClientCtor }))
vi.mock('sockjs-client', () => ({ default: vi.fn() }))
vi.mock('../api/client', () => ({ getRecentFills: vi.fn(async () => []) }))

import { useMarketData } from './useMarketData'
import { getRecentFills } from '../api/client'

afterEach(() => {
  vi.clearAllMocks()
  Object.keys(stomp.subscriptions).forEach((key) => delete stomp.subscriptions[key])
})

function fill(orderId: string) {
  return { body: JSON.stringify({ orderId, symbol: 'AAPL', side: 'BUY', quantity: 1, price: 1, filledAt: `2026-07-01T10:00:${orderId.slice(1).padStart(2, '0')}Z`, sequenceId: Number(orderId.slice(1)) }) }
}

const AAPL_ONLY = ['AAPL'] as const

describe('useMarketData', () => {
  it('does not open a connection without a token', () => {
    renderHook(() => useMarketData(AAPL_ONLY, null))
    expect(stomp.ClientCtor).not.toHaveBeenCalled()
  })

  it('connects with the bearer token and activates', () => {
    renderHook(() => useMarketData(AAPL_ONLY, 'jwt'))

    expect(getRecentFills).toHaveBeenCalledWith('jwt')
    expect(stomp.ClientCtor).toHaveBeenCalledTimes(1)
    const options = stomp.ClientCtor.mock.calls[0][0] as { connectHeaders: { Authorization: string } }
    expect(options.connectHeaders.Authorization).toBe('Bearer jwt')
    expect(stomp.client.activate).toHaveBeenCalled()
  })

  it('subscribes per symbol and maps a price tick into state', () => {
    const { result } = renderHook(() => useMarketData(AAPL_ONLY, 'jwt'))
    act(() => stomp.client.onConnect())

    expect(stomp.client.subscribe).toHaveBeenCalledWith('/topic/prices.AAPL', expect.any(Function))
    act(() =>
      stomp.subscriptions['/topic/prices.AAPL']({ body: JSON.stringify({ symbol: 'AAPL', price: 150 }) }),
    )
    expect(result.current.prices.AAPL).toBe(150)
  })

  it('prepends fills and keeps only the 20 most recent', () => {
    const { result } = renderHook(() => useMarketData(AAPL_ONLY, 'jwt'))
    act(() => stomp.client.onConnect())

    act(() => {
      Array.from({ length: 25 }, (_unused, i) => stomp.subscriptions['/user/queue/fills'](fill('o' + i)))
    })

    expect(result.current.fills).toHaveLength(20)
    expect(result.current.fills[0].orderId).toBe('o24') // most recent first
  })

  it('loads recent fills from the API before live updates', async () => {
    vi.mocked(getRecentFills).mockResolvedValueOnce([
      { orderId: 'o1', symbol: 'AAPL', side: 'BUY', quantity: 1, price: 10, filledAt: '2026-07-01T10:15:30Z', sequenceId: 1 },
    ])
    const { result } = renderHook(() => useMarketData(AAPL_ONLY, 'jwt'))

    await act(async () => undefined)

    expect(result.current.fills[0].orderId).toBe('o1')
  })

  it('preserves API order when recent fills have the same timestamp', async () => {
    vi.mocked(getRecentFills).mockResolvedValueOnce([
      { orderId: 'sell', symbol: 'AAPL', side: 'SELL', quantity: 1, price: 101, filledAt: '2026-07-01T10:15:30Z', sequenceId: 2 },
      { orderId: 'buy', symbol: 'AAPL', side: 'BUY', quantity: 1, price: 100, filledAt: '2026-07-01T10:15:30Z', sequenceId: 1 },
    ])
    const { result } = renderHook(() => useMarketData(AAPL_ONLY, 'jwt'))

    await act(async () => undefined)

    expect(result.current.fills.map((fill) => fill.orderId)).toEqual(['sell', 'buy'])
  })

  it('merges late API fills without overwriting newer live fills', async () => {
    let resolveRecent: ((fills: Array<{ orderId: string; symbol: string; side: 'BUY' | 'SELL'; quantity: number; price: number; filledAt: string; sequenceId: number }>) => void) | undefined
    vi.mocked(getRecentFills).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveRecent = resolve
        }),
    )
    const { result } = renderHook(() => useMarketData(AAPL_ONLY, 'jwt'))
    act(() => stomp.client.onConnect())

    act(() => {
      stomp.subscriptions['/user/queue/fills']({
        body: JSON.stringify({
          orderId: 'buy',
          symbol: 'AAPL',
          side: 'BUY',
          quantity: 1,
          price: 100,
          filledAt: '2026-07-01T10:15:30Z',
          sequenceId: 1,
        }),
      })
      stomp.subscriptions['/user/queue/fills']({
        body: JSON.stringify({
          orderId: 'sell',
          symbol: 'AAPL',
          side: 'SELL',
          quantity: 1,
          price: 101,
          filledAt: '2026-07-01T10:15:30Z',
          sequenceId: 2,
        }),
      })
    })

    await act(async () => {
      resolveRecent?.([
        { orderId: 'buy', symbol: 'AAPL', side: 'BUY', quantity: 1, price: 100, filledAt: '2026-07-01T10:15:30Z', sequenceId: 1 },
        { orderId: 'sell', symbol: 'AAPL', side: 'SELL', quantity: 1, price: 101, filledAt: '2026-07-01T10:15:30Z', sequenceId: 2 },
      ])
    })

    expect(result.current.fills.map((entry) => entry.orderId)).toEqual(['sell', 'buy'])
  })

  it('deactivates the connection on unmount', () => {
    const { unmount } = renderHook(() => useMarketData(AAPL_ONLY, 'jwt'))
    unmount()
    expect(stomp.client.deactivate).toHaveBeenCalled()
  })
})
