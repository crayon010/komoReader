<script setup lang="ts">
import { watch } from 'vue';
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
</script>

<template>
  <div class="app-shell">
    <AppHeader />
    <ReaderTabBar />
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

.app-main {
  flex: 1;
  overflow: hidden;
}

.app-page {
  height: 100%;
}
</style>
