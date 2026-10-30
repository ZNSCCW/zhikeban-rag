import { createRouter, createWebHistory } from 'vue-router'
import { TOKEN_KEY } from '../api'

/**
 * 路由表（`ENG-007` 骨架打底，`ENG-009` 补上 MVP 一 的三个业务面）。
 *
 * ★ 四条路由对应本单落点：
 *   `/`          骨架自查（探活 + ECharts），保留不动
 *   `/login`     任务① 认证/用户（登录 / 注册）
 *   `/courses`   任务② 课程/资料（最小 CRUD / 列表）
 *   `/questions` 任务③ 问答·社区面（发帖 / 回复 / 采纳）
 *
 * ★ 路由级守卫只做"有没有令牌"这一层粗判；真正的授权在后端
 *   （`SecurityConfig` 的 `authenticated()`）—— 前端**不假装自己是安全边界**。
 * ★ 这里**故意不 import App.vue**：根组件由 main.js 直接挂载，路由表若再引它就会形成
 *   循环依赖（App.vue → router → App.vue）。
 * ★ 路由懒加载（`() => import(...)`）⇒ **没有**任何一条路由指向 AI 问答 / 向量检索面
 *   （`ENG-009` 边界 (b)）。
 */
const routes = [
  { path: '/', name: 'home', component: () => import('../views/HomeView.vue') },
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { public: true } },
  { path: '/courses', name: 'courses', component: () => import('../views/CoursesView.vue') },
  { path: '/questions', name: 'questions', component: () => import('../views/QuestionsView.vue') }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  if (to.meta?.public || to.name === 'home') return true
  return localStorage.getItem(TOKEN_KEY) ? true : { name: 'login' }
})

export default router
