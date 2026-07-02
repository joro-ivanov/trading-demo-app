import { useState } from 'react'

import { Login } from './Login'
import { Register } from './Register'
import type { Session } from '../types'

/** Switches between the login and registration forms; both yield a session the same way. */
export function Auth({ onAuthenticated }: { onAuthenticated: (session: Session) => void }) {
  const [mode, setMode] = useState<'login' | 'register'>('login')

  return (
    <div className="auth">
      {mode === 'login' ? (
        <Login onLogin={onAuthenticated} />
      ) : (
        <Register onRegistered={onAuthenticated} />
      )}
      <p className="auth-switch">
        {mode === 'login' ? (
          <button type="button" className="link" onClick={() => setMode('register')}>
            Need an account? Register
          </button>
        ) : (
          <button type="button" className="link" onClick={() => setMode('login')}>
            Have an account? Log in
          </button>
        )}
      </p>
    </div>
  )
}
