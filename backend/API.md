# 接口对接文档 — KomoReader 后端

对接方：KomoReader 前端（Vue 3 + TS，`frontend/src/api/manga.ts`）。本文档以后端**现有实现**与前端的解析逻辑为准。

- Base URL：`http://localhost:1236`（开发环境前端 `/api` 代理已指向此地址）
- JSON 请求体与响应均为 UTF-8；POST 请求头 `Content-Type: application/json`
- 无鉴权
- 状态标记：✅ 已实现

## 1. 通用约定

### 1.1 响应包装

JSON 接口默认使用统一包装 `Result`（前端 `ApiResponse`）：

```json
{ "code": 200, "message": "操作成功", "data": "…" }
```

**两个例外**（前端按裸格式解析，不走 `Result` 包装）：

| 接口 | 前端解析格式 |
| --- | --- |
| `GET /api/manga/{id}/pages` | 裸数组 `[{index}, …]` |
| `POST /api/manga/import` | 裸对象 `{success, message}` |

### 1.2 业务错误码（`Result.code`）

| code | 含义 | 触发场景 |
| --- | --- | --- |
| 200 | 操作成功 | — |
| 400 | 参数错误 | 参数格式非法 |
| 401 | 缺少必要参数（业务码，与 HTTP 401 无关） | 缺 `path`、缺 `index` 等 |
| 500 | 操作失败 | 服务器异常（全局异常处理器兜底也返回它） |
| 501 | 文件操作失败 | zip 读取异常等 IO 错误 |
| 502 | 文件不存在（业务码，与 HTTP 502 无关） | 路径不存在、压缩包丢失 |

注意：全局异常处理器返回 `Result` JSON 时 **HTTP 状态码仍为 200**，前端以 body 中的 `code` 判断成败。

### 1.3 图片流接口约定

- 响应体为图片二进制，`Content-Type: image/jpeg` / `image/png` / `image/webp`（按实际条目后缀），不返回 JSON / base64 / 文件路径
- 失败时返回**真实 HTTP 状态码**（400/404/500），`<img>` 自然显示裂图，前端不解析 body
- 页序 = 图片文件名排序；`index` 从 **0** 开始；封面 = 第一张
- 扫描目录**直下一层**：压缩包 / 书籍文件各一部；含 jpg/jpeg/png/webp 的子文件夹也算一部漫画（页序 = 目录内文件名排序）

## 2. 漫画接口

### 2.1 获取漫画列表

```
GET /api/manga        ✅
```

响应 `Result<MangaMeta[]>`：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    { "id": "3a7b…", "name": "comic.zip", "coverUrl": null, "totalPages": 120, "path": "D:/manga/comic.zip", "type": "manga" }
  ]
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | string | 文件 / 文件夹名的 SHA-256 hex，名称不变则稳定；前端用它拼 cover/pages/page/file URL |
| name | string | 展示名（文件 / 文件夹名） |
| coverUrl | string\|null | 为空时前端按 `/api/manga/{id}/cover` 推导 |
| totalPages | number\|null | 图片总数；novel / pdf / mobi 为 `null` |
| path | string | 后端本地路径，前端仅展示 |
| type | string | `manga`（zip/cbz/图片文件夹）/ `novel`（epub、txt）/ `pdf` |

扫描结果按 mtime+size 缓存在目录下 `.manga-cache.properties`，未变化不重复拆包。

### 2.2 获取封面图片

```
GET /api/manga/{id}/cover        ✅
```

- 成功：`200` + 图片二进制（`image/jpeg` / `image/png` / `image/webp`）
- 失败：`404`（id 不存在或压缩包丢失）/ `500`
- 压缩包：取排序第一的图片条目；图片文件夹：取目录内排序第一张

### 2.3 获取页目录

```
GET /api/manga/{id}/pages        ✅
```

响应为**裸数组**（例外约定，见 1.1）：

```json
[ { "index": 0 }, { "index": 1 }, { "index": 2 } ]
```

- 数组顺序即阅读顺序，长度等于 `totalPages`
- 失败返回 HTTP 4xx/5xx（前端 `request` 封装会抛错）；**不要**返回 `Result` 错误包装

### 2.4 获取单页图片

```
GET /api/manga/{id}/page?index=N        ✅
```

- 参数：`index`，整数，0 基，取值 `0 ~ totalPages-1`
- 成功：`200` + 图片二进制；失败：`400` / `404` / `500`
- 前端用法：`/api/manga/${id}/page?index=${i}`

### 2.5 导入文件夹

```
POST /api/manga/import        ✅
```

请求体（可省略，body 缺省或 `path` 为空则沿用上次目录，首次默认 `D:/manga`）：

```json
{ "path": "D:/manga" }
```

响应为**裸对象**（例外约定，见 1.1）：

```json
{ "success": true, "message": "共导入 3 部" }
{ "success": false, "message": "路径不存在" }
```

行为：

- 识别 `.zip` / `.cbz`（含伪装后缀，试开成功即视为 zip）、`.epub` / `.txt`（novel）、`.pdf`、`.mobi`（仅识别，暂无可读渲染）；含图片的子文件夹记为一部漫画
- 不递归子目录深处，只扫根目录直下
- 每次导入全量重扫该目录，结果幂等（同名覆盖）

### 2.6 获取书籍原始文件流

```
GET /api/manga/{id}/file        ✅
```

- txt → `text/plain; charset=utf-8`；pdf → `application/pdf`；其余按 `application/epub+zip`
- 前端用法：txt 直接 fetch 解码（UTF-8 失败退回 GBK），epub 交给 epub.js（需显式 `openAs: 'epub'`，因路径无扩展名），pdf 用 iframe

## 3. 联调测试接口

| 方法 | 路径 | 响应 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/test` | `"hello world"`（纯文本） | 冒烟测试，确认服务存活 |

## 4. 已知限制

- 压缩包仅 zip / cbz；rar / 7z 未接入
- 单一当前扫描目录，多次导入不同目录会互相覆盖
- mobi 仅识别，无渲染
- 元数据未落库（设计见 [docs/DB_DESIGN.md](docs/DB_DESIGN.md)）

## 5. 快速自测（服务启动后）

```bash
curl http://localhost:1236/api/test
curl http://localhost:1236/api/manga                          # 取 data[].id（SHA-256 hex）
curl -X POST http://localhost:1236/api/manga/import -H "Content-Type: application/json" -d "{\"path\":\"D:/manga\"}"
curl http://localhost:1236/api/manga/{id}/cover -o cover.jpg
curl "http://localhost:1236/api/manga/{id}/page?index=0" -o p0.jpg
curl "http://localhost:1236/api/manga/{id}/file" -o book.bin
```
