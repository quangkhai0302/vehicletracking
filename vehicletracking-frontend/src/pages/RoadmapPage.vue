<script setup lang="ts">
import { ArrowRight, CheckCircle2, CircleDashed, Construction } from '@lucide/vue';
import { RouterLink } from 'vue-router';
defineProps<{
  title: string;
  summary: string;
  capabilities: string[];
  dependency: string;
  nextPath: string;
  nextLabel: string;
}>();
</script>
<template>
  <div class="business-page roadmap-page">
    <section class="roadmap-hero business-surface">
      <span class="roadmap-icon"><Construction :size="24" /></span>
      <div>
        <span>ĐANG TRONG LỘ TRÌNH</span>
        <h2>{{ title }}</h2>
        <p>{{ summary }}</p>
      </div>
    </section>
    <div class="roadmap-grid">
      <section class="business-surface roadmap-card">
        <h2>Phạm vi dự kiến</h2>
        <ul>
          <li
            v-for="item in capabilities"
            :key="item"
          >
            <CircleDashed :size="16" /><span>{{ item }}</span>
          </li>
        </ul>
      </section>
      <section class="business-surface roadmap-card roadmap-dependency">
        <h2>Điều kiện để triển khai</h2>
        <p>{{ dependency }}</p>
        <div>
          <CheckCircle2 :size="16" /><span
            >Không hiển thị dữ liệu mẫu như dữ liệu vận hành thật.</span
          >
        </div>
      </section>
    </div>
    <RouterLink
      class="roadmap-next-link"
      :to="nextPath"
      >{{ nextLabel }}<ArrowRight :size="16"
    /></RouterLink>
  </div>
</template>
