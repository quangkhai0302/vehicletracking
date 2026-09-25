<script setup lang="ts">
import { computed, onScopeDispose, reactive, ref, shallowRef } from 'vue';
import {
  CircleCheck,
  Lock,
  Plus,
  RefreshCw,
  ShieldCheck,
  Unlock,
  UserRound,
  Users,
  X,
} from '@lucide/vue';
import {
  createDriverAccount,
  fetchUserAccounts,
  setUserAccountActive,
  type UserAccount,
} from '@/features/auth/api/users';
import { fetchDrivers } from '@/features/fleet/api/fleet';
import type { Driver } from '@/features/fleet/types/fleet';
import PageHeading from '@/shared/components/PageHeading.vue';
import SidePanel from '@/shared/components/SidePanel.vue';
import { useErrorToast } from '@/shared/composables/useErrorToast';
import { notifySuccess } from '@/shared/notifications/toast';
import '@/features/auth/styles/user-management.css';
const accounts = shallowRef<UserAccount[]>([]),
  drivers = shallowRef<Driver[]>([]);
const form = reactive({ username: '', password: '', driverId: '' });
const loading = ref(true),
  saving = ref(false),
  error = ref<string | null>(null),
  createOpen = ref(false),
  togglingAccountId = ref<number | null>(null);
