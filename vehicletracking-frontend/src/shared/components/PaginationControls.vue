<script setup lang="ts">
withDefaults(
  defineProps<{
    page: number;
    pageCount: number;
    total: number;
    pageSize?: number;
    label?: string;
  }>(),
  { pageSize: 10, label: 'kết quả' },
);
const emit = defineEmits<{ 'update:page': [page: number] }>();
</script>

<template>
  <nav v-if="total > 0" class="pagination-controls" aria-label="Phân trang">
    <span>
      Hiển thị {{ (page - 1) * pageSize + 1 }}–{{ Math.min(page * pageSize, total) }} / {{ total }} {{ label }}
    </span>
    <div>
      <button type="button" :disabled="page <= 1" @click="emit('update:page', page - 1)">Trước</button>
      <span>Trang {{ page }} / {{ pageCount }}</span>
      <button type="button" :disabled="page >= pageCount" @click="emit('update:page', page + 1)">Tiếp</button>
    </div>
  </nav>
</template>

<style scoped>
.pagination-controls,
.pagination-controls > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.pagination-controls {
  width: 100%;
  padding: 12px 16px;
  color: #64748b;
  border-top: 1px solid #e2e8f0;
  font-size: 12px;
}
.pagination-controls > div {
  flex: 0 0 auto;
}
.pagination-controls button {
  min-height: 32px;
  padding: 5px 10px;
  color: #17688c;
  background: #fff;
  border: 1px solid #d5e2ef;
  border-radius: 7px;
  cursor: pointer;
  font: inherit;
}
.pagination-controls button:disabled {
  cursor: default;
  opacity: 0.45;
}
@media (max-width: 480px) {
  .pagination-controls {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
