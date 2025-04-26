import { createRouter, createWebHashHistory } from 'vue-router'
import constRoutes from './routes/index.js'

const router = createRouter({
  history: createWebHashHistory(),
  routes: constRoutes,
  scrollBehavior: () => ({ left: 0, top: 0 }),
})

router.beforeEach((to, from, next) => {
  next()
})

export default router
