<script setup lang="ts">
import { computed, onScopeDispose, reactive, ref, shallowRef } from 'vue';
import {
  CircleCheck,
  KeyRound,
  Lock,
  Plus,
  RefreshCw,
  ShieldCheck,
  Trash2,
  Unlock,
  Upload,
  UserRound,
  Users,
  X,
} from '@lucide/vue';
import {
  createDriverAccount,
  deleteDriverAvatar,
  driverAvatarUrl,
  fetchUserAccounts,
  resetDriverPassword,
  setUserAccountActive,
  uploadDriverAvatar,
  type DriverAccountCreated,
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
const form = reactive({ driverId: '' });
const issuedAccount = shallowRef<DriverAccountCreated | null>(null);
const passwordResetForm = reactive({ password: '', confirmation: '' });
const passwordResetTarget = shallowRef<UserAccount | null>(null);
const loading = ref(true),
  saving = ref(false),
  resettingPassword = ref(false),
  uploadingAvatarId = ref<number | null>(null),
  error = ref<string | null>(null),
  createOpen = ref(false),
  togglingAccountId = ref<number | null>(null);
const avatarUrls = reactive<Record<number, string>>({});
const avatarVisible = reactive<Record<number, boolean>>({});
const avatarLoaded = reactive<Record<number, boolean>>({});
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
        for (const account of a) {
          if (account.role === 'DRIVER' && account.driverId && !avatarUrls[account.id]) {
            avatarUrls[account.id] = driverAvatarUrl(account.driverId);
            avatarVisible[account.id] = true;
          }
        }
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
function openCreate() {
  form.driverId = '';
  issuedAccount.value = null;
  error.value = null;
  createOpen.value = true;
}
function closeCreate() {
  if (saving.value) return;
  createOpen.value = false;
  form.driverId = '';
  issuedAccount.value = null;
}
async function submit() {
  if (saving.value) return;
  saving.value = true;
  error.value = null;
  try {
    const created = await createDriverAccount({ driverId: Number(form.driverId) });
    if (disposed) return;
    accounts.value = [...accounts.value, created].sort((a, b) =>
      a.username.localeCompare(b.username),
    );
    form.driverId = '';
    issuedAccount.value = created;
    notifySuccess(`Đã cấp tài khoản ${created.username}.`);
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

function avatarInputId(account: UserAccount) {
  return `avatar-upload-${account.id}`;
}

function onAvatarLoad(account: UserAccount) {
  avatarVisible[account.id] = true;
  avatarLoaded[account.id] = true;
}

function onAvatarError(account: UserAccount) {
  avatarVisible[account.id] = false;
  avatarLoaded[account.id] = false;
}

async function uploadAvatar(account: UserAccount, event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file || !account.driverId || uploadingAvatarId.value !== null) return;
  if (!['image/jpeg', 'image/png'].includes(file.type)) {
    error.value = 'Chỉ hỗ trợ ảnh PNG hoặc JPEG.';
    return;
  }
  if (file.size > 2 * 1024 * 1024) {
    error.value = 'Ảnh đại diện không được vượt quá 2 MB.';
    return;
  }

  uploadingAvatarId.value = account.id;
  error.value = null;
  try {
    await uploadDriverAvatar(account.driverId, file);
    avatarUrls[account.id] = driverAvatarUrl(account.driverId, Date.now());
    avatarVisible[account.id] = true;
    avatarLoaded[account.id] = false;
    notifySuccess(`Đã cập nhật avatar cho ${account.driverName || account.username}.`);
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : 'Không thể tải avatar lên.';
  } finally {
    uploadingAvatarId.value = null;
  }
}

async function removeAvatar(account: UserAccount) {
  if (!account.driverId || uploadingAvatarId.value !== null) return;
  uploadingAvatarId.value = account.id;
  error.value = null;
  try {
    await deleteDriverAvatar(account.driverId);
    avatarVisible[account.id] = false;
    avatarLoaded[account.id] = false;
    notifySuccess(`Đã xóa avatar của ${account.driverName || account.username}.`);
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : 'Không thể xóa avatar.';
  } finally {
    uploadingAvatarId.value = null;
  }
}

function openPasswordReset(account: UserAccount) {
  passwordResetTarget.value = account;
  Object.assign(passwordResetForm, { password: '', confirmation: '' });
  error.value = null;
}

function closePasswordReset() {
  if (resettingPassword.value) return;
  passwordResetTarget.value = null;
  Object.assign(passwordResetForm, { password: '', confirmation: '' });
}

async function submitPasswordReset() {
  const account = passwordResetTarget.value;
  if (!account || resettingPassword.value) return;
  if (passwordResetForm.password.length < 8 || passwordResetForm.password.length > 100) {
    error.value = 'Mật khẩu mới phải dài từ 8 đến 100 ký tự.';
    return;
  }
  if (passwordResetForm.password !== passwordResetForm.confirmation) {
    error.value = 'Mật khẩu xác nhận không khớp.';
    return;
  }

  resettingPassword.value = true;
  error.value = null;
  try {
    await resetDriverPassword(account.id, { password: passwordResetForm.password });
    if (disposed) return;
    resettingPassword.value = false;
    closePasswordReset();
    notifySuccess(`Đã đặt lại mật khẩu cho tài khoản ${account.username}.`);
  } catch (reason) {
    if (!disposed) {
      error.value = reason instanceof Error ? reason.message : 'Không thể đặt lại mật khẩu.';
    }
  } finally {
    if (!disposed) resettingPassword.value = false;
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
          @click="openCreate"
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
                <img
                  v-if="account.role === 'DRIVER' && avatarVisible[account.id]"
                  :key="avatarUrls[account.id]"
                  :src="avatarUrls[account.id]"
                  :alt="`Avatar của ${account.driverName || account.username}`"
                  class="user-avatar-image"
                  crossorigin="use-credentials"
                  @load="onAvatarLoad(account)"
                  @error="onAvatarError(account)"
                />
                <ShieldCheck
                  v-else-if="account.role === 'ADMIN'"
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
              <div
                v-if="account.role === 'DRIVER'"
                class="user-account-actions"
              >
                <button
                  type="button"
                  class="user-account-action reset"
                  :disabled="resettingPassword || togglingAccountId !== null || uploadingAvatarId !== null"
                  :aria-label="`Đặt lại mật khẩu tài khoản ${account.username}`"
                  @click="openPasswordReset(account)"
                >
                  <KeyRound :size="15" />Đặt lại mật khẩu
                </button>
                <label
                  :for="avatarInputId(account)"
                  :class="['user-account-action', 'avatar', { 'is-busy': uploadingAvatarId === account.id }]"
                  :aria-label="`${avatarLoaded[account.id] ? 'Đổi' : 'Tải'} avatar tài khoản ${account.username}`"
                >
                  <Upload
                    v-if="uploadingAvatarId !== account.id"
                    :size="15"
                  />
                  <RefreshCw
                    v-else
                    :size="15"
                    class="user-action-spinner"
                  />
                  {{ uploadingAvatarId === account.id ? 'Đang tải…' : avatarLoaded[account.id] ? 'Đổi avatar' : 'Tải avatar' }}
                </label>
                <input
                  :id="avatarInputId(account)"
                  class="user-avatar-input"
                  type="file"
                  accept="image/jpeg,image/png"
                  :disabled="uploadingAvatarId !== null || !account.active"
                  @change="uploadAvatar(account, $event)"
                />
                <button
                  v-if="avatarLoaded[account.id]"
                  type="button"
                  class="user-account-action remove-avatar"
                  :disabled="uploadingAvatarId !== null"
                  :aria-label="`Xóa avatar tài khoản ${account.username}`"
                  @click="removeAvatar(account)"
                >
                  <Trash2 :size="15" />Xóa avatar
                </button>
                <button
                  type="button"
                  :class="['user-account-action', account.active ? 'lock' : 'unlock']"
                  :disabled="resettingPassword || togglingAccountId !== null || uploadingAvatarId !== null"
                  :aria-label="`${account.active ? 'Khóa' : 'Mở khóa'} tài khoản ${account.username}`"
                  @click="toggle(account)"
                >
                  <template v-if="togglingAccountId === account.id">
                    <RefreshCw
                      :size="15"
                      class="user-action-spinner"
                    />Đang cập nhật…
                  </template>
                  <template v-else-if="account.active">
                    <Lock :size="15" />Khóa tài khoản
                  </template>
                  <template v-else><Unlock :size="15" />Mở khóa</template>
                </button>
              </div>
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
      :label="issuedAccount ? 'Thông tin tài khoản vừa cấp' : 'Cấp tài khoản tài xế'"
      :busy="saving"
      content-sized
      :on-close="closeCreate"
    >
      <section class="user-create">
        <div class="users-heading">
          <div>
            <h2>{{ issuedAccount ? 'Đã cấp tài khoản' : 'Cấp tài khoản tài xế' }}</h2>
            <p>
              {{
                issuedAccount
                  ? 'Lưu lại thông tin đăng nhập trước khi đóng.'
                  : 'Chọn tài xế, hệ thống sẽ tự tạo thông tin đăng nhập.'
              }}
            </p>
          </div>
          <button
            type="button"
            aria-label="Đóng biểu mẫu"
            :disabled="saving"
            @click="closeCreate"
          >
            <X :size="18" />
          </button>
        </div>
        <div
          v-if="issuedAccount"
          class="user-issued-account"
          role="status"
        >
          <div class="user-issued-heading">
            <span><CircleCheck :size="22" /></span>
            <div>
              <strong>Cấp tài khoản thành công</strong>
              <p>{{ issuedAccount.driverName }}</p>
            </div>
          </div>
          <dl>
            <div>
              <dt>Tên đăng nhập</dt>
              <dd>{{ issuedAccount.username }}</dd>
            </div>
            <div>
              <dt>Mật khẩu tạm thời</dt>
              <dd>{{ issuedAccount.temporaryPassword }}</dd>
            </div>
          </dl>
          <p class="user-issued-note">
            Mật khẩu tạm thời chỉ hiển thị ở bước này. Nếu quên, hãy dùng chức năng đặt lại mật
            khẩu.
          </p>
          <button
            type="button"
            class="user-issued-done"
            @click="closeCreate"
          >
            <CircleCheck :size="16" />Hoàn tất
          </button>
        </div>
        <form
          v-else
          @submit.prevent="submit"
        >
          <label>
            Chọn tài xế
            <select
              v-model="form.driverId"
              required
            >
              <option value="">Chọn tài xế chưa có tài khoản</option>
              <option
                v-for="driver in availableDrivers"
                :key="driver.id"
                :value="String(driver.id)"
              >
                {{ driver.fullName }} · {{ driver.licenseNumber }}
              </option>
            </select>
          </label>
          <p
            v-if="availableDrivers.length > 0"
            class="user-create-rule"
          >
            Tên đăng nhập được tạo từ tên gọi và chữ đầu của họ, tên đệm. Ví dụ:
            <strong>Nguyễn Quang Khải → khainq</strong>. Nếu trùng, hệ thống tự thêm số.
          </p>
          <button :disabled="saving || availableDrivers.length === 0">
            <Plus :size="15" />{{ saving ? 'Đang cấp…' : 'Cấp tài khoản' }}
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
    <SidePanel
      v-if="passwordResetTarget"
      class-name="user-account-modal user-password-reset-modal"
      :label="`Đặt lại mật khẩu ${passwordResetTarget.username}`"
      :busy="resettingPassword"
      content-sized
      :on-close="closePasswordReset"
    >
      <section class="user-create user-password-reset">
        <div class="users-heading">
          <div>
            <h2>Đặt lại mật khẩu</h2>
            <p>Tạo mật khẩu mới cho tài khoản tài xế.</p>
          </div>
          <button
            type="button"
            aria-label="Đóng biểu mẫu đặt lại mật khẩu"
            :disabled="resettingPassword"
            @click="closePasswordReset"
          >
            <X :size="18" />
          </button>
        </div>
        <div class="user-password-target">
          <span><KeyRound :size="18" /></span>
          <div>
            <small>TÀI KHOẢN TÀI XẾ</small>
            <strong>{{ passwordResetTarget.username }}</strong>
            <p>
              {{ passwordResetTarget.driverName }} · GPLX
              {{ passwordResetTarget.driverLicenseNumber }}
            </p>
          </div>
        </div>
        <form @submit.prevent="submitPasswordReset">
          <label>
            Mật khẩu mới
            <input
              v-model="passwordResetForm.password"
              required
              minlength="8"
              maxlength="100"
              type="password"
              autocomplete="new-password"
              placeholder="Từ 8 đến 100 ký tự"
            />
          </label>
          <label>
            Xác nhận mật khẩu mới
            <input
              v-model="passwordResetForm.confirmation"
              required
              minlength="8"
              maxlength="100"
              type="password"
              autocomplete="new-password"
              placeholder="Nhập lại mật khẩu mới"
            />
          </label>
          <p class="user-password-note">
            Sau khi đặt lại, các phiên đăng nhập cũ của tài xế sẽ bị kết thúc. Trạng thái khóa/mở
            khóa của tài khoản không thay đổi.
          </p>
          <button :disabled="resettingPassword">
            <KeyRound :size="15" />{{
              resettingPassword ? 'Đang đặt lại…' : 'Xác nhận đặt lại mật khẩu'
            }}
          </button>
        </form>
      </section>
    </SidePanel>
  </div>
</template>
