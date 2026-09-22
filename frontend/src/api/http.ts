/**
 * 统一的 fetch 封装：仅用于返回 JSON 的接口。
 * 图片流接口（封面、单页）直接拼 URL 交给 <img>，不经过这里。
 */
export async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(url, init);
  if (!res.ok) {
    throw new Error(`请求失败：${res.status} ${res.statusText}（${url}）`);
  }
  return (await res.json()) as T;
}
