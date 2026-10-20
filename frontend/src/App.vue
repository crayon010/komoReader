<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { RouterView, useRoute } from 'vue-router';

import AppHeader from '@/components/AppHeader.vue';
import ReaderTabBar from '@/components/ReaderTabBar.vue';
import BookView from '@/views/BookView.vue';
import ReaderView from '@/views/ReaderView.vue';
import { activateLibrary, activateSettings, activeMangaId, tabsState } from '@/stores/tabs';

const route = useRoute();

// 浏览器前进/后退、顶部导航都会走路由，这里把路由同步到激活视图
watch(
  () => route.name,
  (name) => {
    if (name === 'library') activateLibrary();
    else if (name === 'settings') activateSettings();
  },
);

// 任一阅读 tab 激活时收起顶栏
const isReading = computed(() => activeMangaId.value !== '');

const topbarHidden = ref(false);
const TOP_ZONE = 100;
// 鼠标离开后延迟再收起，避免误触
const HIDE_DELAY = 400;
const TOP_BARS = '.app-topbar, .reader-toolbar, .book-toolbar';
let mouseY = Number.POSITIVE_INFINITY;
let hideTimer = 0;

function inTopBars(target: EventTarget | null) {
  return target instanceof Element && !!target.closest(TOP_BARS);
}

function showTopbar() {
  if (hideTimer) {
    clearTimeout(hideTimer);
    hideTimer = 0;
  }
  topbarHidden.value = false;
}

function scheduleHide() {
  if (hideTimer) return;
  hideTimer = window.setTimeout(() => {
    hideTimer = 0;
    topbarHidden.value = true;
  }, HIDE_DELAY);
}

function onMouseMove(e: MouseEvent) {
  mouseY = e.clientY;
  if (!isReading.value) return;
  if (mouseY <= TOP_ZONE || inTopBars(e.target)) showTopbar();
  else scheduleHide();
}

function onMouseOut(e: MouseEvent) {
  if (!isReading.value) return;
  if (inTopBars(e.target) && !inTopBars(e.relatedTarget)) scheduleHide();
}

watch(isReading, (reading) => {
  if (hideTimer) {
    clearTimeout(hideTimer);
    hideTimer = 0;
  }
  topbarHidden.value = reading && mouseY > TOP_ZONE;
});

onMounted(() => {
  window.addEventListener('mousemove', onMouseMove, { passive: true });
  window.addEventListener('mouseout', onMouseOut, { passive: true });
});
onBeforeUnmount(() => {
  window.removeEventListener('mousemove', onMouseMove);
  window.removeEventListener('mouseout', onMouseOut);
  if (hideTimer) clearTimeout(hideTimer);
});
</script>

<template>
  <div class="app-shell">
    <div class="app-topbar" :class="{ 'is-hidden': topbarHidden }" :inert="topbarHidden">
      <div class="app-topbar-inner">
        <div class="app-topbar-content">
          <AppHeader />
          <ReaderTabBar />
        </div>
      </div>
    </div>
    <main class="app-main">
      <!-- 阅读库/设置：KeepAlive 缓存阅读库，从阅读 tab 回来不重新请求 -->
      <div v-show="activeMangaId === ''" class="app-page">
        <RouterView v-slot="{ Component }">
          <KeepAlive include="LibraryView">
            <component :is="Component" />
          </KeepAlive>
        </RouterView>
      </div>
      <!-- 阅读 tab：全部常驻挂载，v-show 切换，保留各自的阅读进度 -->
      <div
        v-for="tab in tabsState.readerTabs"
        :key="tab.mangaId"
        v-show="activeMangaId === tab.mangaId"
        class="app-page"
      >
        <!-- 小说/PDF 走 BookView，阅读走图片阅读器 -->
        <BookView
          v-if="tab.type === 'novel' || tab.type === 'pdf'"
          :manga-id="tab.mangaId"
          :manga-name="tab.mangaName"
          :type="tab.type"
        />
        <ReaderView
          v-else
          :manga-id="tab.mangaId"
          :manga-name="tab.mangaName"
          :total-pages="tab.totalPages"
          :active="activeMangaId === tab.mangaId"
        />
      </div>
    </main>
  </div>
</template>

<style scoped>
.app-shell {
  display: flex;
  flex-direction: column;
  height: 100vh;
}

.app-topbar {
  display: grid;
  grid-template-rows: 1fr;
  transition: grid-template-rows 0.45s cubic-bezier(0.4, 0, 0.2, 1);
}

.app-topbar.is-hidden {
  grid-template-rows: 0fr;
}

.app-topbar-inner {
  min-height: 0;
  overflow: hidden;
}

.app-topbar-content {
  transition: transform 0.45s cubic-bezier(0.4, 0, 0.2, 1);
}

.app-topbar.is-hidden .app-topbar-content {
  transform: translateY(-100%);
}

@media (prefers-reduced-motion: reduce) {
  .app-topbar,
  .app-topbar-content {
    transition: none;
  }
}

.app-main {
  flex: 1;
  overflow: hidden;
}

.app-page {
  height: 100%;
}
</style>
