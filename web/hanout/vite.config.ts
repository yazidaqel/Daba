/// <reference types="vitest/config" />
import { fileURLToPath } from 'node:url'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig, type Plugin, type ProxyOptions } from 'vite'
import { VitePWA } from 'vite-plugin-pwa'

/** Précharge la police 400 de la langue courante seulement (même règle que langueInitiale dans src/i18n.ts). */
const prechargerPolice: Plugin = {
  name: 'daba-precharger-police',
  transformIndexHtml: {
    order: 'post',
    handler(_, ctx) {
      const url = (motif: string) => Object.keys(ctx.bundle ?? {}).find((f) => f.includes(motif))
      const latin = url('ibm-plex-sans-latin-400'), arabe = url('ibm-plex-sans-arabic-arabic-400')
      if (!latin || !arabe) return // dev : pas de bundle
      const script = `try{var l=localStorage.getItem('daba.langue')||(navigator.language.indexOf('ar')===0?'ary-Arab':'fr'),
p=document.createElement('link');p.rel='preload';p.as='font';p.type='font/woff2';p.crossOrigin='';
p.href='/'+(l==='ar'||l==='ary-Arab'?'${arabe}':'${latin}');document.head.appendChild(p)}catch(e){}`
      return [{ tag: 'script', children: script, injectTo: 'head' }]
    },
  },
}

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
    prechargerPolice,
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
          {
            // Polices à la première utilisation, pas en précache : un francophone ne télécharge pas l'arabe.
            urlPattern: ({ request }) => request.destination === 'font',
            handler: 'CacheFirst',
            options: { cacheName: 'polices', expiration: { maxEntries: 8 } },
          },
        ],
      },
    }),
  ],
  test: { environment: 'jsdom' },
})
