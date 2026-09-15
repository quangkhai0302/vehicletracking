const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '');

export const officialGoogleTilesEnabled = import.meta.env.VITE_GOOGLE_MAP_TILES_ENABLED === 'true';

export function googleMapTileUrl(theme: 'google-roadmap' | 'google-satellite' | 'google-dark') {
  const style = theme === 'google-satellite' ? 'SATELLITE' : theme === 'google-dark' ? 'DARK' : 'ROADMAP';
  return `${API_BASE_URL}/api/v1/maps/tiles/${style}/{z}/{x}/{y}`;
}
