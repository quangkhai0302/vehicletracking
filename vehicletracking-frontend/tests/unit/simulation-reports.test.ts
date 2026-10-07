import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import ReportsPage from '@/pages/ReportsPage.vue';
import writeExcelFile from 'write-excel-file/browser';
import { fetchOperationalReportDetail } from '@/features/reports/api/reports';
import { fetchDrivers, fetchFleetVehicles } from '@/features/fleet/api/fleet';
import type { OperationalReportDetail } from '@/features/reports/types/reports';

vi.mock('@/features/reports/api/reports', () => ({ fetchOperationalReportDetail: vi.fn() }));
vi.mock('@/features/fleet/api/fleet', () => ({ fetchDrivers: vi.fn(), fetchFleetVehicles: vi.fn() }));
vi.mock('@/shared/notifications/toast', () => ({ notifyError: vi.fn() }));
vi.mock('write-excel-file/browser', () => ({ default: vi.fn() }));

const data = (tripCount = 2): OperationalReportDetail => ({
  from: '2026-09-08', to: '2026-10-07', generatedAt: '2026-10-06T18:00:00Z',
  summary: { from: '2026-09-08', to: '2026-10-07', generatedAt: '2026-10-06T18:00:00Z', tripCount, completedTripCount: 1, totalDistanceMeters: 1000, totalRunningSeconds: 3600, onTimeRatePercent: 100, lateTripCount: 0, offRouteEventCount: 0, overspeedEventCount: 0, speedLimitKmh: 80 },
  vehicles: [{ vehicleId: 1, plateNumber: '51A-123', vehicleName: 'Fixture', tripCount, completedTripCount: 1, lateTripCount: 0, lateStopCount: 0, incidentCount: 0, employeePassengerCount: null }],
  drivers: [{ driverId: 1, driverName: 'Driver', tripCount, completedTripCount: 1, lateTripCount: 0, lateStopCount: 0, incidentCount: 0, employeePassengerCount: null,
    trips: Array.from({ length: tripCount }, (_, index) => ({ tripId: index + 1, routeName: `Tuyến đến trường ${index + 1}`, vehiclePlateNumber: '51A-123', scheduledDepartureAt: '2026-10-06T23:00:00Z', startedAt: '2026-10-06T23:10:00Z', endedAt: index === 0 ? '2026-10-07T00:00:00Z' : null, status: index === 0 ? 'COMPLETED' : 'IN_PROGRESS' })),
  }],
  lateStops: [], incidents: [], incidentDetails: [], employeePassengerDataAvailable: false, employeePassengerDataNote: 'Chưa có dữ liệu',
  employeeOccupancy: { completedTripCount: 0, tripsWithCompleteBoardingData: 0, tripsMissingBoardingData: 0, tripsMissingSeatCapacity: 0, totalBoardings: 0, averageBoardingsPerTrip: null, averageOnboard: null, seatUtilizationPercent: null },
  employeeOccupancyByVehicle: [],
});
let wrapper: VueWrapper | undefined;
const originalDialogShow = Object.getOwnPropertyDescriptor(HTMLDialogElement.prototype, 'showModal');
const originalDialogClose = Object.getOwnPropertyDescriptor(HTMLDialogElement.prototype, 'close');
beforeEach(() => {
  vi.clearAllMocks();
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-10-06T18:00:00Z'));
  vi.mocked(fetchDrivers).mockResolvedValue([]);
  vi.mocked(fetchFleetVehicles).mockResolvedValue([]);
  vi.mocked(fetchOperationalReportDetail).mockResolvedValue(data());
  Object.defineProperty(HTMLDialogElement.prototype, 'showModal', { configurable: true, value(this: HTMLDialogElement) { this.setAttribute('open', ''); } });
  Object.defineProperty(HTMLDialogElement.prototype, 'close', { configurable: true, value(this: HTMLDialogElement) { this.removeAttribute('open'); } });
});
afterEach(() => {
  wrapper?.unmount(); wrapper = undefined; vi.useRealTimers(); vi.unstubAllGlobals(); vi.restoreAllMocks();
  if (originalDialogShow) Object.defineProperty(HTMLDialogElement.prototype, 'showModal', originalDialogShow);
  else Reflect.deleteProperty(HTMLDialogElement.prototype, 'showModal');
  if (originalDialogClose) Object.defineProperty(HTMLDialogElement.prototype, 'close', originalDialogClose);
  else Reflect.deleteProperty(HTMLDialogElement.prototype, 'close');
});
function render() { wrapper = mount(ReportsPage, { attachTo: document.body }); return wrapper; }

