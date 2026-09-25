import { toValue, watch, type MaybeRefOrGetter } from 'vue';
import { notifyError } from '@/shared/notifications/toast';

export function useErrorToast(error: MaybeRefOrGetter<string | null | undefined>) {
  let lastMessage: string | null = null;
  watch(
    () => toValue(error),
    (message) => {
      if (!message) {
        lastMessage = null;
        return;
      }
      if (message === lastMessage) return;
      lastMessage = message;
      notifyError(message);
    },
    { immediate: true },
  );
}
