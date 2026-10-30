<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAppStore } from '../stores/app'
import { errText } from '../api'

/**
 * 登录 / 注册页（`ENG-009` 任务① 的前端落点）。
 * ★ 只调 `/api/auth/register` 与 `/api/auth/login`，无任何 AI 调用。
 */
const app = useAppStore()
const router = useRouter()
const mode = ref('login')
const busy = ref(false)
const form = reactive({
  username: '',
  password: '',
  nickname: '',
  email: ''
})

async function submit() {
  busy.value = true
  try {
    if (mode.value === 'login') {
      await app.login({ username: form.username, password: form.password })
      ElMessage.success(`欢迎，${app.displayName}`)
      router.push('/courses')
    } else {
      await app.register({
        username: form.username,
        password: form.password,
        nickname: form.nickname || undefined,
        email: form.email || undefined
      })
      ElMessage.success('注册成功，请登录')
      mode.value = 'login'
    }
  } catch (e) {
    ElMessage.error(errText(e))
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <el-card style="max-width: 460px; margin: 40px auto">
    <template #header>
      <strong>{{ mode === 'login' ? '登录' : '注册' }}</strong>
      <el-tag type="info" size="small" style="margin-left: 8px">ENG-009 · MVP 一</el-tag>
    </template>

    <el-form label-width="72px" @submit.prevent="submit">
      <el-form-item label="用户名">
        <el-input v-model="form.username" placeholder="学号或工号（4~32 位）" />
      </el-form-item>
      <el-form-item label="密码">
        <el-input v-model="form.password" type="password" show-password placeholder="至少 6 位" />
      </el-form-item>
      <template v-if="mode === 'register'">
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" placeholder="选填，默认与用户名相同" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="选填" />
        </el-form-item>
      </template>
      <el-form-item>
        <el-button type="primary" :loading="busy" @click="submit">
          {{ mode === 'login' ? '登录' : '注册' }}
        </el-button>
        <el-button text @click="mode = mode === 'login' ? 'register' : 'login'">
          {{ mode === 'login' ? '没有账号？去注册' : '已有账号？去登录' }}
        </el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>
