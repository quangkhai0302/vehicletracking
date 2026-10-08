import { expect, test } from 'vitest';
import writeExcelFile, { type Cell, type CellObject } from 'write-excel-file/browser';
import { buildReportWorkbook } from '@/features/reports/utils/reportExcel';
import { excelDate } from '@/features/reports/utils/reportExcelLayout';
import type { OperationalReportDetail } from '@/features/reports/types/reports';

function fixture(): OperationalReportDetail {
  return {
    from: '2026-10-01', to: '2026-10-31', generatedAt: '2026-10-06T18:05:00Z',
    summary: { from: '2026-10-01', to: '2026-10-31', generatedAt: '2026-10-06T18:05:00Z', tripCount: 2,
      completedTripCount: 2, lateTripCount: 1, totalDistanceMeters: 1000, totalRunningSeconds: 1000,
      onTimeRatePercent: 50, offRouteEventCount: 0, overspeedEventCount: 0, speedLimitKmh: 80 },
    vehicles: [], drivers: [], incidents: [], incidentDetails: [],
    lateStops: [60, 150].map((delaySeconds, index) => ({ tripId: index + 1, routeName: `Tuyến ${index + 1}`,
      vehiclePlateNumber: '51A-12345', driverName: 'Nguyễn Văn A', stationName: 'Trạm A', stopSequence: 2,
      plannedArrivalAt: '2026-10-06T18:00:00Z', actualArrivalAt: '2026-10-06T18:05:00Z', delaySeconds })),
    employeePassengerDataAvailable: true, employeePassengerDataNote: 'Số người đã được xác nhận.',
    employeeOccupancy: { completedTripCount: 2, tripsWithCompleteBoardingData: 2, tripsMissingBoardingData: 0,
      tripsMissingSeatCapacity: 0, totalBoardings: 15, averageBoardingsPerTrip: 7.5, averageOnboard: null, seatUtilizationPercent: 75 },
    employeeOccupancyByVehicle: [
      { vehicleId: 1, plateNumber: '51A-12345', vehicleName: 'Xe A', seatCapacity: 10, completedTripCount: 2,
        tripsWithCompleteBoardingData: 2, tripsMissingBoardingData: 0, totalBoardings: 15,
        averageBoardingsPerTrip: 7.5, averageOnboard: null, seatUtilizationPercent: 75 },
    ],
    employeeOccupancyByDay: [
      { date: '2026-10-07', completedTripCount: 1, confirmedTripCount: 1, totalBoardings: 10, averageBoardingsPerTrip: 10, seatUtilizationPercent: 100 },
      { date: '2026-10-06', completedTripCount: 1, confirmedTripCount: 1, totalBoardings: 5, averageBoardingsPerTrip: 5, seatUtilizationPercent: 50 },
    ],
    employeeOccupancyByStation: [{ stationId: 1, stationName: '=1+1', visitCount: 2, totalBoardings: 15, averageBoardingsPerVisit: 7.5 }],
    employeeOccupancyTrips: [],
  };
}
const build = (report = fixture()) => buildReportWorkbook(report, {
  vehicle: 'Tất cả xe', driver: 'Tất cả tài xế', lateStops: report.lateStops,
  lateStopFilterDescription: 'Trạm: Tất cả; Trễ tối thiểu: 0 phút',
});
const object = (cell: Cell | undefined) => cell as CellObject;

test('exports typed dates, percentages and minutes without changing the report or summing averages', () => {
  const report = fixture();
  const before = JSON.stringify(report);
  const { sheets } = build(report);
  expect(sheets).toHaveLength(13);
  const sheet = (name: string) => sheets.find((entry) => entry.sheet === name)!;
  const day = sheet('Hành khách theo ngày').data!;
  expect(object(day[6]![0]).value).toEqual(new Date('2026-10-06T00:00:00Z'));
  expect(object(day[6]![0]).format).toBe('dd/mm/yyyy');
  expect(object(day[6]![5])).toMatchObject({ value: 0.5, type: Number, format: '0.0%' });
  const total = day.find((row) => object(row[0])?.value === 'Tổng cộng')!;
  expect(total.map((cell) => object(cell)?.value)).toEqual(['Tổng cộng', 2, 2, 15, '', '']);
  const late = sheet('Trễ trạm').data!;
  expect(object(late[6]![8])).toMatchObject({ value: 2.5, type: Number });
  expect(object(late[7]![8]).value).toBe(1);
  expect(object(late[6]![7])).toMatchObject({ value: new Date('2026-10-07T01:05:00Z'), type: Date, format: 'dd/mm/yyyy hh:mm' });
  expect(excelDate('2026-10-07T01:05:00+07:00')).toEqual(excelDate('2026-10-06T18:05:00Z'));
  expect(JSON.stringify(report)).toBe(before);
});

