import { defineStore } from 'pinia'
import axios from 'axios'
import api, { TOKEN_KEY } from '../api'

/**
 * 应用级状态桶（`ENG-007` 骨架打底，`ENG-009` 任务① 补上登录态）。
 *
 * ★ 保留 `probeBackend`（骨架期就有的探活动作，`App.vue` / `HealthView.vue` 都在用）；
 *   新增 `user` / `register` / `login` / `logout` / `fetchMe`。
 */
export const useAppStore = defineStore('app', {
  state: () => ({
    backendStatus: '未探测',
    loading: false,
    /** 当前登录用户（`null` = 未登录）。 */
    user: null
  }),
  getters: {
    loggedIn: (s) => !!s.user,
    displayName: (s) => s.user?.nickname || s.user?.username || '未登录'
  },
  actions: {
    /** 打后端骨架的探活端点（backend/src/main/java/.../controller/HealthController.java）。 */
    async probeBackend() {
      this.loading = true
      try {
        const { data } = await axios.get('/api/health', { timeout: 3000 })
        this.backendStatus = data?.status === 'UP' ? 'UP' : `未知（${JSON.stringify(data)}）`
      } catch (e) {
        // ★ 后端没起不算错误，如实记下即可（前端可离线单独起）
        this.backendStatus = `不可达（${e?.code || e?.message || 'unknown'}）`
      } finally {
        this.loading = false
      }
      return this.backendStatus
    },

    /** 注册（成功后不自动登录，交用户显式登录）。 */
    async register(payload) {
      const { data } = await api.post('/auth/register', payload)
      return data
    },

    /** 登录：拿令牌 → 落 localStorage → 记下当前用户。 */
    async login(payload) {
      const { data } = await api.post('/auth/login', payload)
      if (data?.token) {
        localStorage.setItem(TOKEN_KEY, data.token)
      }
      this.user = data?.user ?? null
      return data
    },

    /** 拉当前用户（页面刷新后恢复登录态用）。 */
    async fetchMe() {
      if (!localStorage.getItem(TOKEN_KEY)) {
        this.user = null
        return null
      }
      try {
        const { data } = await api.get('/auth/me')
        this.user = data
        return data
      } catch {
        localStorage.removeItem(TOKEN_KEY)
        this.user = null
        return null
      }
    },

    /** 登出：通知后端吊销令牌 → 清本地状态（后端失败也照样清，避免"退不出去"）。 */
    async logout() {
      try {
        await api.post('/auth/logout')
      } catch {
        // 幂等：本地照样清
      }
      localStorage.removeItem(TOKEN_KEY)
      this.user = null
    }
  }
})
