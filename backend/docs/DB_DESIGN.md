# 数据库持久化设计（待实施）

> 状态：设计定稿，**尚未实施**。作为后续开发待办，实施前先读完第 5、6 节。
> 依赖：MySQL `manga_reader` 库 + MyBatis-Plus（依赖已在 `pom.xml`）。

## 1. 核心原则

**磁盘是事实源，数据库只存"扫描结论 + 业务状态"，能实时派生的数据不入库。**

由此划定的读写边界：漫画列表与导入信息走库；翻页、封面走 zip 实时读取（`ZipImageReader`）。引入数据库的最大收益是列表接口不再实时扫盘。

## 2. 表设计（两张表）

### `manga_folder`（导入源表）—— 对应设置页导入的一个文件夹

| 字段 | 作用 |
| --- | --- |
| `id` | 主键 |
| `folder_path`（唯一） | 幂等导入依据：同一路径重复导入直接命中 |
| `last_scan_at` | 支持后续"刷新漫画库"接口做增量扫描 |
| `created_at` | 常规 |

### `manga`（漫画表）—— 一个压缩包一行

| 字段 | 作用 |
| --- | --- |
| `id` | 业务主键，须稳定（见第 4 节用户表前提） |
| `folder_id` | 归属哪个导入源 |
| `name` | 压缩包文件名去扩展名 |
| `file_path`（唯一） | 压缩包绝对路径：幂等键 + 打开时校验文件是否还在 |
| `format` | zip/cbz，为 7z/rar 预留 |
| `total_pages` | 导入时扫描一次写入 |
| `cover_entry` | 封面在压缩包内的条目名（排序第一张），cover 接口免列目录 |
| `file_size` | 弱指纹：重扫时识别"重命名/移动的同一本书" |
| `status` | 0 正常 / 1 文件丢失（软标记，不物理删） |
| `created_at / updated_at` | 常规 |

```sql
CREATE TABLE manga_folder (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  folder_path  VARCHAR(512) NOT NULL UNIQUE,
  last_scan_at DATETIME NOT NULL,
  created_at   DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE manga (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  folder_id   BIGINT NOT NULL,
  name        VARCHAR(255) NOT NULL,
  file_path   VARCHAR(512) NOT NULL UNIQUE,
  format      VARCHAR(8) NOT NULL DEFAULT 'zip',
  total_pages INT NOT NULL DEFAULT 0,
  cover_entry VARCHAR(512),
  file_size   BIGINT,
  status      TINYINT NOT NULL DEFAULT 0,
  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_folder (folder_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## 3. 明确不入库的数据

- **page 表**：几十万行且随时可从 zip 派生，存了要双向同步，得不偿失。
- **coverUrl**：API 层用 `id` 拼出的派生字段。
- **封面缓存**：列表页每本书开一次 zip 读封面，量小可接受；量大后按 `cover_entry` 做磁盘/库缓存，二期再说。

## 4. 关联关系

- **folder : manga = 1 : N**：`manga.folder_id` 做**逻辑外键**——只建索引（`idx_folder`），不建物理 FK 约束（MyBatis-Plus 生态惯例，避免级联与迁移麻烦）；删除 folder 时由 service 层先处理 manga。
- **manga : page**：不建关系，运行时从压缩包派生。
- **user : manga（将来）**：登录体系（`UserLoginResponseDTO` 已有 token/roleType 设计意图）上线后再加中间表，现在**不建表、不预留字段**，唯一前提是 `manga.id` 稳定：
  - `user_manga_read(user_id, manga_id, last_page, updated_at)`，联合主键，存阅读进度；
  - 收藏与进度分开时再加 `user_favorite`。

## 5. 漂移与幂等（文件会被移动/删除/重命名）

导入或刷新时对 folder 做一次 diff：

| 情况 | 处理 |
| --- | --- |
| 磁盘有、库里无 | 插入（同时写入 `total_pages`、`cover_entry`、`file_size`） |
| `file_path` 相同 | 跳过（或刷新 `total_pages`） |
| 库里有、磁盘无 | `status = 1`，不物理删 |
| 重命名/移动 | 用 `file_size` 相同做弱识别；识别不上就当新漫画，进度丢失可接受，**不为此上内容 hash** |

`file_path` 的唯一约束是幂等兜底。

导入接口（`POST /api/manga/import`）流程：

1. 按 `folder_path` upsert `manga_folder`；
2. 遍历压缩包，按 `file_path` 逐个 upsert `manga`；
3. 对库中该 folder 下的记录做 diff，标记丢失；
4. 更新 `last_scan_at`；
5. 按既有契约返回 `{success, message: "新增 X，已存在 Y，丢失 Z"}`（格式见 [API.md](../API.md)）。

列表接口默认过滤 `status != 0`；前端将来需要"丢失提示"时再改为带标记返回。

## 6. 落地注意点

1. **实体拆分**：现 `entity/Manga` 是接口 VO（含 `coverUrl`），入库时新建 `MangaFolderPO` / `MangaPO`（带 `@TableName` 等注解）映射表；VO 结构保持不变。PO→VO 时 `id` 转 String——前端 `Manga.id` 是 string，Long 序列化成数字在大 ID 下有 JS 精度问题。
2. **依赖收敛**：`spring-boot-starter-data-jdbc` 与 MyBatis-Plus starter 并存，启用前二选一（保留 MyBatis-Plus，摘掉 data-jdbc 及其 test starter）。
3. **兼容性验证**：MyBatis-Plus 3.5.7 的 boot3-starter 与 Spring Boot 4.1 的自动装配需实测；起不来就升级 MyBatis-Plus 或退回 `mybatis-spring` 手动配置。
4. **修正数据源笔误**：`application.yml` 中 `serverTimezone-UTC` 应为 `serverTimezone=UTC`，启用库之前必须改。
5. **建表方式**：初期直接手工执行第 2 节 SQL；表结构多版本后再考虑 Flyway/Liquibase，现在不引入。

## 7. 实施待办清单

- [ ] 修正 `application.yml` 数据源笔误（`serverTimezone=UTC`）
- [ ] 摘除 `spring-boot-starter-data-jdbc`（含 test），验证 MyBatis-Plus 3.5.7 + Boot 4.1 自动装配
- [ ] 在 `manga_reader` 库执行第 2 节建表 SQL
- [ ] 新建 `MangaFolderPO` / `MangaPO` 与对应 Mapper
- [ ] `MangaService` 实现导入流程（第 5 节：upsert + diff + last_scan_at）
- [ ] `/api/manga` 切换为查库（PO→VO，`id` 转 String，默认过滤 `status != 0`）
- [ ] cover / page 接口实现时从库取 `file_path` / `cover_entry`（id 必须来自列表接口，见 API.md）
- [ ] 用户体系上线后再建 `user_manga_read` / `user_favorite`（第 4 节）
