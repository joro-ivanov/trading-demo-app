import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { Login } from './Login'

afterEach(() => vi.restoreAllMocks())

describe('Login', () => {
  it('shows an error and does not authenticate when the credentials are rejected', async () => {
    const user = userEvent.setup()
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 401 }))
    const onLogin = vi.fn()
    render(<Login onLogin={onLogin} />)

    await user.click(screen.getByRole('button', { name: 'Enter' }))

    expect(await screen.findByText('Login failed — try demo / password')).toBeInTheDocument()
    expect(onLogin).not.toHaveBeenCalled()
  })

  it('authenticates on success', async () => {
    const user = userEvent.setup()
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ token: 'jwt', username: 'demo' }), { status: 200 }),
    )
    const onLogin = vi.fn()
    render(<Login onLogin={onLogin} />)

    await user.click(screen.getByRole('button', { name: 'Enter' }))

    expect(onLogin).toHaveBeenCalledWith({ token: 'jwt', username: 'demo' })
  })
})
