import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { expect, test, vi } from 'vitest'

vi.hoisted(() => {
  localStorage.setItem('daba.langue', 'ar')
  globalThis.fetch = (async () => Response.json({})) as typeof fetch
})

const { queryClient } = await import('@/api/client')
const { I18nProvider } = await import('@/i18n')
const { default: App } = await import('@/App')

test('en arabe, la page passe en RTL et tous les textes viennent du dictionnaire ar', async () => {
  render(
    <QueryClientProvider client={queryClient}>
      <I18nProvider>
        <App />
      </I18nProvider>
    </QueryClientProvider>,
  )
  expect(await screen.findByText('دخول المسؤول')).toBeInTheDocument()
  expect(document.documentElement).toHaveAttribute('dir', 'rtl')
  expect(document.documentElement).toHaveAttribute('lang', 'ar')
  expect(document.title).toBe('دابا · الإدارة')
  expect(screen.getByRole('button', { name: 'استلام الرمز عبر رسالة قصيرة' })).toBeInTheDocument()
  // Le numéro reste saisi de gauche à droite.
  expect(screen.getByLabelText('رقم الهاتف')).toHaveAttribute('dir', 'ltr')
})
