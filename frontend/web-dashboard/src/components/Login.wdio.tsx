import { $, browser, expect } from '@wdio/globals'
import { cleanup, render } from '@testing-library/react'

import { Login } from './Login'

describe('Login (component, real browser)', () => {
  afterEach(() => cleanup())

  it('shows an error and does not authenticate when credentials are rejected (stubbed 401)', async () => {
    window.fetch = async () => new Response('', { status: 401 })
    render(<Login onLogin={() => undefined} />)

    await $('button=Enter').click()

    await expect($('.error')).toBeDisplayed()
    await expect($('.error')).toHaveText('Login failed — try demo / password')
  })

  it('calls onLogin with the session on success (stubbed 200)', async () => {
    window.fetch = async () =>
      new Response(JSON.stringify({ token: 'jwt', username: 'demo' }), { status: 200 })
    let captured: unknown = null
    render(<Login onLogin={(session) => (captured = session)} />)

    await $('button=Enter').click()

    await browser.waitUntil(() => captured !== null, { timeoutMsg: 'onLogin was never called' })
    expect(captured).toEqual({ token: 'jwt', username: 'demo' })
  })
})
