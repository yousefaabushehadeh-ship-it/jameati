// عميل بسيط للـ API. الباك إند يرجّع دايمًا { success, data, message, timestamp }
// وتسجيل الدخول بيعتمد على كوكيز httpOnly، فبنبعت credentials مع كل طلب.

export class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.status = status;
  }
}

let refreshing = null;

async function raw(path, { method = 'GET', body, headers } = {}) {
  const isForm = body instanceof FormData;
  return fetch(`/api/v1${path}`, {
    method,
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      ...(body && !isForm ? { 'Content-Type': 'application/json' } : {}),
      ...headers,
    },
    body: body ? (isForm ? body : JSON.stringify(body)) : undefined,
  });
}

async function parse(res) {
  if (res.status === 204) return null;
  let json = null;
  try {
    json = await res.json();
  } catch {
    /* ignore */
  }
  if (!res.ok || (json && json.success === false)) {
    throw new ApiError(json?.message || 'صار خطأ، حاول مرة ثانية', res.status);
  }
  return json ? json.data : null;
}

export async function api(path, options = {}) {
  let res = await raw(path, options);
  // لو انتهت صلاحية التوكن نجرب نجدده مرة وحدة
  if (res.status === 401 && !path.startsWith('/auth/')) {
    refreshing ||= raw('/auth/refresh', { method: 'POST' }).finally(() => (refreshing = null));
    const r = await refreshing;
    if (r.ok) res = await raw(path, options);
  }
  return parse(res);
}

export const get = (path) => api(path);
export const post = (path, body) => api(path, { method: 'POST', body });
export const put = (path, body) => api(path, { method: 'PUT', body });
export const patch = (path, body) => api(path, { method: 'PATCH', body });
export const del = (path) => api(path, { method: 'DELETE' });

export function qs(params) {
  const s = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') s.set(k, v);
  });
  const str = s.toString();
  return str ? `?${str}` : '';
}

// Spring Page قد يرجع بشكلين حسب الإعدادات
export function pageOf(p) {
  if (!p) return { items: [], page: 0, totalPages: 0, total: 0 };
  if (p.page && typeof p.page === 'object') {
    return { items: p.content || [], page: p.page.number, totalPages: p.page.totalPages, total: p.page.totalElements };
  }
  return { items: p.content || [], page: p.number || 0, totalPages: p.totalPages || 0, total: p.totalElements || 0 };
}
