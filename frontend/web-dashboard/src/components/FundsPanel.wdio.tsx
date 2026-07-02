import { $, browser, expect } from '@wdio/globals'
import { cleanup, render } from '@testing-library/react'

import { FundsPanel } from './FundsPanel'

describe('FundsPanel (component, real browser)', () => {
  afterEach(() => cleanup())

  it('loads the balance and a deposit updates it', async () => {
    // First fetch is getBalance, second is the deposit (no request branching needed).
    const cash = [1000, 1500]
    let call = 0
    window.fetch = async () => new Response(JSON.stringify({ cash: cash[call++] }), { status: 200 })
    render(<FundsPanel token="jwt" version={0} />)

    await expect($('.funds .balance')).toHaveText('$1,000.00', { containing: true })
    await $('.funds-actions button:nth-of-type(1)').click()
    await expect($('.funds .balance')).toHaveText('$1,500.00', { containing: true })
  })

  it('blocks a non-positive amount without sending a request', async () => {
    let calls = 0
    window.fetch = async () => {
      calls += 1
      return new Response(JSON.stringify({ cash: 1000 }), { status: 200 })
    }
    render(<FundsPanel token="jwt" version={0} />)
    await expect($('.funds .balance')).toHaveText('$1,000.00', { containing: true })

    await $('input[name=amount]').setValue('0')
    await $('.funds-actions button:nth-of-type(1)').click()
    await browser.pause(150)

    await expect($('.funds .error')).toHaveText('Enter a positive amount.')
    expect(calls).toBe(1) // only the initial getBalance, no deposit request
  })
})
