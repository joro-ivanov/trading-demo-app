import type { Fill, OrderRequest, OrderResponse, Position, PriceTick, Session } from '../types'

type JsonRecord = Record<string, unknown>

function authHeaders(token: string): HeadersInit {
  return { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` }
}

function isRecord(value: unknown): value is JsonRecord {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function readString(source: JsonRecord, field: string): string {
  const value = source[field]
  if (typeof value !== 'string') {
    throw new Error(`Invalid response: ${field} must be a string`)
  }
  return value
}

function readNumber(source: JsonRecord, field: string): number {
  const value = source[field]
  if (typeof value !== 'number') {
    throw new Error(`Invalid response: ${field} must be a number`)
  }
  return value
}

function readNullableNumber(source: JsonRecord, field: string): number | null {
  const value = source[field]
  if (value === null) {
    return null
  }
  if (typeof value !== 'number') {
    throw new Error(`Invalid response: ${field} must be a number or null`)
  }
  return value
}

async function readJson(response: Response): Promise<unknown> {
  return response.json() as Promise<unknown>
}

async function readObject(response: Response): Promise<JsonRecord> {
  const value = await readJson(response)
  if (!isRecord(value)) {
    throw new Error('Invalid response: expected object')
  }
  return value
}

async function readArray(response: Response): Promise<unknown[]> {
  const value = await readJson(response)
  if (!Array.isArray(value)) {
    throw new Error('Invalid response: expected array')
  }
  return value
}

function parseSession(value: JsonRecord): Session {
  return {
    token: readString(value, 'token'),
    username: readString(value, 'username'),
  }
}

function parseOrder(value: JsonRecord): OrderResponse {
  return {
    id: readString(value, 'id'),
    symbol: readString(value, 'symbol'),
    side: readString(value, 'side') as OrderResponse['side'],
    quantity: readNumber(value, 'quantity'),
    type: readString(value, 'type') as OrderResponse['type'],
    limitPrice: readNullableNumber(value, 'limitPrice'),
    stopPrice: readNullableNumber(value, 'stopPrice'),
    status: readString(value, 'status'),
  }
}

function parsePosition(value: JsonRecord): Position {
  return {
    symbol: readString(value, 'symbol'),
    quantity: readNumber(value, 'quantity'),
    avgPrice: readNumber(value, 'avgPrice'),
    marketPrice: readNullableNumber(value, 'marketPrice'),
    unrealizedPnl: readNumber(value, 'unrealizedPnl'),
  }
}

function parseFill(value: JsonRecord): Fill {
  return {
    orderId: readString(value, 'orderId'),
    symbol: readString(value, 'symbol'),
    side: readString(value, 'side') as Fill['side'],
    quantity: readNumber(value, 'quantity'),
    price: readNumber(value, 'price'),
    filledAt: readString(value, 'filledAt'),
    sequenceId: readNullableNumber(value, 'sequenceId'),
  }
}

function parsePrice(value: JsonRecord): PriceTick {
  return {
    symbol: readString(value, 'symbol'),
    price: readNumber(value, 'price'),
  }
}

export async function login(username: string, password: string): Promise<Session> {
  const response = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
  if (!response.ok) {
    throw new Error(`Login failed (${response.status})`)
  }
  return parseSession(await readObject(response))
}

// confirmPassword is a client-side concern only — it is validated in the form and never sent here.
export async function register(username: string, email: string, password: string): Promise<Session> {
  const response = await fetch('/api/auth/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, email, password }),
  })
  if (!response.ok) {
    throw new Error(`Registration failed (${response.status})`)
  }
  return parseSession(await readObject(response))
}

export async function placeOrder(token: string, order: OrderRequest): Promise<OrderResponse> {
  const response = await fetch('/api/orders', {
    method: 'POST',
    headers: authHeaders(token),
    // JSON.stringify drops undefined limitPrice/stopPrice, so MARKET orders send neither.
    body: JSON.stringify(order),
  })
  if (!response.ok) {
    throw new Error(`Order rejected (${response.status})`)
  }
  return parseOrder(await readObject(response))
}

export async function getPortfolio(token: string): Promise<Position[]> {
  const response = await fetch('/api/portfolio', { headers: authHeaders(token) })
  if (!response.ok) {
    throw new Error(`Failed to load portfolio (${response.status})`)
  }
  return (await readArray(response)).map((item) => {
    if (!isRecord(item)) {
      throw new Error('Invalid response: portfolio item must be an object')
    }
    return parsePosition(item)
  })
}

export async function getRecentFills(token: string): Promise<Fill[]> {
  const response = await fetch('/api/portfolio/fills', { headers: authHeaders(token) })
  if (!response.ok) {
    throw new Error(`Failed to load recent fills (${response.status})`)
  }
  return (await readArray(response)).map((item) => {
    if (!isRecord(item)) {
      throw new Error('Invalid response: fill item must be an object')
    }
    return parseFill(item)
  })
}

export async function getBalance(token: string): Promise<number> {
  const response = await fetch('/api/funds/balance', { headers: authHeaders(token) })
  if (!response.ok) {
    throw new Error(`Failed to load balance (${response.status})`)
  }
  return readNumber(await readObject(response), 'cash')
}

export async function deposit(token: string, amount: number): Promise<number> {
  return moveFunds('/api/funds/deposit', token, amount, 'Deposit')
}

export async function withdraw(token: string, amount: number): Promise<number> {
  return moveFunds('/api/funds/withdraw', token, amount, 'Withdraw')
}

async function moveFunds(path: string, token: string, amount: number, label: string): Promise<number> {
  const response = await fetch(path, {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify({ amount }),
  })
  if (!response.ok) {
    throw new Error(`${label} failed (${response.status})`)
  }
  return readNumber(await readObject(response), 'cash')
}

export async function getPrice(token: string, symbol: string): Promise<PriceTick | null> {
  const response = await fetch(`/api/prices/${symbol}`, { headers: authHeaders(token) })
  if (response.status === 404) {
    return null
  }
  if (!response.ok) {
    throw new Error(`Failed to load price for ${symbol} (${response.status})`)
  }
  return parsePrice(await readObject(response))
}
