import { afterEach, expect, test, vi } from 'vitest';
import { reverseGeocodeStation } from '@/features/stations/api/stations';

afterEach(() => vi.unstubAllGlobals());

test('reverse geocoding sends latitude/longitude, session credentials and AbortSignal without a body', async () => {
  const result = { address: 'Địa chỉ mẫu', distanceMeters: 3 };
  const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify(result)));
  vi.stubGlobal('fetch', fetch);
  const controller = new AbortController();
  expect(await reverseGeocodeStation(0, -180, controller.signal)).toEqual(result);
  expect(fetch).toHaveBeenCalledOnce();
  const [url, options] = fetch.mock.calls[0];
  const target = new URL(url);
  expect(target.pathname).toBe('/api/v1/stations/reverse-geocode');
  expect(target.searchParams.get('latitude')).toBe('0');
  expect(target.searchParams.get('longitude')).toBe('-180');
  expect(target.searchParams.has('apiKey')).toBe(false);
  expect(options).toMatchObject({ credentials: 'include', signal: controller.signal });
  expect(options.body).toBeUndefined();
});

test('empty lookup result is kept nullable', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue(new Response('{"address":null,"distanceMeters":null}')),
  );
  expect(await reverseGeocodeStation(10.8, 106.7)).toEqual({ address: null, distanceMeters: null });
});

test('provider errors use the backend ProblemDetail message', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue(new Response('{"detail":"Dịch vụ đang bận"}', { status: 503 })),
  );
  await expect(reverseGeocodeStation(10.8, 106.7)).rejects.toThrow('Dịch vụ đang bận');
});

test('non-JSON errors retain a controlled HTTP message', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('gateway error', { status: 502 })));
  await expect(reverseGeocodeStation(10.8, 106.7)).rejects.toThrow('Station API error: HTTP 502');
});
