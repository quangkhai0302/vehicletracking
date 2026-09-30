export type DispatchStartMode = 'MANUAL' | 'AUTO_IF_READY';
export type DispatchState =
  | 'MANUAL' | 'WAITING_READY' | 'READY' | 'SEARCH_WAIT'
  | 'OFFER_PENDING' | 'ATTENTION' | 'STARTED' | 'CLOSED';
export type DispatchAttentionCode =
  | 'DRIVER_NOT_READY' | 'NO_BACKUP' | 'RESOURCE_UNAVAILABLE'
  | 'START_FAILED' | 'SCHEDULE_DISABLED' | 'WINDOW_EXPIRED';

export interface DispatchSummary {
  startMode: DispatchStartMode;
  state: DispatchState;
  attentionCode: DispatchAttentionCode | null;
  readyAt: string | null;
  revision: number | null;
}

export interface DispatchDetail {
  tripId: number;
  summary: DispatchSummary;
  primaryDriverId: number | null;
  currentDriverId: number | null;
  candidates: { driverId: number; fullName: string; priority: number }[];
  activeOffer: { id: string; driverId: number; expiresAt: string } | null;
  history: {
    revision: number; kind: string; actorKind: 'SYSTEM' | 'ADMIN' | 'DRIVER';
    fromDriverId: number | null; toDriverId: number | null;
    reason: string | null; createdAt: string;
  }[];
}

export interface DriverDispatchDetail {
  tripId: number;
  startMode: DispatchStartMode;
  state: DispatchState;
  attentionCode: DispatchAttentionCode | null;
  readyAt: string | null;
  cutoffAt: string;
  revision: number;
  canReady: boolean;
  canReportUnavailable: boolean;
}

export interface DispatchOffer {
  offerId: string; tripId: number; routeName: string; vehiclePlate: string;
  scheduledDepartureAt: string; cutoffAt: string; expiresAt: string; revision: number;
}
export interface DriverDispatchInboxItem {
  id: number; type: string; title: string; detail: string | null;
  tripId: number; offerId: string | null; createdAt: string; readAt: string | null;
}

export const DISPATCH_STATE_LABELS: Record<DispatchState, string> = {
  MANUAL: 'Khởi hành thủ công',
  WAITING_READY: 'Chưa xác nhận',
  READY: 'Sẵn sàng',
  SEARCH_WAIT: 'Đang tìm tài xế',
  OFFER_PENDING: 'Đang chờ nhận lời mời',
  ATTENTION: 'Cần điều phối',
  STARTED: 'Đã khởi hành',
  CLOSED: 'Đã kết thúc',
};
export const DISPATCH_ATTENTION_LABELS: Record<DispatchAttentionCode, string> = {
  DRIVER_NOT_READY: 'Tài xế chưa xác nhận',
  NO_BACKUP: 'Chưa có tài xế phù hợp',
  RESOURCE_UNAVAILABLE: 'Xe hoặc tài xế không khả dụng',
  START_FAILED: 'Tự khởi hành thất bại',
  SCHEDULE_DISABLED: 'Lịch đã tạm dừng',
  WINDOW_EXPIRED: 'Đã hết hạn tự khởi hành',
};
