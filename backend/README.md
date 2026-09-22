# KomoReader 后端（Spring Boot）

本地书籍扫描与图片流服务：读取本地文件夹，识别压缩包 / 书籍文件并提取其中图片条目，以接口形式提供给前端，网页端直接看封面、翻页，无需解压。Java 负责「本地文件 + 压缩包解析 + 图片流」，Vue 3 前端负责「展示 + 阅读体验」。

## 技术栈

- Java 21 / Spring Boot 4.1（`spring-boot-starter-webmvc`）
- Apache Commons Compress：读取 zip 条目
- **无数据库**：`application.yml` 已排除 DataSource / MyBatis-Plus 自动配置，未装 MySQL 的机器也能启动；MySQL 落库为预留设计（见 `docs/DB_DESIGN.md`，未实施）

## 运行

```bash
./mvnw spring-boot:run     # Windows 下用 mvnw.cmd
```

- 端口 `1236`；前端 vite `/api` 代理已指向它
- 首次使用在设置页导入文件夹，或 `POST /api/manga/import`（body `{ "path": "D:/manga" }`）

## 扫描与缓存（MangaService）

- 扫描目录**直下一层**：压缩包 / 书籍文件各算一部；含图片的子文件夹也算一部漫画（页序 = 文件名排序）
- 支持格式：`.zip` / `.cbz`（含伪装成其他后缀的 zip，靠试开识别）、`.epub` / `.txt`（novel）、`.pdf`、`.mobi`（仅识别）
- `id` = 文件 / 文件夹名的 SHA-256 hex，名称不变则稳定
- 扫描结果缓存在目录下的 `.manga-cache.properties`（mtime + size + 页数），未变化不重复拆包
- ponytail: 单一当前目录，多用户同时扫不同目录会互相覆盖；需要时让扫描结果带各自 root

## 目录结构

```
src/main/java/com/example/springboot/
├── controller/
│   ├── MangaList.java      # /api/manga 全部接口
│   └── Test.java           # GET /api/test 冒烟测试
├── service/
│   └── MangaService.java   # 扫描、缓存、目录图片读取
├── entity/
│   └── MangaMeta.java      # id/name/coverUrl/totalPages/path/type
├── common/                 # Result 统一包装 + 全局异常处理
├── DTO/
└── util/
    └── ZipImageReader.java # zip 图片列举 / 读取
```

## 接口契约

详见 [API.md](API.md)，概览：

| 方法 | 路径 | 返回 |
| --- | --- | --- |
| GET | `/api/manga` | `Result<MangaMeta[]>` |
| POST | `/api/manga/import` | 裸对象 `{success, message}` |
| GET | `/api/manga/{id}/cover` | 封面图片流 |
| GET | `/api/manga/{id}/pages` | 裸数组 `[{index}]` |
| GET | `/api/manga/{id}/page?index=N` | 单页图片流 |
| GET | `/api/manga/{id}/file` | 书籍原始文件流（txt/epub/pdf） |

图片接口以流输出（`image/jpeg` 等），不返回 JSON / base64 / 路径。

## 打包

仓库根目录 `build-package.bat`：前端构建 → 静态资源拷入 `src/main/resources/static/` → fat jar → jpackage 生成内置 JRE 的 app-image，产物在 `package/MangaReader/`。

## 待办

- [ ] rar / 7z 支持（rar 需另引依赖）
- [ ] 元数据落库（设计定稿于 [docs/DB_DESIGN.md](docs/DB_DESIGN.md)，未实施）
- [ ] 多扫描目录并存（当前单一 root，互相覆盖）
- [ ] mobi 渲染（当前仅识别条目）
