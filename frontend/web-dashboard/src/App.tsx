import { useEffect, useState } from 'react'

import { Auth } from './components/Auth'
import { Fills } from './components/Fills'
import { FundsPanel } from './components/FundsPanel'
import { OrderTicket } from './components/OrderTicket'
import { PortfolioPanel } from './components/PortfolioPanel'
import { Ticker } from './components/Ticker'
import { SYMBOLS, type Session } from './types'
import { useMarketData } from './ws/useMarketData'

const SESSION_STORAGE_KEY = 'session'

export default function App() {
  const [session, setSession] = useState<Session | null>(() => readSession())
  const [orderVersion, setOrderVersion] = useState(0)
  const { prices, fills } = useMarketData(SYMBOLS, session?.token ?? null)
  const latestFillVersion = fills[0] == null ? 'no-fills' : `${fills[0].orderId}:${fills[0].filledAt}`
  const tradeVersion = `${orderVersion}:${latestFillVersion}`

  useEffect(() => {
    if (session == null) {
      sessionStorage.removeItem(SESSION_STORAGE_KEY)
      return
    }
    sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session))
  }, [session])

  if (!session) {
    return <Auth onAuthenticated={setSession} />
  }

  return (
    <div className="app">
      <header className="header">
        <h1>Trading Dashboard</h1>
        <div className="session">
          <span>{session.username}</span>
          <button type="button" onClick={() => setSession(null)}>
            Log out
          </button>
        </div>
      </header>
      <main className="grid">
        <Ticker prices={prices} />
        <OrderTicket token={session.token} onPlaced={() => setOrderVersion((version) => version + 1)} />
        <FundsPanel token={session.token} version={tradeVersion} />
        <PortfolioPanel token={session.token} version={tradeVersion} />
        <Fills fills={fills} />
      </main>
    </div>
  )
}

function readSession(): Session | null {
  const stored = sessionStorage.getItem(SESSION_STORAGE_KEY)
  if (stored == null) {
    return null
  }
  try {
    return JSON.parse(stored) as Session
  } catch {
    sessionStorage.removeItem(SESSION_STORAGE_KEY)
    return null
  }
}
