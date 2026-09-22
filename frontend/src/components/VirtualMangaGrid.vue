<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import type { Manga } from '@/types/manga';
import MangaCard from '@/components/MangaCard.vue';
import { prefetchCovers } from '@/utils/coverLoader';

/**
 * 按行虚拟化的网格
 * 只渲染可视区附近的行
 * 滚动节流 + 立即加载可视区封面，避免延迟。
 */
const props = defineProps<{ items: Manga[] }>();

const emit = defineEmits<{ open: [manga: Manga] }>();

// 与 LibraryView 原网格保持一致：auto-fill、minmax(160px, 1fr)、gap 16px
const MIN_ITEM_WIDTH = 160;
const GAP = 16;
// 预渲染上下各 2 行，快速滚动时不露白
const BUFFER_ROWS = 2;
// 空闲时间预加载可视区上下各几行
const PREFETCH_ROWS = 3;
// 卡片信息区固定高度，需与 MangaCard 的 .manga-card-info 保持一致
const CARD_INFO_HEIGHT = 56;

const viewport = ref<HTMLElement | null>(null);
const viewportWidth = ref(0);
const viewportHeight = ref(0);
const scrollTop = ref(0);

let resizeObserver: ResizeObserver | null = null;
let scrollRafId = 0;
let prefetchScheduled = false;

const columns = computed(() =>
  Math.max(1, Math.floor((viewportWidth.value + GAP) / (MIN_ITEM_WIDTH + GAP))),
);

const rowHeight = computed(() => {
  const totalGap = GAP * (columns.value - 1);
  const colWidth = Math.floor((viewportWidth.value - totalGap) / columns.value);
  // 封面区按 3:4 撑开，加上信息区即为卡片高度
  return Math.round((colWidth * 4) / 3) + CARD_INFO_HEIGHT;
});

/** 卡片封面的实际物理像素宽度，交给加载器归到对应档位 */
const coverPixelWidth = computed(() =>
  Math.round(
    ((viewportWidth.value - GAP * (columns.value - 1)) / columns.value) *
    Math.min(window.devicePixelRatio || 1, 2),
  ),
);

const rowCount = computed(() => Math.ceil(props.items.length / columns.value));

const rowStride = computed(() => rowHeight.value + GAP);
const firstVisibleRow = computed(() =>
  Math.max(0, Math.floor(scrollTop.value / rowStride.value) - BUFFER_ROWS),
);
const visibleRowCount = computed(
  () => Math.ceil(viewportHeight.value / rowStride.value) + 1 + BUFFER_ROWS * 2,
);
const lastVisibleRow = computed(() =>
  Math.min(rowCount.value, firstVisibleRow.value + visibleRowCount.value),
);

function loadVisibleCovers() {
  if (coverPixelWidth.value <= 0) return;
  const visibleStart = firstVisibleRow.value * columns.value;
  const visibleEnd = lastVisibleRow.value * columns.value;
  const visibleMangas = props.items.slice(visibleStart, visibleEnd);
  prefetchCovers(visibleMangas, coverPixelWidth.value);
}

const totalHeight = computed(() =>
  rowCount.value > 0 ? rowCount.value * rowStride.value - GAP : 0,
);
const offsetY = computed(() => firstVisibleRow.value * rowStride.value);

const visibleItems = computed(() => {
  const start = firstVisibleRow.value * columns.value;
  const end = lastVisibleRow.value * columns.value;
  return props.items.slice(start, end);
});

/** 可视区外、上下各 PREFETCH_ROWS 行的条目，立即预加载 */
const prefetchItems = computed(() => {
  if (rowCount.value === 0 || coverPixelWidth.value <= 0) return [];
  const from = Math.max(0, firstVisibleRow.value - PREFETCH_ROWS) * columns.value;
  const to = Math.min(rowCount.value, lastVisibleRow.value + PREFETCH_ROWS) * columns.value;
  const visibleStart = firstVisibleRow.value * columns.value;
  const visibleEnd = lastVisibleRow.value * columns.value;
  return props.items
    .slice(from, to)
    .filter((_, i) => i < visibleStart - from || i >= visibleEnd - from);
});

function loadPrefetchCovers() {
  if (prefetchItems.value.length > 0) {
    prefetchCovers(prefetchItems.value, coverPixelWidth.value);
  }
}

function onScroll() {
  if (scrollRafId) return;
  scrollRafId = window.requestAnimationFrame(() => {
    scrollRafId = 0;
    if (viewport.value) scrollTop.value = viewport.value.scrollTop;
  });
}

function measure() {
  if (!viewport.value) return;
  viewportWidth.value = viewport.value.clientWidth;
  viewportHeight.value = viewport.value.clientHeight;
}

watch(
  () => [firstVisibleRow.value, lastVisibleRow.value, coverPixelWidth.value],
  () => {
    loadVisibleCovers();
    loadPrefetchCovers();
  },
);

onMounted(() => {
  measure();
  resizeObserver = new ResizeObserver(measure);
  resizeObserver.observe(viewport.value!);
  // 页面加载后立即加载可视区封面
  loadVisibleCovers();
  loadPrefetchCovers();
});

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  resizeObserver = null;
  if (scrollRafId) window.cancelAnimationFrame(scrollRafId);
});
</script>

<template>
  <div ref="viewport" class="virtual-grid" @scroll.passive="onScroll">
    <div class="virtual-grid-spacer" :style="{ height: `${totalHeight}px` }">
      <div class="virtual-grid-window" :style="{
        transform: `translateY(${offsetY}px)`,
        gridTemplateColumns: `repeat(${columns}, 1fr)`,
        gridAutoRows: `${rowHeight}px`,
      }">
        <MangaCard v-for="manga in visibleItems" :key="manga.id" :manga="manga" :cover-width="coverPixelWidth"
          @click="emit('open', manga)" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.virtual-grid {
  height: 100%;
  min-height: 0;
  overflow-y: auto;
}

.virtual-grid-spacer {
  position: relative;
}

.virtual-grid-window {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  display: grid;
  gap: 16px;
  will-change: transform;
}
</style>
