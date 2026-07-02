import { useEffect, useState } from 'react'

import { getPortfolio } from '../api/client'
import type { Position } from '../types'

const money = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', minimumFractionDigits: 2 })

/** Portfolio positions with live P&L. Polls every 2s and refreshes immediately after an order. */
export function PortfolioPanel({ token, version }: { token: string; version: number | string }) {
  const [positions, setPositions] = useState<Position[]>([])

  useEffect(() => {
    let active = true
    const load = () => {
      getPortfolio(token)
        .then((next) => {
          if (active) {
            setPositions(next)
          }
        })
        .catch(() => {
          // transient; next poll retries
        })
    }
    load()
    const timer = setInterval(load, 2000)
    return () => {
      active = false
      clearInterval(timer)
    }
  }, [token, version])

  return (
    <section className="card portfolio">
      <h2>Portfolio</h2>
      {positions.length === 0 ? (
        <p className="empty">No positions yet — place an order.</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>Symbol</th>
              <th>Qty</th>
              <th>Avg</th>
              <th>Mark</th>
              <th>P&amp;L</th>
            </tr>
          </thead>
          <tbody>
            {positions.map((position) => (
              <tr key={position.symbol}>
                <td>{position.symbol}</td>
                <td>{position.quantity}</td>
                <td>{money.format(position.avgPrice)}</td>
                <td>{position.marketPrice != null ? money.format(position.marketPrice) : '—'}</td>
                <td className={position.unrealizedPnl >= 0 ? 'pnl-up' : 'pnl-down'}>
                  {money.format(position.unrealizedPnl)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}
