import { useId, useState } from 'react'

import { login } from '../api/client'
import type { Session } from '../types'

/** Logs in against auth-service (demo users; password "password") and yields a real JWT session. */
export function Login({ onLogin }: { onLogin: (session: Session) => void }) {
  const usernameId = useId()
  const passwordId = useId()
  const [username, setUsername] = useState('demo')
  const [password, setPassword] = useState('password')
  const [error, setError] = useState<string | null>(null)

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    try {
      onLogin(await login(username, password))
    } catch {
      setError('Login failed — try demo / password')
    }
  }

  return (
    <form className="login" onSubmit={submit}>
      <h1>Trading Dashboard</h1>
      <label htmlFor={usernameId}>Username</label>
      <input
        id={usernameId}
        value={username}
        autoComplete="username"
        onChange={(event) => setUsername(event.target.value)}
      />
      <label htmlFor={passwordId}>Password</label>
      <input
        id={passwordId}
        type="password"
        value={password}
        autoComplete="current-password"
        onChange={(event) => setPassword(event.target.value)}
      />
      <button type="submit">Enter</button>
      {error != null ? <p className="error">{error}</p> : null}
    </form>
  )
}
