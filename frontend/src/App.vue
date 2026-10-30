<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAppStore } from './stores/app'
import { useCourseStore } from './stores/course'

/**
 * 应用根组件（`ENG-007` 骨架打底，`ENG-009` 补上导航 / 登录态）。
 *
 * ★ 三件事：① 顶栏（产品名 + 登录态 + 登出）；② 导航到 MVP 一 的三个业务面；
 *   ③ 启动时恢复登录态（`fetchMe`）。
 * ★ 骨架期的 `probeBackend` **保留仍在用**（顶栏那个"后端探活"标签就是它）；
 *   ECharts 就位检查搬到首页 `HomeView`，没有删。
 */
const app = useAppStore()
const courseStore = useCourseStore()
const router = useRouter()
const backend = ref('未探测')

onMounted(async () => {
  backend.value = await app.probeBackend()
  await app.fetchMe()
})

async function doLogout() {
  await app.logout()
  courseStore.$reset()
  router.push('/login')
}
</script>

<template>
  <el-container style="min-height: 100vh">
    <el-header style="display: flex; align-items: center; gap: 12px">
      <strong>智课伴毕设</strong>
      <el-tag type="info" size="small">MVP 一（ENG-009）</el-tag>
      <el-tag size="small">后端探活：{{ backend }}</el-tag>

      <el-menu
        mode="horizontal"
        :ellipsis="false"
        style="flex: 1; border-bottom: none"
        router
        :default-active="$route.path"
      >
        <el-menu-item index="/">骨架自查</el-menu-item>
        <el-menu-item index="/courses">课程 / 资料</el-menu-item>
        <el-menu-item index="/questions">问答社区</el-menu-item>
      </el-menu>

      <el-tag v-if="app.loggedIn" type="success" size="small">{{ app.displayName }}</el-tag>
      <el-button v-if="app.loggedIn" size="small" @click="doLogout">登出</el-button>
      <el-button v-else size="small" type="primary" @click="$router.push('/login')">登录</el-button>
    </el-header>

    <el-main>
      <router-view />
    </el-main>
  </el-container>
</template>