useErrorToast(error);
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
const accountStats = computed(() => ({
  total: accounts.value.length,
  admins: accounts.value.filter((account) => account.role === 'ADMIN').length,
  drivers: accounts.value.filter((account) => account.role === 'DRIVER').length,
  locked: accounts.value.filter((account) => !account.active).length,
}));
async function submit() {
  if (saving.value) return;
  saving.value = true;
  error.value = null;
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
    createOpen.value = false;
    notifySuccess('Đã tạo tài khoản tài xế.');
  } catch (reason) {
    if (!disposed)
      error.value = reason instanceof Error ? reason.message : 'Không thể tạo tài khoản.';
  } finally {
    if (!disposed) saving.value = false;
  }
}
async function toggle(account: UserAccount) {
  if (togglingAccountId.value !== null) return;
  togglingAccountId.value = account.id;
  error.value = null;
  try {
    const updated = await setUserAccountActive(account.id, !account.active);
    if (!disposed) {
      accounts.value = accounts.value.map((item) => (item.id === updated.id ? updated : item));
      notifySuccess(updated.active ? 'Đã mở khóa tài khoản.' : 'Đã khóa tài khoản.');
    }
  } catch (reason) {
    if (!disposed)
      error.value =
        reason instanceof Error ? reason.message : 'Không thể thay đổi trạng thái tài khoản.';
  } finally {
    if (!disposed) togglingAccountId.value = null;
  }
}
</script>
<template>
  <div class="business-page users-page">
    <PageHeading
      eyebrow="QUẢN TRỊ HỆ THỐNG"
      title="Người dùng và phân quyền"
      description="Quản lý tài khoản và cấp quyền truy cập cho đội ngũ tài xế."
    >
      <template #actions>
        <button
          type="button"
          class="business-button primary"
          :disabled="loading || availableDrivers.length === 0"
          @click="createOpen = true"
        >
          <Plus :size="16" />Cấp tài khoản
        </button>
        <button
          type="button"
          class="business-button users-refresh"
          :disabled="loading"
          @click="load"
        >
          <RefreshCw :size="16" />Làm mới
        </button>
      </template>
    </PageHeading>
    <section
      class="users-overview"
      aria-label="Tổng quan tài khoản"
    >
      <article class="users-overview-card total">
        <span class="users-overview-icon"><Users :size="20" /></span>
        <div>
          <span>Tổng tài khoản</span><strong>{{ accountStats.total }}</strong>
        </div>
      </article>
      <article class="users-overview-card admin">
        <span class="users-overview-icon"><ShieldCheck :size="20" /></span>
        <div>
          <span>Quản trị viên</span><strong>{{ accountStats.admins }}</strong>
        </div>
      </article>
      <article class="users-overview-card driver">
        <span class="users-overview-icon"><UserRound :size="20" /></span>
        <div>
          <span>Tài xế</span><strong>{{ accountStats.drivers }}</strong>
        </div>
      </article>
      <article :class="['users-overview-card', 'locked', { empty: accountStats.locked === 0 }]">
        <span class="users-overview-icon"><Lock :size="20" /></span>
        <div>
          <span>Tài khoản bị khóa</span>
          <strong>{{ accountStats.locked }}</strong>
        </div>
      </article>
    </section>
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
        <div
          v-if="!loading && accounts.length > 0"
          class="users-table"
          role="table"
          aria-label="Danh sách tài khoản hệ thống"
        >
          <div
            class="users-table-head"
            role="row"
          >
            <span role="columnheader">Tài khoản</span>
            <span role="columnheader">Vai trò</span>
            <span role="columnheader">Trạng thái truy cập</span>
            <span role="columnheader">Thao tác</span>
          </div>
          <article
            v-for="account in accounts"
            :key="account.id"
            :class="['user-row', { 'is-locked': !account.active }]"
            :data-active="account.active"
            role="row"
          >
            <div
              class="user-identity"
              role="cell"
            >
              <span :class="`user-avatar ${account.role.toLowerCase()}`">
                <ShieldCheck
                  v-if="account.role === 'ADMIN'"
                  :size="19"
                />
                <UserRound
                  v-else
                  :size="19"
                />
              </span>
              <div class="user-account-copy">
                <strong>{{ account.username }}</strong>
                <small>{{
                  account.driverName
                    ? `${account.driverName} · GPLX ${account.driverLicenseNumber}`
                    : 'Tài khoản quản trị hệ thống'
                }}</small>
              </div>
            </div>
            <div
              class="user-role-cell"
              role="cell"
            >
              <span class="user-cell-label">Vai trò</span>
              <span :class="`user-role ${account.role.toLowerCase()}`">
                <ShieldCheck
                  v-if="account.role === 'ADMIN'"
                  :size="14"
                />
                <UserRound
                  v-else
                  :size="14"
                />
                {{ account.role === 'ADMIN' ? 'Quản trị viên' : 'Tài xế' }}
              </span>
              <small>{{
                account.role === 'ADMIN' ? 'Toàn quyền hệ thống' : 'Quyền truy cập tài xế'
              }}</small>
            </div>
            <div
              :class="account.active ? 'user-status user-active' : 'user-status user-inactive'"
              role="cell"
            >
              <span class="user-cell-label">Trạng thái</span>
              <span class="user-status-icon">
                <CircleCheck
                  v-if="account.active"
                  :size="17"
                />
                <Lock
                  v-else
                  :size="17"
                />
              </span>
              <span class="user-status-copy">
                <strong>{{ account.active ? 'Đang hoạt động' : 'Tài khoản bị khóa' }}</strong>
                <small>{{ account.active ? 'Có thể đăng nhập' : 'Không thể đăng nhập' }}</small>
              </span>
            </div>
            <div
              class="user-row-action"
              role="cell"
            >
              <button
                v-if="account.role === 'DRIVER'"
                type="button"
                :class="['user-account-action', account.active ? 'lock' : 'unlock']"
                :disabled="togglingAccountId !== null"
                :aria-label="`${account.active ? 'Khóa' : 'Mở khóa'} tài khoản ${account.username}`"
                @click="toggle(account)"
              >
                <template v-if="togglingAccountId === account.id">
                  <RefreshCw
                    :size="15"
                    class="user-action-spinner"
                  />Đang cập nhật…
                </template>
                <template v-else-if="account.active"> <Lock :size="15" />Khóa tài khoản </template>
                <template v-else><Unlock :size="15" />Mở khóa</template>
              </button>
              <span
                v-else
                class="user-protected"
              >
                <ShieldCheck :size="14" />Quyền hệ thống
              </span>
            </div>
          </article>
        </div>
      </section>
    </div>
    <SidePanel
      v-if="createOpen"
      class-name="user-account-modal"
      label="Cấp tài khoản tài xế"
      :busy="saving"
      :on-close="() => (createOpen = false)"
    >
      <section class="user-create">
        <div class="users-heading">
          <div>
            <h2>Cấp tài khoản tài xế</h2>
            <p>Một tài xế chỉ có tối đa một tài khoản.</p>
          </div>
          <button
            type="button"
            aria-label="Đóng biểu mẫu"
            :disabled="saving"
            @click="createOpen = false"
          >
            <X :size="18" />
          </button>
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
    </SidePanel>
  </div>
</template>
