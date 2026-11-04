/// <reference types="vitest/config" />
import path from 'node:path'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig, type Plugin } from 'vite'

// CSP du build (le dev en est exempt : Vite y injecte un script inline). Tout est servi par la même origine.
// Limite l'effet d'un XSS sur la session en localStorage, d'autant que ce portail montre des données personnelles.
const CSP = [
  "default-src 'self'",
  "script-src 'self'",
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: blob:",
  "object-src 'none'",
  "base-uri 'self'",
  "form-action 'self'",
].join('; ')

const csp = (): Plugin => ({
  name: 'csp',
  apply: 'build',
  transformIndexHtml: () => [{ tag: 'meta', attrs: { 'http-equiv': 'Content-Security-Policy', content: CSP }, injectTo: 'head-prepend' }],
})

// /v1 relayé vers Prism (npm run mock, port 4014) ou le back-end réel : API_URL=http://localhost:8080 npm run dev
// Sur le mock, la connexion OTP renvoie l'exemple de Session au rôle ADMIN (Prefer: example=admin).
export default defineConfig({
  plugins: [react(), tailwindcss(), csp()],
  resolve: { alias: { '@': path.resolve(import.meta.dirname, './src') } },
  server: {
    port: 5179,
    proxy: {
      '/v1': {
        target: process.env.API_URL ?? 'http://127.0.0.1:4014',
        configure: (proxy) =>
          proxy.on('proxyReq', (req) => {
            if (!process.env.API_URL && req.path.startsWith('/v1/auth/otp/verify')) req.setHeader('Prefer', 'example=admin')
          }),
      },
    },
  },
  // userEvent tape caractère par caractère : les formulaires longs dépassent les 5 s par défaut sur une machine chargée.
  test: { environment: 'jsdom', setupFiles: ['./src/test-setup.ts'], testTimeout: 20_000 },
})
