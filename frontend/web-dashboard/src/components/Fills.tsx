import type { Fill } from '../types'

const timestamp = new Intl.DateTimeFormat('sv-SE', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  timeZone: 'UTC',
})

/** Recent fills pushed over the WebSocket gateway. */
export function Fills({ fills }: { fills: Fill[] }) {
  return (
    <section className="card fills">
      <h2>Recent Fills</h2>
      {fills.length === 0 ? (
        <p className="empty">Waiting for fills…</p>
      ) : (
        <ul>
          {fills.map((fill) => (
            <li key={fill.orderId}>
              <time dateTime={fill.filledAt}>{timestamp.format(new Date(fill.filledAt))} UTC</time>{' '}
              <span className={fill.side === 'BUY' ? 'pnl-up' : 'pnl-down'}>{fill.side}</span> {fill.quantity}{' '}
              {fill.symbol} @ {fill.price}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
