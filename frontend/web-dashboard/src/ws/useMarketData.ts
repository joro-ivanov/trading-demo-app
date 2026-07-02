import { Client } from '@stomp/stompjs'
import { useEffect, useState } from 'react'
import SockJS from 'sockjs-client'

import { getRecentFills } from '../api/client'
import type { Fill, PriceTick } from '../types'

interface MarketData {
  prices: Record<string, number>
  fills: Fill[]
}

/**
 * Single STOMP-over-SockJS connection to the notification gateway. Subscribes to live price ticks
 * per symbol and to fills, exposing both as state. The connection is torn down on unmount
 * (react-best-practices §15.1).
 */
export function useMarketData(symbols: readonly string[], token: string | null): MarketData {
  const [prices, setPrices] = useState<Record<string, number>>({})
  const [fills, setFills] = useState<Fill[]>([])
  const symbolsKey = symbols.join(',')

  useEffect(() => {
    setPrices({})
    setFills([])
    if (!token) {
      return
    }
    let active = true
    getRecentFills(token)
      .then((recent) => {
        if (active) {
          setFills((prev) => mergeRecentFills([...prev, ...recent]))
        }
      })
      .catch(() => {
        // transient; live WS updates still populate the panel
      })
    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 2000,
    })

    client.onConnect = () => {
      const subscribedSymbols = symbolsKey.length > 0 ? symbolsKey.split(',') : []
      subscribedSymbols.forEach((symbol) => {
        client.subscribe(`/topic/prices.${symbol}`, (message) => {
          const tick = JSON.parse(message.body) as PriceTick
          setPrices((prev) => ({ ...prev, [tick.symbol]: tick.price }))
        })
      })
      client.subscribe('/user/queue/fills', (message) => {
        const fill = JSON.parse(message.body) as Fill
        setFills((prev) => mergeRecentFills([fill, ...prev]))
      })
    }

    client.activate()
    return () => {
      active = false
      void client.deactivate()
    }
  }, [symbolsKey, token])

  return { prices, fills }
}

function mergeRecentFills(fills: Fill[]): Fill[] {
  const byOrderId = new Map<string, Fill>()
  fills
    .map((fill, index) => ({ fill, index }))
    .sort((left, right) => {
      const byTimestamp = right.fill.filledAt.localeCompare(left.fill.filledAt)
      if (byTimestamp !== 0) {
        return byTimestamp
      }
      const leftSequence = left.fill.sequenceId ?? Number.MIN_SAFE_INTEGER
      const rightSequence = right.fill.sequenceId ?? Number.MIN_SAFE_INTEGER
      if (leftSequence !== rightSequence) {
        return rightSequence - leftSequence
      }
      return left.index - right.index
    })
    .forEach(({ fill }) => {
      if (!byOrderId.has(fill.orderId)) {
        byOrderId.set(fill.orderId, fill)
      }
    })
  return Array.from(byOrderId.values()).slice(0, 20)
}
