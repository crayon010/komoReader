import { computed, reactive } from 'vue';

import type { Manga } from '@/types/manga';

/**
 * 阅读器标签页状态（模块级单例，无需 pinia）。
 *
 * - 最多同时开 5 个阅读 tab，超出时淘汰最久未使用的；
 * - readerTabs 数组按最近使用排序（末尾最新），淘汰时移除头部；
 * - tab 内容组件常驻挂载，由 App.vue 用 v-show 切换，阅读进度得以保留；
 * - 阅读库/设置仍走路由，路由变化由 App.vue 监听后同步到 active。
 */

export const MAX_READER_TABS = 5;

export interface ReaderTab {
  mangaId: string;
  mangaName: string;
  totalPages: number;
  /** manga / novel / pdf；novel|pdf 由 BookView 展示，其余走图片阅读器 */
  type?: string;
  /** 当前阅读进度（百分比 0-1） */
  currentProgress?: number;
}

export type ActiveViewType = 'library' | 'reader' | 'settings';

const state = reactive<{
  readerTabs: ReaderTab[];
  activeType: ActiveViewType;
  activeMangaId: string;
}>({
  readerTabs: [],
  activeType: 'library',
  activeMangaId: '',
});

const activeMangaId = computed(() => (state.activeType === 'reader' ? state.activeMangaId : ''));

export { activeMangaId, state as tabsState };

export function activateLibrary() {
  state.activeType = 'library';
  state.activeMangaId = '';
}

export function activateSettings() {
  state.activeType = 'settings';
  state.activeMangaId = '';
}

/** 激活已打开的 tab，并把它移到末尾标记为最近使用 */
export function activateReaderTab(mangaId: string) {
  const index = state.readerTabs.findIndex((tab) => tab.mangaId === mangaId);
  if (index === -1) return;
  const [tab] = state.readerTabs.splice(index, 1);
  state.readerTabs.push(tab);
  state.activeType = 'reader';
  state.activeMangaId = mangaId;
}

/** 打开（或聚焦）一个阅读 tab；满了就淘汰最久未使用的 */
export function openReaderTab(manga: Manga) {
  if (state.readerTabs.some((tab) => tab.mangaId === manga.id)) {
    activateReaderTab(manga.id);
    return;
  }
  if (state.readerTabs.length >= MAX_READER_TABS) {
    state.readerTabs.shift();
  }
  state.readerTabs.push({
    mangaId: manga.id,
    mangaName: manga.name,
    totalPages: manga.totalPages,
    type: manga.type,
  });
  state.activeType = 'reader';
  state.activeMangaId = manga.id;
}

export function closeReaderTab(mangaId: string) {
  const index = state.readerTabs.findIndex((tab) => tab.mangaId === mangaId);
  if (index === -1) return;
  state.readerTabs.splice(index, 1);
  if (state.activeType === 'reader' && state.activeMangaId === mangaId) {
    activateLibrary();
  }
}

/**
 * 保存阅读进度到 store 和 localStorage。
 * progress 是百分比 0-1；cfi 仅 epub 用，跳转不依赖 locations，可立即渲染。
 * progress 传 undefined 表示当前位置还无法换算成百分比（locations 未生成），此时只存 cfi。
 */
export function saveProgress(mangaId: string, progress?: number, cfi?: string) {
  const tab = state.readerTabs.find((t) => t.mangaId === mangaId);
  if (progress !== undefined) {
    if (tab) {
      tab.currentProgress = progress;
    }
    localStorage.setItem(`tab_progress_${mangaId}`, progress.toString());
  }
  if (cfi) {
    localStorage.setItem(`tab_cfi_${mangaId}`, cfi);
  }
}

/** 从 localStorage 加载阅读进度 */
export function loadProgress(mangaId: string): number | null {
  const saved = localStorage.getItem(`tab_progress_${mangaId}`);
  return saved ? parseFloat(saved) : null;
}

/** 加载 epub 续读位置（CFI） */
export function loadCfi(mangaId: string): string | null {
  return localStorage.getItem(`tab_cfi_${mangaId}`);
}
