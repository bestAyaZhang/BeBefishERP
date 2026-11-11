# Image Loading Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Reduce product image transfer size and origin load while preserving original assets and catalog data.

**Architecture:** Stable image preview URLs resolve to signed Qiniu CDN variants when a verified CDN domain is configured. Otherwise bounded local preview generation and disk caching provide the same display contract. Original URLs remain available for zoom and downloads.

**Tech Stack:** Java 21, Spring Boot 3, Java ImageIO, Qiniu Java SDK, Vue 3, TypeScript, Vitest.

**Spec:** docs/superpowers/specs/2026-11-11-image-loading-design.md

## Global Constraints

- Preserve all original image bytes, file IDs, products, SKUs, inventory and quotes.
- Preview variants: thumb 160px, preview 800px, fit without cropping or upscaling.
- Only enable a real, verified HTTPS CDN domain; no test-domain activation.
- Keep all secrets on server and out of source, logs and browser bundles.

## Review Focus

- Signed URLs must include processing parameters before signing, with expiry-aware redirect caching.
- Invalid variants and path traversal must never reach storage.
- Transparent PNGs, tall images and unsupported formats must not break original access or exhaust memory.
- Concurrent requests for an uncached image must not multiply decoding/storage reads.
- External/signed URLs must not be rewritten; zoom and ZIP downloads must remain original.

### Task 1: Backend preview delivery

**Files:** create QiniuImagePreviewService.java and QiniuImagePreviewServiceTest.java; modify QiniuImageProxyController.java and its tests; document environment options.

**Interfaces:** existing original `/api/files/content/{key}` remains; `?variant=thumb|preview` adds display-only previews. `QINIU_CDN_DOMAIN` optional HTTPS formal CDN, `ERP_IMAGE_CACHE_DIR` optional cache directory.

- [x] Write tests for fit dimensions, transparency, repeated and concurrent cached requests, invalid variants/keys, CDN transform/signature ordering.
- [x] Run focused tests and verify expected failures.
- [x] Implement bounded preview generation and CDN redirects; preserve original handler.
- [x] Run all image tests and package backend.

### Task 2: Frontend sized images

**Files:** create components/media/imageUrls.ts and test; modify ProductDetailImage.vue, ProductOverviewSection.vue, ProductTable.vue, ProductImageAlbum.vue and related tests.

**Interfaces:** `imagePreviewUrl(url, variant)` accepts original URL and returns display-only URL for managed image paths; all originals remain unchanged.

- [x] Write tests asserting resized managed URLs, untouched external URLs, original zoom URL, eager hero and lazy thumbnails.
- [x] Run focused tests and observe expected failures.
- [x] Apply shared helper and loading hints at list, SKU, hero and album images.
- [x] Run full frontend tests and production build.

### Task 3: Review, release and measure

- [x] Review full diff against spec and resolve material findings.
- [x] Verify backend tests, frontend tests and release artifacts.
- [x] Confirm production version and backup current binaries/config before release.
- [x] Configure verified HTTPS CDN and activate signed sized-image delivery.
- [x] Publish artifacts, check service health, measure same original vs preview, inspect list/detail/zoom in browser.
- [x] Push reviewed code and record measurements, release version and rollback files.

## Execution ledger

- User accepted the previously presented proposal; no repeated design approval is needed.
- Native worktree created at image-loading-optimization; base 193ebb6.
- Ruling: execute inline to avoid unnecessary delegation overhead; obtain one final fresh review as required by the execution workflow.
- Ruling: original same-origin download endpoint remains unchanged to preserve ZIP downloads without requiring new cross-origin policies.

- Task 1 and Task 2 complete: final backend `mvn -q verify` passed 684 tests; frontend `npm run test:run` passed 1,202 tests and `npm run build` passed.
- Initial full backend invocation lacked ERP_TEST_DB_URL and failed before database initialization; reran successfully with a newly created localhost-only `bebefish_image_20261111_test` database. No business database used.
- Ruling: backend and frontend use independent sides of the fixed variant contract, so their build/test jobs ran in parallel; final combined review covered both.
- Fresh review: two P2 findings (EXIF orientation; repeated unsupported image reads) reproduced with failing tests and fixed. Focused follow-up review found no material remaining issue. Added all eight EXIF orientations, unsupported outcome persistence, cache eviction and catalog/album wiring coverage.
- Ruling: original same-origin downloads remain unchanged; only display previews can redirect to CDN, preserving ZIP download behavior without cross-origin policy changes.
- Local real-image measurement: original 1,282,306 bytes; thumb 3,054 bytes (160x160), preview 31,151 bytes (800x800); cached service read 0.4ms/0.2ms. These are local measurements, not production network results.
- Preview rollout and final CDN DNS/HTTPS activation are complete as recorded below.

- Production release 94db1d6 installed and verified. Frontend list requests 160px lazy previews; detail hero requests 800px eager/high-priority previews; zoom still loads original bytes. No schema migration introduced.
- Public same-photo measurements: original 1,282,306 bytes / 2,580ms; thumb 3,054 bytes / 220ms then 51ms; preview 31,151 bytes / 466ms then 44ms. Original bytes matched the source exactly. These are observed samples, not a universal performance guarantee.
- Browser initially reused old entry HTML; confirmed refresh retrieved the new bundle. Added `expires -1` to exact index locations in the three static-root server blocks. Nginx syntax check/reload passed; public HTML GET/HEAD now has no-cache. Preview GET retains public, immutable, one-year caching. Existing HTTP redirect blocks were preserved.
- Main fast-forwarded to the reviewed production code and pushed. Server keeps old backend, frontend and site configuration backups; local ignored release artifacts keep deployment receipts, checksums, measurements and screenshots.
- Qiniu custom image domain `img.bebefish.com` created and ownership verified. Aliyun `img` CNAME points to `img-bebefish-com-idvs9rl.qiniudns.com`; public DNS verified. Root/www A records remain intact. Image processing and private-bucket origin authentication remain enabled; unsigned image GET returns 403.
- Free TrustAsia ECC certificate issued after DNS ownership verification. HTTPS, forced HTTPS, HTTP/2, and TLS 1.2/1.3 are enabled. Certificate expires 2027-02-09 16:59:59 (Asia/Shanghai); renewal is required before expiry.
- Server-only HTTPS preflight passed: original bytes matched SHA-256, thumb WebP 160x160 / 1,432 bytes, preview WebP 800x800 / 15,834 bytes. Long-term Qiniu keys stayed on the server.
- Backed up the actual systemd environment file to `/www/backups/erp-image-20261111/before/app.env.before-cdn`, then added only `QINIU_CDN_DOMAIN=https://img.bebefish.com` in `/etc/bebefish-erp/app.env`. Restart, health checks, signed redirect/WebP validation and original-content check all passed. Server-only guarded rollout retains automatic rollback; activation receipt is `/www/backups/erp-image-20261111/cdn-activation-receipt.json`.
- Public end-to-end sample: thumb 302 to HTTPS CDN then 200 image/webp, 1,432 bytes / 515ms including redirect; preview 302 then 200 image/webp, 15,834 bytes / 333ms. Redirect cache is public/max-age=300. Public service health is SUCCESS/200, HTML remains no-cache. These are observed samples, not a general latency guarantee.
- Browser verified list images at 160px, eager/high-priority detail main image at 800px, lazy thumbnails, and original same-origin zoom without a variant parameter. No visible image failures. Browser verification completed and TaskSpace 9 finished; user-owned Aliyun tab preserved. Catalog header remains 147 products (146 published plus 1 draft).
