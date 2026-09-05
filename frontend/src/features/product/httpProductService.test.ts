import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ACCESS_TOKEN_STORAGE_KEY } from '../../types/auth';
import { httpProductService } from './httpProductService';

type Listener = (event: ProgressEvent) => void;

class FakeEventTarget {
  onprogress: Listener | null = null;
  private listeners = new Map<string, Listener>();

  addEventListener(type: string, listener: EventListenerOrEventListenerObject) {
    this.listeners.set(type, listener as Listener);
  }

  emit(type: string, event: ProgressEvent) {
    this.listeners.get(type)?.(event);
    if (type === 'progress') this.onprogress?.(event);
  }
}

class FakeXMLHttpRequest extends FakeEventTarget {
  static latest: FakeXMLHttpRequest | null = null;
  readonly upload = new FakeEventTarget();
  readonly headers: Record<string, string> = {};
  status = 200;
  statusText = 'OK';
  responseText = JSON.stringify({
    code: 'SUCCESS',
    message: '操作成功',
    data: { id: 9, url: '/qiniu-files/image.jpg', originalFileName: 'image.jpg' }
  });
  sentBody: Document | XMLHttpRequestBodyInit | null = null;

  constructor() {
    super();
    FakeXMLHttpRequest.latest = this;
  }

  open() {}

  setRequestHeader(name: string, value: string) {
    this.headers[name] = value;
  }

  getResponseHeader(name: string) {
    return name.toLowerCase() === 'content-type' ? 'application/json' : null;
  }

  send(body: Document | XMLHttpRequestBodyInit | null) {
    this.sentBody = body;
    this.upload.emit('progress', { lengthComputable: true, loaded: 4, total: 10 } as ProgressEvent);
    this.emit('load', {} as ProgressEvent);
  }
}

describe('HTTP product image upload', () => {
  beforeEach(() => {
    localStorage.clear();
    localStorage.setItem(ACCESS_TOKEN_STORAGE_KEY, 'access-token');
    FakeXMLHttpRequest.latest = null;
    vi.stubGlobal('XMLHttpRequest', FakeXMLHttpRequest);
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      new Response(FakeXMLHttpRequest.prototype.responseText, {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      })
    ));
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('reports bytes sent while preserving the authenticated upload response', async () => {
    const progress: number[] = [];

    const uploaded = await httpProductService.uploadImage(
      new File(['image'], 'image.jpg', { type: 'image/jpeg' }),
      (value) => progress.push(value)
    );

    expect(progress).toEqual([40]);
    expect(uploaded).toEqual({ id: 9, url: '/qiniu-files/image.jpg', originalFileName: 'image.jpg' });
    expect(FakeXMLHttpRequest.latest?.headers.Authorization).toBe('Bearer access-token');
    expect(FakeXMLHttpRequest.latest?.sentBody).toBeInstanceOf(FormData);
  });
});
