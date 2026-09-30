import { afterEach, beforeEach, expect, test } from 'vitest';
import { mount, type VueWrapper } from '@vue/test-utils';
import { createMemoryHistory, createRouter } from 'vue-router';
import DriverTripDetail from '@/features/fleet/components/DriverTripDetail.vue';
import { TRIP_STATUS_LABELS, type TripDetail, type TripStatus } from '@/features/fleet/types/fleet';
import { driverSnapshot, stamp } from './fixtures/driverNavigation';

const originalShow = Object.getOwnPropertyDescriptor(HTMLDialogElement.prototype, 'showModal');
const originalClose = Object.getOwnPropertyDescriptor(HTMLDialogElement.prototype, 'close');
const wrappers: VueWrapper[] = [];
const formatTime = (value: string | null) => value ?? 'Chưa có';

function detail(): TripDetail {
  const data = driverSnapshot();
  return { trip: data.trip, stops: data.stops, route: data.route };
}
async function render(data = detail()) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/:pathMatch(.*)*', component: { template: '<div />' } }],
  });
  await router.push('/driver/today');
  await router.isReady();
  const wrapper = mount(DriverTripDetail, {
    attachTo: document.body,
    props: { detail: data, formatTime },
    global: { plugins: [router] },
  });
  wrappers.push(wrapper);
  return wrapper;
}
beforeEach(() => {
  Object.defineProperty(HTMLDialogElement.prototype, 'showModal', {
    configurable: true,
    value(this: HTMLDialogElement) {
      this.open = true;
    },
  });
  Object.defineProperty(HTMLDialogElement.prototype, 'close', {
    configurable: true,
    value(this: HTMLDialogElement) {
      this.open = false;
    },
  });
});
afterEach(() => {
  wrappers.splice(0).forEach((wrapper) => wrapper.unmount());
  for (const [key, descriptor] of [
    ['showModal', originalShow],
    ['close', originalClose],
  ] as const) {
    if (descriptor) Object.defineProperty(HTMLDialogElement.prototype, key, descriptor);
    else Reflect.deleteProperty(HTMLDialogElement.prototype, key);
  }
  document.body.replaceChildren();
});

test('fixed schedule uses admin presentation and keeps planned and actual times separate', async () => {
  const data = detail();
  data.trip = {
    ...data.trip,
    dispatchMode: 'FIXED_SCHEDULE',
    scheduleName: 'Lịch thử nghiệm',
    driver: { id: 1, fullName: 'Tài xế A', phoneNumber: '0900000000', licenseNumber: 'FIXTURE' },
  };
  data.stops.splice(1, 0, { ...data.stops[1]!, sequenceNumber: 3, stationName: 'Trạm giữa' });
  const wrapper = await render(data);
  expect(wrapper.get('dialog').classes()).toContain('is-content-sized');
  expect(wrapper.get('dialog').attributes('aria-label')).toBe('Chi tiết chuyến #7');
  expect(wrapper.get('.trip-summary-vehicle').text()).toContain('51B-12345');
  expect(wrapper.get('.trip-summary-meta').text()).toContain('Tuyến thử nghiệm');
  expect(wrapper.get('.trip-summary-meta').text()).toContain('Tài xế A');
  expect(
    wrapper
      .get('.trip-times')
      .findAll('dt')
      .map((el) => el.text()),
  ).toEqual([
    'Xuất phát kế hoạch',
    'Hoàn thành theo lịch',
    'Khởi hành thực tế',
    'Kết thúc thực tế',
  ]);
  expect(wrapper.findAll('.stop-order').map((el) => el.classes().slice(-1)[0])).toEqual([
    'start',
    'stop',
    'end',
  ]);
  expect(wrapper.findAll('.trip-stop-card')).toHaveLength(3);
  expect(wrapper.get('.trip-itinerary-heading').text()).toContain('3 trạm');
  expect(
    wrapper.findAll('.trip-eta-stop').every((el) => el.text().includes('Dự kiến đến (theo lịch)')),
  ).toBe(true);
  expect(wrapper.get('.trip-card-heading').text()).toContain('Lịch thử nghiệm');
});

