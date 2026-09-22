# KomoReader 前端

Vue 3 + TypeScript + Vite 的本地漫画 / 小说 / PDF 阅读器前端，对接 `backend/` 的 Spring Boot 服务（开发时 `/api` 代理到 `http://localhost:1236`）。

## 目录结构

```
src/
  api/          HTTP 封装与接口（manga.ts、http.ts）
  components/   AppHeader、ReaderTabBar、MangaCard、VirtualMangaGrid
  router/       路由（hash 模式，打包进 jar 后刷新不 404）
  stores/       tabs.ts 模块级单例：阅读 tab 状态 + 进度读写
  types/        Manga 等类型定义
  utils/        coverLoader.ts 封面并发队列 + blob 缓存 + WebP 缩略图
  views/        LibraryView / ReaderView / BookView / SettingsView
  App.vue       tab 常驻挂载、v-show 切换的总壳
```

## 运行

```bash
npm install
npm run dev          # 开发
npm run build        # 类型检查 + 构建
npm run type-check   # 仅 vue-tsc
```

## 页面与路由

| 路由 | 页面 | 说明 |
| --- | --- | --- |
| `/` | 阅读库 | 虚拟列表 + 封面卡片，点击进入阅读 tab |
| `/settings` | 设置 | 导入本地文件夹路径 |

阅读器**不走路由**：点击书籍由 `stores/tabs.ts` 开 tab，`App.vue` 将其常驻挂载后用 `v-show` 切换，最多 5 个（超出淘汰最久未用），切换不丢进度。

## 阅读器

- **漫画（ReaderView）**：单页 / 双页、翻页 / 滚动模式（localStorage 持久化）；`←→` 翻页、`↑↓` / `+-` 缩放、Esc 复位
- **小说 / PDF（BookView）**：txt 自动识别 UTF-8 / GBK；epub 用 epub.js 分页；PDF 走 iframe
- **进度**：统一以百分比存 `localStorage["tab_progress_" + mangaId]`，刷新后按类型恢复（翻页页码 / 滚动位置 / epub locations 百分比）

## 接口约定

前端解析逻辑即契约，详见 `../backend/API.md`。要点：`/api/manga` 走 `Result` 包装，`/pages` 是裸数组，`/import` 是裸对象，图片一律 `<img src>` 直读流。

## 备注

- Prettier 格式化，分号结尾
