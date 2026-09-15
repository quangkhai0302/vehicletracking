import type { RouteDetail } from './route';

export type VehicleType = 'CAR' | 'MOTORCYCLE';
export const VEHICLE_TYPE_LABELS: Record<VehicleType, string> = {
  CAR: 'Ô tô',
  MOTORCYCLE: 'Xe máy',
};
export const vehicleTypeLabel = (type: VehicleType | undefined) => VEHICLE_TYPE_LABELS[type ?? 'CAR'];
export interface FleetVehicle {
  id: number;
  plateNumber: string;
  name: string;
  description: string | null;
  vehicleType: VehicleType;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}
export interface VehicleInput { plateNumber: string; name: string; description: string | null; vehicleType: VehicleType }
export type TripStatus = 'SCHEDULED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
export type TripAction = 'start' | 'complete' | 'cancel';
export interface TripInput { vehicleId: number; routeId: number; scheduledDepartureAt: string }
export interface TripUpdateInput { scheduledDepartureAt: string }
export interface TripSummary {
  attemptNumber?: number;
  id: number; vehicleId: number; vehiclePlateNumber: string; vehicleType: VehicleType; routeId: number; routeName: string;
  status: TripStatus; scheduledDepartureAt: string; plannedEndAt: string;
  startedAt: string | null; endedAt: string | null; createdAt: string;
}
export interface TripStop {
  sequenceNumber: number; stationId: number; stationName: string;
  latitude: number; longitude: number; checkinRadiusMeters: number; dwellDurationSeconds: number;
  arrivalOffsetSeconds: number; departureOffsetSeconds: number;
  plannedArrivalAt: string; plannedDepartureAt: string;
}
export interface TripDetail { trip: TripSummary; stops: TripStop[]; route: RouteDetail }
export const TRIP_STATUS_LABELS: Record<TripStatus, string> = {
  SCHEDULED: 'Chờ khởi hành', IN_PROGRESS: 'Đang thực hiện', COMPLETED: 'Hoàn thành', CANCELLED: 'Đã hủy',
};
