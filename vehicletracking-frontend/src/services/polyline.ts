/**
 * Flexible Polyline Decoder for HERE Technologies (TypeScript).
 * Decodes flexible polyline strings into [latitude, longitude] pairs for Leaflet.
 */

const FORMAT_VERSION = 1;

const DECODING_TABLE = [
  62, -1, -1, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, -1, -1, -1, -1, -1, -1, -1,
  0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21,
  22, 23, 24, 25, -1, -1, -1, -1, 63, -1, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35,
  36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51
];

function decodeChar(c: string): number {
  const code = c.charCodeAt(0);
  const pos = code - 45;
  if (pos < 0 || pos > 77) {
    return -1;
  }
  return DECODING_TABLE[pos];
}

class PolylineIterator {
  private str: string;
  private index: number = 0;

  constructor(str: string) {
    this.str = str;
  }

  hasNext(): boolean {
    return this.index < this.str.length;
  }

  next(): string {
    return this.str.charAt(this.index++);
  }

  decodeUnsignedVarint(): number {
    let shift = 0;
    let result = 0;
    while (this.hasNext()) {
      const c = this.next();
      const value = decodeChar(c);
      if (value < 0) {
        throw new Error(`Unexpected character in polyline: ${c}`);
      }
      result |= (value & 0x1f) << shift;
      if ((value & 0x20) === 0) {
        return result;
      }
      shift += 5;
    }
    throw new Error('Unexpected end of encoded polyline');
  }

  decodeValue(multiplier: number, lastValue: { val: number }): number {
    let l = this.decodeUnsignedVarint();
    if ((l & 1) !== 0) {
      l = ~l;
    }
    l = l >> 1;
    lastValue.val += l;
    return lastValue.val / multiplier;
  }
}

export function decodeFlexiblePolyline(encoded: string | null | undefined): [number, number][] {
  if (!encoded || !encoded.trim()) {
    return [];
  }

  try {
    const iter = new PolylineIterator(encoded.trim());
    const version = iter.decodeUnsignedVarint();
    if (version !== FORMAT_VERSION) {
      return [];
    }

    const header = iter.decodeUnsignedVarint();
    const precision = header & 15;
    const multiplier = Math.pow(10, precision);
    const hasThirdDimension = ((header >> 4) & 7) !== 0;
    const thirdDimensionPrecision = (header >> 7) & 15;
    const thirdDimensionMultiplier = Math.pow(10, thirdDimensionPrecision);

    const lastLat = { val: 0 };
    const lastLng = { val: 0 };
    const lastZ = { val: 0 };

    const coordinates: [number, number][] = [];

    while (iter.hasNext()) {
      const lat = iter.decodeValue(multiplier, lastLat);
      const lng = iter.decodeValue(multiplier, lastLng);

      if (hasThirdDimension) {
        iter.decodeValue(thirdDimensionMultiplier, lastZ);
      }

      coordinates.push([Math.round(lat * 1e6) / 1e6, Math.round(lng * 1e6) / 1e6]);
    }

    return coordinates;
  } catch {
    return [];
  }
}

export function decodeGooglePolyline(encoded: string | null | undefined): [number, number][] {
  if (!encoded || encoded.length > 2_000_000) throw new Error('Google polyline không hợp lệ');
  const points: [number, number][] = [];
  let index = 0;
  let latitude = 0;
  let longitude = 0;
  const read = () => {
    let result = 0;
    let shift = 0;
    while (index < encoded.length && shift <= 30) {
      const value = encoded.charCodeAt(index++) - 63;
      if (value < 0 || value > 63) throw new Error('Google polyline không hợp lệ');
      result |= (value & 0x1f) << shift;
      if (value < 0x20) return (result & 1) ? ~(result >>> 1) : result >>> 1;
      shift += 5;
    }
    throw new Error('Google polyline không hợp lệ');
  };
  while (index < encoded.length) {
    latitude += read();
    longitude += read();
    const point: [number, number] = [latitude / 100_000, longitude / 100_000];
    if (!Number.isFinite(point[0]) || !Number.isFinite(point[1])
      || Math.abs(point[0]) > 90 || Math.abs(point[1]) > 180) throw new Error('Google polyline không hợp lệ');
    points.push(point);
  }
  if (!points.length) throw new Error('Google polyline không hợp lệ');
  return points;
}

export function decodeRoutePolyline(
  encoded: string | null | undefined,
  encoding: 'HERE_FLEXIBLE_POLYLINE' | 'GOOGLE_ENCODED_POLYLINE' | null | undefined,
): [number, number][] {
  return encoding === 'GOOGLE_ENCODED_POLYLINE'
    ? decodeGooglePolyline(encoded)
    : decodeFlexiblePolyline(encoded);
}
