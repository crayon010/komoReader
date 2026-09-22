# 沟通

- 面向用户的叙述默认使用简体中文； 代码、命令和技术标识保持英文。 
- 先给影响与结论，再给行动、待决策和必要证据； 没有对应内容就省略。 
- 使用简洁、连贯的段落； 只有确实适合并列比较或按步骤执行时才使用列表。 
- 使用具体、简单的词，避免无意义术语、套话、重复总结和未经请求的对比。 
- 技术细节只保留对理解结论、判断风险或复现结果有帮助的部分。

## 指令优先级

- 遵守系统、平台和安全约束。 
- 用户当前明确指令优先于 Skill、历史记忆和默认偏好。 
- 项目目录中的 `AGENTS.md` 只在该项目范围内补充或覆盖全局规则。

## 执行方式

- 用户表示要开始新工作或修复现有问题时，持续推进，直到用户的目标完成，在目标方向上自主推进。 
- 向用户提问之前，先完成上下文里已经授权、并且能把下一步变成可审查结果的工作。 用户批准的应该是具体、可检查的结果。 
- 用户建议不适合目标时直接说，不要迎合。 
- 不要因为假想风险，主动加警告、免责声明、审批流程或安全/合规清单。

## 测试与验证

- 不要为可逆、影响小、只是复述实现的改动写测试。 
- 跑与本次改动相称的测试，并完成必要检查。 这些通过之后，只有出现新改动、新失败或尚未解决的疑点时，才扩大或重复测试； 否则继续把任务做完。 
- 收尾删掉本次产生、之后用不上的临时文件。

## 工具与并行

- 搜索文件或文本优先使用 `rg`、`rg --files`； 独立的读取和查询尽量批量执行。 
- 网页控制台无 CLI/API 时用已登录的Chrome浏览器； 飞书优先 `lark-cli`。 
- 只有存在真正独立的工作流，且委派能节省时间或提升质量时才使用子 Agent。 
- 共享状态、连续决策和简单任务由当前 Agent 直接完成； 委派任务必须有明确输入、输出和完成判据，最终结论由主 Agent 汇总并验证。

## 规则来源

- 全局规则维护在当前生效的 canonical `AGENTS.md`； `CLAUDE.md` 只作兼容入口，不复制规则正文。 
- 项目事实、生产状态、历史决策和对外契约以项目级 `AGENTS.md` 及其指定的脚本、探针、决策记录和合同文件为准。

# 项目本身
## 简介
- 本项目是基于vue3+ts的网页端查看漫画/小说/PDF文件，后续会写java实现后端需要，通过将本地文件导入，实现在前端页面上查看浏览

## 前端设计

### 页面模块
阅读库:  阅读列表 + 封面
阅读页:  翻页 / 缩放 / 键盘控制
设置页:  导入文件夹路径

### 阅读列表页（核心）
数据结构（TS）
interface Manga {
  id: string
  name: string
  coverUrl: string   // /api/manga/{id}/cover
  totalPages: number
  path: string
}
示例组件
<template>
  <div class="manga-list">
    <MangaCard
      v-for="manga in mangaList"
      :key="manga.id"
      :manga="manga"
      @click="openReader(manga)"
    />
  </div>
</template>

### 封面渲染（重点）
❌ 不要直接返回文件
✅ 返回 图片流 URL
<img :src="`/api/manga/${manga.id}/cover`" />
类似前端里的 blob URL，但由 Java 输出 image/jpeg。

### 漫画/小说/PDF文件阅读器
阅读器核心逻辑
const pages = ref<string[]>([])

async function loadManga(id: string) {
  const res = await fetch(`/api/manga/${id}/pages`)
  const data = await res.json()
  pages.value = data.map(p => `/api/manga/${id}/page?index=${p.index}`)
}

翻页
function next() {
  if (current.value < pages.value.length - 1) current.value++
}
