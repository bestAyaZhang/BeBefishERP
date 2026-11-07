import type { ProductImagePack } from '../../types';
import { albumImageName, albumImageUrl, productImageAlbums } from './productImageAlbums';

const encoder = new TextEncoder();
const localHeaderLength = 30;
const directoryHeaderLength = 46;
const endRecordLength = 22;

interface ZipEntry {
  path: string;
  data: Uint8Array<ArrayBuffer>;
}

const crcTable = Uint32Array.from({ length: 256 }, (_, index) => {
  let crc = index;
  for (let bit = 0; bit < 8; bit++) {
    crc = (crc >>> 1) ^ ((crc & 1) ? 0xedb88320 : 0);
  }
  return crc;
});

function writeEntryFields(view: DataView, offset: number, data: Uint8Array, nameLength: number) {
  let crc = 0xffffffff;
  for (const byte of data) {
    crc = (crc >>> 8) ^ crcTable[(crc ^ byte) & 0xff];
  }
  view.setUint16(offset, 20, true);
  view.setUint16(offset + 2, 0x0800, true); // UTF-8 paths, STORE compression.
  view.setUint16(offset + 8, 0x0021, true); // DOS date: 1980-01-01.
  view.setUint32(offset + 10, (crc ^ 0xffffffff) >>> 0, true);
  view.setUint32(offset + 14, data.byteLength, true);
  view.setUint32(offset + 18, data.byteLength, true);
  view.setUint16(offset + 22, nameLength, true);
}

// Adapted from the editor prototype's ZIP32 writer. Images are already compressed.
function makeAlbumZip(entries: ZipEntry[]) {
  if (entries.length > 0xffff) throw new RangeError('资料包最多包含 65,535 个文件和目录');

  const localParts: BlobPart[] = [];
  const directoryParts: BlobPart[] = [];
  let directoryOffset = 0;
  let directoryLength = 0;
  for (const { path, data } of entries) {
    const name = encoder.encode(path);
    const localOffset = directoryOffset;
    directoryOffset += localHeaderLength + name.length + data.byteLength;
    directoryLength += directoryHeaderLength + name.length;
    if (directoryOffset + directoryLength + endRecordLength >= 0xffffffff) {
      throw new RangeError('资料包超出 ZIP32 的 4 GiB 大小限制');
    }

    const local = new Uint8Array(localHeaderLength + name.length);
    const localView = new DataView(local.buffer);
    localView.setUint32(0, 0x04034b50, true);
    writeEntryFields(localView, 4, data, name.length);
    local.set(name, localHeaderLength);
    localParts.push(local, data);

    const directory = new Uint8Array(directoryHeaderLength + name.length);
    const directoryView = new DataView(directory.buffer);
    directoryView.setUint32(0, 0x02014b50, true);
    directoryView.setUint16(4, 20, true);
    // The common fields occupy the same 24 bytes in both headers.
    directory.set(local.subarray(4, 28), 6);
    if (path.endsWith('/')) directoryView.setUint32(38, 0x10, true);
    directoryView.setUint32(42, localOffset, true);
    directory.set(name, directoryHeaderLength);
    directoryParts.push(directory);
  }

  const end = new Uint8Array(endRecordLength);
  const endView = new DataView(end.buffer);
  endView.setUint32(0, 0x06054b50, true);
  endView.setUint16(8, entries.length, true);
  endView.setUint16(10, entries.length, true);
  endView.setUint32(12, directoryLength, true);
  endView.setUint32(16, directoryOffset, true);
  return new Blob([...localParts, ...directoryParts, end], { type: 'application/zip' });
}

function safeArchiveName(value: string, fallback: string) {
  const clean = value.normalize('NFC')
    .replace(/[<>:"/\\|?*\x00-\x1f\x7f]/g, '_')
    .trim()
    .replace(/^\.+|[. ]+$/g, '');
  return /^(con|prn|aux|nul|com[1-9¹²³]|lpt[1-9¹²³])(?:\.|$)/i.test(clean)
    ? '_' + clean
    : clean || fallback;
}

function fitArchiveName(stem: string, ending: string) {
  // Common file systems cap one component at 255 bytes.
  const limit = 255 - encoder.encode(ending).length;
  let length = 0;
  let fitted = '';
  for (const character of stem) {
    length += encoder.encode(character).length;
    if (length > limit) break;
    fitted += character;
  }
  return fitted + ending;
}

function uniquePictureName(name: string, extension: string, usedNames: Set<string>) {
  const clean = safeArchiveName(name, '图片');
  const existingExtension = clean.match(/\.(?:png|jpe?g|webp|gif|avif|bmp)$/i)?.[0] ?? '';
  const matchingExtension = existingExtension.toLowerCase() === extension
    || (extension === '.jpg' && existingExtension.toLowerCase() === '.jpeg');
  const stem = existingExtension ? clean.slice(0, -existingExtension.length) : clean;
  const ending = matchingExtension ? existingExtension : extension;
  let filename = fitArchiveName(stem, ending);
  for (let copy = 2; usedNames.has(filename.toLowerCase()); copy++) {
    filename = fitArchiveName(stem, ` (${copy})${ending}`);
  }
  usedNames.add(filename.toLowerCase());
  return filename;
}

function imageExtension(data: Uint8Array) {
  const header = String.fromCharCode(...data.subarray(0, 16));
  if (data[0] === 0xff && data[1] === 0xd8 && data[2] === 0xff) return '.jpg';
  if (data[0] === 0x89 && header.slice(1, 8) === 'PNG\r\n\u001a\n') return '.png';
  if (header.startsWith('RIFF') && header.slice(8, 12) === 'WEBP') return '.webp';
  if (header.startsWith('GIF87a') || header.startsWith('GIF89a')) return '.gif';
  if (header.startsWith('BM') && data.byteLength >= 26) return '.bmp';
  if (/^ftypavi[fs]/.test(header.slice(4))) return '.avif';
  return null;
}

export async function buildProductImagePackArchive(pack: ProductImagePack, signal?: AbortSignal) {
  const filename = fitArchiveName(safeArchiveName(pack.name, '商品资料包'), '.zip');
  // Snapshot album order before any requests so edits cannot change an in-flight archive.
  const albums = productImageAlbums.map(({ kind, label }) => ({ label, images: [...pack[kind]] }));
  const entries: ZipEntry[] = [];
  for (const { label, images } of albums) {
    entries.push({ path: label + '/', data: new Uint8Array() });
    const usedNames = new Set<string>();
    for (const image of images) {
      const name = albumImageName(image);
      try {
        const url = albumImageUrl(image.url);
        if (!url) throw new Error();
        const response = await fetch(url, { signal });
        if (!response.ok) throw new Error();
        const data = new Uint8Array(await response.arrayBuffer());
        const extension = imageExtension(data);
        if (!extension) throw new Error();
        entries.push({ path: label + '/' + uniquePictureName(name, extension, usedNames), data });
      } catch {
        throw new Error(`无法读取图片「${name}」，请检查后重试`);
      }
    }
  }
  return { archive: makeAlbumZip(entries), filename };
}
