import { useActionState, useId, useState } from 'react'

import { placeOrder } from '../api/client'
import { SYMBOLS, type OrderRequest, type OrderType, type Side } from '../types'

/**
 * Order entry using React 19 form Actions + {@code useActionState}: errors are returned as state
 * (never thrown) so the form can render them (react-best-practices §4.1). The order type drives a
 * conditional limit/stop price field; MARKET sends neither.
 */
export function OrderTicket({ token, onPlaced }: { token: string; onPlaced: () => void }) {
  const symbolId = useId()
  const sideId = useId()
  const typeId = useId()
  const quantityId = useId()
  const limitId = useId()
  const stopId = useId()
  const [type, setType] = useState<OrderType>('MARKET')

  const [error, submitAction, pending] = useActionState(
    async (_previous: string | null, formData: FormData) => {
      const orderType = formData.get('type') as OrderType
      const order: OrderRequest = {
        symbol: String(formData.get('symbol')),
        side: formData.get('side') as Side,
        quantity: Number(formData.get('quantity')),
        type: orderType,
        limitPrice: orderType === 'LIMIT' ? Number(formData.get('limitPrice')) : undefined,
        stopPrice: orderType === 'STOP' ? Number(formData.get('stopPrice')) : undefined,
      }
      try {
        await placeOrder(token, order)
        onPlaced()
        return null
      } catch (cause) {
        return (cause as Error).message
      }
    },
    null,
  )

  return (
    <section className="card order">
      <h2>Place Order</h2>
      <form action={submitAction}>
        <label htmlFor={symbolId}>Symbol</label>
        <select id={symbolId} name="symbol">
          {SYMBOLS.map((symbol) => (
            <option key={symbol} value={symbol}>
              {symbol}
            </option>
          ))}
        </select>

        <label htmlFor={sideId}>Side</label>
        <select id={sideId} name="side">
          <option value="BUY">BUY</option>
          <option value="SELL">SELL</option>
        </select>

        <label htmlFor={typeId}>Type</label>
        <select
          id={typeId}
          name="type"
          value={type}
          onChange={(event) => setType(event.target.value as OrderType)}
        >
          <option value="MARKET">MARKET</option>
          <option value="LIMIT">LIMIT</option>
          <option value="STOP">STOP</option>
        </select>

        {type === 'LIMIT' ? (
          <>
            <label htmlFor={limitId}>Limit price</label>
            <input id={limitId} name="limitPrice" type="number" min={0.01} step={0.01} defaultValue={100} />
          </>
        ) : null}

        {type === 'STOP' ? (
          <>
            <label htmlFor={stopId}>Stop price</label>
            <input id={stopId} name="stopPrice" type="number" min={0.01} step={0.01} defaultValue={100} />
          </>
        ) : null}

        <label htmlFor={quantityId}>Quantity</label>
        <input id={quantityId} name="quantity" type="number" min={1} defaultValue={10} />

        <button type="submit" disabled={pending}>
          {pending ? 'Placing…' : 'Place'}
        </button>
      </form>
      {error != null ? <p className="error">{error}</p> : null}
    </section>
  )
}
