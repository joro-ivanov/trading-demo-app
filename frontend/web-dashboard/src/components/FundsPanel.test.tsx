import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../api/client', () => ({
  getBalance: vi.fn(),
  deposit: vi.fn(),
  withdraw: vi.fn(),
}))

import { deposit, getBalance, withdraw } from '../api/client'
import { FundsPanel } from './FundsPanel'

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(getBalance).mockResolvedValue(1000)
})
afterEach(() => {
  vi.useRealTimers()
})

describe('FundsPanel', () => {
  it('shows the loaded balance', async () => {
    render(<FundsPanel token="jwt" version={0} />)
    expect(await screen.findByText(/\$1,000\.00/)).toBeInTheDocument()
  })

  it('polls and updates the balance when cash changes after a trade', async () => {
    vi.mocked(getBalance)
      .mockResolvedValueOnce(10000)
      .mockResolvedValueOnce(9899.25)
    render(<FundsPanel token="jwt" version={0} />)

    expect(await screen.findByText(/\$10,000\.00/)).toBeInTheDocument()

    await waitFor(() => expect(screen.getByText(/\$9,899\.25/)).toBeInTheDocument(), { timeout: 3000 })
  })

  it('deposits the entered amount and updates the balance', async () => {
    const user = userEvent.setup()
    vi.mocked(deposit).mockResolvedValue(1500)
    render(<FundsPanel token="jwt" version={0} />)
    await screen.findByText(/\$1,000\.00/)

    await user.click(screen.getByRole('button', { name: 'Deposit' }))

    expect(deposit).toHaveBeenCalledWith('jwt', 1000) // default amount
    expect(await screen.findByText(/\$1,500\.00/)).toBeInTheDocument()
  })

  it('rejects a non-positive amount client-side without calling the API', async () => {
    const user = userEvent.setup()
    render(<FundsPanel token="jwt" version={0} />)
    await screen.findByText(/\$1,000\.00/)

    const amount = screen.getByLabelText('Amount')
    await user.clear(amount)
    await user.type(amount, '0')
    await user.click(screen.getByRole('button', { name: 'Deposit' }))

    expect(screen.getByText('Enter a positive amount.')).toBeInTheDocument()
    expect(deposit).not.toHaveBeenCalled()
  })

  it('surfaces a withdraw failure', async () => {
    const user = userEvent.setup()
    vi.mocked(withdraw).mockRejectedValue(new Error('Withdraw failed (422)'))
    render(<FundsPanel token="jwt" version={0} />)
    await screen.findByText(/\$1,000\.00/)

    await user.click(screen.getByRole('button', { name: 'Withdraw' }))

    expect(await screen.findByText('Withdraw failed (422)')).toBeInTheDocument()
  })
})
