import '@testing-library/jest-dom/vitest'

import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

// With explicit imports (globals disabled) RTL does not auto-register cleanup, so do it here —
// otherwise renders accumulate across tests and queries find duplicate elements.
afterEach(() => cleanup())
