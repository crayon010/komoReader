# KomoReader

本地漫画/小说/PDF 阅读器。前端 Vue 3 + Vite，后端 Spring Boot，书籍直接读本地文件夹，无需数据库。

## 功能

- 漫画：zip/cbz 内图片翻页阅读
- 小说：txt（自动识别 UTF-8/GBK 编码）、epub（epub.js 渲染）
- PDF：浏览器内置查看器
- 多标签阅读、封面缓存、扫描结果缓存

## 目录结构

```
backend/    Spring Boot（Java 21，端口 1236）
frontend/   Vue 3 + Vite（开发端口 5173，/api 代理到 1236）
```

后端接口契约见 `backend/API.md`。

## 开发

两个终端分别启动：

```
cd backend && mvnw.cmd spring-boot:run
cd frontend && npm run dev
```

浏览器打开 http://localhost:5173 。首次使用在「设置」页导入书籍文件夹（默认 `D:\manga`）。

前端依赖：`cd frontend && npm install`。

## 打包（免安装、无需 Java 的独立程序）

在**装有 JDK 21** 的机器上执行：

```
build-package.bat
```

产物在 `backend\package\MangaReader\`（约 180MB，内置 JRE）：
拷到任意 Windows 电脑，双击 `启动阅读器.bat` 即用，浏览器访问 http://localhost:1236 。

步骤：前端构建 → dist 拷入 `backend/src/main/resources/static/` → `mvnw package` → `jpackage` 生成 app-image。
