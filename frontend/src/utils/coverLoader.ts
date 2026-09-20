import type { Manga } from '@/types/manga'
import { mangaCoverUrl } from '@/api/manga'

/**
 * 封面加载器：并发限流 + 失败重试 + Blob 内存缓存 + 缩略图压缩。
 *
 * - 同一张封面同时只有一个网络请求，滚动来回不会重复请求；
 * - 最多同时 4 个请求，避免打爆后端返回 500；
 * - 请求成功后把原图解码、缩放到卡片实际需要的大小并转成 WebP，
 *   之后一直用 Blob URL 渲染，解码和显存开销大幅下降；
 * - 缓存满时按 LRU 淘汰并释放 Blob URL。
 */

/** 缩略图宽度档位：取最小能覆盖目标宽度的档位，同类卡片共享缓存 */
const WIDTH_BUCKETS = [160, 256, 384, 512, 768, 1024]
/** 最多缓存多少张缩略图 */
const MAX_CACHE_ENTRIES = 600
/** 并发请求上限，防止一次性请求过多导致后端 500 */
const MAX_CONCURRENT = 4
/** WebP 编码质量 */
const WEBP_QUALITY = 0.82

const cache = new Map<string, string>()
/** 加载失败的封面，永久标记，不再重复请求（刷新页面才重置） */
const failedKeys = new Set<string>()
const inflight = new Map<string, Promise<string>>()
const highQueue: Array<() => void> = []
const lowQueue: Array<() => void> = []
let activeCount = 0

export type CoverPriority = 'high' | 'low'

export function bucketWidth(target: number): number {
  for (const bucket of WIDTH_BUCKETS) {
    if (target <= bucket) return bucket
  }
  return WIDTH_BUCKETS[WIDTH_BUCKETS.length - 1]
}

function cacheKey(manga: Manga, width: number): string {
  return `${manga.id}:${width}`
}

function coverRequestUrl(manga: Manga, width: number): string {
  const base = mangaCoverUrl(manga)
  // w 参数：后端暂不处理，等后端支持缩略图后可直接收效
  return `${base}${base.includes('?') ? '&' : '?'}w=${width}`
}

function touchCache(key: string, url: string) {
  cache.delete(key)
  cache.set(key, url)
  while (cache.size > MAX_CACHE_ENTRIES) {
    const oldest = cache.keys().next().value as string
    const oldestUrl = cache.get(oldest)!
    cache.delete(oldest)
    URL.revokeObjectURL(oldestUrl)
  }
}

function schedule(task: () => void, low: boolean) {
  ;(low ? lowQueue : highQueue).push(task)
  pump()
}

function pump() {
  while (activeCount < MAX_CONCURRENT) {
    const task = highQueue.shift() ?? lowQueue.shift()
    if (!task) return
    activeCount++
    task()
  }
}

async function fetchCoverBlob(manga: Manga, width: number): Promise<Blob> {
  const res = await fetch(coverRequestUrl(manga, width))
  if (!res.ok) throw new Error(`封面请求失败：HTTP ${res.status}`)
  return res.blob()
}

/** 解码原图并压缩到目标宽度的 WebP；任何一步失败都退回原始 Blob */
async function toThumbnailUrl(blob: Blob, width: number): Promise<string> {
  let bitmap: ImageBitmap
  try {
    bitmap = await createImageBitmap(blob)
  } catch {
    return URL.createObjectURL(blob)
  }
  try {
    if (bitmap.width <= width) {
      return URL.createObjectURL(blob)
    }
    const height = Math.max(1, Math.round(bitmap.height * (width / bitmap.width)))
    const canvas = document.createElement('canvas')
    canvas.width = width
    canvas.height = height
    const ctx = canvas.getContext('2d')
    if (!ctx) return URL.createObjectURL(blob)
    ctx.drawImage(bitmap, 0, 0, width, height)
    const webp = await new Promise<Blob | null>((resolve) =>
      canvas.toBlob(resolve, 'image/webp', WEBP_QUALITY),
    )
    return URL.createObjectURL(webp ?? blob)
  } catch {
    return URL.createObjectURL(blob)
  } finally {
    bitmap.close()
  }
}

async function fetchAndResize(manga: Manga, width: number): Promise<string> {
  const blob = await fetchCoverBlob(manga, width)
  return toThumbnailUrl(blob, width)
}

/**
 * 加载封面缩略图，返回可用的 Blob URL。
 * priority='low' 的任务只在空闲时排队，不抢可视区的请求。
 */
export function loadCover(
  manga: Manga,
  targetWidth: number,
  priority: CoverPriority = 'high',
): Promise<string> {
  const width = bucketWidth(targetWidth)
  const key = cacheKey(manga, width)

  const cached = cache.get(key)
  if (cached) {
    touchCache(key, cached)
    return Promise.resolve(cached)
  }

  if (failedKeys.has(key)) {
    return Promise.reject(new Error('封面加载失败，已跳过'))
  }

  const existing = inflight.get(key)
  if (existing) return existing

  const promise = new Promise<string>((resolve, reject) => {
    const task = () => {
      fetchAndResize(manga, width)
        .then((url) => {
          failedKeys.delete(key)
          touchCache(key, url)
          resolve(url)
        })
        .catch((err) => {
          failedKeys.add(key)
          reject(err)
        })
        .finally(() => {
          inflight.delete(key)
          activeCount--
          pump()
        })
    }
    schedule(task, priority === 'low')
  })
  inflight.set(key, promise)
  return promise
}

/** 空闲时间预加载一批封面（低优先级），失败静默忽略 */
export function prefetchCovers(mangas: Manga[], targetWidth: number): void {
  for (const manga of mangas) {
    loadCover(manga, targetWidth, 'low').catch(() => {})
  }
}

/** 供调试：当前缓存条数 */
export function coverCacheSize(): number {
  return cache.size
}
