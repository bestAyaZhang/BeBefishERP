import { afterEach, describe, expect, it, vi } from 'vitest';
import type { ProductImagePack } from '../../types';
import { buildProductImagePackArchive } from './productImagePackArchive';

const png = Uint8Array.from(atob('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aAxkAAAAASUVORK5CYII='), (char) => char.charCodeAt(0));

function readArchive(archive: Blob) {
  return new Promise<ArrayBuffer>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result as ArrayBuffer);
    reader.onerror = () => reject(reader.error);
    reader.readAsArrayBuffer(archive);
  });
}

function archiveEntries(archive: ArrayBuffer) {
  const view = new DataView(archive);
  const decoder = new TextDecoder();
  const entries = new Map<string, Uint8Array>();
  let offset = 0;
  while (view.getUint32(offset, true) === 0x04034b50) {
    const size = view.getUint32(offset + 18, true);
    const length = view.getUint16(offset + 26, true);
    const name = decoder.decode(new Uint8Array(archive, offset + 30, length));
    const start = offset + 30 + length + view.getUint16(offset + 28, true);
    entries.set(name, new Uint8Array(archive, start, size));
    offset = start + size;
  }
  expect(view.getUint32(offset, true)).toBe(0x02014b50);
  expect(view.getUint32(view.byteLength - 22, true)).toBe(0x06054b50);
  expect(view.getUint16(view.byteLength - 12, true)).toBe(entries.size);
  return entries;
}

const pack: ProductImagePack = {
  name: 'COM1.',
  main: [
    { fileId: 1, name: '../商品.png', url: '/uploads/front.png' },
    { fileId: 2, name: '../商品.PNG', url: '/qiniu-files/front-2.png' }
  ],
  sku: [],
  detail: [],
  long: []
};

describe('product image pack ZIP', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('stores each original byte stream in safe, case-insensitively unique paths and includes all four folders', async () => {
    vi.stubGlobal('fetch', async (url: string) => {
      const contentType = url.startsWith('/uploads/') ? 'application/octet-stream' : 'image/png';
      return new Response(png, { headers: { 'Content-Type': contentType } });
    });
    const { archive, filename } = await buildProductImagePackArchive(pack);
    expect(filename).toBe('_COM1.zip');
    expect(archive.type).toBe('application/zip');
    const entries = archiveEntries(await readArchive(archive));
    expect([...entries.keys()]).toEqual([
      '主图/', '主图/_商品.png', '主图/_商品 (2).PNG', 'SKU 图/', '详情图/', '详情长图/'
    ]);
    expect(entries.get('主图/_商品.png')).toEqual(png);
    expect(entries.get('主图/_商品 (2).PNG')).toEqual(png);
  });

  it('rejects the whole archive when an asset returns invalid data', async () => {
    vi.stubGlobal('fetch', async (url: string) => new Response(
      url === '/uploads/front.png' ? png : '<html>404</html>',
      { headers: { 'Content-Type': 'image/png' } }
    ));
    await expect(buildProductImagePackArchive(pack)).rejects.toThrow('无法读取图片「../商品.PNG」');
  });
});
