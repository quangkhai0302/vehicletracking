<script setup lang="ts">
import { ref } from 'vue';
import { ArrowRight, LockKeyhole, Navigation, UserRound } from '@lucide/vue';
import { RouterLink, useRouter } from 'vue-router';
import { useAuth } from '@/features/auth/composables/useAuth';
import { authHome } from '@/app/router/guards';
import AuthLayout from '@/app/layouts/AuthLayout.vue';
import { notifyError } from '@/shared/notifications/toast';
import '@/features/auth/styles/auth-pages.css';
const auth = useAuth();
const router = useRouter();
const username = ref(''),
  password = ref(''),
  busy = ref(false);
async function submit() {
  if (busy.value) return;
  busy.value = true;
  const requested: unknown = window.history.state?.from;
  try {
    const next = await auth.login({ username: username.value.trim(), password: password.value });
    await router.replace(
      typeof requested === 'string' && requested && next.role === 'ADMIN'
        ? requested
        : authHome(next),
    );
  } catch (reason) {
    notifyError(reason instanceof Error ? reason.message : 'Không thể đăng nhập.');
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <AuthLayout>
    <div class="auth-brand">
      <span><Navigation :size="20" /></span>
      <div><strong>Vehicle Tracking</strong><small>Operations Manager</small></div>
    </div>
    <div class="auth-heading">
      <span>ĐĂNG NHẬP HỆ THỐNG</span>
      <h1>Chào mừng trở lại</h1>
      <p>Đăng nhập để xem đúng không gian được phân quyền.</p>
    </div>
    <form
      class="auth-form"
      @submit.prevent="submit"
    >
      <label
        ><span>Tên đăng nhập</span>
        <div class="auth-input">
          <UserRound :size="16" /><input
            v-model="username"
            required
            autocomplete="username"
          /></div
      ></label>
      <label
        ><span>Mật khẩu</span>
        <div class="auth-input">
          <LockKeyhole :size="16" /><input
            v-model="password"
            required
            type="password"
            autocomplete="current-password"
          /></div
      ></label>
      <button
        type="submit"
        :disabled="busy"
      >
        {{ busy ? 'Đang xác thực…' : 'Đăng nhập' }}<ArrowRight :size="16" />
      </button>
    </form>
    <p class="auth-note">Tài khoản tài xế chỉ xem các chuyến và lịch được phân công.</p>
    <RouterLink
      class="auth-link"
      to="/register"
      >Đăng ký tài khoản quản trị</RouterLink
    >
  </AuthLayout>
</template>
