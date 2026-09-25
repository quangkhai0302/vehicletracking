import { toast, type Id, type ToastOptions } from 'vue3-toastify';
import { syncToastLayer } from './toastLayer';

type NotificationOptions = Omit<ToastOptions, 'type'>;

const defaults: NotificationOptions = {
  autoClose: 4000,
  clearOnUrlChange: false,
  closeOnClick: true,
  pauseOnFocusLoss: true,
  pauseOnHover: true,
  position: 'top-right',
  role: 'alert',
  theme: 'colored',
};

const options = (overrides?: NotificationOptions): NotificationOptions => ({
  ...defaults,
  ...overrides,
});

function nonEmptyString(value: unknown): string | null {
  return typeof value === 'string' && value.trim() ? value.trim() : null;
}

function validationMessages(value: unknown): string[] {
  if (Array.isArray(value)) {
    return value.flatMap((item) => {
      const direct = nonEmptyString(item);
      if (direct) return [direct];
      if (!item || typeof item !== 'object') return [];

      const record = item as Record<string, unknown>;
      const message =
        nonEmptyString(record.message) ??
        nonEmptyString(record.defaultMessage) ??
        nonEmptyString(record.reason);
      if (!message) return [];
      const field =
        nonEmptyString(record.field) ??
        nonEmptyString(record.property) ??
        nonEmptyString(record.path);
      return [field ? `${field}: ${message}` : message];
    });
  }

  if (!value || typeof value !== 'object') return [];
  return Object.entries(value as Record<string, unknown>).flatMap(([field, issue]) => {
    const messages = Array.isArray(issue) ? issue : [issue];
    return messages.flatMap((message) => {
      const detail = nonEmptyString(message);
      return detail ? [`${field}: ${detail}`] : [];
    });
  });
}

export function errorMessage(
  reason: unknown,
  fallback = 'Đã xảy ra lỗi. Vui lòng thử lại.',
): string {
  const direct = nonEmptyString(reason);
  if (direct) return direct;

  if (reason instanceof Error) {
    const message = nonEmptyString(reason.message);
    if (message) return message;
  }

  if (reason && typeof reason === 'object') {
    const problem = reason as Record<string, unknown>;
    const primary =
      nonEmptyString(problem.detail) ??
      nonEmptyString(problem.message) ??
      nonEmptyString(problem.error) ??
      nonEmptyString(problem.title);
    const validation = validationMessages(problem.errors ?? problem.violations);
    const messages = [primary, ...validation].filter(
      (message, index, all): message is string =>
        Boolean(message) && all.indexOf(message) === index,
    );
    if (messages.length) return messages.join('\n');
  }

  return fallback;
}

function display(createToast: () => Id): Id {
  const id = createToast();
  syncToastLayer();
  return id;
}

export const notifySuccess = (message: string, overrides?: NotificationOptions): Id =>
  display(() => toast.success(message, options({ autoClose: 3500, role: 'status', ...overrides })));

export const notifyError = (reason: unknown, overrides?: NotificationOptions): Id =>
  display(() => toast.error(errorMessage(reason), options({ autoClose: 5500, ...overrides })));

export const notifyWarning = (message: string, overrides?: NotificationOptions): Id =>
  display(() => toast.warning(message, options({ autoClose: 5000, ...overrides })));

export const notifyInfo = (message: string, overrides?: NotificationOptions): Id =>
  display(() => toast.info(message, options({ role: 'status', ...overrides })));

export function notifyLegacy(message: string): Id {
  const normalized = message.trim();
  return /^(lỗi|không thể|chưa tải|mất kết nối)\b/i.test(normalized)
    ? notifyError(normalized.replace(/^lỗi:\s*/i, ''))
    : notifySuccess(normalized);
}
