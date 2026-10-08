import type { OperationalReportDriverRow, OperationalReportVehicleRow } from './reports';

export type ReportSection = 'vehicles' | 'drivers' | 'occupancy' | 'late-stops' | 'incidents';

export type ReportTripSelection =
  | { resource: 'vehicle'; row: OperationalReportVehicleRow }
  | { resource: 'driver'; row: OperationalReportDriverRow };
