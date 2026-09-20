<script setup lang="ts">
import { ref, watch } from 'vue'

import type { Manga } from '@/types/manga'
import { mangaTypeLabel } from '@/types/manga'
import { loadCover } from '@/utils/coverLoader'

const props = withDefaults(
  defineProps<{
    manga: Manga
    /** 卡片实际显示宽度（px），用于请求对应档位的缩略图 */
    coverWidth?: number
  }>(),
  { coverWidth: 320 },
)

const coverSrc = ref('')
const loaded = ref(false)
const failed = ref(false)

let requestToken = 0

watch(
  () => [props.manga, props.coverWidth] as const,
  async ([manga, width]) => {
    const token = ++requestToken
    coverSrc.value = ''
    loaded.value = false
    failed.value = false
    try {
      const url = await loadCover(manga, width)
      if (token !== requestToken) return
      coverSrc.value = url
    } catch {
      if (token === requestToken) failed.value = true
    }
  },
  { immediate: true },
)
</script>

<template>
  <article class="manga-card">
    <div class="manga-card-cover">
      <span class="manga-card-badge" :class="`is-${manga.type ?? 'manga'}`">
        {{ mangaTypeLabel(manga.type) }}
      </span>
      <img
        v-if="coverSrc"
        :src="coverSrc"
        :alt="manga.name"
        decoding="async"
        draggable="false"
        :class="{ 'is-loaded': loaded }"
        @load="loaded = true"
      />
      <span v-else-if="failed" class="manga-card-cover-fallback" :title="manga.name">{{
        manga.name
      }}</span>
      <span v-if="!loaded && !failed" class="manga-card-cover-skeleton" aria-hidden="true" />
    </div>
    <div class="manga-card-info">
      <h3 class="manga-card-name" :title="manga.name">{{ manga.name }}</h3>
      <p v-if="manga.totalPages != null" class="manga-card-pages">{{ manga.totalPages }} 页</p>
    </div>
  </article>
</template>

<style scoped>
.manga-card {
  display: flex;
  flex-direction: column;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.15s ease, border-color 0.15s ease;
}

.manga-card:hover {
  transform: translateY(-2px);
  border-color: var(--accent);
}

.manga-card-cover {
  position: relative;
  flex: 1;
  min-height: 0;
  overflow: hidden;
  background: #000;
}

.manga-card-badge {
  position: absolute;
  top: 6px;
  right: 6px;
  z-index: 1;
  padding: 2px 8px;
  font-size: 12px;
  line-height: 1.4;
  color: #fff;
  border-radius: 4px;
  pointer-events: none;
}

.manga-card-badge.is-manga {
  background: #2f6fe0;
}

.manga-card-badge.is-novel {
  background: #f5a623;
}

.manga-card-badge.is-pdf {
  background: #e5484d;
}

.manga-card-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  opacity: 0;
  transition: opacity 0.25s ease;
}

.manga-card-cover img.is-loaded {
  opacity: 1;
}

.manga-card-cover-skeleton {
  position: absolute;
  inset: 0;
  background: linear-gradient(100deg, #1a1a1f 40%, #26262c 50%, #1a1a1f 60%);
  background-size: 200% 100%;
  animation: manga-card-shimmer 1.4s ease-in-out infinite;
}

@keyframes manga-card-shimmer {
  from {
    background-position: 120% 0;
  }
  to {
    background-position: -80% 0;
  }
}

.manga-card-cover-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 8px;
  font-size: 13px;
  color: var(--text-dim);
  text-align: center;
  word-break: break-all;
  overflow: hidden;
}

/* 高度需与 VirtualMangaGrid 的 CARD_INFO_HEIGHT 保持一致 */
.manga-card-info {
  box-sizing: border-box;
  height: 56px;
  padding: 10px 12px;
}

.manga-card-name {
  margin: 0;
  font-size: 14px;
  font-weight: 500;
  line-height: 1.2;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.manga-card-pages {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.2;
  color: var(--text-dim);
}
</style>
