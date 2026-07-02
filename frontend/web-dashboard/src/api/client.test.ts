import { afterEach, describe, expect, it, vi } from 'vitest'

import { deposit, getBalance, getPortfolio, getPrice, getRecentFills, login, placeOrder, register, withdraw } from './client'
import type { OrderRequest } from '../types'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

const ORDER = {
  id: 'o1',
  symbol: 'AAPL',
  side: 'BUY',
  quantity: 10,
  type: 'MARKET',
  limitPrice: null,
  stopPrice: null,
  status: 'SUBMITTED',
}

const POSITION = {
  symbol: 'AAPL',
  quantity: 10,
  avgPrice: 190.4,
  marketPrice: 191,
  unrealizedPnl: 6,
}

const FILL = {
  orderId: 'o1',
  symbol: 'AAPL',
  side: 'BUY',
  quantity: 10,
  price: 190.4,
  filledAt: '2026-07-01T10:15:30Z',
  sequenceId: 1,
}

afterEach(() => vi.restoreAllMocks())

describe('login', () => {
  it('maps a 200 to a Session', async () => {
    const fetchSpy = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(jsonResponse({ token: 'jwt', username: 'demo' }))

    await expect(login('demo', 'password')).resolves.toEqual({ token: 'jwt', username: 'demo' })
    expect(fetchSpy).toHaveBeenCalledWith('/api/auth/login', expect.objectContaining({ method: 'POST' }))
  })

  it('throws a status-bearing error on a non-ok response', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({}, 401))
    await expect(login('demo', 'wrong')).rejects.toThrow('Login failed (401)')
  })
})

describe('register', () => {
  it('maps a 201 to a Session and sends username/email/password only (no confirmPassword)', async () => {
    const fetchSpy = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(jsonResponse({ token: 'jwt', username: 'carol' }, 201))

    await expect(register('carol', 'carol@demo.example.com', 's3cret-pw')).resolves.toEqual({
      token: 'jwt',
      username: 'carol',
    })

    const [, init] = fetchSpy.mock.calls[0]
    const body = JSON.parse(String(init?.body))
    expect(body).toEqual({ username: 'carol', email: 'carol@demo.example.com', password: 's3cret-pw' })
    expect(body).not.toHaveProperty('confirmPassword')
  })

  it('throws on a 409 conflict', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({}, 409))
    await expect(register('alice', 'a@b.com', 's3cret-pw')).rejects.toThrow('Registration failed (409)')
  })
})

describe('placeOrder', () => {
  const market: OrderRequest = { symbol: 'AAPL', side: 'BUY', quantity: 10, type: 'MARKET' }

  it('maps a 201 to an OrderResponse and sends the bearer token', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(ORDER, 201))
    await expect(placeOrder('jwt', market)).resolves.toEqual(ORDER)
    const [, init] = fetchSpy.mock.calls[0]
    expect((init?.headers as Record<string, string>).Authorization).toBe('Bearer jwt')
  })

  it('omits undefined limitPrice/stopPrice from the body (MARKET order)', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(ORDER, 201))
    await placeOrder('jwt', market)
    const body = JSON.parse(String(fetchSpy.mock.calls[0][1]?.body))
    expect(body).not.toHaveProperty('limitPrice')
    expect(body).not.toHaveProperty('stopPrice')
  })

  it('throws "Order rejected (status)" on a non-ok response', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({}, 422))
    await expect(placeOrder('jwt', market)).rejects.toThrow('Order rejected (422)')
  })
})

describe('getPortfolio', () => {
  it('maps a 200 to a Position list', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse([POSITION]))
    await expect(getPortfolio('jwt')).resolves.toEqual([POSITION])
  })

  it('throws on a non-ok response', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({}, 401))
    await expect(getPortfolio('jwt')).rejects.toThrow('Failed to load portfolio (401)')
  })
})

describe('getRecentFills', () => {
  it('maps a 200 to a Fill list', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse([FILL]))
    await expect(getRecentFills('jwt')).resolves.toEqual([FILL])
  })

  it('throws on a non-ok response', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({}, 401))
    await expect(getRecentFills('jwt')).rejects.toThrow('Failed to load recent fills (401)')
  })
})

describe('getPrice', () => {
  it('maps a 200 to a PriceTick', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({ symbol: 'AAPL', price: 190.4 }))
    await expect(getPrice('jwt', 'AAPL')).resolves.toEqual({ symbol: 'AAPL', price: 190.4 })
  })

  it('returns null on 404 (no price yet is not an error)', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 404 }))
    await expect(getPrice('jwt', 'AAPL')).resolves.toBeNull()
  })

  it('throws on a non-ok, non-404 response', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({}, 500))
    await expect(getPrice('jwt', 'AAPL')).rejects.toThrow('Failed to load price for AAPL (500)')
  })
})

describe('getBalance', () => {
  it('returns the cash field on a 200', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({ cash: 1234.5 }))
    await expect(getBalance('jwt')).resolves.toBe(1234.5)
  })

  it('throws on a non-ok response', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({}, 401))
    await expect(getBalance('jwt')).rejects.toThrow('Failed to load balance (401)')
  })
})

describe('deposit', () => {
  it('POSTs the amount and returns the new cash', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({ cash: 1500 }))
    await expect(deposit('jwt', 500)).resolves.toBe(1500)
    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/funds/deposit',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ amount: 500 }) }),
    )
  })

  it('throws a status-bearing error on a non-ok response', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({}, 400))
    await expect(deposit('jwt', -5)).rejects.toThrow('Deposit failed (400)')
  })
})

describe('withdraw', () => {
  it('returns the new cash on success', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({ cash: 800 }))
    await expect(withdraw('jwt', 200)).resolves.toBe(800)
  })

  it('throws on insufficient funds (422)', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({}, 422))
    await expect(withdraw('jwt', 999999)).rejects.toThrow('Withdraw failed (422)')
  })
})
