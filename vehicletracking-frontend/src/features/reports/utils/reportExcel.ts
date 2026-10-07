import type { CellObject, Options, Row } from 'write-excel-file/browser';
import { TRIP_STATUS_LABELS } from '@/features/fleet/types/fleet';
import type { OperationalReportDetail, OperationalReportLateStop } from '@/features/reports/types/reports';
import { incidentLabel, severityLabel, statusLabel } from './reportLabels';
import {
  excelColors as colors, excelDate, mergedRow, reportCell, reportExcelFeatures, reportHeaders,
  type ExcelValue, type ReportColumn, type ReportSheet, type ReportSheetLayout,
} from './reportExcelLayout';

interface ExportScope {
  vehicle: string;
  driver: string;
  lateStops: OperationalReportLateStop[];
  lateStopFilterDescription: string;
}
interface TableOptions {
  totals?: ExcelValue[];
  notes?: string[];
  bars?: ReportSheetLayout['bars'];
  empty?: string;
}
const col = (title: string, width: number, kind: ReportColumn['kind'] = 'text'): ReportColumn => ({ title, width, kind });
const vehicleName = (plate: string | null, name: string | null) => [plate, name].filter(Boolean).join(' · ') || 'Chưa có xe';
const dateLabel = (date: string) => date.split('-').reverse().join('/');
const compare = (a: string | null, b: string | null) => (a ?? '').localeCompare(b ?? '', 'vi');
const sum = <T>(rows: T[], value: (row: T) => number) => rows.reduce((total, row) => total + value(row), 0);
const occupancyNote = 'Chỉ tính số người của chuyến hoàn tất, đã xác nhận mọi trạm đón và đã đến điểm cuối. Một người đi nhiều chuyến được tính nhiều lượt.';
const seatNote = 'Tỷ lệ sử dụng ghế dùng số ghế lưu lúc từng chuyến khởi hành; chuyến chưa biết số ghế không tham gia tính tỷ lệ.';
const lateTripNote = 'Chuyến quá giờ dự kiến chỉ xét chuyến theo lịch cố định. Số lần đến trạm trễ xét từng lần đến trạm sau giờ dự kiến ban đầu.';

