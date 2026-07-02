/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// Dev proxy: the browser talks to a single origin; each /api/* path and /ws are forwarded to the
// matching backend service (their default local ports). In the container, nginx does the same.
const stripApi = (path: string) => path.replace(/^\/api/, '')

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api/auth': { target: 'http://localhost:8082', changeOrigin: true, rewrite: stripApi },
      '/api/orders': { target: 'http://localhost:8080', changeOrigin: true, rewrite: stripApi },
      '/api/portfolio': { target: 'http://localhost:8083', changeOrigin: true, rewrite: stripApi },
      '/api/funds': { target: 'http://localhost:8083', changeOrigin: true, rewrite: stripApi },
      '/api/prices': { target: 'http://localhost:8081', changeOrigin: true, rewrite: stripApi },
      '/ws': { target: 'http://localhost:8084', changeOrigin: true, ws: true },
    },
  },
  // Vitest unit tests (jsdom). WebdriverIO component tests (*.wdio.tsx) run in a real browser and
  // are excluded here. `vite build` ignores this block.
  test: {
    environment: 'jsdom',
    // The default worker pool has hung on shutdown in this repo after successful test completion.
    // Use a single forked worker as the stable default so `npm run test:unit` exits cleanly.
    pool: 'forks',
    maxWorkers: 1,
    setupFiles: ['./vitest.setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    exclude: ['**/*.wdio.tsx', 'node_modules', 'dist'],
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/**/*.test.{ts,tsx}', 'src/**/*.wdio.tsx', 'src/main.tsx', 'src/vite-env.d.ts'],
    },
  },
})
