import { onScopeDispose, ref } from 'vue';

export function useCompactLayout() {
  const media = window.matchMedia('(max-width: 1279px), (max-height: 650px)');
  const compact = ref(media.matches);
  const update = () => { compact.value = media.matches; };
  media.addEventListener('change', update);
  onScopeDispose(() => media.removeEventListener('change', update));
  return compact;
}