test('uses Vietnam calendar date and renders operational breakdowns', async () => {
  const page = render();
  await flushPromises();
  expect(fetchOperationalReportDetail).toHaveBeenCalledWith(expect.objectContaining({ from: '2026-10-01', to: '2026-10-31' }), expect.any(AbortSignal));
  expect(page.text()).toContain('Thống kê theo xe');
  expect(page.text()).toContain('Thống kê theo tài xế');
  expect(page.text()).toContain('Chưa xác nhận');
  expect(page.findAll('[role="tabpanel"]').filter((panel) => panel.isVisible())).toHaveLength(1);
  expect(page.get('#report-panel-vehicles').isVisible()).toBe(true);
});

test('report tabs and overview shortcuts show one panel without reloading the report', async () => {
  const page = render();
  await flushPromises();
  for (const name of ['drivers', 'occupancy', 'late-stops', 'incidents', 'vehicles']) {
    await page.get(`#report-tab-${name}`).trigger('click');
    expect(page.get(`#report-panel-${name}`).isVisible()).toBe(true);
    expect(page.findAll('[role="tabpanel"]').filter((panel) => panel.isVisible())).toHaveLength(1);
    expect(page.get(`#report-tab-${name}`).attributes('aria-selected')).toBe('true');
  }
  await page.get('.report-stat.is-rose button').trigger('click');
  expect(page.get('#report-panel-late-stops').isVisible()).toBe(true);
  await page.get('.report-stat.is-orange button').trigger('click');
  expect(page.get('#report-panel-incidents').isVisible()).toBe(true);
  expect(fetchOperationalReportDetail).toHaveBeenCalledTimes(1);
});

test('tabs support keyboard navigation and keep only the selected tab in the tab sequence', async () => {
  wrapper = mount(ReportsPage, { attachTo: document.body });
  await flushPromises();
  await wrapper.get('#report-tab-vehicles').trigger('keydown', { key: 'ArrowRight' });
  expect(document.activeElement?.id).toBe('report-tab-drivers');
  expect(wrapper.get('#report-panel-drivers').isVisible()).toBe(true);
  await wrapper.get('#report-tab-drivers').trigger('keydown', { key: 'End' });
  expect(document.activeElement?.id).toBe('report-tab-incidents');
  await wrapper.get('#report-tab-incidents').trigger('keydown', { key: 'ArrowRight' });
  expect(document.activeElement?.id).toBe('report-tab-vehicles');
  await wrapper.get('#report-tab-vehicles').trigger('keydown', { key: 'ArrowLeft' });
  expect(document.activeElement?.id).toBe('report-tab-incidents');
  await wrapper.get('#report-tab-incidents').trigger('keydown', { key: 'Home' });
  expect(document.activeElement?.id).toBe('report-tab-vehicles');
  expect(wrapper.findAll('[role="tab"][tabindex="0"]')).toHaveLength(1);
  expect(fetchOperationalReportDetail).toHaveBeenCalledTimes(1);
});

test('collapsed filters keep the selected period and scope visible without resetting them', async () => {
  const page = render();
  await flushPromises();
  expect(page.get('#report-filter-fields').isVisible()).toBe(false);
  await page.get('.report-filter-toggle').trigger('click');
  expect(page.get('#report-filter-fields').isVisible()).toBe(true);
  await page.get('[aria-label="Tháng báo cáo"]').setValue('09');
  await flushPromises();
  await page.get('.report-filter-toggle').trigger('click');
  expect(page.get('.report-filter-toggle').attributes('aria-expanded')).toBe('false');
  expect(page.get('.report-period-summary').text()).toContain('Tháng 9, 2026');
  expect(page.get('.report-period-summary').text()).toContain('01/09/2026 – 30/09/2026');
  expect(page.get('.report-scope-chips').text()).toBe('Toàn bộ xe và tài xế');
  expect(fetchOperationalReportDetail).toHaveBeenCalledTimes(2);
});

