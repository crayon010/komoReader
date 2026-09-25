/** 阅读文件条目，对应后端 /api/manga 列表项 */
export interface Manga {
  id: string;
  name: string;
  /** 封面图片流地址，形如 /api/manga/{id}/cover */
  coverUrl: string;
  totalPages: number;
  /** 后端存储该文件的本地路径 */
  path: string;
  /** 内容类型：manga / novel / pdf / gallery；后端未标注时按漫画处理 */
  type?: string;
}

/** 导入的根目录（阅读库第一级） */
export interface MangaRoot {
  name: string;
  path: string;
  /** 读物条目数（后端扫描列表长度），翻开卡片时展示 */
  itemCount: number;
}

/** 类型标签；角标配色见 MangaCard（漫画蓝 / 小说红 / PDF 黄 / 图库绿） */
export function mangaTypeLabel(type?: string | null): string {
  const labels: Record<string, string> = { manga: '漫画', novel: '小说', pdf: 'PDF', gallery: '图库' };
  return labels[type ?? ''] ?? '漫画';
}

/** 单页信息，对应 /api/manga/{id}/pages 列表项 */
export interface MangaPage {
  index: string;
}

// 【新增】后端统一返回的外层包装结构
export interface ApiResponse<T> {
  code: number;
  data: T;
  message: string;
}
