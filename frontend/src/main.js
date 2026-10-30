import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

import App from './App.vue'
import router from './router'

/**
 * 前端入口骨架（ENG-007 任务①）。
 *
 * ★ 四层栈的就位点：
 *   · Vue3        → createApp(App)
 *   · Pinia       → createPinia()（状态桶见 src/stores/app.js）
 *   · Element Plus→ ElementPlus 全量注册 + 中文语言包
 *   · ECharts     → 见 src/components/HealthChart.vue
 */
const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
