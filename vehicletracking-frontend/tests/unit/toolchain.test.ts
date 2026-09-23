import { afterEach, expect, test } from 'vitest';
import { mount } from '@vue/test-utils';
import ToolchainFixture from './fixtures/ToolchainFixture.vue';

afterEach(() => { document.body.innerHTML = ''; });

test('typed Vue SFC renders the existing icon geometry and cleans up Leaflet', async () => {
  const wrapper = mount(ToolchainFixture, { props: { count: 2 }, slots: { default: 'Fixture slot' }, attachTo: document.body });
  expect(wrapper.find('button').text()).toContain('2');
  expect(wrapper.text()).toContain('Fixture slot');
  expect(wrapper.find('svg').attributes('viewBox')).toBe('0 0 24 24');
  expect(wrapper.find('.leaflet-container').exists()).toBe(true);
  await wrapper.find('button').trigger('click');
  expect(wrapper.emitted('select')).toEqual([[2]]);
  wrapper.unmount();
  expect(document.querySelector('.leaflet-container')).toBeNull();
});
