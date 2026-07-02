import { render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { PortfolioPanel } from './PortfolioPanel'
import type { Position } from '../types'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

const position = (over: Partial<Position> = {}): Position => ({
  symbol: 'AAPL',
  quantity: 10,
  avgPrice: 190,
  marketPrice: 195,
  unrealizedPnl: 50,
  ...over,
})

afterEach(() => vi.restoreAllMocks())

describe('PortfolioPanel', () => {
  it('shows the empty state when there are no positions', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse([]))
    render(<PortfolioPanel token="jwt" version={0} />)
    expect(await screen.findByText('No positions yet — place an order.')).toBeInTheDocument()
  })

  it('renders a position with green P&L for a gain', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse([position({ unrealizedPnl: 50 })]))
    render(<PortfolioPanel token="jwt" version={0} />)
    const pnl = await screen.findByText('$50.00')
    expect(pnl).toHaveClass('pnl-up')
  })

  it('renders red P&L for a loss', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse([position({ unrealizedPnl: -100 })]))
    render(<PortfolioPanel token="jwt" version={0} />)
    const pnl = await screen.findByText('-$100.00')
    expect(pnl).toHaveClass('pnl-down')
  })

  it('shows a dash when the market price is not yet known', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse([position({ marketPrice: null })]))
    render(<PortfolioPanel token="jwt" version={0} />)
    expect(await screen.findByText('—')).toBeInTheDocument()
  })

  it('refetches when the version changes (after an order is placed)', async () => {
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse([position()]))
    const { rerender } = render(<PortfolioPanel token="jwt" version={0} />)
    expect(await screen.findByText('No positions yet — place an order.')).toBeInTheDocument()

    rerender(<PortfolioPanel token="jwt" version={1} />)
    await waitFor(() => expect(screen.getByText('AAPL')).toBeInTheDocument())
  })
})
