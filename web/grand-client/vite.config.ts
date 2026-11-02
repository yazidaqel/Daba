/// <reference types="vitest/config" />
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  server: {
    port: 5177,
    // Prism (npm run mock) par défaut ; DABA_API=http://localhost:8080 pour le vrai back-end
    proxy: { '/v1': process.env.DABA_API ?? 'http://localhost:4013' },
  },
  build: { chunkSizeWarningLimit: 1100 }, // MapLibre (~1 Mo) : chunk à part, chargé avec les pages à carte
  test: { environment: 'jsdom', setupFiles: ['./src/test-setup.ts'] },
})
