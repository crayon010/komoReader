<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';

import { activeMangaId, tabsState } from '@/stores/tabs';

const router = useRouter();

const isDark = ref((document.documentElement.dataset.theme ?? 'dark') === 'dark');

function toggleTheme() {
  isDark.value = !isDark.value;
  document.documentElement.dataset.theme = isDark.value ? 'dark' : 'light';
  localStorage.setItem('theme', isDark.value ? 'dark' : 'light');
}

// 激活态由 tab 状态驱动：阅读 tab 打开时「阅读库」导航不再误亮
function isActive(target: 'library' | 'settings') {
  return tabsState.activeType === target && activeMangaId.value === '';
}
</script>

<template>
  <header class="app-header">
    <a href="/" class="app-logo" @click.prevent="router.push('/')">漫画/小说/PDF阅读器</a>
    <nav class="app-nav">
      <a
        href="/"
        class="app-nav-link"
        :class="{ 'is-active': isActive('library') }"
        @click.prevent="router.push('/')"
        >阅读库</a
      >
      <a
        href="/settings"
        class="app-nav-link"
        :class="{ 'is-active': isActive('settings') }"
        @click.prevent="router.push('/settings')"
        >设置</a
      >
    </nav>
    <button
      type="button"
      class="app-theme-toggle"
      :title="isDark ? '切换到浅色' : '切换到深色'"
      @click="toggleTheme"
    >
      {{ isDark ? '🌙' : '☀️' }}
    </button>
  </header>
</template>

<style scoped>
.app-header {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 0 20px;
  height: 52px;
  background: var(--bg-elevated);
  border-bottom: 1px solid var(--border);
}

.app-logo {
  font-size: 16px;
  font-weight: 600;
}

.app-nav {
  display: flex;
  gap: 12px;
}

.app-nav-link {
  color: var(--text-dim);
  padding: 4px 8px;
  border-radius: 6px;
}

.app-nav-link.is-active {
  color: var(--text);
  background: var(--bg-card);
}

.app-theme-toggle {
  margin-left: auto;
  padding: 0.35em 0.7em;
  font-size: 15px;
  line-height: 1;
}
</style>
