import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { OrderTicket } from './OrderTicket'

afterEach(() => vi.restoreAllMocks())

const ORDER_RESPONSE = {
  id: 'o1',
  symbol: 'AAPL',
  side: 'BUY',
  quantity: 10,
  type: 'MARKET',
  limitPrice: null,
  stopPrice: null,
  status: 'SUBMITTED',
}

describe('OrderTicket', () => {
  it('reveals only the price field matching the selected order type', async () => {
    const user = userEvent.setup()
    render(<OrderTicket token="jwt" onPlaced={vi.fn()} />)

    expect(screen.queryByLabelText('Limit price')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Stop price')).not.toBeInTheDocument()

    await user.selectOptions(screen.getByLabelText('Type'), 'LIMIT')
    expect(screen.getByLabelText('Limit price')).toBeInTheDocument()
    expect(screen.queryByLabelText('Stop price')).not.toBeInTheDocument()

    await user.selectOptions(screen.getByLabelText('Type'), 'STOP')
    expect(screen.getByLabelText('Stop price')).toBeInTheDocument()
    expect(screen.queryByLabelText('Limit price')).not.toBeInTheDocument()
  })

  it('submits a MARKET order without limit/stop prices and calls onPlaced', async () => {
    const user = userEvent.setup()
    const fetchSpy = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(JSON.stringify(ORDER_RESPONSE), { status: 201 }))
    const onPlaced = vi.fn()
    render(<OrderTicket token="jwt" onPlaced={onPlaced} />)

    await user.click(screen.getByRole('button', { name: 'Place' }))

    await waitFor(() => expect(onPlaced).toHaveBeenCalled())
    const body = JSON.parse(String(fetchSpy.mock.calls[0][1]?.body))
    expect(body).toMatchObject({ symbol: 'AAPL', side: 'BUY', quantity: 10, type: 'MARKET' })
    expect(body).not.toHaveProperty('limitPrice')
    expect(body).not.toHaveProperty('stopPrice')
  })

  it('shows the rejection error and does not call onPlaced when the order is rejected', async () => {
    const user = userEvent.setup()
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 422 }))
    const onPlaced = vi.fn()
    render(<OrderTicket token="jwt" onPlaced={onPlaced} />)

    await user.click(screen.getByRole('button', { name: 'Place' }))

    expect(await screen.findByText('Order rejected (422)')).toBeInTheDocument()
    expect(onPlaced).not.toHaveBeenCalled()
  })
})
