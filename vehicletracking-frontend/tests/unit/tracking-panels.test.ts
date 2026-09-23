import { expect, test, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import TrackingPanel from '@/features/tracking/components/TrackingPanel.vue';
import VehicleDrawer from '@/features/fleet/components/VehicleDrawer.vue';
import type { Vehicle } from '@/features/fleet/types/vehicle';

const vehicle: Vehicle = {
  id: 'fixture-1', plateNumber: '51B12345', model: 'Fixture car', routeId: '1', routeName: 'Central route', driverName: 'Fixture driver', driverPhone: '090 123 4567',
  speedKmh: 65, desiredSpeedKmh: 50, speedLimitKmh: 60, trafficSpeedKmh: 50, status: 'RUNNING', latitude: 10.8, longitude: 106.7, heading: 90,
  nextStationName: 'Central station', etaMinutes: 5, distanceToNextMeters: 1200, tripProgressPercent: 25,
  timeline: [{ stationId: '1', stationName: 'First', plannedTime: '08:00', actualOrEtaTime: '08:01', status: 'PASSED' }, { stationId: '2', stationName: 'Next', plannedTime: '08:10', actualOrEtaTime: '08:11', status: 'CURRENT' }],
};

test('standalone tracking panel preserves filters, keyboard selection, progress and empty-state action', async () => {
  const onSelectVehicle = vi.fn(), onManageStations = vi.fn();
  const delayed = { ...vehicle, id: 'fixture-2', driverName: 'Delayed driver', status: 'DELAYED' as const, tripProgressPercent: 125 };
  const wrapper = mount(TrackingPanel, { props: { vehicles: [vehicle, delayed], selectedVehicleId: delayed.id, onSelectVehicle, onManageStations } });
  expect(wrapper.findAll('.vehicle-card')).toHaveLength(2);
  await wrapper.findAll('[role=tab]')[2].trigger('click');
  expect(wrapper.findAll('.vehicle-card')).toHaveLength(1); expect(wrapper.get('.vehicle-card').attributes('aria-pressed')).toBe('true');
  expect(wrapper.get('.vehicle-progress-fill').attributes('style')).toContain('width: 100%');
  await wrapper.get('.vehicle-card').trigger('keydown', { key: ' ' }); expect(onSelectVehicle).toHaveBeenCalledWith(delayed);
  await wrapper.get('input').setValue('missing'); expect(wrapper.get('.station-empty').text()).toContain('Không tìm thấy');
  await wrapper.setProps({ vehicles: [] }); await wrapper.get('.primary-action').trigger('click'); expect(onManageStations).toHaveBeenCalledTimes(1);
  wrapper.unmount();
});

test('standalone vehicle drawer reacts to selection/follow state and preserves timeline/actions', async () => {
  const follow = vi.fn(), fit = vi.fn(), close = vi.fn();
  const wrapper = mount(VehicleDrawer, { props: { vehicle: null, following: false, onToggleFollow: follow, onFitRoute: fit, onClose: close } });
  expect(wrapper.find('aside').exists()).toBe(false);
  await wrapper.setProps({ vehicle });
  expect(wrapper.get('a').attributes('href')).toBe('tel:0901234567'); expect(wrapper.get('.speed-progress-fill').classes()).toContain('danger');
  expect(wrapper.get('.time-actual.passed').text()).toBe('Đã đến: 08:01'); expect(wrapper.get('.timeline-item.current .time-actual').text()).toBe('ETA: 08:11');
  await wrapper.get('.follow-vehicle-btn').trigger('click'); expect(follow).toHaveBeenCalledTimes(1);
  await wrapper.setProps({ following: true, vehicle: { ...vehicle, status: 'DELAYED' } });
  expect(wrapper.get('.follow-vehicle-btn').attributes('aria-pressed')).toBe('true'); expect(wrapper.find('[role=alert]').exists()).toBe(true);
  await wrapper.get('.fit-route-btn').trigger('click'); await wrapper.get('.icon-action').trigger('click'); expect(fit).toHaveBeenCalledTimes(1); expect(close).toHaveBeenCalledTimes(1);
  wrapper.unmount();
});
