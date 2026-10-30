<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useCourseStore } from '../stores/course'
import { errText } from '../api'

/**
 * 课程 / 资料页（`ENG-009` 任务② 的前端落点）。
 * ★ 只做"最小 CRUD / 列表"：列表、新建课程、按课程看资料、登记资料元数据。
 *   **不做上传落地、不解析、不切片、不向量化**（边界 (a)）。
 */
const store = useCourseStore()
const busy = ref(false)
const selected = ref(null)
const courseForm = reactive({ name: '', semester: '2026-2027-1' })
const materialForm = reactive({ name: '', type: 'PDF', chapter: '' })

async function refresh() {
  busy.value = true
  try {
    await store.loadCourses()
  } catch (e) {
    ElMessage.error(errText(e))
  } finally {
    busy.value = false
  }
}

async function createCourse() {
  if (!courseForm.name) return ElMessage.warning('课程名称不能为空')
  try {
    await store.createCourse({ name: courseForm.name, semester: courseForm.semester })
    courseForm.name = ''
    ElMessage.success('课程已创建')
  } catch (e) {
    ElMessage.error(errText(e))
  }
}

async function pick(row) {
  selected.value = row
  try {
    await store.loadMaterials(row.id)
  } catch (e) {
    ElMessage.error(errText(e))
  }
}

async function createMaterial() {
  if (!selected.value) return ElMessage.warning('请先选一门课程')
  if (!materialForm.name) return ElMessage.warning('资料名称不能为空')
  try {
    await store.createMaterial({
      courseId: selected.value.id,
      name: materialForm.name,
      type: materialForm.type,
      chapter: materialForm.chapter || undefined,
      size: 0
    })
    materialForm.name = ''
    ElMessage.success('资料元数据已登记（本单不做上传落地与解析）')
  } catch (e) {
    ElMessage.error(errText(e))
  }
}

onMounted(refresh)
</script>

<template>
  <el-row :gutter="16">
    <el-col :span="13">
      <el-card>
        <template #header>
          <strong>课程列表</strong>
          <el-tag type="info" size="small" style="margin-left: 8px">任务② / FR-2 子集</el-tag>
          <el-button size="small" style="float: right" :loading="busy" @click="refresh">刷新</el-button>
        </template>
        <el-table :data="store.courses" height="320" highlight-current-row @current-change="pick">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="name" label="课程名称" />
          <el-table-column prop="semester" label="学期" width="130" />
          <el-table-column prop="inviteCode" label="邀请码" width="100" />
        </el-table>
        <el-divider />
        <el-form :inline="true">
          <el-form-item label="课程名">
            <el-input v-model="courseForm.name" placeholder="如：数据结构" style="width: 150px" />
          </el-form-item>
          <el-form-item label="学期">
            <el-input v-model="courseForm.semester" style="width: 140px" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="createCourse">新建课程</el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </el-col>

    <el-col :span="11">
      <el-card>
        <template #header>
          <strong>课程资料</strong>
          <el-tag v-if="selected" size="small" style="margin-left: 8px">{{ selected.name }}</el-tag>
          <el-tag v-else type="warning" size="small" style="margin-left: 8px">未选课程</el-tag>
        </template>
        <el-table :data="store.materials" height="240">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="name" label="名称" />
          <el-table-column prop="type" label="类型" width="80" />
          <el-table-column prop="chapter" label="章节" width="100" />
        </el-table>
        <el-divider />
        <el-form :inline="true">
          <el-form-item label="名称">
            <el-input v-model="materialForm.name" placeholder="如：第1章.pdf" style="width: 140px" />
          </el-form-item>
          <el-form-item label="类型">
            <el-select v-model="materialForm.type" style="width: 100px">
              <el-option v-for="t in ['PDF', 'PPT', 'DOC', 'VIDEO']" :key="t" :label="t" :value="t" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="createMaterial">登记资料</el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </el-col>
  </el-row>
</template>