test('distinguishes confirmed zero passengers from missing confirmations and unknown capacity', () => {
  const report = fixture();
  report.employeeOccupancyByVehicle[0]!.averageBoardingsPerTrip = 0;
  report.employeeOccupancyByVehicle[0]!.seatUtilizationPercent = 0;
  report.employeeOccupancyByVehicle.push({ ...report.employeeOccupancyByVehicle[0]!, vehicleId: 2,
    plateNumber: '51B-12345', seatCapacity: null, averageBoardingsPerTrip: null, seatUtilizationPercent: null });
  const rows = build(report).sheets[1]!.data!;
  expect(object(rows[6]![2]).value).toBe(0);
  expect(object(rows[6]![3])).toMatchObject({ value: 0, type: Number, format: '0.0%' });
  expect(object(rows[7]![1]).value).toBe('Chưa cấu hình');
  expect(object(rows[7]![2]).value).toBe('Chưa xác nhận');
  expect(object(rows[7]![3]).value).toBe('Chưa tính được');
});

test('serializes valid worksheet XML with bounded filters, frozen headings, safe text and numeric data bars', async () => {
  const { sheets, options } = build();
  const files = new Map<string, string>();
  const blob = await writeExcelFile(sheets, { ...options, features: [...options.features!, {
    files: { write: { files(_sheets, { read }) {
      for (let id = 1; id <= sheets.length; id++) {
        const path = `xl/worksheets/sheet${id}.xml`;
        const content = read(path);
        if (typeof content === 'string') files.set(path, content);
      }
      files.set('workbook', String(read('xl/workbook.xml')));
      return undefined;
    } } },
  }] }).toBlob();
  expect(blob.size).toBeGreaterThan(1000);
  expect(files.size).toBe(14);
  const xml = (id: number) => new DOMParser().parseFromString(files.get(`xl/worksheets/sheet${id}.xml`)!, 'application/xml');
  for (let id = 1; id <= sheets.length; id++) {
    expect(xml(id).querySelector('parsererror')).toBeNull();
    expect(xml(id).querySelector('pageSetup')?.getAttribute('fitToWidth')).toBe('1');
    expect(xml(id).querySelector('pageSetup')?.getAttribute('orientation')).toBe('landscape');
  }
  expect(xml(1).querySelector('pageSetup')?.getAttribute('fitToHeight')).toBe('1');
  const workbook = new DOMParser().parseFromString(files.get('workbook')!, 'application/xml');
  expect(workbook.querySelectorAll('definedName[name="_xlnm.Print_Titles"]')).toHaveLength(12);
  expect(workbook.querySelector('definedName[localSheetId="10"]')?.textContent).toBe("'Trễ trạm'!$1:$6");
  const days = xml(3);
  expect(days.querySelector('autoFilter')?.getAttribute('ref')).toBe('A6:F8');
  expect(days.querySelector('pane')?.getAttribute('topLeftCell')).toBe('B7');
  expect(days.querySelector('conditionalFormatting')?.getAttribute('sqref')).toBe('F7:F8');
  expect(days.querySelector('dataBar cfvo[type="num"][val="1"]')).not.toBeNull();
  expect(days.querySelector('dataBar')?.getAttribute('minLength')).toBe('0');
  expect(days.querySelector('dataBar')?.getAttribute('maxLength')).toBe('100');
  expect(days.querySelector('c[r="F7"] v')?.textContent).toBe('0.5');
  expect(days.querySelector('mergeCell[ref="A7:F7"]')).toBeNull();
  // Empty sheets must not filter the empty-state message or notes.
  expect(xml(5).querySelector('autoFilter')).toBeNull();
  const station = xml(4).querySelector('c[r="A7"]');
  expect(station?.querySelector('f')).toBeNull();
  expect(station?.getAttribute('t')).toBe('s');
  expect(object(sheets[3]!.data![6]![0])).toMatchObject({ value: '=1+1', type: String, format: '@' });
  expect(xml(11).querySelector('autoFilter')?.getAttribute('ref')).toBe('A6:I8');
  expect(xml(11).querySelector('conditionalFormatting')?.getAttribute('sqref')).toBe('I7:I8');
  expect(xml(11).querySelector('c[r="I7"] v')?.textContent).toBe('2.5');
});

