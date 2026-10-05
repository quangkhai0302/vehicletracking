export interface DriverAssignmentRequest {
  requestId: string;
  tripId: number;
  routeName: string;
  vehiclePlate: string;
  tripCreatedAt: string;
  requestedAt: string;
}
export interface AssignmentActionResponse {
  requestId: string;
  status: 'ACCEPTED' | 'DECLINED';
  tripId: number;
}
export interface DriverDispatchInboxItem {
  id: number; type: string; title: string; detail: string | null;
  tripId: number; offerId: string | null; assignmentRequestId?: string | null;
  createdAt: string; readAt: string | null;
}
