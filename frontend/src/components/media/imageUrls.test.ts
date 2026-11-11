import { describe, expect, it } from 'vitest';
import { imagePreviewUrl } from './imageUrls';

describe('managed image preview URLs', () => {
  it('adds a bounded display variant while retaining other query parameters and fragments', () => {
    expect(imagePreviewUrl('/api/files/content/glass.png?version=2#image', 'thumb'))
      .toBe('/api/files/content/glass.png?version=2&variant=thumb#image');
    expect(imagePreviewUrl('/api/files/content/glass.png?variant=thumb', 'preview'))
      .toBe('/api/files/content/glass.png?variant=preview');
  });

  it('preserves external signed URLs, local uploads, and missing images', () => {
    const external = 'https://images.example.com/api/files/content/glass.png?e=123&token=signed';
    expect(imagePreviewUrl(external, 'thumb')).toBe(external);
    expect(imagePreviewUrl('/uploads/glass.png', 'thumb')).toBe('/uploads/glass.png');
    expect(imagePreviewUrl('data:image/png;base64,abc', 'thumb')).toBe('data:image/png;base64,abc');
    expect(imagePreviewUrl(null, 'thumb')).toBeNull();
    expect(imagePreviewUrl('/api/files/content/..%2Fsecret', 'thumb')).toBe('/api/files/content/..%2Fsecret');
  });
});
