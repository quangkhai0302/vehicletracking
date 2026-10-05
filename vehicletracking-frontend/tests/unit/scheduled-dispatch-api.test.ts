import { afterEach, expect, test, vi } from 'vitest';
import { appFetch } from '@/shared/api/http';
import {
  DispatchApiError, fetchDispatchInbox, readDispatchInboxItem,
  fetchDriverAssignmentRequests, acceptDriverAssignmentRequest, declineDriverAssignmentRequest,
} from '@/features/dispatch/api/dispatch';

vi.mock('@/shared/api/http', () => ({ appFetch: vi.fn() }));
afterEach(() => vi.resetAllMocks());

test('direct assignment and inbox use scoped endpoints without scheduled dispatch calls', async () => {
  vi.mocked(appFetch).mockImplementation(async () => new Response('{}', { status: 200 }));
  const signal = new AbortController().signal;
  await fetchDriverAssignmentRequests(signal);
  await acceptDriverAssignmentRequest('request-uuid');
  await declineDriverAssignmentRequest('request-uuid-2', 'Không thể nhận chuyến');
  await fetchDispatchInbox(signal);
  await readDispatchInboxItem(5);
  expect(appFetch).toHaveBeenNthCalledWith(1, expect.stringContaining('/driver/assignment-requests'),
    expect.objectContaining({ signal }));
  expect(appFetch).toHaveBeenNthCalledWith(2, expect.stringContaining('/driver/assignment-requests/request-uuid/accept'),
    expect.objectContaining({ method: 'POST' }));
  expect(appFetch).toHaveBeenNthCalledWith(3, expect.stringContaining('/driver/assignment-requests/request-uuid-2/decline'),
    expect.objectContaining({ method: 'POST', body: '{"reason":"Không thể nhận chuyến"}' }));
  expect(appFetch).toHaveBeenNthCalledWith(4, expect.stringContaining('/driver/dispatch/inbox?limit=50'),
    expect.objectContaining({ signal }));
  expect(appFetch).toHaveBeenNthCalledWith(5, expect.stringContaining('/driver/dispatch/inbox/5/read'),
    expect.objectContaining({ method: 'POST' }));
  expect(appFetch).toHaveBeenCalledTimes(5);
  expect(vi.mocked(appFetch).mock.calls.some(([url]) => /trips\/.*\/dispatch|dispatch\/offers/.test(String(url)))).toBe(false);
});

test('assignment conflict preserves machine-readable code for refresh handling', async () => {
  vi.mocked(appFetch).mockResolvedValue(new Response(
    JSON.stringify({ detail: 'Yêu cầu đã thay đổi', code: 'ASSIGNMENT_INVALID_STATE' }),
    { status: 409, headers: { 'Content-Type': 'application/problem+json' } },
  ));
  await expect(acceptDriverAssignmentRequest('request-uuid')).rejects.toMatchObject({
    status: 409, code: 'ASSIGNMENT_INVALID_STATE', message: 'Yêu cầu đã thay đổi',
  } satisfies Partial<DispatchApiError>);
});
