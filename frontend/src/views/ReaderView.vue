<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { mangaPageUrl } from '@/api/manga'
import { activateLibrary } from '@/stores/tabs'

const props = defineProps<{
  mangaId: string
  mangaName: string
  totalPages: number
  /** tab 是否处于激活态；只有激活的 tab 响应键盘操作 */
  active: boolean
}>()

const router = useRouter()

const current = ref(0)
const scale = ref(0.3)

// 浏览模式：单页/双页 + 翻页/滚动，选择持久化到 localStorage
const pageMode = ref<'single' | 'double'>(
  localStorage.getItem('readerPageMode') === 'double' ? 'double' : 'single',
)
const readMode = ref<'page' | 'scroll'>(
  localStorage.getItem('readerScrollMode') === 'scroll' ? 'scroll' : 'page',
)

watch(pageMode, (v) => localStorage.setItem('readerPageMode', v))
watch(readMode, (v) => localStorage.setItem('readerScrollMode', v))

const total = computed(() => props.totalPages)
const step = computed(() => (pageMode.value === 'double' ? 2 : 1))
const currentUrl = computed(() =>
  total.value > 0 ? mangaPageUrl(props.mangaId, current.value) : '',
)
const nextUrl = computed(() =>
  total.value > 0 && current.value + 1 < total.value
    ? mangaPageUrl(props.mangaId, current.value + 1)
    : '',
)

function prev() {
  if (current.value > 0) current.value = Math.max(0, current.value - step.value)
}

function next() {
  if (current.value < total.value - 1) {
    current.value = Math.min(total.value - 1, current.value + step.value)
  }
}

function zoomIn() {
  scale.value = Math.min(scale.value + 0.2, 4)
}

function zoomOut() {
  scale.value = Math.max(scale.value - 0.2, 0.1)
}

function resetZoom() {
  scale.value = 1
}

function onKeydown(e: KeyboardEvent) {
  if (!props.active) return
  switch (e.key) {
    case 'ArrowLeft':
      prev()
      break
    case 'ArrowRight':
      next()
      break
    case 'ArrowUp':
    case '+':
    case '=':
      zoomIn()
      break
    case 'ArrowDown':
    case '-':
      zoomOut()
      break
    case 'Escape':
      resetZoom()
      break
  }
}

/** 回到漫画库：tab 保持打开，随时可切回来 */
function goLibrary() {
  activateLibrary()
  router.push('/')
}

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <div class="reader">
    <div class="reader-toolbar">
      <button type="button" @click="goLibrary">← 返回</button>
      <span class="reader-manga-name" :title="mangaName">{{ mangaName }}</span>
      <span class="reader-page-indicator">
        {{
          total
            ? `${current + 1}${pageMode === 'double' && current + 1 < total ? `-${current + 2}` : ''} / ${total}`
            : ''
        }}
      </span>
      <div class="reader-modes">
        <button
          type="button"
          :class="{ 'is-on': pageMode === 'single' }"
          @click="pageMode = 'single'"
        >单页</button>
        <button
          type="button"
          :class="{ 'is-on': pageMode === 'double' }"
          @click="pageMode = 'double'"
        >双页</button>
        <button
          type="button"
          :class="{ 'is-on': readMode === 'page' }"
          @click="readMode = 'page'"
        >翻页</button>
        <button
          type="button"
          :class="{ 'is-on': readMode === 'scroll' }"
          @click="readMode = 'scroll'"
        >滚动</button>
      </div>
      <div class="reader-zoom">
        <button type="button" @click="zoomOut">−</button>
        <button type="button" @click="resetZoom">{{ Math.round(scale * 100) }}%</button>
        <button type="button" @click="zoomIn">＋</button>
      </div>
    </div>

    <div v-if="total" class="reader-stage">
      <!-- 滚动模式：全部页面纵向排列，原生 loading=lazy 按需加载 -->
      <template v-if="readMode === 'scroll'">
        <img
          v-for="i in total"
          :key="i"
          :src="mangaPageUrl(mangaId, i - 1)"
          :alt="mangaName"
          loading="lazy"
          :style="{ width: `${scale * 100}%` }"
        />
      </template>
      <div v-else class="reader-spread">
        <img
          v-if="currentUrl"
          :src="currentUrl"
          :alt="mangaName"
          :style="{ width: `${scale * 100}%` }"
          @click="next"
        />
        <img
          v-if="pageMode === 'double' && nextUrl"
          :src="nextUrl"
          :alt="mangaName"
          :style="{ width: `${scale * 100}%` }"
          @click="next"
        />
      </div>
    </div>
    <p v-else class="state">暂无页面信息</p>

    <div class="reader-controls">
      <button type="button" :disabled="current <= 0" @click="prev">上一页</button>
      <button type="button" :disabled="current >= total - 1" @click="next">下一页</button>
    </div>
  </div>
</template>

<style scoped>
.reader {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.reader-toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 8px 16px;
  background: var(--bg-elevated);
  border-bottom: 1px solid var(--border);
}

.reader-manga-name {
  max-width: 240px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 14px;
  font-weight: 500;
}

.reader-page-indicator {
  flex: 1;
  text-align: center;
  color: var(--text-dim);
  font-size: 14px;
}

.reader-zoom {
  display: flex;
  gap: 4px;
}

.reader-modes {
  display: flex;
  gap: 4px;
}

.reader-modes button {
  padding: 0.35em 0.7em;
  font-size: 13px;
}

.reader-modes button.is-on {
  border-color: var(--accent);
  color: var(--accent);
}

.reader-stage {
  flex: 1;
  overflow: auto;
  padding: 16px;
  background: #000;
}

.reader-stage img {
  display: block;
  margin: 0 auto;
}

.reader-spread {
  display: flex;
  align-items: flex-start;
  justify-content: center;
  gap: 8px;
}

/* 点击翻页只属于翻页模式 */
.reader-spread img {
  cursor: pointer;
}

.reader-controls {
  display: flex;
  justify-content: center;
  gap: 12px;
  padding: 12px;
  background: var(--bg-elevated);
  border-top: 1px solid var(--border);
}

.state {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-dim);
}
</style>
