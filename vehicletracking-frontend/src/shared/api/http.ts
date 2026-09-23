const API_BASE = `${(import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')}/api/v1`;
const CSRF_COOKIE = 'XSRF-TOKEN';
const CSRF_HEADER = 'X-XSRF-TOKEN';
let csrfRequest: Promise<void> | null = null;

function cookieValue(name: string): string | null {
  const prefix = `${name}=`;
  const cookie = document.cookie.split(';').map(value => value.trim()).find(value => value.startsWith(prefix));
  return cookie ? decodeURIComponent(cookie.slice(prefix.length)) : null;
}

async function ensureCsrfToken(): Promise<void> {
  if (cookieValue(CSRF_COOKIE)) return;
  if (!csrfRequest) {
    // Do not bind the shared bootstrap request to one page's AbortController:
    // another concurrent mutation may still need the same token.
    csrfRequest = fetch(`${API_BASE}/auth/csrf`, { credentials: 'include' })
      .then(response => {
        if (!response.ok) throw new Error(`Không thể khởi tạo phiên bảo mật (HTTP ${response.status}).`);
      })
      .finally(() => { csrfRequest = null; });
  }
  await csrfRequest;
}

/** Shared application request wrapper. Unsafe methods attach the session CSRF token. */
export async function appFetch(input: RequestInfo | URL, options: RequestInit = {}): Promise<Response> {
  const method = (options.method ?? 'GET').toUpperCase();
  const headers = new Headers(options.headers);
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    await ensureCsrfToken();
    const token = cookieValue(CSRF_COOKIE);
    if (token) headers.set(CSRF_HEADER, token);
  }
  return fetch(input, { ...options, headers, credentials: 'include' });
}
