<script setup lang="ts">
import { RouterView } from 'vue-router';
import { useAuth } from '@/features/auth/composables/useAuth';
const auth = useAuth();
</script>

<template>
  <div v-if="auth.loading" class="auth-loading" role="status">Đang xác thực phiên đăng nhập…</div>
  <RouterView v-else />
</template>