test('switching sections preserves pagination and local late-stop filters', async () => {
  const result = data();
  result.vehicles = Array.from({ length: 11 }, (_, index) => ({ ...result.vehicles[0]!, vehicleId: index + 1, plateNumber: `XE-${index + 1}` }));
  result.lateStops = [{ tripId: 1, routeName: 'Tuyến trường học', vehiclePlateNumber: 'XE-1', driverName: 'Tài xế A', stationName: 'Trạm A', stopSequence: 2, plannedArrivalAt: result.generatedAt, actualArrivalAt: result.generatedAt, delaySeconds: 1200 }];
  vi.mocked(fetchOperationalReportDetail).mockResolvedValueOnce(result);
  const page = render();
  await flushPromises();
  await page.get('#report-panel-vehicles .pagination-controls').findAll('button')[1]!.trigger('click');
  expect(page.get('#report-panel-vehicles tbody').text()).toContain('XE-11');
  await page.get('#report-tab-late-stops').trigger('click');
  await page.get('#report-panel-late-stops input[type="search"]').setValue('trường học');
  await page.get('#report-tab-vehicles').trigger('click');
  expect(page.get('#report-panel-vehicles tbody').text()).toContain('XE-11');
  expect(page.get('#report-panel-vehicles tbody').findAll('tr')).toHaveLength(1);
  expect(page.get('.report-export-hint').text()).toContain('File Excel sẽ áp dụng bộ lọc riêng');
  await page.get('#report-tab-late-stops').trigger('click');
  expect((page.get('#report-panel-late-stops input[type="search"]').element as HTMLInputElement).value).toBe('trường học');
  expect(fetchOperationalReportDetail).toHaveBeenCalledTimes(1);
});

test('driver details show actual trips, Vietnam times and status without changing report scope', async () => {
  const page = render();
  await flushPromises();
  await page.get('#report-tab-drivers').trigger('click');
  const opener = page.get('.report-driver-trips-button');
  (opener.element as HTMLButtonElement).focus();
  await opener.trigger('click');
  const dialog = page.get('dialog');
  expect(dialog.attributes('aria-label')).toBe('Chuyến của Driver');
  expect(dialog.findAll('.driver-report-trip')).toHaveLength(2);
  expect(dialog.text()).toContain('Tuyến đến trường 1');
  expect(dialog.text()).toContain('Chuyến #1 · 51A-123');
  expect(dialog.text()).toContain('Hoàn thành');
  expect(dialog.text()).toContain('Đang thực hiện');
  expect(dialog.text()).toContain('06:10 7/10/26');
  expect(dialog.text()).not.toMatch(/IN_PROGRESS|COMPLETED/);
  expect(dialog.findAll('time')).toHaveLength(5);
  expect(fetchOperationalReportDetail).toHaveBeenCalledTimes(1);
  await dialog.get('[aria-label="Đóng danh sách chuyến"]').trigger('click');
  expect(page.find('dialog').exists()).toBe(false);
  expect(document.activeElement).toBe(opener.element);
  expect(page.get('#report-panel-drivers').isVisible()).toBe(true);
});