test('fixed-schedule stop times stay on the scheduled day after live ETA refresh', async () => {
  const data = detail();
  data.trip = {
    ...data.trip,
    dispatchMode: 'FIXED_SCHEDULE',
    scheduledDepartureAt: '2026-09-30T02:00:00Z',
    plannedEndAt: '2026-09-30T02:01:00Z',
    status: 'IN_PROGRESS',
  };
  const wrapper = await render(data);
  expect(wrapper.findAll('.trip-eta-stop')[1]!.text()).toContain('2026-09-30T02:01:00.000Z');
  expect(wrapper.findAll('.trip-eta-stop')[1]!.text()).not.toContain(stamp);
});

test('on-demand pending trip shows offsets instead of misleading planned arrival timestamps', async () => {
  const wrapper = await render();
  expect(wrapper.get('.trip-times').text()).toContain('Tạo chuyến tức thời');
  expect(wrapper.get('.trip-times').text()).not.toContain('Xuất phát kế hoạch');
  expect(wrapper.findAll('.trip-eta-stop')[1]!.text()).toContain('Sau 1 phút từ lúc khởi hành');
  expect(
    wrapper
      .get('.trip-times')
      .findAll('dd')
      .map((el) => el.text()),
  ).toEqual([stamp, 'Chưa có', 'Chưa có']);
  expect(wrapper.get('.trip-summary-meta').text()).toContain('Chưa phân công');
  expect(wrapper.find('[data-visited]').exists()).toBe(false);
  expect(wrapper.find('.trip-checkin-done').exists()).toBe(false);
});

test('on-demand started trip bases stop arrival on actual departure like admin', async () => {
  const data = detail();
  data.trip.startedAt = stamp;
  const wrapper = await render(data);
  expect(wrapper.findAll('.trip-eta-stop')[1]!.text()).toContain('2026-09-29T02:01:00.000Z');
  expect(wrapper.text()).not.toContain('Sau 1 phút từ lúc khởi hành');
});

test.each<TripStatus>(['SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'])(
  '%s keeps the correct status and scoped navigation link without admin controls',
  async (status) => {
    const data = detail();
    data.trip.status = status;
    const wrapper = await render(data);
    expect(wrapper.get('.trip-status').classes()).toContain(status.toLowerCase());
    expect(wrapper.get('.trip-status').text()).toBe(TRIP_STATUS_LABELS[status]);
    expect(wrapper.get('.driver-trip-map-link').attributes('href')).toBe(
      '/driver/trips/7/navigate',
    );
    expect(wrapper.get('.driver-trip-map-link').text()).toBe(
      status === 'SCHEDULED' ? 'Mở bản đồ và khởi hành' : 'Xem bản đồ chuyến',
    );
    expect(wrapper.findAll('button')).toHaveLength(1);
    expect(wrapper.text()).not.toMatch(/Đổi xe|Đổi tài xế|Mở điều khiển chuyến|Xóa chuyến/);
  },
);

test('empty itinerary and cancelled trip explain the state without fabricated progress', async () => {
  const data = detail();
  data.stops = [];
  data.trip = { ...data.trip, status: 'CANCELLED', cancellationReason: '<b>Lý do từ API</b>' };
  const wrapper = await render(data);
  expect(wrapper.get('[role="status"]').text()).toBe('Chuyến chưa có thông tin điểm dừng.');
  expect(wrapper.find('ol').exists()).toBe(false);
  expect(wrapper.get('.trip-times').text()).toContain('Hủy lúc');
  expect(wrapper.get('.driver-trip-cancellation').text()).toContain('<b>Lý do từ API</b>');
  expect(wrapper.get('.driver-trip-cancellation').find('b').exists()).toBe(false);
});

test('close button and native Escape emit close and unmount restores opener focus', async () => {
  const opener = document.createElement('button');
  document.body.append(opener);
  opener.focus();
  const wrapper = await render();
  expect((wrapper.get('dialog').element as HTMLDialogElement).open).toBe(true);
  await wrapper.get('.driver-trip-close').trigger('click');
  await wrapper.get('dialog').trigger('cancel');
  expect(wrapper.emitted('close')).toHaveLength(2);
  wrapper.unmount();
  expect(document.activeElement).toBe(opener);
});
