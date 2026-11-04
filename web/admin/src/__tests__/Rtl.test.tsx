import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { expect, test, vi } from 'vitest'

vi.hoisted(() => {
  localStorage.setItem('daba.langue', 'ar')
  // Choix « mot de passe » déjà retenu sur ce navigateur, pour vérifier ses libellés et ses attributs.
  localStorage.setItem('daba.connexion', 'motDePasse')
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
  expect(screen.getByRole('radio', { name: 'كلمة المرور' })).toBeChecked()
  expect(screen.getByRole('radio', { name: 'رمز عبر رسالة قصيرة' })).not.toBeChecked()
  expect(screen.getByLabelText('كلمة المرور', { selector: '#mdp' })).toHaveAttribute('autocomplete', 'current-password')
  expect(screen.getByRole('button', { name: 'دخول' })).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'نسيت كلمة المرور؟' })).toBeInTheDocument()
  // Le numéro reste saisi de gauche à droite.
  expect(screen.getByLabelText('رقم الهاتف')).toHaveAttribute('dir', 'ltr')
})
