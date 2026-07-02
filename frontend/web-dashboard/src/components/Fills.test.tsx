import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { Fills } from './Fills'
import type { Fill } from '../types'

const fill = (over: Partial<Fill> = {}): Fill => ({
  orderId: 'o1',
  symbol: 'AAPL',
  side: 'BUY',
  quantity: 5,
  price: 190.4,
  filledAt: '2026-07-01T10:15:30Z',
  sequenceId: 1,
  ...over,
})

describe('Fills', () => {
  it('shows the empty state when there are no fills', () => {
    render(<Fills fills={[]} />)
    expect(screen.getByText('Waiting for fills…')).toBeInTheDocument()
  })

  it('colors a BUY green and a SELL red', () => {
    render(<Fills fills={[fill({ orderId: 'b', side: 'BUY' }), fill({ orderId: 's', side: 'SELL' })]} />)
    expect(screen.getByText('BUY')).toHaveClass('pnl-up')
    expect(screen.getByText('SELL')).toHaveClass('pnl-down')
  })

  it('shows the fill timestamp', () => {
    render(<Fills fills={[fill()]} />)
    expect(screen.getByText('2026-07-01 10:15:30 UTC')).toBeInTheDocument()
  })
})
