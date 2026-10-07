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
  expect(sheets).toHaveLength(12);
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
      for (let id = 1; id <= 12; id++) {
        const path = `xl/worksheets/sheet${id}.xml`;
        const content = read(path);
        if (typeof content === 'string') files.set(path, content);
      }
      files.set('workbook', String(read('xl/workbook.xml')));
      return undefined;
    } } },
  }] }).toBlob();
  expect(blob.size).toBeGreaterThan(1000);
  expect(files.size).toBe(13);
  const xml = (id: number) => new DOMParser().parseFromString(files.get(`xl/worksheets/sheet${id}.xml`)!, 'application/xml');
  for (let id = 1; id <= 12; id++) {
    expect(xml(id).querySelector('parsererror')).toBeNull();
    expect(xml(id).querySelector('pageSetup')?.getAttribute('fitToWidth')).toBe('1');
    expect(xml(id).querySelector('pageSetup')?.getAttribute('orientation')).toBe('landscape');
  }
  expect(xml(1).querySelector('pageSetup')?.getAttribute('fitToHeight')).toBe('1');
  const workbook = new DOMParser().parseFromString(files.get('workbook')!, 'application/xml');
  expect(workbook.querySelectorAll('definedName[name="_xlnm.Print_Titles"]')).toHaveLength(11);
  expect(workbook.querySelector('definedName[localSheetId="9"]')?.textContent).toBe("'Trễ trạm'!$1:$6");
  const days = xml(3);
  expect(days.querySelector('autoFilter')?.getAttribute('ref')).toBe('A6:F8');
  expect(days.querySelector('pane')?.getAttribute('topLeftCell')).toBe('B7');
  expect(days.querySelector('conditionalFormatting')?.getAttribute('sqref')).toBe('F7:F8');
  expect(days.querySelector('dataBar cfvo[type="num"][val="1"]')).not.toBeNull();
  expect(days.querySelector('c[r="F7"] v')?.textContent).toBe('0.5');
  expect(days.querySelector('mergeCell[ref="A7:F7"]')).toBeNull();
  // Empty sheets must not filter the empty-state message or notes.
  expect(xml(5).querySelector('autoFilter')).toBeNull();
  const station = xml(4).querySelector('c[r="A7"]');
  expect(station?.querySelector('f')).toBeNull();
  expect(station?.getAttribute('t')).toBe('s');
  expect(object(sheets[3]!.data![6]![0])).toMatchObject({ value: '=1+1', type: String, format: '@' });
  expect(xml(10).querySelector('autoFilter')?.getAttribute('ref')).toBe('A6:I8');
  expect(xml(10).querySelector('conditionalFormatting')?.getAttribute('sqref')).toBe('I7:I8');
  expect(xml(10).querySelector('c[r="I7"] v')?.textContent).toBe('2.5');
});
