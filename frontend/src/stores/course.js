import { defineStore } from 'pinia'
import api from '../api'

/**
 * 业务数据桶（`ENG-009` 任务②③：课程 / 资料 / 问答社区面）。
 *
 * ★ 全部走 `/api`（见 `src/api/index.js`，自动带 Bearer 令牌）。
 * ★ **不含任何 AI / 向量调用** —— 本单边界 (a)(b)（`STANDARDS.md` §ENG-009 的 `R` 组）。
 */
export const useCourseStore = defineStore('course', {
  state: () => ({
    courses: [],
    materials: [],
    questions: [],
    /** 当前帖子的回复。 */
    answers: [],
    loading: false
  }),
  actions: {
    async loadCourses() {
      const { data } = await api.get('/courses')
      this.courses = data ?? []
      return this.courses
    },

    async createCourse(payload) {
      const { data } = await api.post('/courses', payload)
      this.courses = [data, ...this.courses]
      return data
    },

    async loadMaterials(courseId) {
      const { data } = await api.get('/materials', { params: courseId ? { courseId } : {} })
      this.materials = data ?? []
      return this.materials
    },

    async createMaterial(payload) {
      const { data } = await api.post('/materials', payload)
      this.materials = [data, ...this.materials]
      return data
    },

    async loadQuestions(courseId) {
      const { data } = await api.get('/questions', { params: courseId ? { courseId } : {} })
      this.questions = data ?? []
      return this.questions
    },

    async createQuestion(payload) {
      const { data } = await api.post('/questions', payload)
      this.questions = [data, ...this.questions]
      return data
    },

    async loadAnswers(questionId) {
      const { data } = await api.get(`/questions/${questionId}/answers`)
      this.answers = data ?? []
      return this.answers
    },

    async reply(questionId, payload) {
      const { data } = await api.post(`/questions/${questionId}/answers`, payload)
      this.answers = [...this.answers, data]
      return data
    },

    async accept(questionId, answerId) {
      const { data } = await api.post(`/questions/${questionId}/accept`, { answerId })
      return data
    }
  }
})
