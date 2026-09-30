import { afterEach, expect, test, vi } from 'vitest';
import { appFetch } from '@/shared/api/http';
import {
  DispatchApiError, acceptDispatchOffer, fetchDispatchDetail, fetchDispatchInbox,
  fetchDispatchOffers, fetchDriverDispatch, readyForTrip, reportUnavailable,
  updateTripDispatchPolicy,
} from '@/features/dispatch/api/dispatch';

vi.mock('@/shared/api/http', () => ({ appFetch: vi.fn() }));
afterEach(() => vi.resetAllMocks());

test('admin and driver dispatch use scoped endpoints and revision payloads', async () => {
  vi.mocked(appFetch).mockImplementation(async () => new Response('{}', { status: 200 }));
  const signal = new AbortController().signal;
  await fetchDispatchDetail(7, signal);
  await updateTripDispatchPolicy(7, {
    expectedRevision: 3, startMode: 'AUTO_IF_READY', backupEnabled: true, backupDriverIds: [5, 6],
  });
  await fetchDriverDispatch(7, signal);
  await readyForTrip(7, 4);
  await reportUnavailable(7, 5, 'Bận việc');
  await fetchDispatchOffers(signal);
  await acceptDispatchOffer('offer-uuid', 6);
  await fetchDispatchInbox(signal);
  expect(appFetch).toHaveBeenNthCalledWith(1, expect.stringContaining('/trips/7/dispatch'), expect.objectContaining({ signal }));
  expect(appFetch).toHaveBeenNthCalledWith(2, expect.stringContaining('/trips/7/dispatch/policy'),
    expect.objectContaining({ method: 'PUT', body: expect.stringContaining('"backupDriverIds":[5,6]') }));
  expect(appFetch).toHaveBeenNthCalledWith(3, expect.stringContaining('/driver/trips/7/dispatch'), expect.objectContaining({ signal }));
  expect(appFetch).toHaveBeenNthCalledWith(4, expect.stringContaining('/driver/trips/7/dispatch/ready'),
    expect.objectContaining({ method: 'POST', body: '{"expectedRevision":4}' }));
  expect(appFetch).toHaveBeenNthCalledWith(5, expect.stringContaining('/driver/trips/7/dispatch/unavailable'),
    expect.objectContaining({ body: '{"expectedRevision":5,"reason":"Bận việc"}' }));
  expect(appFetch).toHaveBeenNthCalledWith(6, expect.stringContaining('/driver/dispatch/offers'), expect.objectContaining({ signal }));
  expect(appFetch).toHaveBeenNthCalledWith(7, expect.stringContaining('/driver/dispatch/offers/offer-uuid/accept'),
    expect.objectContaining({ body: '{"expectedRevision":6}' }));
  expect(appFetch).toHaveBeenNthCalledWith(8, expect.stringContaining('/driver/dispatch/inbox?limit=50'),
    expect.objectContaining({ signal }));
});

test('409 preserves machine-readable code for refresh handling', async () => {
  vi.mocked(appFetch).mockResolvedValue(new Response(
    JSON.stringify({ detail: 'Phiên bản đã thay đổi', code: 'DISPATCH_STALE_REVISION' }),
    { status: 409, headers: { 'Content-Type': 'application/problem+json' } },
  ));
  await expect(readyForTrip(7, 1)).rejects.toMatchObject({
    status: 409, code: 'DISPATCH_STALE_REVISION', message: 'Phiên bản đã thay đổi',
  } satisfies Partial<DispatchApiError>);
});
