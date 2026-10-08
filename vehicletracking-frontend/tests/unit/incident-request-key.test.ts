import { afterEach, expect, test, vi } from 'vitest';
import { createIncidentRequestKey } from '@/features/simulation/utils/incidentRequestKey';

afterEach(() => vi.unstubAllGlobals());

test('uses native UUID generation when available', () => {
  const randomUUID = vi.fn(() => 'b239df9e-dc10-4323-ab7e-4ae53198c481');
  const getRandomValues = vi.fn();
  vi.stubGlobal('crypto', { randomUUID, getRandomValues });
  expect(createIncidentRequestKey()).toBe('b239df9e-dc10-4323-ab7e-4ae53198c481');
  expect(randomUUID).toHaveBeenCalledOnce();
  expect(getRandomValues).not.toHaveBeenCalled();
});

test('generates RFC UUID v4 keys using secure random bytes without randomUUID on HTTP', () => {
  const webCrypto = globalThis.crypto;
  const getRandomValues = vi.fn((bytes: Uint8Array<ArrayBuffer>) => webCrypto.getRandomValues(bytes));
  vi.stubGlobal('crypto', { getRandomValues });
  const keys = Array.from({ length: 50 }, () => createIncidentRequestKey());
  for (const key of keys) expect(key).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
  expect(new Set(keys).size).toBe(keys.length);
  expect(getRandomValues).toHaveBeenCalledTimes(keys.length);
});
