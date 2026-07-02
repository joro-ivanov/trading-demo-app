import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { Ticker } from './Ticker'

describe('Ticker', () => {
  it('formats known prices as USD and shows a dash for unknown ones', () => {
    render(<Ticker prices={{ AAPL: 190.4 }} />)

    expect(screen.getByText('$190.40')).toBeInTheDocument()
    // MSFT and GOOG have no price → dash for each
    expect(screen.getAllByText('—').length).toBeGreaterThanOrEqual(1)
  })
})
