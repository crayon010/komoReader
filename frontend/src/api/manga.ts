import type { ApiResponse, Manga, MangaPage, MangaRoot } from '@/types/manga';
import { request } from './http';

/** 获取导入的文件夹列表（不扫描内容） */
export function fetchRoots(): Promise<ApiResponse<MangaRoot[]>> {
  return request<ApiResponse<MangaRoot[]>>('/api/roots');
}

/** 删除导入的文件夹：不再展示/访问，磁盘文件不删 */
export function removeRoot(path: string): Promise<ImportResult> {
  return request<ImportResult>(`/api/roots?path=${encodeURIComponent(path)}`, {
    method: 'DELETE',
  });
}

/** 获取某导入文件夹内的条目；不传 root 则扫全部 */
export function fetchMangaList(root?: string): Promise<ApiResponse<Manga[]>> {
  return request<ApiResponse<Manga[]>>(
    root ? `/api/manga?root=${encodeURIComponent(root)}` : '/api/manga',
  );
}

/** 获取某部阅读的页列表 */
export function fetchMangaPages(id: string): Promise<MangaPage[]> {
  return request<MangaPage[]>(`/api/manga/${id}/pages?index=2`);
}

/** 封面图片地址：优先用后端返回的 coverUrl，否则按 id 推导 */
export function mangaCoverUrl(manga: Manga): string {
  return manga.coverUrl || `/api/manga/${manga.id}/cover`;
}

/** 单页图片地址 */
export function mangaPageUrl(id: string, index: number): string {
  return `/api/manga/${id}/page?index=${index}`;
}

/** 书籍原始文件流地址（txt/epub/pdf 通用），后端按此路径返回文件即可 */
export function mangaFileUrl(id: string): string {
  return `/api/manga/${id}/file`;
}

/** 导入结果（占位，按后端实际返回调整） */
export interface ImportResult {
  success: boolean;
  message?: string;
}

/** 设置页：导入文件夹路径 */
export function importFolder(path: string): Promise<ImportResult> {
  return request<ImportResult>('/api/manga/import', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ path }),
  });
}
