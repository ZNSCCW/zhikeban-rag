<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAppStore } from '../stores/app'
import { useCourseStore } from '../stores/course'
import { errText } from '../api'

/**
 * 问答·**社区面**页（`ENG-009` 任务③ 的前端落点）：**发帖 / 回复 / 采纳**。
 * ★★ **不做 AI 问答** —— 本页没有任何"智能提问 / 生成回答"入口（边界 (b)）。
 */
const app = useAppStore()
const store = useCourseStore()
const busy = ref(false)
const current = ref(null)
const postForm = reactive({ courseId: null, title: '', content: '' })
const replyText = ref('')

async function refresh() {
  busy.value = true
  try {
    await Promise.all([store.loadCourses(), store.loadQuestions()])
  } catch (e) {
    ElMessage.error(errText(e))
  } finally {
    busy.value = false
  }
}

async function submitPost() {
  if (!postForm.courseId) return ElMessage.warning('请选择课程')
  if (postForm.title.trim().length < 5) return ElMessage.warning('标题至少 5 个字')
  if (postForm.content.trim().length < 10) return ElMessage.warning('正文至少 10 个字')
  try {
    await store.createQuestion({ ...postForm })
    postForm.title = ''
    postForm.content = ''
    ElMessage.success('发帖成功')
  } catch (e) {
    ElMessage.error(errText(e))
  }
}

async function open(row) {
  current.value = row
  try {
    await store.loadAnswers(row.id)
  } catch (e) {
    ElMessage.error(errText(e))
  }
}

async function submitReply() {
  if (!current.value) return ElMessage.warning('请先打开一个帖子')
  if (replyText.value.trim().length < 2) return ElMessage.warning('回复至少 2 个字')
  try {
    await store.reply(current.value.id, { content: replyText.value })
    replyText.value = ''
    ElMessage.success('回复成功')
  } catch (e) {
    ElMessage.error(errText(e))
  }
}

async function accept(answer) {
  if (!current.value) return
  try {
    await store.accept(current.value.id, answer.id)
    current.value.acceptedAnswerId = answer.id
    current.value.status = 1
    answer.isAccepted = 1
    ElMessage.success('已采纳')
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
          <strong>问答社区（发帖 / 回复 / 采纳）</strong>
          <el-tag type="info" size="small" style="margin-left: 8px">任务③ / 只做社区面</el-tag>
          <el-button size="small" style="float: right" :loading="busy" @click="refresh">刷新</el-button>
        </template>
        <el-table :data="store.questions" height="330" highlight-current-row @current-change="open">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="title" label="标题" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'warning'" size="small">
                {{ row.status === 1 ? '已解决' : '待解决' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="answerCount" label="回复" width="70" />
        </el-table>
        <el-divider />
        <el-form label-width="60px">
          <el-form-item label="课程">
            <el-select v-model="postForm.courseId" placeholder="选择课程" style="width: 100%">
              <el-option v-for="c in store.courses" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="标题">
            <el-input v-model="postForm.title" placeholder="至少 5 个字" />
          </el-form-item>
          <el-form-item label="正文">
            <el-input v-model="postForm.content" type="textarea" :rows="3" placeholder="至少 10 个字" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="submitPost">发帖</el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </el-col>

    <el-col :span="11">
      <el-card>
        <template #header>
          <strong>回复</strong>
          <el-tag v-if="current" size="small" style="margin-left: 8px">{{ current.title }}</el-tag>
          <el-tag v-else type="warning" size="small" style="margin-left: 8px">未打开帖子</el-tag>
        </template>
        <el-empty v-if="!current" description="点左侧任一帖子查看回复" :image-size="70" />
        <template v-else>
          <div v-for="a in store.answers" :key="a.id" style="padding: 6px 0; border-bottom: 1px dashed #eee">
            <span style="color: #909399">#{{ a.id }}</span>
            <span style="margin: 0 6px">{{ a.content }}</span>
            <el-tag v-if="a.isAccepted === 1" type="success" size="small">已采纳</el-tag>
            <el-button
              v-else
              size="small"
              text
              type="primary"
              @click="accept(a)"
            >采纳</el-button>
          </div>
          <el-empty v-if="!store.answers.length" description="还没有回复" :image-size="60" />
          <el-divider />
          <el-input v-model="replyText" type="textarea" :rows="2" placeholder="写回复（至少 2 个字）" />
          <el-button type="primary" style="margin-top: 8px" @click="submitReply">回复</el-button>
        </template>
      </el-card>
    </el-col>
  </el-row>
</template>
