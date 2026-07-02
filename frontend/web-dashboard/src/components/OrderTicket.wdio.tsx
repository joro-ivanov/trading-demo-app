import { $, browser, expect } from '@wdio/globals'
import { cleanup, render } from '@testing-library/react'

import { OrderTicket } from './OrderTicket'

// Real-browser native validation on the number inputs (min/step) — which jsdom does not enforce.
describe('OrderTicket (component, real browser)', () => {
  afterEach(() => cleanup())

  it('blocks a quantity below the minimum — no request is sent', async () => {
    let calls = 0
    window.fetch = async () => {
      calls += 1
      return new Response(JSON.stringify({ id: 'o1' }), { status: 201 })
    }
    render(<OrderTicket token="jwt" onPlaced={() => undefined} />)

    await $('input[name=quantity]').setValue('0')
    await $('.order button[type=submit]').click()
    await browser.pause(200)

    expect(calls).toBe(0)
  })

  it('reveals the limit-price field for a LIMIT order and enforces its minimum', async () => {
    let calls = 0
    window.fetch = async () => {
      calls += 1
      return new Response(JSON.stringify({ id: 'o1' }), { status: 201 })
    }
    render(<OrderTicket token="jwt" onPlaced={() => undefined} />)

    await $('select[name=type]').selectByAttribute('value', 'LIMIT')
    await expect($('input[name=limitPrice]')).toBeExisting()

    await $('input[name=limitPrice]').setValue('0') // below the 0.01 minimum
    await $('.order button[type=submit]').click()
    await browser.pause(200)

    expect(calls).toBe(0)
  })
})
