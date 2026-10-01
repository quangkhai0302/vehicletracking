import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import {
  createDriverAccount,
  resetDriverPassword,
  uploadDriverAvatar,
} from '@/features/auth/api/users';
import { assignTripVehicle } from '@/features/fleet/api/fleet';
import { appFetch } from '@/shared/api/http';

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; Path=/';
});
afterEach(() => {
  vi.unstubAllGlobals();
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; Path=/';
});
test.each(['GET', 'HEAD', 'OPTIONS'])(
  '%s uses credentials without CSRF bootstrap or changing supplied headers',
  async (method) => {
    const fetch = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetch);
    const headers = new Headers({ 'X-Fixture': 'preserved' });
    await appFetch('/fixture', { method, headers });
    expect(fetch).toHaveBeenCalledTimes(1);
    const options = fetch.mock.calls[0][1] as RequestInit;
    expect(options.credentials).toBe('include');
    expect(new Headers(options.headers).get('X-Fixture')).toBe('preserved');
    expect(headers.has('X-XSRF-TOKEN')).toBe(false);
  },
);
test('concurrent unsafe requests share bootstrap without binding it to a page abort signal', async () => {
  let finish!: (value: Response) => void;
  const bootstrap = new Promise<Response>((resolve) => {
    finish = resolve;
  });
  const fetch = vi.fn((input: RequestInfo | URL, options?: RequestInit) => {
    if (String(input).endsWith('/auth/csrf')) return bootstrap;
    if (options?.signal?.aborted)
      return Promise.reject(new DOMException('Fixture aborted', 'AbortError'));
    return Promise.resolve(new Response(null, { status: 204 }));
  });
  vi.stubGlobal('fetch', fetch);
  const controller = new AbortController();
  const abandoned = appFetch('/fixture/a', { method: 'POST', signal: controller.signal }).catch(
    (error) => error as Error,
  );
  const active = appFetch('/fixture/b', { method: 'DELETE' });
  expect(fetch).toHaveBeenCalledTimes(1);
  expect(fetch.mock.calls[0][1]?.signal).toBeUndefined();
  controller.abort();
  document.cookie = 'XSRF-TOKEN=fixture%2Btoken; Path=/';
  finish(new Response('{}', { status: 200 }));
  expect(await abandoned).toMatchObject({ name: 'AbortError' });
  expect((await active).status).toBe(204);
  const survivor = fetch.mock.calls.find((call) => call[0] === '/fixture/b')![1]!;
  expect(survivor.credentials).toBe('include');
  expect(new Headers(survivor.headers).get('X-XSRF-TOKEN')).toBe('fixture+token');
});
test('failed CSRF bootstrap prevents mutation and a later attempt can retry', async () => {
  const fetch = vi.fn().mockResolvedValueOnce(new Response(null, { status: 503 }));
  vi.stubGlobal('fetch', fetch);
  await expect(appFetch('/fixture', { method: 'PATCH' })).rejects.toThrow('HTTP 503');
  expect(fetch).toHaveBeenCalledTimes(1);
  fetch.mockImplementation(async (input: RequestInfo | URL) => {
    if (String(input).endsWith('/auth/csrf')) document.cookie = 'XSRF-TOKEN=fixture-retry; Path=/';
    return new Response(null, { status: 204 });
  });
  expect((await appFetch('/fixture', { method: 'PATCH' })).status).toBe(204);
  expect(fetch).toHaveBeenCalledTimes(3);
  expect(new Headers((fetch.mock.calls[2][1] as RequestInit).headers).get('X-XSRF-TOKEN')).toBe(
    'fixture-retry',
  );
});

test('driver password reset sends the new password to the dedicated account endpoint', async () => {
  document.cookie = 'XSRF-TOKEN=fixture-token; Path=/';
  const fetch = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
  vi.stubGlobal('fetch', fetch);

  await resetDriverPassword(12, { password: 'new-password' });

  expect(fetch).toHaveBeenCalledTimes(1);
  expect(String(fetch.mock.calls[0][0])).toMatch(/\/api\/v1\/users\/12\/reset-password$/);
  const options = fetch.mock.calls[0][1] as RequestInit;
  expect(options.method).toBe('POST');
  expect(options.body).toBe('{"password":"new-password"}');
  expect(new Headers(options.headers).get('Content-Type')).toBe('application/json');
  expect(new Headers(options.headers).get('X-XSRF-TOKEN')).toBe('fixture-token');
});

test('driver account creation only sends the selected driver id', async () => {
  document.cookie = 'XSRF-TOKEN=fixture-token; Path=/';
  const fetch = vi.fn().mockResolvedValue(
    new Response(
      JSON.stringify({
        id: 3,
        username: 'khainq',
        role: 'DRIVER',
        active: true,
        driverId: 7,
        driverName: 'Nguyễn Quang Khải',
        driverLicenseNumber: 'B2-123',
        temporaryPassword: 'Tmp8Pass',
      }),
      { status: 201, headers: { 'Content-Type': 'application/json' } },
    ),
  );
  vi.stubGlobal('fetch', fetch);

  const created = await createDriverAccount({ driverId: 7 });

  expect(created.username).toBe('khainq');
  expect(created.temporaryPassword).toBe('Tmp8Pass');
  const options = fetch.mock.calls[0][1] as RequestInit;
  expect(options.method).toBe('POST');
  expect(options.body).toBe('{"driverId":7}');
});

test('driver avatar upload leaves multipart content type to the browser', async () => {
  document.cookie = 'XSRF-TOKEN=fixture-token; Path=/';
  const fetch = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
  vi.stubGlobal('fetch', fetch);
  const file = new File(['avatar'], 'driver.png', { type: 'image/png' });

  await uploadDriverAvatar(7, file);

  const options = fetch.mock.calls[0][1] as RequestInit;
  expect(String(fetch.mock.calls[0][0])).toMatch(/\/api\/v1\/drivers\/7\/avatar$/);
  expect(options.method).toBe('POST');
  expect(options.body).toBeInstanceOf(FormData);
  expect(new Headers(options.headers).has('Content-Type')).toBe(false);
});

test('trip vehicle assignment sends the selected vehicle to the dedicated endpoint', async () => {
  document.cookie = 'XSRF-TOKEN=fixture-token; Path=/';
  const fetch = vi.fn().mockResolvedValue(
    new Response(JSON.stringify({ trip: { id: 5 }, stops: [], route: null }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }),
  );
  vi.stubGlobal('fetch', fetch);

  await assignTripVehicle(5, 9);

  expect(String(fetch.mock.calls[0][0])).toMatch(/\/api\/v1\/trips\/5\/vehicle$/);
  const options = fetch.mock.calls[0][1] as RequestInit;
  expect(options.method).toBe('PUT');
  expect(options.body).toBe('{"vehicleId":9}');
});
