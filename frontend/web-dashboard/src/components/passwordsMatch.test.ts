import { describe, expect, it } from 'vitest'

import { passwordsMatch } from './passwordsMatch'

describe('passwordsMatch', () => {
  it('is true when both are equal and non-empty', () => {
    expect(passwordsMatch('s3cret-pw', 's3cret-pw')).toBe(true)
  })

  it('is false when they differ', () => {
    expect(passwordsMatch('s3cret-pw', 's3cret-pX')).toBe(false)
  })

  it('is false when both are empty', () => {
    expect(passwordsMatch('', '')).toBe(false)
  })
})
