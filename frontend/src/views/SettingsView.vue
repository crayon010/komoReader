<script setup lang="ts">
import { ref } from 'vue';

import { importFolder } from '@/api/manga';

const path = ref('');
const loading = ref(false);
const message = ref('');

async function submit() {
  const p = path.value.trim();
  if (!p || loading.value) return;

  loading.value = true;
  message.value = '';
  try {
    const res = await importFolder(p);
    message.value = res.message || '导入成功';
    path.value = '';
  } catch (e) {
    message.value = e instanceof Error ? e.message : String(e);
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="settings">
    <h1>设置</h1>

    <form class="settings-form" @submit.prevent="submit">
      <label for="folder-path">文件夹路径</label>
      <p class="settings-hint">
        填写后端可访问的本地文件夹路径（支持 zip / cbz 等压缩包），提交后由后端扫描并导入。
      </p>
      <input
        id="folder-path"
        v-model="path"
        type="text"
        placeholder="例如：D:\manga"
        autocomplete="off"
      />

      <button type="submit" :disabled="loading || !path.trim()">
        {{ loading ? '导入中…' : '导入' }}
      </button>
    </form>
    <p v-if="message" class="settings-message">{{ message }}</p>
  </div>
</template>

<style scoped>
.settings {
  height: 100%;
  overflow-y: auto;
  padding: 20px;
  max-width: 640px;
}

.settings h1 {
  margin: 0 0 16px;
  font-size: 20px;
}

.settings-form {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.settings-form label {
  font-weight: 500;
}

.settings-hint {
  margin: 0;
  font-size: 13px;
  color: var(--text-dim);
}

.settings-form input {
  font: inherit;
  padding: 0.5em 0.75em;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--bg-card);
  color: var(--text);
}

.settings-form input:focus {
  outline: none;
  border-color: var(--accent);
}

.settings-message {
  margin-top: 12px;
  font-size: 14px;
  color: var(--accent);
}
</style>
