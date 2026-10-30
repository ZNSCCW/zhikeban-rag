import axios from 'axios'

/**
 * API 客户端（`ENG-009` 任务①~③ 前端侧）。
 *
 * ★ 令牌放 `localStorage`（**MVP 一 的最小闭环**）—— 真正的"刷新令牌 / 会话过期 /
 *   多标签同步"属后续编码单；这里只保证"登录后请求带得上令牌、登出后清掉"。
 * ★ 401 拦截：清令牌并回登录页（不弹错、不循环重试）。
 */
export const TOKEN_KEY = 'zhikeban.token'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers = config.headers ?? {}
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (resp) => resp,
  (err) => {
    if (err?.response?.status === 401) {
      localStorage.removeItem(TOKEN_KEY)
    }
    return Promise.reject(err)
  }
)

/** 人读的错误消息（后端 `ApiExceptionHandler` 统一给 `message`）。 */
export const errText = (e) =>
  e?.response?.data?.message || e?.response?.data?.error || e?.message || '请求失败'

export default api