test('driver trips paginate independently and drivers with the same name remain distinct', async () => {
  const result = data(11);
  result.drivers.push({ ...result.drivers[0]!, driverId: 2, tripCount: 1, trips: [{ ...result.drivers[0]!.trips[0]!, tripId: 99, routeName: 'Tuyến của tài xế thứ hai' }] });
  vi.mocked(fetchOperationalReportDetail).mockResolvedValueOnce(result);
  const page = render();
  await flushPromises();
  await page.get('#report-tab-drivers').trigger('click');
  await page.findAll('.report-driver-trips-button')[0]!.trigger('click');
  expect(page.findAll('dialog .driver-report-trip')).toHaveLength(10);
  const scrollBody = page.get('.driver-report-trips-body').element as HTMLDivElement;
  scrollBody.scrollTop = 100;
  await page.get('dialog .pagination-controls').findAll('button')[1]!.trigger('click');
  expect(scrollBody.scrollTop).toBe(0);
  expect(page.findAll('dialog .driver-report-trip')).toHaveLength(1);
  expect(page.get('dialog').text()).toContain('Tuyến đến trường 11');
  await page.get('dialog').trigger('cancel');
  await page.findAll('.report-driver-trips-button')[1]!.trigger('click');
  expect(page.get('dialog').text()).toContain('Tuyến của tài xế thứ hai');
  expect(page.get('dialog').text()).not.toContain('Tuyến đến trường 11');
  expect(page.get('dialog .pagination-controls').text()).toContain('Trang 1 / 1');
  expect(fetchOperationalReportDetail).toHaveBeenCalledTimes(1);
  await page.get('.reports-refresh').trigger('click');
  await flushPromises();
  expect(page.find('dialog').exists()).toBe(false);
});

