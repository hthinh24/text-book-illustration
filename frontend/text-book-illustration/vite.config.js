import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'
import { fileURLToPath } from 'url'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const rootDir = import.meta.dirname || path.dirname(fileURLToPath(import.meta.url))
  const envDir = path.resolve(rootDir, '../..')
  const env = loadEnv(mode, envDir, '')
  const frontendPort = parseInt(env.FRONTEND_PORT || '5173', 10)
  const serverPort = env.SERVER_PORT || '8080'
  const proxyTarget = env.VITE_PROXY_TARGET || `http://localhost:${serverPort}`

  return {
    envDir: '../..',
    plugins: [react()],
    server: {
      port: frontendPort,
      proxy: {
        '/api': proxyTarget,
      },
    },
    test: {
      globals: true,
      environment: 'jsdom',
      setupFiles: './src/test/setup.js',
    },
  }
})
