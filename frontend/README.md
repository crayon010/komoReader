# 漫画阅读器（前端）

基于 Vue 3 + TypeScript + Vite 的网页端漫画查看器前端框架。后端由 Java 实现（后续接入）。

## 目录结构

```
src/
  api/          HTTP 封装与漫画接口
  components/   通用组件（AppHeader、MangaCard）
  router/       路由
  types/        类型定义
  views/        页面（漫画库 / 阅读页 / 设置页）
  App.vue
  main.ts
  style.css
```

## 运行

```bash
npm install
npm run dev      # 开发，/api 会代理到 http://localhost:8080
npm run build    # 类型检查 + 构建
npm run type-check
```

## 页面与路由

| 路由 | 页面 | 说明 |
| --- | --- | --- |
| `/` | 漫画库 | 漫画列表 + 封面 |
| `/reader/:id` | 阅读页 | 翻页 / 缩放 / 键盘控制（←→ 翻页，↑↓/+- 缩放，Esc 复位） |
| `/settings` | 设置页 | 导入文件夹路径 |

## 与后端的接口约定（假设，后端需对齐）

- `GET /api/manga` → `Manga[]`
- `GET /api/manga/{id}/cover` → 封面图片流（`image/jpeg`）
- `GET /api/manga/{id}/pages` → `MangaPage[]`（`{ index: number }`）
- `GET /api/manga/{id}/page?index=N` → 单页图片流
- `POST /api/manga/import`（body `{ path }`）→ 导入结果（占位，待后端确定）

核心类型见 `src/types/manga.ts`。生产部署时若用 history 路由，后端需对 SPA 做回退到 `index.html`；否则可改用 hash 路由。
