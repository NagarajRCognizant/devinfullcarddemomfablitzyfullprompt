import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Local only: the dev server binds to the loopback interface and proxies the API calls to the
// Spring Cloud Gateway so the browser talks to a single origin, as the terminal talked to one CICS
// region regardless of which program served the screen.
export default defineConfig({
  plugins: [react()],
  server: {
    host: '127.0.0.1',
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8090',
        changeOrigin: false,
      },
    },
  },
  preview: {
    host: '127.0.0.1',
    port: 4173,
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./vitest.setup.ts'],
  },
});