test('driver trip fallback does not claim zero when an older report omits details', async () => {
  const result = data();
  result.drivers[0]!.trips = [];
  vi.mocked(fetchOperationalReportDetail).mockResolvedValueOnce(result);
  const page = render();
  await flushPromises();
  await page.get('#report-tab-drivers').trigger('click');
  await page.get('.report-driver-trips-button').trigger('click');
  expect(page.get('dialog').text()).toContain('2 chuyến');
  expect(page.get('dialog').text()).toContain('Chưa có danh sách chuyến');
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

test('Vietnamese month selector remains explicit and handles leap February and year/month transitions', async () => {
  const page = render();
  await flushPromises();
  expect(page.find('input[type="month"]').exists()).toBe(false);
  expect(page.get('[aria-label="Tháng báo cáo"]').findAll('option').map((option) => option.text()))
    .toEqual(Array.from({ length: 12 }, (_, index) => `Tháng ${index + 1}`));
  await page.get('[aria-label="Năm báo cáo"]').setValue('2024');
  await page.get('[aria-label="Tháng báo cáo"]').setValue('02');
  await flushPromises();
  expect(fetchOperationalReportDetail).toHaveBeenLastCalledWith(expect.objectContaining({ from: '2024-02-01', to: '2024-02-29' }), expect.any(AbortSignal));
  await page.get('[aria-label="Kỳ báo cáo"]').setValue('YEAR');
  await flushPromises();
  expect(fetchOperationalReportDetail).toHaveBeenLastCalledWith(expect.objectContaining({ from: '2024-01-01', to: '2024-12-31' }), expect.any(AbortSignal));
  await page.get('[aria-label="Kỳ báo cáo"]').setValue('MONTH');
  await flushPromises();
  expect(fetchOperationalReportDetail).toHaveBeenLastCalledWith(expect.objectContaining({ from: '2024-02-01', to: '2024-02-29' }), expect.any(AbortSignal));
});

test('incident details show the trip, vehicle, driver, time and actual resolution status in Vietnamese', async () => {
  const result = data();
  result.incidents = [{ type: 'VEHICLE_BREAKDOWN', severity: 'CRITICAL', count: 1 }];
  result.incidentDetails = [{ id: 'incident-1', tripId: 7, routeName: 'Tuyến đến trường', vehiclePlateNumber: '51B12345', driverName: 'Nguyễn Văn A', type: 'VEHICLE_BREAKDOWN', severity: 'CRITICAL', occurredAt: result.generatedAt, status: 'RESOLVED', detail: 'Đã sửa động cơ' }];
  vi.mocked(fetchOperationalReportDetail).mockResolvedValueOnce(result);
  const page = render();
  await flushPromises();
  const section = page.get('[aria-label="Sự cố và cảnh báo an toàn"]');
  expect(section.text()).toContain('Tuyến đến trường');
  expect(section.text()).toContain('Chuyến #7 · 51B12345');
  expect(section.text()).toContain('Nguyễn Văn A');
  expect(section.text()).toContain('Xe gặp sự cố');
  expect(section.text()).toContain('Khẩn cấp');
  expect(section.text()).toContain('Đã xử lý');
  expect(section.text()).not.toMatch(/CRITICAL|RESOLVED|VEHICLE_BREAKDOWN/);
});

test('occupancy tab keeps only the per-vehicle table even when report data is incomplete', async () => {
  const result = data();
  result.employeeOccupancy = { ...result.employeeOccupancy, completedTripCount: 1, tripsWithCompleteBoardingData: 1, totalBoardings: 5, averageBoardingsPerTrip: 5, seatUtilizationPercent: 50 };
  result.employeeOccupancyByVehicle = [{ vehicleId: 1, plateNumber: 'XE-1', vehicleName: 'Xe một', seatCapacity: 10, completedTripCount: 1, tripsWithCompleteBoardingData: 1, tripsMissingBoardingData: 0, totalBoardings: 5, averageBoardingsPerTrip: 5, averageOnboard: null, seatUtilizationPercent: 50 }];
  vi.mocked(fetchOperationalReportDetail).mockResolvedValueOnce(result);
  const page = render();
  await flushPromises();
  const section = page.get('[aria-label="Thống kê người trên xe"]');
  expect(section.find('.reports-data-note').exists()).toBe(false);
  expect(section.find('.reports-data-indicator').exists()).toBe(false);
  expect(section.find('.reports-occupancy-metrics').exists()).toBe(false);
  expect(section.find('.reports-method-details').exists()).toBe(false);
  expect(section.get('.report-occupancy-table').text()).toContain('50%');
  vi.mocked(fetchOperationalReportDetail).mockResolvedValueOnce({ ...result, employeeOccupancy: { ...result.employeeOccupancy, tripsMissingBoardingData: 2, tripsMissingSeatCapacity: 3 } });
  await page.get('.reports-refresh').trigger('click');
  await flushPromises();
  const updatedSection = page.get('[aria-label="Thống kê người trên xe"]');
  expect(updatedSection.find('.reports-occupancy-metrics').exists()).toBe(false);
  expect(updatedSection.find('.reports-method-details').exists()).toBe(false);
  expect(updatedSection.text()).not.toContain('2 chuyến chưa xác nhận');
  expect(updatedSection.get('.report-occupancy-table').text()).toContain('50%');
});

test('seat usage distinguishes unknown seats from confirmed zero occupancy', async () => {
  const result = data();
  const baseRow = { vehicleId: 1, plateNumber: 'XE-1', vehicleName: 'Xe một', seatCapacity: 10, completedTripCount: 1, tripsWithCompleteBoardingData: 1, tripsMissingBoardingData: 0, totalBoardings: 0, averageBoardingsPerTrip: 0, averageOnboard: null, seatUtilizationPercent: 0 };
  result.employeeOccupancyByVehicle = [baseRow, { ...baseRow, vehicleId: 2, plateNumber: 'XE-2', seatUtilizationPercent: null }];
  vi.mocked(fetchOperationalReportDetail).mockResolvedValueOnce(result);
  const page = render();
  await flushPromises();
  await page.get('#report-tab-occupancy').trigger('click');
  const usages = page.findAll('.report-seat-usage');
  expect(usages[0]!.text()).toBe('0%');
  expect(usages[0]!.get('.report-seat-track > span').attributes('style')).toContain('width: 0%');
  expect(usages[1]!.text()).toBe('Chưa tính được');
  expect(usages[1]!.find('.report-seat-track').exists()).toBe(false);
});

test('Excel exports separate sheets with all rows, Vietnamese labels and the independent late-stop filter', async () => {
  const result = data(11);
  result.drivers.push({ ...result.drivers[0]!, driverId: 2, driverName: 'Tài xế khác', tripCount: 1, trips: [{ ...result.drivers[0]!.trips[0]!, tripId: 99, routeName: 'Tuyến của tài xế khác' }] });
  result.incidents = [{ type: 'VEHICLE_BREAKDOWN', severity: 'CRITICAL', count: 11 }];
  result.incidentDetails = Array.from({ length: 11 }, (_, index) => ({ id: `incident-${index}`, tripId: index + 1, routeName: `Tuyến ${index + 1}`, vehiclePlateNumber: '51B12345', driverName: 'Nguyễn Văn A', type: 'VEHICLE_BREAKDOWN', severity: 'CRITICAL', occurredAt: result.generatedAt, status: 'RESOLVED', detail: `Sự cố ${index + 1}` }));
  result.lateStops = [{ tripId: 1, routeName: 'Tuyến cần xuất', vehiclePlateNumber: '51B12345', driverName: 'Nguyễn Văn A', stationName: 'Trạm trường học', stopSequence: 3, plannedArrivalAt: result.generatedAt, actualArrivalAt: result.generatedAt, delaySeconds: 1200 }, { tripId: 2, routeName: 'Tuyến bị lọc', vehiclePlateNumber: '51B67890', driverName: 'Nguyễn Văn B', stationName: 'Trạm khác', stopSequence: 2, plannedArrivalAt: result.generatedAt, actualArrivalAt: result.generatedAt, delaySeconds: 600 }];
  vi.mocked(fetchOperationalReportDetail).mockResolvedValueOnce(result);
  const page = render();
  await flushPromises();
  expect(page.get('.report-incidents-table').findAll('tbody tr')).toHaveLength(10);
  await page.get('[aria-label="Các lần trễ trạm"] input[type="search"]').setValue('Tuyến cần xuất');
  const toFile = vi.fn().mockResolvedValue(undefined);
  vi.mocked(writeExcelFile).mockReturnValue({ toFile, toBlob: vi.fn().mockResolvedValue(new Blob()) });
  await page.get('.reports-export').trigger('click');
  await flushPromises();
  expect(toFile).toHaveBeenCalledWith('bao-cao-van-hanh-2026-09-08-2026-10-07.xlsx');
  const sheets = vi.mocked(writeExcelFile).mock.calls[0]![0] as unknown as Array<{ sheet: string; data: unknown[][] }>;
  expect(sheets.map((sheet) => sheet.sheet)).toEqual(['Tổng quan', 'Hành khách', 'Theo xe', 'Theo tài xế', 'Chuyến tài xế', 'Trễ trạm', 'Sự cố', 'Chi tiết sự cố']);
  const cellValue = (cell: unknown) => cell && typeof cell === 'object' && 'value' in cell ? (cell as { value: unknown }).value : cell;
  const values = (sheetName: string) => sheets.find((sheet) => sheet.sheet === sheetName)!.data.flat().map(cellValue);
  expect(values('Tổng quan')).toContain('Bộ lọc trễ trạm');
  expect(values('Tổng quan')).toContain('Tìm: Tuyến cần xuất; Trạm: Tất cả; Xe: Tất cả; Tài xế: Tất cả; Trễ tối thiểu: 0 phút');
  expect(sheets.find((sheet) => sheet.sheet === 'Trễ trạm')!.data).toHaveLength(2);
  expect(values('Trễ trạm')).toContain('Tuyến cần xuất');
  expect(values('Trễ trạm')).not.toContain('Tuyến bị lọc');
  expect(values('Sự cố')).toContain('Khẩn cấp');
  expect(sheets.find((sheet) => sheet.sheet === 'Chi tiết sự cố')!.data).toHaveLength(12);
  expect(values('Chi tiết sự cố')).toContain('Sự cố 11');
  expect(values('Chi tiết sự cố')).toContain('Đã xử lý');
  expect(values('Chuyến tài xế')).toContain('Tuyến đến trường 11');
  expect(values('Chuyến tài xế')).toContain('Tuyến của tài xế khác');
  expect(values('Chuyến tài xế')).toContain('Đang thực hiện');
  expect(values('Chuyến tài xế')).not.toContain('IN_PROGRESS');
  expect(values('Theo xe')).toContain(result.vehicles[0]!.tripCount);
});
