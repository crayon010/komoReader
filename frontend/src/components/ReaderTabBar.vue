<script setup lang="ts">
import { useRouter } from 'vue-router';

import {
  MAX_READER_TABS,
  activeMangaId,
  closeReaderTab,
  activateReaderTab,
  tabsState,
  activateLibrary,
} from '@/stores/tabs';

const router = useRouter();

function goLibrary() {
  activateLibrary();
  router.push('/');
}
</script>

<template>
  <div v-if="tabsState.readerTabs.length" class="reader-tab-bar" role="tablist">
    <button
      type="button"
      role="tab"
      class="reader-tab"
      :class="{ 'is-active': activeMangaId === '' }"
      :aria-selected="activeMangaId === ''"
      @click="goLibrary"
    >
      阅读库
    </button>
    <div
      v-for="tab in tabsState.readerTabs"
      :key="tab.mangaId"
      class="reader-tab reader-tab-file"
      role="tab"
      tabindex="0"
      :class="{ 'is-active': activeMangaId === tab.mangaId }"
      :aria-selected="activeMangaId === tab.mangaId"
      @click="activateReaderTab(tab.mangaId)"
      @keydown.enter="activateReaderTab(tab.mangaId)"
    >
      <span class="reader-tab-name" :title="tab.mangaName">{{ tab.mangaName }}</span>
      <button
        type="button"
        class="reader-tab-close"
        title="关闭标签页"
        @click.stop="closeReaderTab(tab.mangaId)"
      >
        ×
      </button>
    </div>
    <span class="reader-tab-count">{{ tabsState.readerTabs.length }} / {{ MAX_READER_TABS }}</span>
  </div>
</template>

<style scoped>
.reader-tab-bar {
  display: flex;
  align-items: stretch;
  gap: 4px;
  padding: 4px 12px 0;
  background: var(--bg-elevated);
  border-bottom: 1px solid var(--border);
  overflow-x: auto;
}

.reader-tab {
  display: flex;
  align-items: center;
  gap: 6px;
  max-width: 180px;
  padding: 6px 10px;
  font: inherit;
  font-size: 13px;
  color: var(--text-dim);
  background: transparent;
  border: 1px solid transparent;
  border-bottom: none;
  border-radius: 8px 8px 0 0;
  cursor: pointer;
  white-space: nowrap;
}

.reader-tab:hover {
  color: var(--text);
  background: var(--bg-card);
}

.reader-tab.is-active {
  color: var(--text);
  background: var(--bg-card);
  border-color: var(--border);
}

.reader-tab-name {
  overflow: hidden;
  text-overflow: ellipsis;
}

.reader-tab-close {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  padding: 0;
  font-size: 14px;
  line-height: 1;
  color: var(--text-dim);
  background: transparent;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

.reader-tab-close:hover {
  color: var(--text);
  background: var(--border);
}

.reader-tab-count {
  align-self: center;
  margin-left: auto;
  padding-left: 12px;
  font-size: 12px;
  color: var(--text-dim);
  white-space: nowrap;
}
</style>
