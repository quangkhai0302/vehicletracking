<script setup lang="ts">
import { ref } from 'vue';
import {
  ArrowLeft,
  ArrowRight,
  LockKeyhole,
  Navigation,
  TriangleAlert,
  UserRound,
} from '@lucide/vue';
import { RouterLink } from 'vue-router';
import { registerAdmin } from '@/features/auth/api/auth';
import AuthLayout from '@/app/layouts/AuthLayout.vue';
import '@/features/auth/styles/auth-pages.css';
const username = ref(''),
  password = ref(''),
  confirmPassword = ref('');
const busy = ref(false),
  error = ref<string | null>(null),
  success = ref(false);
async function submit() {
  if (busy.value) return;
  error.value = null;
  if (password.value !== confirmPassword.value) {
    error.value = 'Mật khẩu xác nhận không khớp.';
    return;
  }
  busy.value = true;
  try {
    await registerAdmin({ username: username.value.trim(), password: password.value });
    success.value = true;
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : 'Không thể tạo tài khoản admin.';
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
      <span>ĐĂNG KÝ TÀI KHOẢN</span>
      <h1>Tạo tài khoản quản trị</h1>
      <p>Tạo tài khoản admin để bắt đầu quản lý vận hành.</p>
    </div>
    <div
      v-if="error"
      class="auth-error"
      role="alert"
    >
      <TriangleAlert :size="17" /><span>{{ error }}</span>
    </div>
    <div
      v-if="success"
      class="auth-success"
      role="status"
    >
      <strong>Đăng ký thành công</strong
      ><span
        >Tài khoản <b>{{ username.trim().toLowerCase() }}</b> đã được tạo. Bạn có thể đăng nhập
        ngay.</span
      ><RouterLink to="/login">Đi tới đăng nhập <ArrowRight :size="15" /></RouterLink>
    </div>
    <form
      v-else
      class="auth-form"
      @submit.prevent="submit"
    >
      <label
        ><span>Tên đăng nhập</span>
        <div class="auth-input">
          <UserRound :size="16" /><input
            v-model="username"
            required
            minlength="3"
            maxlength="100"
            autocomplete="username"
          /></div
      ></label>
      <label
        ><span>Mật khẩu (tối thiểu 12 ký tự)</span>
        <div class="auth-input">
          <LockKeyhole :size="16" /><input
            v-model="password"
            required
            minlength="12"
            maxlength="100"
            type="password"
            autocomplete="new-password"
          /></div
      ></label>
      <label
        ><span>Xác nhận mật khẩu</span>
        <div class="auth-input">
          <LockKeyhole :size="16" /><input
            v-model="confirmPassword"
            required
            minlength="12"
            maxlength="100"
            type="password"
            autocomplete="new-password"
          /></div
      ></label>
      <button
        type="submit"
        :disabled="busy"
      >
        {{ busy ? 'Đang tạo tài khoản…' : 'Tạo tài khoản admin' }}<ArrowRight :size="16" />
      </button>
    </form>
    <p class="auth-note">Tài khoản mới được tạo với quyền quản trị hệ thống.</p>
    <RouterLink
      class="auth-link auth-back-link"
      to="/login"
      ><ArrowLeft :size="14" />Quay lại đăng nhập</RouterLink
    >
  </AuthLayout>
</template>
