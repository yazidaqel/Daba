/// <reference types="vitest/config" />
import path from 'node:path'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig, type Plugin } from 'vite'

// CSP du build (le dev en est exempt : Vite y injecte un script inline). Tout est servi par la même origine ;
// 'wasm-unsafe-eval' pour le lecteur QR zxing. Limite l'effet d'un XSS sur la session en localStorage.
const CSP = [
  "default-src 'self'",
  "script-src 'self' 'wasm-unsafe-eval'",
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

export default defineConfig({
  plugins: [react(), tailwindcss(), csp()],
  resolve: { alias: { '@': path.resolve(import.meta.dirname, './src') } },
  // /v1 relayé vers Prism (npm run mock, port 4012 : 4010 est souvent pris par les autres portails) ou le back-end réel : API_URL=http://localhost:8080 npm run dev
  // Sur le mock, la connexion OTP renvoie l'exemple de Session au rôle GROSSISTE (Prefer: example=grossiste).
  server: {
    port: 5175,
    proxy: {
      '/v1': {
        target: process.env.API_URL ?? 'http://127.0.0.1:4012',
        configure: (proxy) =>
          proxy.on('proxyReq', (req) => {
            if (!process.env.API_URL && req.path.startsWith('/v1/auth/otp/verify')) req.setHeader('Prefer', 'example=grossiste')
          }),
      },
    },
  },
  test: { environment: 'jsdom', setupFiles: ['./src/test-setup.ts'] },
})
