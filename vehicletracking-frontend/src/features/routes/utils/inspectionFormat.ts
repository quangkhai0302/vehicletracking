const number = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 });
export const inspectionDistance = (value: number) => value >= 1000 ? `${number.format(value / 1000)} km` : `${Math.round(value)} m`;
export function inspectionDuration(value: number | null) {
  if (value === null || !Number.isFinite(value)) return 'Chưa ước tính được';
  const seconds = Math.round(value);
  return seconds < 60 ? `${seconds} giây` : `${Math.floor(seconds / 60)} phút${seconds % 60 ? ` ${seconds % 60} giây` : ''}`;
}
export function inspectionStamp(value: string | null) {
  return value && Number.isFinite(Date.parse(value)) ? new Date(value).toLocaleString('vi-VN') : 'Không rõ thời điểm';
}
