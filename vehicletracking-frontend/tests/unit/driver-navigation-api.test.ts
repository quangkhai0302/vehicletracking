import { afterEach, expect, test, vi } from 'vitest';
import { appFetch } from '@/shared/api/http';
import { applyDriverRouteOption, fetchDriverNavigation, fetchDriverRouteOptions, startDriverTrip } from '@/features/fleet/api/driverPortal';
vi.mock('@/shared/api/http', () => ({ appFetch: vi.fn() }));
afterEach(() => vi.resetAllMocks());
test('all requests use driver-scoped paths and shared session/CSRF wrapper', async () => {
  vi.mocked(appFetch).mockImplementation(async () => new Response('{}', { status: 200 }));
  const signal = new AbortController().signal;
  await fetchDriverNavigation(7, signal); await startDriverTrip(7, signal); await fetchDriverRouteOptions(7, signal);
  await applyDriverRouteOption(7, 'fixture-token', 1, signal);
  expect(appFetch).toHaveBeenNthCalledWith(1, expect.stringContaining('/api/v1/driver/trips/7/navigation'), { signal });
  expect(appFetch).toHaveBeenNthCalledWith(2, expect.stringContaining('/driver/trips/7/start'), { method: 'POST', signal });
  expect(appFetch).toHaveBeenNthCalledWith(3, expect.stringContaining('/driver/trips/7/route-options'), { method: 'POST', signal });
  expect(appFetch).toHaveBeenNthCalledWith(4, expect.stringContaining('/driver/trips/7/route-options/fixture-token/apply'), {
    method: 'POST', signal, headers: { 'Content-Type': 'application/json' }, body: '{"optionIndex":1}',
  });
});
test('controlled backend error is surfaced to the driver', async () => {
  vi.mocked(appFetch).mockResolvedValue(new Response('{"detail":"Phương án đã hết hạn"}', { status: 409 }));
  await expect(applyDriverRouteOption(7, 'token', 0)).rejects.toThrow('Phương án đã hết hạn');
});
