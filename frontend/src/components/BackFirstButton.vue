<script setup lang="ts">
defineEmits<{ click: [] }>()
</script>

<template>
  <button type="button" class="back-first" title="回到第一页" @click="$emit('click')">
    回到第一页
  </button>
</template>

<style scoped>
.back-first {
  position: fixed;
  right: 20px;
  bottom: 20px;
  z-index: 10;
  padding: 8px 14px;
  font-size: 13px;
  border-radius: 999px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  color: var(--text);
  cursor: pointer;
  opacity: 0.75;
}

.back-first:hover {
  opacity: 1;
  border-color: var(--accent);
  color: var(--accent);
}
</style>
