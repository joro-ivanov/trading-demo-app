import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { Auth } from './Auth'

describe('Auth', () => {
  it('starts on login and toggles to register and back', async () => {
    const user = userEvent.setup()
    render(<Auth onAuthenticated={vi.fn()} />)

    expect(screen.getByRole('heading', { name: 'Trading Dashboard' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Need an account? Register' }))
    expect(screen.getByRole('heading', { name: 'Create account' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Have an account? Log in' }))
    expect(screen.getByRole('heading', { name: 'Trading Dashboard' })).toBeInTheDocument()
  })
})
