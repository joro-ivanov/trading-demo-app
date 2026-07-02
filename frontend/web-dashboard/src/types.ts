export type Side = 'BUY' | 'SELL'

export type OrderType = 'MARKET' | 'LIMIT' | 'STOP'

export interface Session {
  token: string
  username: string
}

export interface OrderRequest {
  symbol: string
  side: Side
  quantity: number
  type: OrderType
  limitPrice?: number
  stopPrice?: number
}

export interface OrderResponse {
  id: string
  symbol: string
  side: Side
  quantity: number
  type: OrderType
  limitPrice: number | null
  stopPrice: number | null
  status: string
}

export interface Position {
  symbol: string
  quantity: number
  avgPrice: number
  marketPrice: number | null
  unrealizedPnl: number
}

export interface PriceTick {
  symbol: string
  price: number
}

export interface Fill {
  orderId: string
  symbol: string
  side: Side
  quantity: number
  price: number
  filledAt: string
  sequenceId: number | null
}

export const SYMBOLS = ['AAPL', 'MSFT', 'GOOG'] as const
