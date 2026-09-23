<script setup lang="ts">
import { ArrowLeft, MapPinOff } from '@lucide/vue';
import { RouterLink } from 'vue-router';
</script>
<template>
  <div class="business-page not-found-page">
    <section class="business-surface">
      <MapPinOff :size="34" /><span>LỖI 404</span>
      <h2>Trang bạn cần không tồn tại</h2>
      <p>Đường dẫn có thể đã thay đổi hoặc chưa được cấu hình trong hệ thống.</p>
      <RouterLink to="/dashboard"><ArrowLeft :size="16" />Quay lại dashboard</RouterLink>
    </section>
  </div>
</template>
