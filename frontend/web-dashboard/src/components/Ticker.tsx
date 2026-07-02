import { SYMBOLS } from '../types'

const money = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  minimumFractionDigits: 2,
})

/** Live price ticker fed by the WebSocket gateway. */
export function Ticker({ prices }: { prices: Record<string, number> }) {
  return (
    <section className="card ticker">
      <h2>Live Prices</h2>
      <table>
        <tbody>
          {SYMBOLS.map((symbol) => (
            <tr key={symbol}>
              <td className="symbol">{symbol}</td>
              <td className="price">{prices[symbol] != null ? money.format(prices[symbol]) : '—'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  )
}
