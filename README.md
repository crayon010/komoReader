# KomoReader

本地漫画 / 小说 / PDF 阅读器。前端 Vue 3 + Vite，后端 Spring Boot，书籍直接读本地文件夹，无需数据库、无需解压，导入即可看。

## 功能

- **漫画**：zip / cbz 压缩包、图片文件夹（jpg / jpeg / png / webp），单页 / 双页、翻页 / 滚动两种模式，缩放与键盘操作
- **小说**：txt（自动识别 UTF-8 / GBK 编码）、epub（epub.js 分页渲染）
- **PDF**：浏览器内置查看器
- **多标签阅读**：最多 5 个阅读 tab，常驻挂载随意切换，阅读进度不丢
- **进度续读**：翻页位置 / 滚动位置 / epub 百分比均存 localStorage，刷新页面后从上次位置继续
- **封面优化**：并发受限的封面加载队列 + blob 缓存 + WebP 缩略图，虚拟列表只渲染可视行

## 技术栈

| 端 | 技术 |
| --- | --- |
| 前端 | Vue 3 + TypeScript + Vite + vue-router（hash 模式）+ epub.js |
| 后端 | Java 21 + Spring Boot 4 + Apache Commons Compress + Lombok |
| 持久化 | 无数据库。扫描结果缓存为书籍目录下的 `.manga-cache.properties`，阅读进度存浏览器 localStorage |

## 快速开始

两个终端分别启动：

```
cd backend && mvnw.cmd spring-boot:run
cd frontend && npm install && npm run dev
```

浏览器打开 http://localhost:5173 ，在「设置」页导入书籍文件夹（默认 `D:\manga`）。

- 后端端口 `1236`，前端 `/api` 已代理到它
- 打包形态下前端由后端 jar 直接托管，单端口 `1236`

## 打包（免安装独立程序，目标机器无需 Java）

在**装有 JDK 21 和 Node.js** 的机器上执行：

```
build-package.bat
```

产物在 `backend\package\MangaReader\`（约 180MB，内置 JRE）：拷到任意 Windows 电脑，双击 `启动阅读器.bat`，自动打开 http://localhost:1236 。

步骤：前端构建 → dist 拷入 `backend/src/main/resources/static/` → `mvnw package` → `jpackage` 生成 app-image。`stop-port.bat` 用于释放被占用的 1236 端口。

## 目录结构

```
backend/    Spring Boot 服务（扫描、图片流、打包脚本入口）
frontend/   Vue 3 前端（阅读库 / 阅读器 / 设置）
```

接口契约见 `backend/API.md`，数据库设计（未实施）见 `backend/docs/DB_DESIGN.md`。

## 已知限制

- 压缩包仅支持 zip / cbz（rar / 7z 需另引依赖，未接入）
- 同一时间只扫描一个目录；mobi 仅被识别，暂无可读渲染
- 元数据落库（MySQL）设计已定稿未实施，见 `backend/docs/DB_DESIGN.md`
