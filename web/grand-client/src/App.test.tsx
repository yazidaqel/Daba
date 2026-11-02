import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { I18nProvider } from '@/i18n'
import { App } from '@/App'

afterEach(cleanup)

describe('connexion', () => {
  it('passe en arabe et en RTL', () => {
    render(
      <I18nProvider>
        <App />
      </I18nProvider>,
    )
    expect(screen.getByText('Recevoir le code par SMS')).toBeInTheDocument()
    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'ar' } })
    expect(screen.getByText('استلام الرمز عبر رسالة قصيرة')).toBeInTheDocument()
    expect(document.documentElement.dir).toBe('rtl')
    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'ary-Latn' } })
    expect(document.documentElement.dir).toBe('ltr')
  })
})
