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
    <button class="title-button" data-text="Awesome" @click.prevent="router.push('/')">
      <span class="actual-text">&nbsp;komoReader&nbsp;</span>
      <span aria-hidden="true" class="hover-text">&nbsp;komoReader&nbsp;</span>
    </button>
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
.title-button {
  margin: 0;
  height: auto;
  background: transparent;
  padding: 0;
  border: none;
  cursor: pointer;
}

.title-button {
  --border-right: 6px;
  --text-stroke-color: rgba(255, 255, 255, 0.6);
  --animation-color: #59c7ee;
  --fs-size: 16px;
  letter-spacing: 3px;
  text-decoration: none;
  font-size: var(--fs-size);
  font-family: 'Arial';
  position: relative;
  -webkit-text-stroke: 1px var(--text-stroke-color);
}

.hover-text {
  position: absolute;
  box-sizing: border-box;
  content: attr(data-text);
  color: var(--animation-color);
  width: 0%;
  inset: 0;
  border-right: var(--border-right) solid var(--animation-color);
  overflow: hidden;
  transition: 0.5s;
  -webkit-text-stroke: 1px var(--animation-color);
}

.title-button:hover .hover-text {
  width: 100%;
  filter: drop-shadow(0 0 23px var(--animation-color));
}

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
