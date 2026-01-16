import type { Image } from '@/type'

export type ResolveImageUrlOptions = {
  preferThumb?: boolean
  preferOriginalForGif?: boolean
}

const isGifImage = (image?: Image | null): boolean => {
  if (!image) return false
  const hint = [
    image.imageName,
    image.imageUrl,
    image.imageThumbUrl,
    image.blobUrl,
    image.blobThumbUrl
  ].filter(Boolean).join(' ').toLowerCase()
  return hint.includes('.gif')
}

export function resolveImageUrl(
  image?: Image | null,
  options: ResolveImageUrlOptions = {}
): { primary: string; fallback: string } {
  const { preferThumb = true, preferOriginalForGif = true } = options

  if (!image) {
    return { primary: '', fallback: '' }
  }

  const thumb = image.blobThumbUrl || image.imageThumbUrl || ''
  const original = image.blobUrl || image.imageUrl || ''
  const isGif = isGifImage(image)

  if (isGif && preferOriginalForGif) {
    return {
      primary: original || thumb,
      fallback: original && thumb && original !== thumb ? thumb : ''
    }
  }

  if (preferThumb) {
    return {
      primary: thumb || original,
      fallback: thumb && original && thumb !== original ? original : ''
    }
  }

  return {
    primary: original || thumb,
    fallback: original && thumb && original !== thumb ? thumb : ''
  }
}

export function resolveImageUrlSingle(
  image?: Image | null,
  options: ResolveImageUrlOptions = {}
): string {
  const { primary, fallback } = resolveImageUrl(image, options)
  return primary || fallback || ''
}