export function buildReportWorkbook(report: OperationalReportDetail, scope: ExportScope): {
  sheets: ReportSheet[]; options: Options<File | Blob | ArrayBuffer>;
} {
  const sheets: ReportSheet[] = [];
  const layouts: (ReportSheetLayout | null)[] = [];
  const period = `Kỳ báo cáo: ${dateLabel(report.from)} – ${dateLabel(report.to)} · Giờ Việt Nam`;
  const scopeText = `Phương tiện: ${scope.vehicle} · Tài xế: ${scope.driver}`;
  const intro = (title: string, count: number, description: string): Row[] => [
    mergedRow(title.toLocaleUpperCase('vi-VN'), count, { fontSize: 18, fontWeight: 'bold',
      backgroundColor: colors.navy, textColor: '#FFFFFF', height: 38 }),
    mergedRow(period, count, { backgroundColor: colors.blue, height: 26 }),
    mergedRow(scopeText, count, { textColor: colors.muted, height: 30 }),
    mergedRow(description, count, { textColor: colors.muted, fontSize: 10, height: 32 }),
  ];
  const notes = (data: Row[], count: number, width: number, lines: string[]) => {
    data.push([]);
    data.push(mergedRow('GHI CHÚ', count, { fontWeight: 'bold', backgroundColor: colors.blue, height: 25 }));
    for (const line of lines) {
      data.push(mergedRow(line, count, { textColor: colors.muted, fontSize: 10,
        height: Math.max(28, Math.ceil(line.length / Math.max(30, width * 0.8)) * 15 + 12) }));
    }
  };
  const table = (name: string, description: string, columns: ReportColumn[], rows: ExcelValue[][], config: TableOptions = {}) => {
    const data = [...intro(name, columns.length, description), [], reportHeaders(columns)];
    const headerRow = data.length;
    if (rows.length) {
      rows.forEach((row, index) => {
        const lines = Math.max(...row.map((value, i) => value instanceof Date ? 1
          : String(value ?? '').split('\n').reduce((total, line) => total + Math.max(1, Math.ceil(line.length / Math.max(8, columns[i]!.width - 3))), 0)));
        data.push(row.map((value, i) => reportCell(value, columns[i]!, {
          backgroundColor: index % 2 ? colors.stripe : '#FFFFFF', height: Math.max(30, lines * 15 + 12),
        })));
      });
      if (config.totals) data.push(config.totals.map((value, i) => reportCell(value, columns[i]!, {
        fontWeight: 'bold', backgroundColor: colors.blue, height: 34, topBorderStyle: 'medium',
      })));
    } else {
      data.push(mergedRow(config.empty ?? 'Không có bản ghi trong phạm vi báo cáo đã chọn.', columns.length, {
        textColor: colors.muted, backgroundColor: colors.stripe, height: 40,
      }));
    }
    notes(data, columns.length, sum(columns, (column) => column.width), [
      `${rows.length.toLocaleString('vi-VN')} bản ghi. Ngày báo cáo được xác định theo giờ khởi hành dự kiến tại Việt Nam.`,
      ...(config.notes ?? []),
    ]);
    sheets.push({ sheet: name, data, columns: columns.map(({ width }) => ({ width })),
      stickyRowsCount: headerRow, stickyColumnsCount: 1, showGridLines: false, orientation: 'landscape',
      zoomScale: columns.length > 7 ? 85 : 100 });
    layouts.push({ headerRow, lastDataRow: headerRow + rows.length, columnCount: columns.length, bars: config.bars });
  };

  const occupancy = report.employeeOccupancy;
  const incidentCount = sum(report.incidents, (row) => row.count);
  const passengerTotal: ExcelValue = occupancy.completedTripCount > 0 && !occupancy.tripsWithCompleteBoardingData
    ? 'Chưa xác nhận' : occupancy.totalBoardings;
  const overview = intro('Báo cáo vận hành', 6, 'Kết quả trong kỳ · Chi tiết nằm tại các trang tính cùng tên với mục báo cáo.');
  const metrics = (title: string, items: { title: string; value: ExcelValue; span: number; kind?: ReportColumn['kind'] }[]) => {
    overview.push([], mergedRow(title, 6, { fontWeight: 'bold', height: 28 }));
    const headers: Row = [], values: Row = [];
    for (const item of items) {
      const column = col(item.title, 22 * item.span, item.kind ?? 'integer');
      headers.push({ ...reportCell(item.title, column, { fontWeight: 'bold', backgroundColor: colors.blue, height: 46 }), columnSpan: item.span },
        ...Array.from({ length: item.span - 1 }, () => null));
      const valueStyle: Partial<CellObject> = { fontWeight: 'bold', fontSize: typeof item.value === 'number' ? 22 : 12,
        textColor: colors.teal, backgroundColor: '#F5FBFD', height: 54, columnSpan: item.span };
      values.push(reportCell(item.value, column, valueStyle), ...Array.from({ length: item.span - 1 }, () => null));
    }
    overview.push(headers, values);
  };
  metrics('HOẠT ĐỘNG CHUYẾN XE', [
    { title: 'Chuyến đã chạy', value: report.summary.tripCount, span: 1 },
    { title: 'Chuyến hoàn tất', value: report.summary.completedTripCount, span: 1 },
    { title: 'Chuyến quá giờ dự kiến', value: report.summary.lateTripCount, span: 1 },
    { title: 'Lần đến trạm trễ', value: report.lateStops.length, span: 1 },
    { title: 'Sự cố / cảnh báo', value: incidentCount, span: 2 },
  ]);
  metrics('HÀNH KHÁCH VÀ MỨC SỬ DỤNG GHẾ', [
    { title: 'Tổng lượt người được chở', value: passengerTotal, span: 2 },
    { title: 'Người trung bình/chuyến', value: occupancy.averageBoardingsPerTrip ?? 'Chưa xác nhận', span: 2, kind: 'decimal' },
    { title: 'Tỷ lệ sử dụng ghế/chuyến', value: occupancy.seatUtilizationPercent ?? 'Chưa tính được', span: 2, kind: 'percent' },
  ]);
  notes(overview, 6, 132, [
    `Đã xác nhận số người ở mọi trạm đón: ${occupancy.tripsWithCompleteBoardingData} chuyến. Còn thiếu xác nhận: ${occupancy.tripsMissingBoardingData} chuyến. Chưa biết số ghế lúc khởi hành: ${occupancy.tripsMissingSeatCapacity} chuyến.`,
    lateTripNote, occupancyNote,
    'Người trung bình/chuyến = tổng lượt người được chở / số chuyến hoàn tất đã xác nhận mọi trạm đón.',
    'Tỷ lệ sử dụng ghế/chuyến = tổng người được chở / tổng số ghế lúc khởi hành trên cùng các chuyến đủ xác nhận và biết số ghế.',
    report.employeePassengerDataNote,
    'Bộ lọc trễ trạm', scope.lateStopFilterDescription,
    `Sheet Trễ trạm có ${scope.lateStops.length} bản ghi sau bộ lọc riêng. Các chỉ tiêu Tổng quan và các sheet khác theo phạm vi chung ở đầu trang.`,
  ]);
  overview.push([], [reportCell('Thời điểm lập báo cáo', col('', 22), { columnSpan: 2, borderStyle: undefined }), null,
    reportCell(excelDate(report.generatedAt), col('', 22, 'datetime'), { columnSpan: 2, borderStyle: undefined }), null, null, null]);
  sheets.push({ sheet: 'Tổng quan', data: overview, columns: Array.from({ length: 6 }, () => ({ width: 22 })),
    stickyRowsCount: 4, showGridLines: false, orientation: 'landscape' });
  layouts.push(null);

  table('Hành khách theo xe', 'Một dòng là một xe · Đối chiếu số người và sức chứa.', [
    col('Xe', 34), col('Số ghế hiện tại', 20, 'integer'), col('Người trung bình/chuyến', 24, 'decimal'), col('Tỷ lệ sử dụng ghế/chuyến', 26, 'percent'),
  ], [...report.employeeOccupancyByVehicle].sort((a, b) => compare(a.plateNumber, b.plateNumber)).map((row) => [
    vehicleName(row.plateNumber, row.vehicleName), row.seatCapacity ?? 'Chưa cấu hình', row.averageBoardingsPerTrip,
    row.seatUtilizationPercent ?? 'Chưa tính được',
  ]), { notes: [occupancyNote, seatNote, 'Thanh màu trong ô thể hiện tỷ lệ sử dụng trên thang 0–100%.'], bars: [{ column: 3, color: 'B2DFEB', max: 1 }] });

  const days = [...(report.employeeOccupancyByDay ?? [])].sort((a, b) => compare(a.date, b.date));
  table('Hành khách theo ngày', 'Một dòng là một ngày chạy · Ngày được sắp tăng dần.', [
    col('Ngày chạy', 16, 'date'), col('Chuyến hoàn tất', 18, 'integer'), col('Chuyến đã xác nhận đủ số người', 25, 'integer'),
    col('Lượt người được chở', 23, 'integer'), col('Người trung bình/chuyến', 24, 'decimal'), col('Tỷ lệ sử dụng ghế', 22, 'percent'),
  ], days.map((row) => [excelDate(row.date), row.completedTripCount, row.confirmedTripCount,
    row.confirmedTripCount ? row.totalBoardings : 'Chưa xác nhận', row.averageBoardingsPerTrip, row.seatUtilizationPercent ?? 'Chưa tính được']), {
    notes: [occupancyNote, seatNote], totals: ['Tổng cộng', sum(days, (r) => r.completedTripCount), sum(days, (r) => r.confirmedTripCount), passengerTotal, '', ''],
    bars: [{ column: 5, color: 'B2DFEB', max: 1 }],
  });

  const stations = [...(report.employeeOccupancyByStation ?? [])].sort((a, b) => b.totalBoardings - a.totalBoardings || compare(a.stationName, b.stationName));
  table('Hành khách theo trạm', 'Một dòng là một trạm đón · Trạm có nhiều lượt người lên xe được xếp trước.', [
    col('Trạm đón', 38), col('Lượt xe ghé', 20, 'integer'), col('Lượt người lên xe', 24, 'integer'), col('Người trung bình/lượt ghé', 28, 'decimal'),
  ], stations.map((row) => [row.stationName ?? 'Chưa có tên trạm', row.visitCount, row.totalBoardings, row.averageBoardingsPerVisit ?? 'Chưa tính được']), {
    totals: ['Tổng các trạm', sum(stations, (r) => r.visitCount), sum(stations, (r) => r.totalBoardings), ''],
    notes: [occupancyNote, 'Lượt xe ghé là số lượt dừng đón tại trạm, không phải số xe khác nhau. Trạm cuối không đón thêm người.'],
    empty: 'Chưa có chuyến hoàn tất đã xác nhận số người ở mọi trạm đón để thống kê theo trạm.',
  });

  const trips = [...(report.employeeOccupancyTrips ?? [])].sort((a, b) => compare(a.scheduledDepartureAt, b.scheduledDepartureAt) || a.tripId - b.tripId);
  table('Hành khách theo chuyến', 'Một dòng là một chuyến · Xem số ghế và tình trạng xác nhận hành khách.', [
    col('Ngày chạy', 16, 'date'), col('Khởi hành dự kiến', 23, 'datetime'), col('Tên tuyến', 32), col('Mã chuyến', 14, 'integer'),
    col('Xe', 19), col('Tài xế', 25), col('Số ghế lúc khởi hành', 21, 'integer'), col('Người được chở', 21, 'integer'), col('Tình trạng xác nhận', 25, 'status'),
  ], trips.map((row) => [excelDate(row.serviceDate), excelDate(row.scheduledDepartureAt), row.routeName ?? 'Chưa có tên tuyến', row.tripId,
    row.vehiclePlateNumber ?? 'Chưa có xe', row.driverName ?? 'Chưa phân công', row.seatCapacity ?? 'Chưa biết số ghế',
    row.totalBoardings, row.complete ? 'Đủ xác nhận' : 'Chưa xác nhận đủ']), { notes: [occupancyNote, seatNote] });

  table('Người lên từng trạm', 'Một dòng là một trạm đón trong chuyến · Sắp theo ngày, chuyến rồi thứ tự trạm.', [
    col('Ngày chạy', 16, 'date'), col('Tên tuyến', 32), col('Mã chuyến', 14, 'integer'), col('Xe', 19),
    col('Thứ tự trạm', 16, 'integer'), col('Trạm đón', 32), col('Người lên tại trạm', 23, 'integer'), col('Người trên xe sau trạm', 25, 'integer'),
  ], trips.flatMap((trip) => [...trip.pickupStops].sort((a, b) => a.stopSequence - b.stopSequence).map((stop) => [
    excelDate(trip.serviceDate), trip.routeName ?? 'Chưa có tên tuyến', trip.tripId, trip.vehiclePlateNumber ?? 'Chưa có xe',
    stop.stopSequence, stop.stationName ?? 'Chưa có tên trạm', stop.boardingCount, stop.onboardAfterStop ?? 'Chưa rõ',
  ])), { notes: ['Hành khách chỉ lên tại trạm đón và cùng xuống ở điểm cuối. Số người trên xe là cộng dồn, không cộng cột này thành tổng.',
    'Bảng gồm cả trạm chưa xác nhận trong các chuyến hoàn tất để đối chiếu. Chưa xác nhận không có nghĩa là 0 người.'] });

  const resources = [
    { name: 'Theo xe', identity: 'Xe', rows: [...report.vehicles].sort((a, b) => compare(a.plateNumber, b.plateNumber))
      .map((row) => ({ ...row, label: vehicleName(row.plateNumber, row.vehicleName) })) },
    { name: 'Theo tài xế', identity: 'Tài xế', rows: [...report.drivers].sort((a, b) => compare(a.driverName, b.driverName))
      .map((row) => ({ ...row, label: row.driverName ?? 'Chưa phân công' })) },
  ];
  for (const resource of resources) {
    const rows = [...resource.rows];
    table(resource.name, `Một dòng là một ${resource.identity.toLocaleLowerCase('vi-VN')} · Kết quả vận hành trong kỳ.`, [
      col(resource.identity, 32), col('Chuyến đã thực hiện', 22, 'integer'), col('Chuyến đã hoàn tất', 22, 'integer'),
      col('Chuyến quá giờ dự kiến', 25, 'integer'), col('Số lần đến trạm trễ', 22, 'integer'), col('Sự cố / cảnh báo', 21, 'integer'), col('Lượt người được chở', 24, 'integer'),
    ], rows.map((row) => [row.label, row.tripCount, row.completedTripCount, row.lateTripCount, row.lateStopCount, row.incidentCount, row.employeePassengerCount]), {
      totals: ['Tổng cộng', sum(rows, (r) => r.tripCount), sum(rows, (r) => r.completedTripCount), sum(rows, (r) => r.lateTripCount),
        sum(rows, (r) => r.lateStopCount), sum(rows, (r) => r.incidentCount), rows.some((r) => r.employeePassengerCount == null)
          ? 'Chưa xác nhận đủ' : sum(rows, (r) => r.employeePassengerCount ?? 0)],
      notes: [lateTripNote, occupancyNote],
    });
  }

  table('Chuyến tài xế', 'Một dòng là một chuyến của tài xế · Sắp theo tài xế và ngày chạy.', [
    col('Tài xế', 26), col('Tên tuyến', 32), col('Mã chuyến', 14, 'integer'), col('Xe', 19),
    col('Khởi hành dự kiến', 23, 'datetime'), col('Bắt đầu thực tế', 23, 'datetime'), col('Kết thúc / hủy', 23, 'datetime'), col('Trạng thái', 22, 'status'),
  ], [...report.drivers].sort((a, b) => compare(a.driverName, b.driverName)).flatMap((driver) => [...(driver.trips ?? [])]
    .sort((a, b) => compare(a.scheduledDepartureAt, b.scheduledDepartureAt) || a.tripId - b.tripId)
    .map((trip) => [driver.driverName ?? 'Chưa phân công', trip.routeName ?? 'Chưa có tên tuyến', trip.tripId,
      trip.vehiclePlateNumber ?? 'Chưa có xe', excelDate(trip.scheduledDepartureAt), excelDate(trip.startedAt) ?? 'Chưa khởi hành',
      excelDate(trip.endedAt) ?? 'Chưa kết thúc', TRIP_STATUS_LABELS[trip.status]])));

  table('Trễ trạm', 'Một dòng là một lần đến trạm trễ · Sắp theo số phút trễ giảm dần.', [
    col('Tên tuyến', 30), col('Mã chuyến', 14, 'integer'), col('Xe', 19), col('Tài xế', 25), col('Trạm', 30),
    col('Thứ tự trạm', 16, 'integer'), col('Giờ đến dự kiến', 23, 'datetime'), col('Giờ đến thực tế', 23, 'datetime'), col('Số phút trễ', 20, 'decimal'),
  ], [...scope.lateStops].sort((a, b) => b.delaySeconds - a.delaySeconds || compare(b.actualArrivalAt, a.actualArrivalAt))
    .map((row) => [row.routeName ?? 'Chưa có tên tuyến', row.tripId, row.vehiclePlateNumber ?? 'Chưa có xe', row.driverName ?? 'Chưa phân công',
      row.stationName, row.stopSequence, excelDate(row.plannedArrivalAt), excelDate(row.actualArrivalAt), row.delaySeconds / 60]), {
    notes: ['Bộ lọc trễ trạm', scope.lateStopFilterDescription, 'Số phút trễ = (giờ đến thực tế − giờ dự kiến ban đầu) / 60 giây. Thanh màu dài hơn biểu thị trễ lâu hơn trong bảng.'],
    bars: [{ column: 8, color: 'F3B5AD' }], empty: 'Không có lần trễ trạm nào khớp phạm vi và bộ lọc đã chọn.',
  });

  table('Sự cố', 'Một dòng là một loại sự cố ở một mức độ · Sắp theo số lần giảm dần.', [
    col('Loại sự cố / cảnh báo', 34), col('Mức độ', 24, 'status'), col('Số lần', 20, 'integer'),
  ], [...report.incidents].sort((a, b) => b.count - a.count).map((row) => [incidentLabel(row.type), severityLabel(row.severity), row.count]), {
    totals: ['Tổng cộng', '', incidentCount], notes: ['Xem thời điểm, xe, tài xế và nội dung ở sheet Chi tiết sự cố.'],
  });
  table('Chi tiết sự cố', 'Một dòng là một sự cố / cảnh báo · Bản ghi mới nhất ở trên.', [
    col('Thời điểm', 23, 'datetime'), col('Tên tuyến', 30), col('Mã chuyến', 14, 'integer'), col('Xe', 19), col('Tài xế', 25),
    col('Loại sự cố / cảnh báo', 26), col('Mức độ', 20, 'status'), col('Tình trạng', 20, 'status'), col('Ghi chú', 48),
  ], [...(report.incidentDetails ?? [])].sort((a, b) => compare(b.occurredAt, a.occurredAt)).map((row) => [
    excelDate(row.occurredAt), row.routeName ?? 'Chưa có tên tuyến', row.tripId, row.vehiclePlateNumber ?? 'Chưa có xe', row.driverName ?? 'Chưa phân công',
    incidentLabel(row.type), severityLabel(row.severity), statusLabel(row.status), row.detail ?? '',
  ]));
  return { sheets, options: { fontFamily: 'Calibri', fontSize: 11, features: [reportExcelFeatures(layouts)] } };
}
