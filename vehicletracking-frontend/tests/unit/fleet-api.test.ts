import { afterEach, expect, test, vi } from 'vitest';
import { appFetch } from '@/shared/api/http';
import { FleetApiError, deleteTrip } from '@/features/fleet/api/fleet';

vi.mock('@/shared/api/http', () => ({ appFetch: vi.fn() }));

afterEach(() => vi.resetAllMocks());

test('fleet API preserves machine-readable assignment conflict codes', async () => {
  vi.mocked(appFetch).mockResolvedValue(new Response(
    JSON.stringify({
      detail: 'Chuyến đã có lịch sử phân công; hãy hủy chuyến để bảo toàn lịch sử.',
      code: 'ASSIGNMENT_HISTORY_REQUIRES_CANCEL',
    }),
    { status: 409, headers: { 'Content-Type': 'application/problem+json' } },
  ));

  await expect(deleteTrip(7)).rejects.toEqual(expect.objectContaining({
    status: 409,
    code: 'ASSIGNMENT_HISTORY_REQUIRES_CANCEL',
    message: 'Chuyến đã có lịch sử phân công; hãy hủy chuyến để bảo toàn lịch sử.',
  } satisfies Partial<FleetApiError>));
});
