import { expect, test, vi } from 'vitest';
import { defineComponent, h, provide } from 'vue';
import { flushPromises, mount } from '@vue/test-utils';
import { fetchOperations, subscribeOperations } from '@/features/tracking/api/operations';
import { liveOperationsKey, useLiveOperations, type LiveOperationsState } from '@/features/tracking/composables/useLiveOperations';
vi.mock('@/features/tracking/api/operations', () => ({ fetchOperations: vi.fn(), subscribeOperations: vi.fn() }));
test('admin shell and descendant pages share one stream and clean it up once', async () => {
  vi.mocked(fetchOperations).mockResolvedValue({ serverTime: new Date().toISOString(), positions: [], trips: [], simulations: [], checkIns: [], notifications: [] });
  const close = vi.fn(); vi.mocked(subscribeOperations).mockReturnValue(close);
  let parent!: LiveOperationsState, child!: LiveOperationsState;
  const Child = defineComponent({ setup() { child = useLiveOperations(); return () => h('span', child.connection); } });
  const Parent = defineComponent({ setup() { parent = useLiveOperations(); provide(liveOperationsKey, parent); return () => h(Child); } });
  const wrapper = mount(Parent); await flushPromises();
  expect(parent).toBe(child); expect(fetchOperations).toHaveBeenCalledTimes(1); expect(subscribeOperations).toHaveBeenCalledTimes(1);
  wrapper.unmount(); expect(close).toHaveBeenCalledTimes(1);
});
