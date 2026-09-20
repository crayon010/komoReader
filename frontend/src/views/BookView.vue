<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import ePub, { type Rendition } from 'epubjs'

import { mangaFileUrl } from '@/api/manga'
import { activateLibrary } from '@/stores/tabs'
import { mangaTypeLabel } from '@/types/manga'

const props = defineProps<{
  mangaId: string
  mangaName: string
  /** novel / pdf */
  type: string
}>()

const router = useRouter()

const isTxt = computed(() => props.mangaName.toLowerCase().endsWith('.txt'))
const isEpub = computed(() => props.type === 'novel' && !isTxt.value)
const fileUrl = computed(() => mangaFileUrl(props.mangaId))

const txt = ref('')
const error = ref('')
const epubEl = ref<HTMLElement | null>(null)

// epub.js 的 rendition 不需要响应式，普通变量即可
let rendition: Rendition | null = null

onMounted(async () => {
  try {
    if (isTxt.value) {
      const res = await fetch(fileUrl.value)
      if (!res.ok) throw new Error(`请求失败：${res.status} ${res.statusText}`)
      // 严格 UTF-8 解码失败（GBK 文件必然失败）就退回 GBK
      const buf = await res.arrayBuffer()
      try {
        txt.value = new TextDecoder('utf-8', { fatal: true }).decode(buf)
      } catch {
        txt.value = new TextDecoder('gbk').decode(buf)
      }
    } else if (isEpub.value && epubEl.value) {
      // ponytail: /file 无扩展名，epub.js 会误判为目录，必须显式 openAs
      const book = ePub(fileUrl.value, { openAs: 'epub' })
      rendition = book.renderTo(epubEl.value, { width: '100%', height: '100%' })
      await rendition.display()
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
})

onBeforeUnmount(() => {
  rendition?.destroy()
  rendition = null
})

/** 回到漫画库：tab 保持打开 */
function goLibrary() {
  activateLibrary()
  router.push('/')
}
</script>

<template>
  <div class="book">
    <div class="book-toolbar">
      <button type="button" @click="goLibrary">← 返回</button>
      <span class="book-name" :title="mangaName">{{ mangaName }}</span>
      <span class="book-badge" :class="`is-${type}`">{{ mangaTypeLabel(type) }}</span>
    </div>

    <p v-if="error" class="book-state error">{{ error }}</p>
    <!-- PDF：直接用浏览器自带的 PDF 查看器 -->
    <iframe v-else-if="type === 'pdf'" class="book-frame" :src="fileUrl" :title="mangaName" />
    <div v-else-if="isTxt" class="book-text">
      {{ txt || '加载中…' }}
    </div>
    <template v-else>
      <div ref="epubEl" class="book-epub" />
      <div class="book-controls">
        <button type="button" @click="rendition?.prev()">上一页</button>
        <button type="button" @click="rendition?.next()">下一页</button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.book {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.book-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 16px;
  background: var(--bg-elevated);
  border-bottom: 1px solid var(--border);
}

.book-name {
  flex: 1;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 14px;
  font-weight: 500;
}

.book-badge {
  padding: 2px 8px;
  font-size: 12px;
  line-height: 1.4;
  color: #fff;
  border-radius: 4px;
}

.book-badge.is-manga {
  background: #2f6fe0;
}

.book-badge.is-novel {
  background: #f5a623;
}

.book-badge.is-pdf {
  background: #e5484d;
}

.book-frame {
  flex: 1;
  border: none;
  background: var(--bg);
}

.book-text {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  max-width: 860px;
  margin: 0 auto;
  white-space: pre-wrap;
  font-size: 16px;
  line-height: 1.8;
}

.book-epub {
  flex: 1;
  min-height: 0;
}

.book-controls {
  display: flex;
  justify-content: center;
  gap: 12px;
  padding: 12px;
  background: var(--bg-elevated);
  border-top: 1px solid var(--border);
}

.book-state {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-dim);
}

.book-state.error {
  color: #ff6b6b;
}
</style>
