import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    rollupOptions: {
      output: {
        /* 大体积依赖独立分包，避免单个 chunk 过大 */
        manualChunks(id: string) {
          if (id.includes('node_modules/echarts') || id.includes('node_modules/zrender')) {
            return 'echarts'
          }
          if (
            id.includes('node_modules/element-plus') ||
            id.includes('node_modules/@element-plus')
          ) {
            return 'element-plus'
          }
          if (id.includes('node_modules/vue') || id.includes('node_modules/pinia')) {
            return 'vue-vendor'
          }
        },
      },
    },
  },
})
