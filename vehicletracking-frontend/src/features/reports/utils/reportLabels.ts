const incidents: Record<string, string> = {
  OFF_ROUTE_DETECTED: 'Lệch tuyến', OVERSPEED: 'Vượt tốc độ',
  VEHICLE_BREAKDOWN: 'Xe gặp sự cố', EMERGENCY_STOP: 'Dừng khẩn cấp',
  ROAD_BLOCKED: 'Đường bị chặn', OTHER: 'Sự cố khác', SIMULATION_INCIDENT: 'Sự cố tài xế báo',
};
const severities: Record<string, string> = { MAJOR: 'Nghiêm trọng', CRITICAL: 'Khẩn cấp' };
const statuses: Record<string, string> = {
  OPEN: 'Chưa xử lý', ACKNOWLEDGED: 'Đã tiếp nhận', RESOLVED: 'Đã xử lý', RECORDED: 'Đã ghi nhận',
};
export const incidentLabel = (type: string) => incidents[type] ?? 'Cảnh báo khác';
export const severityLabel = (severity: string) => severities[severity] ?? 'Chưa xác định';
export const statusLabel = (status: string) => statuses[status] ?? 'Chưa xác định';
