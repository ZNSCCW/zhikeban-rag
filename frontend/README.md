# 前端骨架（`frontend/`）—— Vue3 + Vite + Pinia + Element Plus + ECharts

> 本文件属 `ENG-007` 授权面（`frontend/**`，见 `§ENG-007` 的 `G1`：项目说明**落在源码树内**，不新开根文件）。
> **可空实现**：本单只要求"能编译、能起、目录结构清晰"，**不写业务功能**。

## 一句话说清怎么起

```bash
cd frontend && npm install && npm run dev      # 开发服务器 http://localhost:5173
```

## 四层栈的就位点（依据任务①）

| 依赖 | 版本 | 落点（**真实引用**，不是只在 `package.json` 挂名） |
| --- | --- | --- |
| **Vue3** | `^3.5` | `src/main.js` 的 `createApp(App)` |
| **Vite** | `^6.0` | `vite.config.js` ＋ `package.json` 的 `dev`/`build` 脚本 |
| **Pinia** | `^2.3` | `src/main.js` 的 `createPinia()` ＋ `src/stores/app.js` 的 `defineStore` |
| **Element Plus** | `^2.9` | `src/main.js` 全量注册 ＋ `src/App.vue` 用 `el-container`/`el-card`/`el-tag` |
| **ECharts** | `^5.6` | `src/components/HealthChart.vue` 的 `import * as echarts from 'echarts'` |

## 目录结构

```
frontend/
├── package.json
├── vite.config.js
├── index.html
├── README.md                       ← 本文件
└── src/
    ├── main.js                     ← 入口（Vue + Pinia + Element Plus）
    ├── App.vue                     ← 根组件（后端探活 + ECharts 占位图）
    ├── router/index.js             ← 路由骨架
    ├── stores/app.js               ← Pinia 状态桶（真实引用）
    └── components/HealthChart.vue  ← ECharts 就位检查
```

## 与后端的对接

- 开发期 `/api` 由 `vite.config.js` 反向代理到 `http://localhost:8080`（可用 `VITE_API_TARGET` 覆盖）。
- 探活动作见 `src/stores/app.js` 的 `probeBackend()`；**后端没起不算错误**（骨架单允许前端离线单独起）。

## 边界（不在本单范围内）

- **不写业务页面**：问答社区、AI 助教、错题本、管理后台等页面都**不属本单**。
- `npm run build` 需要联网拉依赖；本单"能编译／能起"的机器判据落在 `§ENG-007` 的 `C` 组（见 `G4`）。
