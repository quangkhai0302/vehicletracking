import type { SimulationScenario } from '@/features/tracking/types/operations';

export const SIMULATION_SCENARIOS: Record<
  SimulationScenario,
  { label: string; description: string }
> = {
  CURRENT_TRAFFIC: {
    label: 'Theo giao thông hiện tại',
    description:
      'Chạy theo dữ liệu giao thông khả dụng; nếu chưa có dữ liệu, dùng tốc độ của tuyến.',
  },
  NORMAL: {
    label: 'Bình thường',
    description: 'Chạy theo thời lượng tuyến ban đầu, không áp dụng tình huống ùn tắc.',
  },
  CONGESTION: {
    label: 'Ùn tắc',
    description: 'Xe chạy chậm còn một nửa tốc độ trên đường. Thời gian hành trình vẫn tăng.',
  },
  BLOCKED: {
    label: 'Đường bị chặn',
    description: 'Xe đứng chờ, thời gian hành trình vẫn tăng. Chọn Bình thường để tiếp tục đi.',
  },
  OFF_ROUTE: {
    label: 'Lệch tuyến',
    description:
      'Vị trí mô phỏng được đưa ra ngoài tuyến. Xe chờ trở lại tuyến trước khi check-in trạm kế tiếp; thời gian hành trình vẫn tăng. Chọn Bình thường để tiếp tục.',
  },
};
