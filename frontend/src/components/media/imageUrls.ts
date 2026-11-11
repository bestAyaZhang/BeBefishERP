export type ImagePreviewVariant = 'thumb' | 'preview';

/** Originals remain available for downloads and full-size preview dialogs. */
export function imagePreviewUrl(src: string | null | undefined, variant: ImagePreviewVariant): string | null {
  if (!src) return null;
  try {
    const origin = typeof location === 'undefined' ? 'http://localhost' : location.origin;
    const url = new URL(src, origin);
    const apiOrigin = new URL(import.meta.env.VITE_API_BASE_URL || origin, origin).origin;
    if (url.origin !== apiOrigin || !/^\/api\/files\/content\/[A-Za-z0-9][A-Za-z0-9._-]{0,254}$/.test(url.pathname)
      || url.searchParams.has('token') || url.searchParams.has('e')) return src;
    url.searchParams.set('variant', variant);
    return src.startsWith('/') && !src.startsWith('//') ? url.pathname + url.search + url.hash : url.toString();
  } catch { return src; }
}
