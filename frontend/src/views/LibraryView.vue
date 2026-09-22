<script setup lang="ts">
import { onMounted, ref } from 'vue';

import { fetchMangaList } from '@/api/manga';
import type { Manga } from '@/types/manga';
import VirtualMangaGrid from '@/components/VirtualMangaGrid.vue';
import { openReaderTab } from '@/stores/tabs';

defineOptions({ name: 'LibraryView' });

const mangaList = ref<Manga[]>([]);
const loading = ref(false);
const error = ref('');

async function load() {
  loading.value = true;
  error.value = '';
  try {
    const res = await fetchMangaList();
    mangaList.value = res.data;
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e);
  } finally {
    loading.value = false;
  }
}

function openReader(manga: Manga) {
  openReaderTab(manga);
}

onMounted(load);
</script>

<template>
  <div class="library">
    <div class="library-toolbar">
      <h1>阅读库</h1>
      <button type="button" :disabled="loading" @click="load">刷新</button>
    </div>

    <p v-if="loading" class="state">加载中…</p>
    <p v-else-if="error" class="state error">{{ error }}</p>
    <p v-else-if="mangaList.length === 0" class="state">暂无文件，去「设置」导入文件夹</p>
    <VirtualMangaGrid v-else :items="mangaList" @open="openReader" />
  </div>
</template>

<style scoped>
.library {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 20px;
}

.library-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  flex-shrink: 0;
}

.library-toolbar h1 {
  margin: 0;
  font-size: 20px;
}

.library > :not(.library-toolbar) {
  flex: 1;
  min-height: 0;
}

.state {
  color: var(--text-dim);
  padding: 40px 0;
  text-align: center;
}

.state.error {
  color: #ff6b6b;
}
</style>
