<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';

import { fetchMangaList, fetchRoots, removeRoot } from '@/api/manga';
import type { Manga, MangaRoot } from '@/types/manga';
import VirtualMangaGrid from '@/components/VirtualMangaGrid.vue';
import { resetFailedCovers } from '@/utils/coverLoader';
import { openReaderTab } from '@/stores/tabs';

defineOptions({ name: 'LibraryView' });

const roots = ref<MangaRoot[]>([]);
const currentRoot = ref<MangaRoot | null>(null);
const mangaList = ref<Manga[]>([]);
const loading = ref(false);
const error = ref('');
// 搜索只在进入文件夹后可用，过滤当前文件夹内条目
const query = ref('');

const filtered = computed(() => {
  const q = query.value.trim().toLowerCase();
  if (!q) return mangaList.value;
  return mangaList.value.filter((m) => m.name.toLowerCase().includes(q));
});

/** 第一级：加载导入的文件夹列表（只读名字，不扫描内容） */
async function loadRoots() {
  loading.value = true;
  error.value = '';
  try {
    roots.value = (await fetchRoots()).data;
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e);
  } finally {
    loading.value = false;
  }
}

/** 第二级：扫描当前文件夹内容 */
async function load() {
  if (!currentRoot.value) return;
  loading.value = true;
  error.value = '';
  resetFailedCovers(); // 上次加载失败的封面这次重试
  try {
    mangaList.value = (await fetchMangaList(currentRoot.value.path)).data;
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e);
  } finally {
    loading.value = false;
  }
}

function enterFolder(root: MangaRoot) {
  currentRoot.value = root;
  mangaList.value = [];
  query.value = '';
  load();
}

function backToRoots() {
  currentRoot.value = null;
  query.value = '';
  error.value = '';
}

async function deleteRoot(root: MangaRoot) {
  if (!window.confirm(`删除「${root.name}」？磁盘文件不会被删除`)) return;
  try {
    await removeRoot(root.path);
    await loadRoots();
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e);
  }
}

function openReader(manga: Manga) {
  openReaderTab(manga);
}

onMounted(loadRoots);
</script>

<template>
  <div class="library">
    <div class="library-toolbar">
      <button v-if="currentRoot" type="button" @click="backToRoots">← 返回</button>
      <h1>{{ currentRoot ? currentRoot.name : '阅读库' }}</h1>
      <div v-if="currentRoot" class="form__group">
        <input v-model="query" type="text" class="form__field" placeholder=" " />
        <label class="form__label">按名称搜索</label>
      </div>
      <button type="button" :disabled="loading" @click="currentRoot ? load() : loadRoots()">
        刷新
      </button>
    </div>

    <!-- 第一级：导入的文件夹 -->
    <div v-if="!currentRoot" class="root-list">
      <p v-if="loading" class="state">加载中…</p>
      <p v-else-if="error" class="state error">{{ error }}</p>
      <p v-else-if="roots.length === 0" class="state">暂无文件夹，去「设置」导入</p>
      <div v-else class="root-grid">
        <div v-for="r in roots" :key="r.path" class="book" @click="enterFolder(r)">
          <div class="book-inside">
            <p class="book-count">{{ r.itemCount }} 部</p>
            <p class="book-path" :title="r.path">{{ r.path }}</p>
          </div>
          <button type="button" class="root-del" @click.stop="deleteRoot(r)">×</button>
          <div class="book-cover">
            <p class="book-name">{{ r.name }}</p>
          </div>
        </div>
      </div>
    </div>

    <!-- 第二级：文件夹内容 -->
    <template v-else>
      <p v-if="loading" class="state">加载中…</p>
      <p v-else-if="error" class="state error">{{ error }}</p>
      <p v-else-if="mangaList.length === 0" class="state">文件夹内暂无支持的文件</p>
      <p v-else-if="filtered.length === 0" class="state">没有匹配「{{ query }}」的结果</p>
      <VirtualMangaGrid v-else :items="filtered" @open="openReader" />
    </template>
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
  gap: 12px;
  margin-bottom: 16px;
  flex-shrink: 0;
}

.library-toolbar h1 {
  margin: 0;
  font-size: 20px;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.form__group {
  position: relative;
  padding: 12px 0 0;
  width: 200px;
  flex-shrink: 0;
}

.form__field {
  width: 100%;
  border: none;
  border-bottom: 2px solid var(--border);
  outline: 0;
  font-size: 14px;
  color: var(--text);
  padding: 6px 0;
  background: transparent;
  transition: border-color 0.2s;
}

.form__field::placeholder {
  color: transparent;
}

.form__field:placeholder-shown ~ .form__label {
  font-size: 14px;
  cursor: text;
  top: 12px;
}

.form__label {
  position: absolute;
  top: 0;
  left: 0;
  display: block;
  transition: 0.2s;
  font-size: 14px;
  color: var(--text-dim);
  pointer-events: none;
}

.form__field:focus {
  padding-bottom: 5px;
  font-weight: 700;
  border-width: 3px;
  border-image: linear-gradient(to right, #116399, #38caef);
  border-image-slice: 1;
}

.form__field:focus ~ .form__label {
  top: 0;
  color: #38caef;
  font-weight: 700;
}

.library > :not(.library-toolbar) {
  flex: 1;
  min-height: 0;
}

.root-list {
  overflow-y: auto;
  padding: 24px 8px 24px 28px;
}

.root-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 20px;
  align-content: start;
  padding: 4px 0 8px;
}

.book {
  position: relative;
  border-radius: 10px;
  height: 280px;
  background-color: whitesmoke;
  box-shadow: 1px 1px 12px #000;
  transform: preserve-3d;
  perspective: 2000px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #000;
  cursor: pointer;
}

.book-inside {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 20px 16px;
  text-align: center;
  width: 76%;
  margin-left: auto;
}

.book-count {
  margin: 0;
  font-size: 20px;
  font-weight: bolder;
}

.book-path {
  margin: 0;
  font-size: 12px;
  color: #555;
  word-break: break-all;
  display: -webkit-box;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.book-cover {
  top: 0;
  position: absolute;
  background-color: lightgray;
  width: 100%;
  height: 100%;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.5s;
  transform-origin: 0;
  box-shadow: 1px 1px 12px #000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
}

.book:hover .book-cover {
  transition: all 0.5s;
  transform: rotatey(-80deg);
}

.book-name {
  margin: 0;
  font-size: 20px;
  font-weight: bolder;
  text-align: center;
  word-break: break-all;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.root-del {
  position: absolute;
  top: 4px;
  right: 8px;
  padding: 0 4px;
  font-size: 18px;
  line-height: 1.4;
  color: #b8b8b8;
  background: none;
  border: none;
  cursor: pointer;
}

.root-del:hover {
  color: #c0392b;
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
