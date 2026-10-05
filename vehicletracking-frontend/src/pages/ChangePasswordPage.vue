<script setup lang="ts">
import { computed, onScopeDispose, reactive, ref } from 'vue';
import { ArrowLeft, KeyRound, LockKeyhole, LogOut, Navigation } from '@lucide/vue';
import { useRouter } from 'vue-router';
import AuthLayout from '@/app/layouts/AuthLayout.vue';
import { useAuth } from '@/features/auth/composables/useAuth';
import { notifyError, notifySuccess } from '@/shared/notifications/toast';
import '@/features/auth/styles/auth-pages.css';

const auth = useAuth();
const router = useRouter();
const required = computed(() => auth.user?.passwordChangeRequired === true);
const form = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' });
const busy = ref(false);
let disposed = false;
function clearForm() {
  Object.assign(form, { currentPassword: '', newPassword: '', confirmPassword: '' });
}
onScopeDispose(() => {
  disposed = true;
  clearForm();
});

async function submit() {
  if (busy.value) return;
  if (!form.currentPassword) {
    notifyError('Hãy nhập mật khẩu hiện tại.');
    return;
  }
  const newPasswordLength = Array.from(form.newPassword).length;
  if (newPasswordLength < 8 || newPasswordLength > 100) {
    notifyError('Mật khẩu mới phải dài từ 8 đến 100 ký tự.');
    return;
  }
  if (new TextEncoder().encode(form.newPassword).length > 72) {
    notifyError('Mật khẩu quá dài. Hãy dùng mật khẩu ngắn hơn, nhất là khi có ký tự có dấu.');
    return;
  }
  if (form.newPassword !== form.confirmPassword) {
    notifyError('Mật khẩu xác nhận không khớp.');
    return;
  }
  if (form.newPassword === form.currentPassword) {
    notifyError('Mật khẩu mới phải khác mật khẩu hiện tại.');
    return;
  }
  busy.value = true;
  try {
    await auth.changePassword({ ...form });
    clearForm();
    notifySuccess('Đã đổi mật khẩu. Vui lòng đăng nhập lại bằng mật khẩu mới.');
    await router.replace('/login');
  } catch (reason) {
    if (!disposed)
      notifyError(reason instanceof Error ? reason.message : 'Không thể đổi mật khẩu.');
  } finally {
    if (!disposed) busy.value = false;
  }
}

async function signOut() {
  if (busy.value) return;
  busy.value = true;
  clearForm();
  try {
    await auth.logout();
  } catch (reason) {
    if (!disposed) notifyError(reason instanceof Error ? reason.message : 'Không thể đăng xuất.');
  } finally {
    await router.replace('/login');
    if (!disposed) busy.value = false;
  }
}
</script>

<template>
  <AuthLayout>
    <div class="auth-brand">
      <span><Navigation :size="20" /></span>
      <div><strong>Vehicle Tracking</strong><small>Cổng tài xế</small></div>
    </div>
    <div class="auth-heading">
      <span>{{ required ? 'THIẾT LẬP MẬT KHẨU CỦA BẠN' : 'BẢO MẬT TÀI KHOẢN' }}</span>
      <h1>Đổi mật khẩu</h1>
      <p v-if="required">Bạn cần đổi mật khẩu được cấp trước khi xem chuyến đi và lịch chạy.</p>
      <p v-else>Đổi mật khẩu tài khoản {{ auth.user?.username }}.</p>
    </div>
    <form
      class="auth-form"
      :aria-busy="busy"
      novalidate
      @submit.prevent="submit"
    >
      <label>
        <span>Mật khẩu hiện tại</span>
        <div class="auth-input">
          <LockKeyhole :size="16" />
          <input
            v-model="form.currentPassword"
            :disabled="busy"
            required
            type="password"
            autocomplete="current-password"
          />
        </div>
      </label>
      <label>
        <span>Mật khẩu mới</span>
        <div class="auth-input">
          <KeyRound :size="16" />
          <input
            v-model="form.newPassword"
            :disabled="busy"
            required
            minlength="8"
            maxlength="100"
            type="password"
            autocomplete="new-password"
            aria-describedby="password-guidance"
          />
        </div>
      </label>
      <label>
        <span>Xác nhận mật khẩu mới</span>
        <div class="auth-input">
          <KeyRound :size="16" />
          <input
            v-model="form.confirmPassword"
            :disabled="busy"
            required
            minlength="8"
            maxlength="100"
            type="password"
            autocomplete="new-password"
          />
        </div>
      </label>
      <p
        id="password-guidance"
        class="auth-password-guidance"
      >
        Dùng ít nhất 8 ký tự và mật khẩu khác mật khẩu hiện tại. Bạn sẽ đăng nhập lại sau khi đổi.
      </p>
      <button
        type="submit"
        :disabled="busy"
      >
        <KeyRound :size="16" />{{ busy ? 'Đang xử lý…' : 'Đổi mật khẩu' }}
      </button>
    </form>
    <div class="auth-password-actions">
      <button
        v-if="!required"
        type="button"
        class="auth-link auth-back-link"
        :disabled="busy"
        @click="router.push('/driver/today')"
      >
        <ArrowLeft :size="15" />Quay lại cổng tài xế
      </button>
      <button
        type="button"
        class="auth-link auth-back-link"
        :disabled="busy"
        @click="signOut"
      >
        <LogOut :size="15" />Đăng xuất
      </button>
    </div>
  </AuthLayout>
</template>
