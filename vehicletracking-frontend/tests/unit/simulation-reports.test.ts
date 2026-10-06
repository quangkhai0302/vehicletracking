import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import ReportsPage from '@/pages/ReportsPage.vue';
import { fetchOperationalReportDetail } from '@/features/reports/api/reports';
import { fetchDrivers, fetchFleetVehicles } from '@/features/fleet/api/fleet';
import type { OperationalReportDetail } from '@/features/reports/types/reports';

vi.mock('@/features/reports/api/reports', () => ({ fetchOperationalReportDetail: vi.fn() }));
vi.mock('@/features/fleet/api/fleet', () => ({ fetchDrivers: vi.fn(), fetchFleetVehicles: vi.fn() }));
vi.mock('@/shared/notifications/toast', () => ({ notifyError: vi.fn() }));

const data = (tripCount = 2): OperationalReportDetail => ({
  from: '2026-09-08', to: '2026-10-07', generatedAt: '2026-10-06T18:00:00Z',
  summary: { from: '2026-09-08', to: '2026-10-07', generatedAt: '2026-10-06T18:00:00Z', tripCount, completedTripCount: 1, totalDistanceMeters: 1000, totalRunningSeconds: 3600, onTimeRatePercent: 100, lateTripCount: 0, offRouteEventCount: 0, overspeedEventCount: 0, speedLimitKmh: 80 },
  vehicles: [{ vehicleId: 1, plateNumber: '51A-123', vehicleName: 'Fixture', tripCount, completedTripCount: 1, lateTripCount: 0, lateStopCount: 0, incidentCount: 0, employeePassengerCount: null }],
  drivers: [{ driverId: 1, driverName: 'Driver', tripCount, completedTripCount: 1, lateTripCount: 0, lateStopCount: 0, incidentCount: 0, employeePassengerCount: null }],
  lateStops: [], incidents: [], employeePassengerDataAvailable: false, employeePassengerDataNote: 'Chưa có dữ liệu',
  employeeOccupancy: { completedTripCount: 0, tripsWithCompleteBoardingData: 0, tripsMissingBoardingData: 0, tripsMissingSeatCapacity: 0, totalBoardings: 0, averageBoardingsPerTrip: null, averageOnboard: null, seatUtilizationPercent: null },
  employeeOccupancyByVehicle: [],
});
let wrapper: VueWrapper | undefined;
beforeEach(() => {
  vi.clearAllMocks();
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-10-06T18:00:00Z'));
  vi.mocked(fetchDrivers).mockResolvedValue([]);
  vi.mocked(fetchFleetVehicles).mockResolvedValue([]);
  vi.mocked(fetchOperationalReportDetail).mockResolvedValue(data());
});
afterEach(() => { wrapper?.unmount(); wrapper = undefined; vi.useRealTimers(); });
function render() { wrapper = mount(ReportsPage); return wrapper; }

test('uses Vietnam calendar date and renders operational breakdowns', async () => {
  const page = render();
  await flushPromises();
  expect(fetchOperationalReportDetail).toHaveBeenCalledWith(expect.objectContaining({ from: '2026-10-01', to: '2026-10-31' }), expect.any(AbortSignal));
  expect(page.text()).toContain('Số lượt theo xe');
  expect(page.text()).toContain('Số lượt theo tài xế');
  expect(page.text()).toContain('Chưa có dữ liệu');
});

test('reset aborts the previous detail request and reloads the base dates', async () => {
  let resolveOld!: (value: OperationalReportDetail) => void;
  const oldResponse = new Promise<OperationalReportDetail>((resolve) => { resolveOld = resolve; });
  const page = render();
  await flushPromises();
  vi.mocked(fetchOperationalReportDetail).mockReturnValueOnce(oldResponse);
  await page.get('.reports-reset').trigger('click');
  await flushPromises();
  const calls = vi.mocked(fetchOperationalReportDetail).mock.calls;
  expect(calls[calls.length - 1]![0]).toMatchObject({ from: '2026-10-01', to: '2026-10-31' });
  expect(calls[calls.length - 1]![1]).toBeInstanceOf(AbortSignal);
  resolveOld(data(999));
  await flushPromises();
});

test('year period requests the complete Vietnam calendar year', async () => {
  const page = render();
  await flushPromises();
  await page.get('[aria-label="Kỳ báo cáo"]').setValue('YEAR');
  await page.get('[aria-label="Năm báo cáo"]').setValue('2025');
  await flushPromises();
  const calls = vi.mocked(fetchOperationalReportDetail).mock.calls;
  expect(calls[calls.length - 1]![0]).toMatchObject({ from: '2025-01-01', to: '2025-12-31' });
});

test('unmount aborts an in-flight detail report', async () => {
  let resolveLate!: (value: OperationalReportDetail) => void;
  vi.mocked(fetchOperationalReportDetail).mockReturnValue(new Promise((resolve) => { resolveLate = resolve; }));
  render();
  const signal = vi.mocked(fetchOperationalReportDetail).mock.calls[0]![1]!;
  wrapper!.unmount(); wrapper = undefined;
  expect(signal.aborted).toBe(true);
  resolveLate(data());
  await flushPromises();
});
