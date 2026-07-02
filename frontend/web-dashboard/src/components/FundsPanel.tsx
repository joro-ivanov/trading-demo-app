import { useEffect, useId, useState } from 'react'

import { deposit, getBalance, withdraw } from '../api/client'

const money = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', minimumFractionDigits: 2 })
const REFRESH_INTERVAL_MS = 2000

/** Cash balance with deposit/withdraw. Re-reads after trades and polls to recover from missed events. */
export function FundsPanel({ token, version }: { token: string; version: number | string }) {
  const amountId = useId()
  const [balance, setBalance] = useState<number | null>(null)
  const [amount, setAmount] = useState('1000')
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  useEffect(() => {
    let active = true
    const load = () => {
      getBalance(token)
        .then((cash) => {
          if (active) {
            setBalance(cash)
          }
        })
        .catch(() => {
          // transient; the next poll or order/version refresh retries
        })
    }
    load()
    const timer = setInterval(load, REFRESH_INTERVAL_MS)
    return () => {
      active = false
      clearInterval(timer)
    }
  }, [token, version])

  async function move(action: (token: string, amount: number) => Promise<number>) {
    const value = Number(amount)
    if (!(value > 0)) {
      setError('Enter a positive amount.')
      return
    }
    setError(null)
    setPending(true)
    try {
      setBalance(await action(token, value))
    } catch (cause) {
      setError((cause as Error).message)
    } finally {
      setPending(false)
    }
  }

  return (
    <section className="card funds">
      <h2>Funds</h2>
      <p className="balance">Balance: {balance != null ? money.format(balance) : '—'}</p>
      <label htmlFor={amountId}>Amount</label>
      <input
        id={amountId}
        name="amount"
        type="number"
        min={0.01}
        step={0.01}
        value={amount}
        onChange={(event) => setAmount(event.target.value)}
      />
      <div className="funds-actions">
        <button type="button" disabled={pending} onClick={() => move(deposit)}>
          Deposit
        </button>
        <button type="button" disabled={pending} onClick={() => move(withdraw)}>
          Withdraw
        </button>
      </div>
      {error != null ? <p className="error">{error}</p> : null}
    </section>
  )
}
