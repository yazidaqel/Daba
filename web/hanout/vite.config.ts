/// <reference types="vitest/config" />
import { fileURLToPath } from 'node:url'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig, type ProxyOptions } from 'vite'
import { VitePWA } from 'vite-plugin-pwa'

// Prism (npm run mock) par défaut ; API_URL=http://localhost:8080 pour le vrai back-end.
// Avec Prism, `Prefer: example=hanout` fait toujours répondre une session hanout à la connexion.
const proxy: Record<string, string | ProxyOptions> = process.env.API_URL
  ? { '/v1': process.env.API_URL }
  : {
      '/v1/auth/otp/verify': { target: 'http://localhost:4010', headers: { Prefer: 'example=hanout' } },
      '/v1': 'http://localhost:4010',
    }

export default defineConfig({
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  server: { proxy },
  preview: { proxy },
  plugins: [
    react(),
    tailwindcss(),
    VitePWA({
      registerType: 'autoUpdate',
      pwaAssets: { config: true },
      manifest: {
        name: 'Daba',
        short_name: 'Daba',
        description: 'Rupture, bons de livraison et solde',
        lang: 'fr',
        start_url: '/',
        display: 'standalone',
        theme_color: '#0f766e',
        background_color: '#ffffff',
      },
      workbox: {
        navigateFallback: '/index.html',
        navigateFallbackDenylist: [/^\/v1\//],
        runtimeCaching: [
          {
            // Dernières données vues, affichées si le réseau ne répond pas (vidé à la déconnexion).
            urlPattern: ({ url, request }) =>
              url.pathname.startsWith('/v1/') && request.method === 'GET' && !url.pathname.startsWith('/v1/liens/'),
            handler: 'NetworkFirst',
            options: { cacheName: 'api', networkTimeoutSeconds: 10, expiration: { maxEntries: 100 } },
          },
          {
            urlPattern: ({ request }) => request.destination === 'image',
            handler: 'CacheFirst',
            options: { cacheName: 'images', expiration: { maxEntries: 200, maxAgeSeconds: 30 * 24 * 3600 } },
          },
        ],
      },
    }),
  ],
  test: { environment: 'jsdom' },
})
