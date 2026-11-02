/// <reference types="vitest/config" />
import path from 'node:path'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: { alias: { '@': path.resolve(import.meta.dirname, './src') } },
  // /v1 relayé vers Prism (npm run mock, port 4012 : 4010 est souvent pris par les autres portails) ou le back-end réel : API_URL=http://localhost:8080 npm run dev
  server: { port: 5175, proxy: { '/v1': process.env.API_URL ?? 'http://127.0.0.1:4012' } },
  test: { environment: 'jsdom', setupFiles: ['./src/test-setup.ts'] },
})
