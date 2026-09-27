import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import AppDatePicker from '@/shared/components/AppDatePicker.vue';

let showPopover: ReturnType<typeof vi.fn>;
let hidePopover: ReturnType<typeof vi.fn>;

beforeEach(() => {
  showPopover = vi.fn();
  hidePopover = vi.fn();
  Object.defineProperties(HTMLElement.prototype, {
    showPopover: { configurable: true, value: showPopover },
    hidePopover: { configurable: true, value: hidePopover },
  });
});

afterEach(() => {
  delete (HTMLElement.prototype as unknown as { showPopover?: () => void }).showPopover;
  delete (HTMLElement.prototype as unknown as { hidePopover?: () => void }).hidePopover;
  document.body.innerHTML = '';
});

test('date picker opens in the top layer and follows its input in the viewport', async () => {
  const wrapper = mount(AppDatePicker, {
    attachTo: document.body,
    props: { modelValue: '2026-09-25', ariaLabel: 'Ngày chạy' },
  });
  const anchor = wrapper.get('.app-datepicker-input-wrapper').element as HTMLElement;
  anchor.getBoundingClientRect = () => ({
    x: 100,
    y: 50,
    top: 50,
    right: 400,
    bottom: 90,
    left: 100,
    width: 300,
    height: 40,
    toJSON: () => ({}),
  });

  await wrapper.get('.app-datepicker-input-wrapper').trigger('click');
  await flushPromises();

  const popup = wrapper.get('.app-datepicker-popup');
  expect(popup.attributes('popover')).toBe('manual');
  expect(showPopover).toHaveBeenCalledOnce();
  expect((popup.element as HTMLElement).style.top).toBe('96px');
  expect((popup.element as HTMLElement).style.left).toBe('100px');

  wrapper.unmount();
});

test('date picker closes its top-layer popup after selecting a day', async () => {
  const wrapper = mount(AppDatePicker, {
    attachTo: document.body,
    props: { modelValue: '2026-09-25' },
  });

  await wrapper.get('.app-datepicker-input-wrapper').trigger('click');
  await flushPromises();
  await wrapper.get('.day-cell.is-current-month:not(.is-disabled)').trigger('click');

  expect(hidePopover).toHaveBeenCalledOnce();
  expect(wrapper.emitted('update:modelValue')).toHaveLength(1);
  expect(wrapper.find('.app-datepicker-popup').exists()).toBe(false);

  wrapper.unmount();
});
