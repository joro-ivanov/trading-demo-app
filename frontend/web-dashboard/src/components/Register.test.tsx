import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { Register } from './Register'

function okJson(body: unknown, status = 201): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

afterEach(() => vi.restoreAllMocks())

describe('Register', () => {
  it('shows a mismatch error, disables submit, and never calls the API when passwords differ', async () => {
    const user = userEvent.setup()
    const fetchSpy = vi.spyOn(globalThis, 'fetch')
    render(<Register onRegistered={vi.fn()} />)

    await user.type(screen.getByLabelText('Password'), 'password1')
    await user.type(screen.getByLabelText('Confirm password'), 'password2')

    expect(screen.getByText('Passwords do not match.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Register' })).toBeDisabled()
    expect(fetchSpy).not.toHaveBeenCalled()
  })

  it('registers and calls onRegistered with the session when valid', async () => {
    const user = userEvent.setup()
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(okJson({ token: 'jwt', username: 'carol' }))
    const onRegistered = vi.fn()
    render(<Register onRegistered={onRegistered} />)

    await user.type(screen.getByLabelText('Username'), 'carol')
    await user.type(screen.getByLabelText('Email'), 'carol@demo.example.com')
    await user.type(screen.getByLabelText('Password'), 's3cret-pw')
    await user.type(screen.getByLabelText('Confirm password'), 's3cret-pw')
    await user.click(screen.getByRole('button', { name: 'Register' }))

    await waitFor(() => expect(onRegistered).toHaveBeenCalledWith({ token: 'jwt', username: 'carol' }))
  })

  it('surfaces a 409 conflict as a friendly message', async () => {
    const user = userEvent.setup()
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 409 }))
    render(<Register onRegistered={vi.fn()} />)

    await user.type(screen.getByLabelText('Username'), 'alice')
    await user.type(screen.getByLabelText('Email'), 'alice@demo.example.com')
    await user.type(screen.getByLabelText('Password'), 's3cret-pw')
    await user.type(screen.getByLabelText('Confirm password'), 's3cret-pw')
    await user.click(screen.getByRole('button', { name: 'Register' }))

    expect(await screen.findByText('That username is already taken.')).toBeInTheDocument()
  })
})
