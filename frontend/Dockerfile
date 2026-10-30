# 前端骨架镜像（ENG-007 任务①）
# ★ 多阶段构建：node 阶段 `npm ci` + `vite build`（这就是"能编译"的落点），
#   nginx 阶段托管 dist 静态产物。
#   ★ 依赖联网拉 npm 依赖（首次构建需要网络）。
FROM node:22-alpine AS builder
WORKDIR /build
COPY package.json ./
# 骨架单没有 lockfile（`package-lock.json` 由 `npm install` 生成、不入库），
# 故用 `npm install`；有 lockfile 后应改回 `npm ci` 以保证可复现。
RUN npm install --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginx:1.27-alpine AS runtime
COPY --from=builder /build/dist /usr/share/nginx/html
# 单页应用回退：任何路径都交给 index.html
RUN printf 'server {\n  listen 80;\n  root /usr/share/nginx/html;\n  index index.html;\n  location /api/ { proxy_pass http://backend:8080; }\n  location / { try_files $uri $uri/ /index.html; }\n}\n' \
    > /etc/nginx/conf.d/default.conf
EXPOSE 80
