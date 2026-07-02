import { useActionState, useId, useState } from 'react'

import { register } from '../api/client'
import type { Session } from '../types'
import { passwordsMatch } from './passwordsMatch'

// Friendlier copy for the statuses the API returns; falls back to the raw error otherwise.
const STATUS_MESSAGES: Record<number, string> = {
  409: 'That username is already taken.',
  400: 'Please check your details and try again.',
}

/**
 * Self-service registration. Native HTML5 validation (required / type=email / minLength / pattern)
 * covers per-field rules; the confirm-password match is the one rule HTML5 can't express, so it is
 * checked here. Errors are returned as state (never thrown), mirroring OrderTicket (§4.1).
 */
export function Register({ onRegistered }: { onRegistered: (session: Session) => void }) {
  const usernameId = useId()
  const emailId = useId()
  const passwordId = useId()
  const confirmId = useId()
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')

  const [error, submitAction, pending] = useActionState(
    async (_previous: string | null, formData: FormData) => {
      const pwd = String(formData.get('password'))
      const cnf = String(formData.get('confirmPassword'))
      if (!passwordsMatch(pwd, cnf)) {
        return 'Passwords do not match.'
      }
      try {
        onRegistered(
          await register(String(formData.get('username')), String(formData.get('email')), pwd),
        )
        return null
      } catch (cause) {
        const status = Number((cause as Error).message.match(/\((\d+)\)/)?.[1])
        return STATUS_MESSAGES[status] ?? (cause as Error).message
      }
    },
    null,
  )

  const mismatch = confirm.length > 0 && !passwordsMatch(password, confirm)

  return (
    <form className="login" action={submitAction}>
      <h1>Create account</h1>

      <label htmlFor={usernameId}>Username</label>
      <input
        id={usernameId}
        name="username"
        required
        minLength={3}
        maxLength={20}
        pattern="[A-Za-z0-9_]+"
        autoComplete="username"
      />

      <label htmlFor={emailId}>Email</label>
      <input id={emailId} name="email" type="email" required autoComplete="email" />

      <label htmlFor={passwordId}>Password</label>
      <input
        id={passwordId}
        name="password"
        type="password"
        required
        minLength={8}
        autoComplete="new-password"
        value={password}
        onChange={(event) => setPassword(event.target.value)}
      />

      <label htmlFor={confirmId}>Confirm password</label>
      <input
        id={confirmId}
        name="confirmPassword"
        type="password"
        required
        minLength={8}
        autoComplete="new-password"
        value={confirm}
        onChange={(event) => setConfirm(event.target.value)}
      />

      <button type="submit" disabled={pending || mismatch}>
        {pending ? 'Creating…' : 'Register'}
      </button>

      {mismatch ? <p className="error">Passwords do not match.</p> : null}
      {error != null ? <p className="error">{error}</p> : null}
    </form>
  )
}
