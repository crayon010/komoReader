# 接口对接文档 — 漫画阅读器后端

对接方：vue3test 前端（Vue 3 + TS）。本文档依据前端 `src/api/manga.ts`、`src/types/manga.ts` 的**现有解析逻辑**与后端已有代码整理——前端怎么解析，后端就得怎么返回；格式待定处均已标注。

- Base URL：`http://localhost:1236`（开发环境前端 `/api` 代理已指向此地址）
- JSON 请求体与响应均为 UTF-8；POST 请求头 `Content-Type: application/json`
- 当前阶段无鉴权
- 状态标记：✅ 已实现　🚧 待实现

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

若后端坚持全部统一为 `Result` 包装，前端 `manga.ts` 需同步改两处解析——实现前二选一，本文档按前端现状（裸格式）书写。

### 1.2 业务错误码（`Result.code`）

| code | 含义 | 触发场景 |
| --- | --- | --- |
| 200 | 操作成功 | — |
| 400 | 参数错误 | 参数格式非法 |
| 401 | 缺少必要参数（业务码，与 HTTP 401 无关） | 缺 `path`、缺 `index` 等 |
| 500 | 操作失败 | 服务器异常（全局异常处理器兜底也返回它） |
| 501 | 文件操作失败 | zip 读取异常等 IO 错误 |
| 502 | 文件不存在（业务码，与 HTTP 502 无关） | 路径不存在、压缩包丢失 |

注意：当前全局异常处理器返回 `Result` JSON 时 **HTTP 状态码仍为 200**，前端以 body 中的 `code` 判断成败。

### 1.3 图片流接口约定

- 响应体为图片二进制，`Content-Type: image/jpeg` 或 `image/png`（按实际条目后缀），不返回 JSON / base64 / 文件路径
- 失败时返回**真实 HTTP 状态码**（400/404/500），`<img>` 自然显示裂图，前端不解析 body
- 页序 = 压缩包内图片文件名排序（即 `ZipImageReader.listImages` 的排序结果）；`index` 从 **0** 开始
- 封面 = 排序后的第一张图
- 可选：加 `Cache-Control` 头减少封面/单页重复拉取

## 2. 漫画接口

### 2.1 获取漫画列表

```
GET /api/manga        ✅ 已实现（当前为 mock 数据）
```

响应 `Result<Manga[]>`：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    { "id": "1", "name": "漫画一", "coverUrl": "/api/manga/1/cover", "totalPages": 120, "path": "D:/manga/manga1" },
    { "id": "2", "name": "漫画二", "coverUrl": "/api/manga/2/cover", "totalPages": 99, "path": "D:/manga/manga2" }
  ]
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | string | 唯一标识，前端用于拼 cover/pages/page URL，须保持稳定（接真实数据后建议用压缩包绝对路径的 hash 或落库自增 ID） |
| name | string | 展示名（默认取压缩包文件名） |
| coverUrl | string | 前端优先使用此字段；为空时前端按 `/api/manga/{id}/cover` 推导 |
| totalPages | number | 图片总数，与 `/pages` 返回的数组长度一致；novel/pdf 条目为 `null` |
| path | string | 后端本地存储路径，前端仅展示用途 |
| type | string | 条目类型：`manga` 漫画（zip/cbz 等）/ `novel` 小说（epub、txt）/ `pdf` |

### 2.2 获取封面图片

```
GET /api/manga/{id}/cover        🚧 待实现
```

- 成功：`200` + 图片二进制（`image/jpeg` / `image/png`）
- 失败：`404`（id 不存在或压缩包丢失）/ `500`
- 实现：取该漫画压缩包内排序第一的图片字节流写出
- 前端用法：`<img :src="manga.coverUrl || \`/api/manga/${manga.id}/cover\`" />`

### 2.3 获取页目录

```
GET /api/manga/{id}/pages        🚧 待实现（现有 /api/manga/1/pages 为占位，格式不符）
```

响应为**裸数组**（例外约定，见 1.1）：

```json
[ { "index": 0 }, { "index": 1 }, { "index": 2 } ]
```

- 可附加字段如 `name`（前端忽略多余字段）：`[{"index":0,"name":"001.jpg"}]`
- 数组顺序即阅读顺序（排序结果），长度须等于 `totalPages`
- 失败：返回 HTTP 4xx/5xx（前端 `request` 封装会抛错）；**不要**返回 `Result` 错误包装——前端会把它当数组解析而报错

### 2.4 获取单页图片

```
GET /api/manga/{id}/page?index=N        🚧 待实现
```

- 参数：`index`，整数，0 基，取值 `0 ~ totalPages-1`
- 成功：`200` + 图片二进制
- 失败：`400`（index 非法）/ `404`（id 不存在或 index 越界）/ `500`
- 前端用法：`/api/manga/${id}/page?index=${i}`（`i` 来自 `/pages` 返回的 `index`，前端无硬编码）

### 2.5 导入文件夹

```
POST /api/manga/import        ✅ 已实现
```

请求体：

```json
{ "path": "D:/manga" }
```

响应为**裸对象**（例外约定，见 1.1；前端 `ImportResult` 只有 `success` / `message` 两个字段）：

```json
// 成功
{ "success": true, "message": "共导入 3 部漫画" }

// 失败（同样 HTTP 200，用 success 区分）
{ "success": false, "message": "路径不存在" }
```

行为约定：

- `path` 缺省/为空 → 扫描默认目录 `D:/manga`
- 遍历 `path` 下的压缩文件（当前支持 `.zip` / `.cbz`）与书籍文件（`.epub` / `.txt` / `.pdf`），每个文件视为一部作品，`type` 字段见 2.1
- 子目录是否递归**待定**，默认建议递归
- 重复导入同一 `path` 应幂等（返回已存在数量，不产生重复条目）
- `path` 不存在/非目录 → `success: false`

## 3. 联调测试接口 ✅

| 方法 | 路径 | 响应 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/test` | `"hello world"`（纯文本） | 冒烟测试，确认服务存活 |
| POST | `/api/post` | `{"code":200,"message":"操作成功","data":"post"}` | 前端 `postTest` 已接，验证 POST 链路与代理 |
| POST | `/api/user` | `{"code":200,"message":"操作成功","data":"1111"}` | 同上 |

## 4. 待对齐事项

- [ ] `pages` / `import` 用裸格式还是统一 `Result`（本文档按前端现状取裸格式，见 1.1）
- [ ] `index` 0 基已定为本文档约定（前端从 `/pages` 取 index，无硬编码，无冲突）
- [ ] 导入时子目录递归策略、重复导入幂等策略
- [ ] rar / 7z 支持时间点（当前 zip/cbz；rar 需另引依赖）

## 5. 快速自测（服务启动后）

```bash
curl http://localhost:1236/api/test
curl http://localhost:1236/api/manga
curl -X POST http://localhost:1236/api/manga/import -H "Content-Type: application/json" -d "{\"path\":\"D:/manga\"}"
curl http://localhost:1236/api/manga/1/cover -o cover.jpg
curl "http://localhost:1236/api/manga/1/page?index=0" -o p0.jpg
```
