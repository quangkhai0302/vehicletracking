<script setup lang="ts">
import { CalendarClock, Navigation, Route, ShieldCheck } from '@lucide/vue';
</script>
<template>
  <main class="auth-page business-ui">
    <aside class="auth-introduction">
      <div class="auth-product-brand">
        <span><Navigation :size="24" /></span>
        <div><strong>Vehicle Tracking</strong><small>QUẢN LÝ VẬN HÀNH</small></div>
      </div>
      <div class="auth-product-copy">
        <span class="auth-product-eyebrow">KẾT NỐI MỌI HÀNH TRÌNH</span>
        <h2>
          Một không gian.<br />
          Toàn bộ đội xe.
        </h2>
        <p>Từ lập lịch đến giám sát hành trình, chủ động quản lý mọi hoạt động vận tải của bạn.</p>
        <div class="auth-capabilities">
          <span><Route :size="19" />Theo dõi hành trình</span
          ><span><CalendarClock :size="19" />Điều phối lịch chạy</span
          ><span><ShieldCheck :size="19" />Quản lý phân quyền</span>
        </div>
      </div>
      <p class="auth-product-footer">Đồng hành cùng từng chuyến đi.</p>
    </aside>
    <div class="auth-form-pane">
      <section class="auth-card"><slot /></section>
      <p class="auth-pane-footer">Vehicle Tracking · Không gian quản lý vận hành</p>
    </div>
  </main>
</template>
