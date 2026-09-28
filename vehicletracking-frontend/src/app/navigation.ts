import { BellRing, BusFront, CalendarClock, ClipboardList, LayoutDashboard, MapPinned, ShieldCheck, SquareChartGantt, UserRound, UsersRound } from '@lucide/vue';
import type { Component } from 'vue';
export interface NavigationItem { path: string; aliases?: readonly string[]; label: string; title: string; description: string; icon: Component; planned?: boolean; fullBleed?: boolean }
export interface NavigationGroup { label: string; items: NavigationItem[] }
export const navigationGroups: NavigationGroup[] = [
  { label: 'Tổng quan', items: [
    { path: '/dashboard', label: 'Dashboard', title: 'Tổng quan vận hành', description: 'Theo dõi nhanh quy mô đội xe và tiến độ chuyến đi.', icon: LayoutDashboard },
    { path: '/operations', label: 'Giám sát trực tiếp', title: 'Giám sát trực tiếp', description: 'Vị trí xe, mô phỏng và trạng thái vận hành trên bản đồ.', icon: MapPinned, fullBleed: true },
  ] },
  { label: 'Quản lý vận hành', items: [
    { path: '/vehicles', label: 'Phương tiện', title: 'Quản lý phương tiện', description: 'Quản lý hồ sơ xe và phân công tài xế phụ trách.', icon: BusFront },
    { path: '/drivers', label: 'Tài xế', title: 'Quản lý tài xế', description: 'Quản lý thông tin và trạng thái hoạt động của tài xế.', icon: UserRound },
    { path: '/stations', label: 'Trạm dừng', title: 'Quản lý trạm dừng', description: 'Quản lý vị trí tọa độ và bán kính nhận diện trạm.', icon: SquareChartGantt, fullBleed: true },
    { path: '/trips', aliases: ['/routes'], label: 'Tuyến & chuyến', title: 'Kế hoạch vận hành', description: 'Thiết lập tuyến đường, lập chuyến và phân công vận hành.', icon: ClipboardList },
  ] },
  { label: 'Mở rộng hệ thống', items: [
    { path: '/schedules', label: 'Lịch chạy tự động', title: 'Lịch chạy tự động', description: 'Cấu hình lịch cố định và tự động tạo chuyến.', icon: CalendarClock },
    { path: '/alerts', label: 'Cảnh báo', title: 'Trung tâm cảnh báo', description: 'Theo dõi lệch tuyến và các cảnh báo cần xử lý.', icon: BellRing },
    { path: '/reports', label: 'Báo cáo', title: 'Báo cáo và thống kê', description: 'Phân tích hiệu suất vận hành theo thời gian.', icon: SquareChartGantt },
    { path: '/users', label: 'Người dùng', title: 'Người dùng và phân quyền', description: 'Quản lý tài khoản và phạm vi truy cập.', icon: UsersRound },
  ] },
];
export const allNavigationItems = navigationGroups.flatMap(group => group.items);
export const fallbackRoute: NavigationItem = { path: '', label: 'Không tìm thấy', title: 'Không tìm thấy trang', description: 'Đường dẫn không tồn tại trong hệ thống quản lý.', icon: ShieldCheck };
export function matchesNavigationItem(item: NavigationItem, pathname: string): boolean {
  return [item.path, ...(item.aliases ?? [])].some(path => pathname === path || pathname.startsWith(`${path}/`));
}
export function findRoute(pathname: string): NavigationItem {
  return allNavigationItems.find(item => matchesNavigationItem(item, pathname)) ?? fallbackRoute;
}
