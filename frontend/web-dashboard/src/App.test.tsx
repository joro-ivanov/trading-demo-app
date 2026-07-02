import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import App from './App'

const marketData = vi.hoisted(() => ({
  value: {
    prices: {},
    fills: [] as Array<{
      orderId: string
      symbol: string
      side: 'BUY' | 'SELL'
      quantity: number
      price: number
      filledAt: string
      sequenceId?: number
    }>,
  },
}))

// Stub the WS hook and the API client so App's session/logout flow can be driven in jsdom.
vi.mock('./ws/useMarketData', () => ({ useMarketData: () => marketData.value }))
vi.mock('./api/client', () => ({
  login: vi.fn(async () => ({ token: 'jwt', username: 'demo' })),
  register: vi.fn(),
  placeOrder: vi.fn(),
  getPortfolio: vi.fn(async () => []),
  getPrice: vi.fn(async () => null),
  getBalance: vi.fn(async () => 0),
  deposit: vi.fn(),
  withdraw: vi.fn(),
}))

describe('App', () => {
  beforeEach(() => {
    sessionStorage.clear()
    localStorage.clear()
    marketData.value = { prices: {}, fills: [] }
  })

  it('keeps the user logged in after a remount and uses sessionStorage only', async () => {
    const user = userEvent.setup()
    const { unmount } = render(<App />)

    expect(screen.getByRole('button', { name: 'Enter' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Enter' }))
    expect(await screen.findByRole('button', { name: 'Log out' })).toBeInTheDocument()
    expect(sessionStorage.getItem('session')).toBe('{"token":"jwt","username":"demo"}')
    expect(localStorage.getItem('session')).toBeNull()

    unmount()
    render(<App />)

    expect(screen.getByRole('button', { name: 'Log out' })).toBeInTheDocument()
    expect(screen.getByText('demo')).toBeInTheDocument()
  })

  it('shows login again after logout and clears sessionStorage', async () => {
    const user = userEvent.setup()
    render(<App />)

    expect(screen.getByRole('button', { name: 'Enter' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Enter' }))
    expect(await screen.findByRole('button', { name: 'Log out' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Log out' }))
    expect(screen.getByRole('button', { name: 'Enter' })).toBeInTheDocument()
    expect(sessionStorage.getItem('session')).toBeNull()
  })

  it('refreshes funds and portfolio after a fill arrives', async () => {
    sessionStorage.setItem('session', '{"token":"jwt","username":"demo"}')
    const { getBalance, getPortfolio } = await import('./api/client')

    const view = render(<App />)

    expect(await screen.findByRole('button', { name: 'Log out' })).toBeInTheDocument()
    const initialBalanceCalls = vi.mocked(getBalance).mock.calls.length
    const initialPortfolioCalls = vi.mocked(getPortfolio).mock.calls.length

    marketData.value = {
      prices: {},
      fills: [
        {
          orderId: 'o1',
          symbol: 'AAPL',
          side: 'BUY',
          quantity: 1,
          price: 100,
          filledAt: '2026-07-02T09:30:00Z',
          sequenceId: 1,
        },
      ],
    }
    view.rerender(<App />)

    await waitFor(() => {
      expect(vi.mocked(getBalance).mock.calls.length).toBeGreaterThan(initialBalanceCalls)
      expect(vi.mocked(getPortfolio).mock.calls.length).toBeGreaterThan(initialPortfolioCalls)
    })
  })
})
