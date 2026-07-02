import { $, browser, expect } from '@wdio/globals'
import { cleanup, render } from '@testing-library/react'

import { Register } from './Register'

// These are the scenarios that only a REAL browser can verify: native HTML5 validation
// (required / type=email / minLength) actually blocking submission. jsdom (Vitest) does not enforce
// these, so the value of the WDIO layer is exactly here.
describe('Register (component, real browser)', () => {
  afterEach(() => cleanup())

  async function fill(username: string, email: string, password: string, confirm: string) {
    await $('input[name=username]').setValue(username)
    await $('input[name=email]').setValue(email)
    await $('input[name=password]').setValue(password)
    await $('input[name=confirmPassword]').setValue(confirm)
  }

  it('native validation blocks submit (invalid email) — no request is sent', async () => {
    let calls = 0
    window.fetch = async () => {
      calls += 1
      return new Response(JSON.stringify({ token: 'jwt', username: 'x' }), { status: 201 })
    }
    render(<Register onRegistered={() => undefined} />)

    await fill('validuser', 'not-an-email', 's3cret-pw', 's3cret-pw')
    await $('button=Register').click()
    await browser.pause(200)

    expect(calls).toBe(0)
  })

  it('disables submit and shows an error on confirm-password mismatch', async () => {
    let calls = 0
    window.fetch = async () => {
      calls += 1
      return new Response('{}', { status: 201 })
    }
    render(<Register onRegistered={() => undefined} />)

    await fill('validuser', 'valid@demo.example.com', 's3cret-pw', 'different')

    await expect($('button=Register')).toBeDisabled()
    await expect($('.error')).toHaveText('Passwords do not match.')
    expect(calls).toBe(0)
  })

  it('registers on valid input and omits confirmPassword from the request body', async () => {
    let sentBody: Record<string, unknown> | null = null
    let captured: unknown = null
    window.fetch = async (_url, init) => {
      sentBody = JSON.parse(String((init as RequestInit).body))
      return new Response(JSON.stringify({ token: 'jwt', username: 'carol' }), { status: 201 })
    }
    render(<Register onRegistered={(session) => (captured = session)} />)

    await fill('carol', 'carol@demo.example.com', 's3cret-pw', 's3cret-pw')
    await $('button=Register').click()

    await browser.waitUntil(() => captured !== null, { timeoutMsg: 'onRegistered was never called' })
    expect(captured).toEqual({ token: 'jwt', username: 'carol' })
    expect(sentBody).toEqual({ username: 'carol', email: 'carol@demo.example.com', password: 's3cret-pw' })
    expect(sentBody).not.toHaveProperty('confirmPassword')
  })

  it('shows a friendly conflict message on a 409', async () => {
    window.fetch = async () => new Response('', { status: 409 })
    render(<Register onRegistered={() => undefined} />)

    await fill('alice', 'alice@demo.example.com', 's3cret-pw', 's3cret-pw')
    await $('button=Register').click()

    await expect($('.error')).toHaveText('That username is already taken.')
  })
})
