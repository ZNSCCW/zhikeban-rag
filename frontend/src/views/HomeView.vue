<script setup>
import HealthChart from '../components/HealthChart.vue'

/**
 * 首页 / 探活页（`ENG-007` 骨架的落点，`ENG-009` 保留不动）。
 * ★ 这条 `ECharts 就位检查` 是骨架期的自查面，本单**不删**（避免把已通过的东西弄丢）。
 */
</script>

<template>
  <el-card>
    <template #header>
      <strong>骨架自查（ENG-007 打底）</strong>
      <el-tag type="info" size="small" style="margin-left: 8px">ECharts 就位检查</el-tag>
    </template>
    <HealthChart />
    <el-alert
      style="margin-top: 12px"
      type="info"
      :closable="false"
      title="ENG-009 落的是 MVP 一 最小闭环：认证/用户 · 课程/资料 · 问答社区面 · MySQL 迁移脚本 · 集成冒烟"
      description="★ 本单不含 RAG 生成链路，问答只做社区面（发帖/回复/采纳）—— 见 shared/STANDARDS.md §ENG-009 的 R 组。"
    />
  </el-card>
</template>
