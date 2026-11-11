# Image Loading Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

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

- [ ] Write tests for fit dimensions, transparency, repeated and concurrent cached requests, invalid variants/keys, CDN transform/signature ordering.
- [ ] Run focused tests and verify expected failures.
- [ ] Implement bounded preview generation and CDN redirects; preserve original handler.
- [ ] Run all image tests and package backend.

### Task 2: Frontend sized images

**Files:** create productImageUrls.ts and test; modify ProductDetailImage.vue, ProductOverviewSection.vue, ProductTable.vue, ProductImageAlbum.vue and related tests.

**Interfaces:** `productImagePreviewUrl(url, variant)` accepts original URL and returns display-only URL for managed image paths; all originals remain unchanged.

- [ ] Write tests asserting resized managed URLs, untouched external URLs, original zoom URL, eager hero and lazy thumbnails.
- [ ] Run focused tests and observe expected failures.
- [ ] Apply shared helper and loading hints at list, SKU, hero and album images.
- [ ] Run full frontend tests and production build.

### Task 3: Review, release and measure

- [ ] Review full diff against spec and resolve material findings.
- [ ] Verify backend tests, frontend tests and release artifacts.
- [ ] Confirm production version and backup current binaries/config before release.
- [ ] Configure verified CDN if access permits; otherwise keep cache previews active and report exact remaining domain setup.
- [ ] Publish artifacts, check service health, measure same original vs preview, inspect list/detail/zoom in browser.
- [ ] Push reviewed code and record measurements, release version and rollback files.

## Execution ledger

- User accepted the previously presented proposal; no repeated design approval is needed.
- Native worktree created at image-loading-optimization; base 193ebb6.
- Ruling: execute inline to avoid unnecessary delegation overhead; obtain one final fresh review as required by the execution workflow.
- Ruling: original same-origin download endpoint remains unchanged to preserve ZIP downloads without requiring new cross-origin policies.
