<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue';
import { useRouter } from 'vue-router';
import ePub, { type Rendition } from 'epubjs';

import { mangaFileUrl } from '@/api/manga';
import { activateLibrary, saveProgress, loadProgress } from '@/stores/tabs';
import { mangaTypeLabel } from '@/types/manga';

const props = defineProps<{
  mangaId: string;
  mangaName: string;
  /** novel / pdf */
  type: string;
}>();

const router = useRouter();

const isTxt = computed(() => props.mangaName.toLowerCase().endsWith('.txt'));
const isEpub = computed(() => props.type === 'novel' && !isTxt.value);
const fileUrl = computed(() => mangaFileUrl(props.mangaId));

const txt = ref('');
const error = ref('');
const epubEl = ref<HTMLElement | null>(null);

// epub.js 的 rendition 不需要响应式，普通变量即可
const rendition = shallowRef<Rendition | null>(null);

// 页码相关
const totalPages = ref<number | undefined>(undefined);
const currentPage = ref<number | undefined>(undefined);

onMounted(async () => {
  try {
    if (isTxt.value) {
      const res = await fetch(fileUrl.value);
      if (!res.ok) throw new Error(`请求失败：${res.status} ${res.statusText}`);
      // 严格 UTF-8 解码失败（GBK 文件必然失败）就退回 GBK
      const buf = await res.arrayBuffer();
      try {
        txt.value = new TextDecoder('utf-8', { fatal: true }).decode(buf);
      } catch {
        txt.value = new TextDecoder('gbk').decode(buf);
      }
    } else if (isEpub.value && epubEl.value) {
      // ponytail: /file 无扩展名，epub.js 会误判为目录，必须显式 openAs
      const book = ePub(fileUrl.value, { openAs: 'epub' });
      rendition.value = book.renderTo(epubEl.value, {
        width: '100%',
        height: '100%',
        spread: 'false',
        flow: 'paginated',
        manager: 'default',
        allowScriptedContent: false,
      });
      // relocated 里的 percentage、按百分比跳转的 display(0-1) 都依赖 locations，必须先生成
      await book.ready;
      await book.locations.generate(1600);

      // 续读上次进度：进度存的是百分比（0-1）
      const saved = loadProgress(props.mangaId);
      if (saved !== null && saved > 0 && saved <= 1) {
        await rendition.value.display(saved);
      } else {
        await rendition.value.display();
        // 导航到第一页（避免渲染默认的 page-list 符号）
        const firstHref = book.spine.get(0)?.href;
        if (firstHref) {
          await rendition.value.display(firstHref);
        }
      }

      // 翻页时保存进度（百分比 0-1，比 CFI 更稳定）
      rendition.value.on('relocated', (location: any) => {
        const percent = location?.start?.percentage ?? 0;
        saveProgress(props.mangaId, percent);

        const locationCount = book.locations.length();
        if (locationCount > 0) {
          totalPages.value = locationCount;
          currentPage.value = Math.min(locationCount, Math.round(percent * locationCount) + 1);
        }
      });
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e);
  }
});

onBeforeUnmount(() => {
  rendition.value?.destroy();
  rendition.value = null;
});

/** 回到阅读库：tab 保持打开 */
function goLibrary() {
  activateLibrary();
  router.push('/');
}
</script>

<template>
  <div class="book">
    <div class="book-toolbar">
      <button type="button" @click="goLibrary">← 返回</button>
      <span class="book-name" :title="mangaName">{{ mangaName }}</span>
      <span class="book-badge" :class="`is-${type}`">{{ mangaTypeLabel(type) }}</span>
      <span class="book-page-info" v-if="isEpub">
        第 {{ currentPage ?? '—' }} / {{ totalPages ?? '—' }} 页
      </span>
    </div>

    <p v-if="error" class="book-state error">{{ error }}</p>
    <!-- PDF：直接用浏览器自带的 PDF 查看器 -->
    <iframe v-else-if="type === 'pdf'" class="book-frame" :src="fileUrl" :title="mangaName" />
    <div v-else-if="isTxt" class="book-text">
      {{ txt || '加载中…' }}
    </div>
    <template v-else>
      <!-- sandbox 属性限制 iframe 权限，避免 "unload is not allowed" 警告 -->
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

.book-page-info {
  font-size: 13px;
  color: var(--text-dim);
  padding: 0 12px;
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
  background: #e5484d;
}

.book-badge.is-pdf {
  background: #f5a623;
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

/* 隐藏 epub.js 默认的 page-list 列表符号（段落前的特殊符号） */
.epub-page-list {
  display: none;
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
