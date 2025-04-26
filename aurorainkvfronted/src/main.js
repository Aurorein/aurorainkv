import { createApp } from 'vue'

import App from './App.vue'
import router from './router/index.js'
import store from './store/index.js';
import 'virtual:uno.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

const app = createApp(App)

// 注册element-icon
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
    app.component(key, component)
  }
window.global = window;


app.use(router)
app.use(store)
app.mount('#app')
