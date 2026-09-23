<script setup lang="ts">
import { computed, onScopeDispose, reactive, ref, shallowRef } from 'vue';
import { KeyRound, Plus, ShieldCheck, TriangleAlert, UserRound } from '@lucide/vue';
import {
  createDriverAccount,
  fetchUserAccounts,
  setUserAccountActive,
  type UserAccount,
} from '@/features/auth/api/users';
import { fetchDrivers } from '@/features/fleet/api/fleet';
import type { Driver } from '@/features/fleet/types/fleet';
import PageHeading from '@/shared/components/PageHeading.vue';
import '@/features/auth/styles/user-management.css';
const accounts = shallowRef<UserAccount[]>([]),
  drivers = shallowRef<Driver[]>([]);
const form = reactive({ username: '', password: '', driverId: '' });
const loading = ref(true),
  saving = ref(false),
  error = ref<string | null>(null),
  notice = ref<string | null>(null);
let controller: AbortController | null = null,
  disposed = false;
function load() {
  controller?.abort();
  const request = new AbortController();
  controller = request;
  loading.value = true;
  error.value = null;
  Promise.all([fetchUserAccounts(request.signal), fetchDrivers(request.signal)])
    .then(([a, d]) => {
      if (!request.signal.aborted) {
        accounts.value = a;
        drivers.value = d;
      }
    })
    .catch((reason) => {
      if (!request.signal.aborted)
        error.value = reason instanceof Error ? reason.message : 'Không thể tải tài khoản.';
    })
    .finally(() => {
      if (!request.signal.aborted) loading.value = false;
    });
}
load();
onScopeDispose(() => {
  disposed = true;
  controller?.abort();
});
const availableDrivers = computed(() =>
  drivers.value.filter(
    (driver) => driver.active && !accounts.value.some((account) => account.driverId === driver.id),
  ),
);
async function submit() {
  if (saving.value) return;
  saving.value = true;
  error.value = null;
  notice.value = null;
  try {
    const created = await createDriverAccount({
      username: form.username.trim(),
      password: form.password,
      driverId: Number(form.driverId),
    });
    if (disposed) return;
    accounts.value = [...accounts.value, created].sort((a, b) =>
      a.username.localeCompare(b.username),
    );
    Object.assign(form, { username: '', password: '', driverId: '' });
    notice.value = 'Đã tạo tài khoản tài xế.';
  } catch (reason) {
    if (!disposed)
      error.value = reason instanceof Error ? reason.message : 'Không thể tạo tài khoản.';
  } finally {
    if (!disposed) saving.value = false;
  }
}
async function toggle(account: UserAccount) {
  try {
    const updated = await setUserAccountActive(account.id, !account.active);
    if (!disposed)
      accounts.value = accounts.value.map((item) => (item.id === updated.id ? updated : item));
  } catch (reason) {
    if (!disposed)
      error.value =
        reason instanceof Error ? reason.message : 'Không thể thay đổi trạng thái tài khoản.';
  }
}
</script>
<template>
  <div class="business-page users-page">
    <PageHeading
      eyebrow="QUẢN TRỊ HỆ THỐNG"
      title="Người dùng và phân quyền"
      description="Quản lý tài khoản và cấp quyền truy cập cho đội ngũ tài xế."
    />
    <div
      v-if="error"
      class="users-error"
      role="alert"
    >
      <TriangleAlert :size="18" /><span>{{ error }}</span
      ><button @click="load">Thử lại</button>
    </div>
    <div
      v-if="notice"
      class="users-notice"
      role="status"
    >
      {{ notice }}
    </div>
    <div class="users-columns">
      <section class="business-surface users-list">
        <div class="users-heading">
          <div>
            <h2>Tài khoản hệ thống</h2>
            <p>Danh sách tài khoản và quyền truy cập hiện tại.</p>
          </div>
          <span>{{ loading ? 'Đang tải…' : `${accounts.length} tài khoản` }}</span>
        </div>
        <p
          v-if="loading"
          class="users-state"
        >
          Đang tải tài khoản…
        </p>
        <div
          v-if="!loading && accounts.length === 0"
          class="users-empty"
        >
          <UserRound :size="28" /><strong>Chưa có tài khoản</strong>
          <p>Các tài khoản được tạo sẽ hiển thị tại đây.</p>
        </div>
        <template v-if="!loading"
          ><article
            v-for="account in accounts"
            :key="account.id"
            class="user-row"
          >
            <span :class="`user-role ${account.role.toLowerCase()}`"
              ><ShieldCheck :size="14" />{{ account.role === 'ADMIN' ? 'Admin' : 'Tài xế' }}</span
            >
            <div>
              <strong>{{ account.username }}</strong
              ><small>{{
                account.driverName
                  ? `${account.driverName} · GPLX ${account.driverLicenseNumber}`
                  : 'Quản trị toàn hệ thống'
              }}</small>
            </div>
            <span :class="account.active ? 'user-active' : 'user-inactive'">{{
              account.active ? 'Đang hoạt động' : 'Đã khóa'
            }}</span
            ><button
              v-if="account.role === 'DRIVER'"
              @click="toggle(account)"
            >
              {{ account.active ? 'Khóa' : 'Mở khóa' }}
            </button>
          </article></template
        >
      </section>
      <section class="business-surface user-create">
        <div class="users-heading">
          <div>
            <h2>Cấp tài khoản tài xế</h2>
            <p>Một tài xế chỉ có tối đa một tài khoản.</p>
          </div>
          <KeyRound :size="19" />
        </div>
        <form @submit.prevent="submit">
          <label
            >Tên đăng nhập<input
              v-model="form.username"
              required
              pattern="[a-z0-9][a-z0-9._-]{2,99}"
              placeholder="nguyen.van.a" /></label
          ><label
            >Mật khẩu tạm thời<input
              v-model="form.password"
              required
              minlength="8"
              type="password"
              placeholder="Tối thiểu 8 ký tự" /></label
          ><label
            >Hồ sơ tài xế<select
              v-model="form.driverId"
              required
            >
              <option value="">Chọn tài xế</option>
              <option
                v-for="driver in availableDrivers"
                :key="driver.id"
                :value="String(driver.id)"
              >
                {{ driver.fullName }} · {{ driver.licenseNumber }}
              </option>
            </select></label
          ><button :disabled="saving || availableDrivers.length === 0">
            <Plus :size="15" />{{ saving ? 'Đang tạo…' : 'Tạo tài khoản' }}
          </button>
          <p
            v-if="availableDrivers.length === 0"
            class="user-create-help"
          >
            Tất cả tài xế đang hoạt động đã được cấp tài khoản.
          </p>
        </form>
      </section>
    </div>
  </div>
</template>