test('exports every vehicle trip and incident group with the current popup details and labels', async () => {
  const report = fixture();
  const trip = { tripId: 7, routeName: 'Tuyến trường học', driverName: 'Nguyễn Văn A', scheduledDepartureAt: '2026-10-06T00:00:00Z', startedAt: '2026-10-06T00:05:00Z', endedAt: null, status: 'IN_PROGRESS' as const };
  report.vehicles = [{ vehicleId: 1, plateNumber: '51A-12345', vehicleName: 'Xe A', tripCount: 11, completedTripCount: 0, lateTripCount: 0, lateStopCount: 0, incidentCount: 12, employeePassengerCount: null,
    trips: Array.from({ length: 11 }, (_, index) => ({ ...trip, tripId: index + 1, startedAt: `2026-10-06T00:${String(index).padStart(2, '0')}:00Z` })) }];
  report.incidents = [{ type: 'VEHICLE_BREAKDOWN', severity: 'CRITICAL', count: 11 }, { type: 'VEHICLE_BREAKDOWN', severity: 'MAJOR', count: 1 }];
  report.incidentDetails = Array.from({ length: 12 }, (_, index) => ({ id: `incident-${index}`, tripId: index + 1, routeName: trip.routeName,
    vehiclePlateNumber: '51A-12345', driverName: trip.driverName, type: 'VEHICLE_BREAKDOWN', severity: index === 11 ? 'MAJOR' : 'CRITICAL',
    occurredAt: `2026-10-06T01:${String(index).padStart(2, '0')}:00Z`, status: index === 11 ? 'RESOLVED' as const : 'OPEN' as const, detail: `Sự cố ${index}` }));
  const before = JSON.stringify(report);
  const { sheets, options } = build(report);
  const sheet = (name: string) => sheets.find(entry => entry.sheet === name)!;
  const vehicleTrips = sheet('Chuyến theo xe').data!;
  const trips = vehicleTrips.filter(row => object(row[1])?.value === trip.routeName);
  expect(trips).toHaveLength(11);
  expect(trips[0]!.map(cell => object(cell)?.value)).toEqual(['51A-12345 · Xe A', trip.routeName, 11, trip.driverName,
    new Date('2026-10-06T07:00:00Z'), new Date('2026-10-06T07:10:00Z'), 'Đang thực hiện', 'Đang thực hiện']);
  expect(object(trips[0]![4]).type).toBe(Date);
  expect(object(sheet('Theo xe').data![5]![3]).value).toBe('Chuyến quá thời gian dự kiến');
  expect(object(sheet('Theo xe').data![5]![6]).value).toBe('Số lượng nhân viên đi xe');
  const incidentRows = sheet('Chi tiết sự cố').data!.filter(row => object(row[1])?.value === trip.routeName);
  expect(incidentRows).toHaveLength(12);
  expect(incidentRows[0]!.slice(5).map(cell => object(cell)?.value)).toEqual(['Xe gặp sự cố', 'Nghiêm trọng', 'Đã xử lý', 'Sự cố 11']);
  expect(incidentRows[1]!.slice(5, 8).map(cell => object(cell)?.value)).toEqual(['Xe gặp sự cố', 'Khẩn cấp', 'Chưa xử lý']);
  const files = new Map<string, string>();
  await writeExcelFile(sheets, { ...options, features: [...options.features!, { files: { write: { files(_sheets, { read }) {
    for (const name of ['Chuyến theo xe', 'Chi tiết sự cố']) {
      files.set(name, String(read(`xl/worksheets/sheet${sheets.indexOf(sheet(name)) + 1}.xml`)));
    }
    return undefined;
  } } } }] }).toBlob();
  const xml = (name: string) => new DOMParser().parseFromString(files.get(name)!, 'application/xml');
  expect(xml('Chuyến theo xe').querySelector('autoFilter')?.getAttribute('ref')).toBe('A6:H17');
  expect(xml('Chi tiết sự cố').querySelector('autoFilter')?.getAttribute('ref')).toBe('A6:I18');
  expect(JSON.stringify(report)).toBe(before);
});
