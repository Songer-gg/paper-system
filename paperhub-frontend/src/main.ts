import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import '@/styles/index.css'
import App from './App.vue'
import router from './router'
import { reportActionError } from '@/utils/errors'

const app = createApp(App)
app.config.errorHandler = reportActionError

/* 全局注册 Element Plus 图标，便于通过 <component :is> 动态渲染 */
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')
window.addEventListener('paperhub:unauthorized', () => {
  void router.replace({ path: '/login' }).catch(reportActionError)
})

window.addEventListener('unhandledrejection', (event) => {
  event.preventDefault()
  reportActionError(event.reason)
})
