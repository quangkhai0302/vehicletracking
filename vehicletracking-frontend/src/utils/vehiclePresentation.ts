import type { VehicleType } from '../types/fleet';

const carGlyph = `<svg class="live-vehicle-glyph" viewBox="0 0 32 40" aria-hidden="true" focusable="false">
  <path d="m13 4 3-3 3 3" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
  <rect x="5" y="13" width="4" height="7" rx="1.5" fill="#0f172a"/>
  <rect x="23" y="13" width="4" height="7" rx="1.5" fill="#0f172a"/>
  <rect x="5" y="27" width="4" height="6" rx="1.5" fill="#0f172a"/>
  <rect x="23" y="27" width="4" height="6" rx="1.5" fill="#0f172a"/>
  <rect x="8" y="7" width="16" height="30" rx="6" fill="currentColor" stroke="#fff" stroke-width="1.5"/>
  <path d="m11 14 1 6h8l1-6c-3-2-7-2-10 0Z" fill="#0c4a6e"/>
  <path d="m12 29-1 4h10l-1-4Z" fill="#0c4a6e"/>
  <path d="M11 10h2m6 0h2" stroke="#fff" stroke-width="2" stroke-linecap="round"/>
  <path d="M11 35h2m6 0h2" stroke="#fb7185" stroke-width="2" stroke-linecap="round"/>
</svg>`;

const motorcycleGlyph = `<svg class="live-vehicle-glyph" viewBox="0 0 32 40" aria-hidden="true" focusable="false">
  <path d="m13 4 3-3 3 3" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
  <ellipse cx="16" cy="7" rx="4" ry="5" fill="#0f172a" stroke="#fff" stroke-width="1.3"/>
  <path d="M9 13h14M11 11l5 5 5-5" fill="none" stroke="#0f172a" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/>
  <path d="M16 14c-4 3-5 8-3 13l3 6 3-6c2-5 1-10-3-13Z" fill="currentColor" stroke="#fff" stroke-width="1.4"/>
  <path d="M13 21h6" stroke="#0c4a6e" stroke-width="3.5" stroke-linecap="round"/>
  <ellipse cx="16" cy="34" rx="4" ry="5" fill="#0f172a" stroke="#fff" stroke-width="1.3"/>
  <path d="M14 29h4" stroke="#fb7185" stroke-width="2" stroke-linecap="round"/>
</svg>`;

export function vehicleMarkerGlyph(type: VehicleType | undefined) {
  return type === 'MOTORCYCLE' ? motorcycleGlyph : carGlyph;
}
