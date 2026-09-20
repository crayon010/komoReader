# 漫画阅读器后端（Spring Boot）

本项目是 [vue3test](../vue-test/vue3test)（Vue 3 + TypeScript 漫画阅读器前端）的后端服务。

核心职责：**获取本地漫画文件夹信息，提取其中压缩包内的图片条目，以接口形式提供给前端**，让网页端可以直接查看漫画封面、浏览压缩包内部页面，而无需手动解压。图片一律以流（`image/jpeg` 等）直接输出，前端通过 `<img src="/api/manga/{id}/cover">` 直接引用。

重点: Java 负责“本地文件 + 压缩包解析 + 图片流”，Vue3 只负责“展示 + 阅读体验”

## 技术栈

- Java 21 / Spring Boot 4.1.1（`spring-boot-starter-webmvc`）
- Apache Commons Compress 1.28.0：读取 zip 压缩包条目
- MySQL + MyBatis-Plus：依赖已引入，元数据持久化预留（当前业务尚未使用）
- Lombok

## 运行

```bash
./mvnw spring-boot:run     # Windows 下用 mvnw.cmd
```

- 服务端口：`1236`（见 `src/main/resources/application.yml`）
- 前端 `vite.config.ts` 的 `/api` 代理已指向 `http://localhost:1236`，本地联调直接 `npm run dev` 即可
- 数据源指向本地 MySQL 的 `manga_reader` 库；当前接口不依赖数据库。注意 yml 中 `serverTimezone-UTC` 应为 `serverTimezone=UTC`（缺 `=`），启用数据库前需修正

## 目录结构

```
src/main/java/com/example/springboot/
├── controller/
│   ├── MangaList.java      # 漫画接口（当前为 mock 数据 + 占位）
│   ├── User.java           # POST 联调测试（/api/user、/api/post）
│   └── Test.java           # GET /api/test 冒烟测试
├── service/
│   └── MangaService.java   # 业务层（占位）
├── entity/
│   └── Manga.java          # 漫画条目：id/name/coverUrl/totalPages/path
├── DTO/
│   ├── command/            # 请求入参（mangaDTO，占位）
│   └── response/           # 响应出参
├── common/
│   ├── Result.java         # 统一返回包装 { code, message, data }
│   ├── ResultCode.java     # 状态码枚举（200 成功 / 4xx 参数 / 5xx 文件等）
│   └── GlobarExceptionHandler.java  # 全局异常 → 统一 Result JSON
└── util/
    └── ZipImageReader.java # 压缩包图片读取工具（已可用）
```

`entity/Manga` 与前端 `types/manga.ts` 中的 `MangaMeta` 接口字段一一对应。

## 与前端的接口契约

详细的请求/响应格式、错误码与示例见 [API.md](API.md)，以下为概览：

依据前端 `README.md` 与 `src/api/manga.ts` 的约定：

| 方法 | 路径 | 返回 | 状态 |
| --- | --- | --- | --- |
| GET | `/api/manga` | `Result<Manga[]>`（`{ code, data, message }`） | 已实现，目前返回两条模拟数据 |
| GET | `/api/manga/{id}/cover` | 封面图片流（非 JSON） | 待实现 |
| GET | `/api/manga/{id}/pages` | `MangaPage[]` 裸数组（`{ index: number }`） | 待实现（现有 `/api/manga/1/pages` 为占位） |
| GET | `/api/manga/{id}/page?index=N` | 单页图片流 | 待实现 |
| POST | `/api/manga/import` | `{ success: boolean, message?: string }`，body `{ path }` | 待实现 |
| POST | `/api/post` | `Result<String>` | 已实现（联调测试） |
| GET | `/api/test` | `"hello world"` | 已实现（冒烟测试） |

两条关键约定：

1. **JSON 包装不一致**：`/api/manga` 前端按 `ApiResponse`（即 `Result` 包装）解析，而 `/api/manga/{id}/pages` 前端按**裸数组**解析。实现 `/pages` 时要么直接返回裸数组，要么让前端改为解包，两边需对齐。
2. **图片接口不返回文件路径或 base64**：封面和单页由后端从压缩包读出字节后直接以图片流写出（`produces = IMAGE_JPEG_VALUE` 等），前端仅拼 URL。

## 核心流程（后端待完成的部分）

1. **导入文件夹**：`POST /api/manga/import` 接收本地路径，遍历目录识别压缩包文件（当前工具支持 `.zip`/`.cbz`；Commons Compress 亦可扩展 7z、tar 等格式，RAR 需另引依赖）。
2. **提取压缩包信息**：`ZipImageReader` 已提供两个静态方法，可直接作为实现基础：
   - `listImages(Path)`：列出压缩包内全部 `jpg/jpeg/png` 条目名并排序（排序即页序，封面取第一张）；
   - `readImage(Path, name)`：读取指定条目的字节。
3. **输出图片流**：`/cover` 取排序后的第一张图、`/page?index=N` 按索引取图，读取字节后写出，控制 `Content-Type` 与缓存头。
4. **持久化**：MySQL + MyBatis-Plus 依赖已就绪，将导入的漫画路径、页数等元数据落库，替代每次实时扫描。表结构与流程设计已定稿，见 [docs/DB_DESIGN.md](docs/DB_DESIGN.md)。

## 待办清单

- [ ] 文件夹扫描与 `/api/manga/import` 导入接口
- [ ] `/api/manga/{id}/cover` 与 `/api/manga/{id}/page?index=N` 图片流接口
- [ ] `/api/manga/{id}/pages` 页目录（同时与前端确认裸数组 / Result 包装）
- [ ] `/api/manga` 由模拟数据切换为真实扫描结果
- [ ] 元数据持久化（设计已定稿，实施清单见 [docs/DB_DESIGN.md](docs/DB_DESIGN.md) 第 7 节）
- [ ] 若前端生产部署使用 history 路由，后端需将非 `/api` 请求回退到 `index.html`
