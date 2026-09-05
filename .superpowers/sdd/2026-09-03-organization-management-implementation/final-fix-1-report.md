# Organization Management Final Fix 1 Report

Date: 2026-09-03
Branch: `codex/organization-management`

## Scope

Implemented the final organization-management review fixes in the frontend Mock service, employee drawer, employee/department/position views, and their focused tests. No screenshots were modified.

## Changes

- Enforced password-login invariants in the employee drawer and Mock service using the existing employee state. New password accounts and transitions to temporary require a password, established accounts can be edited without replacement, disabled password login clears and omits stale credentials, and passwords are never materialized in employee records.
- Corrected employee login availability labels for status, employment type, Feishu binding, and password-login combinations.
- Enforced enabled department/position hierarchy rules for employee, department, and position create/update/status operations while retaining occupancy and descendant disable guards.
- Applied reactive `organization:manage` checks to create, edit, and status controls while preserving employee view access for `organization:view` users.
- Added request generations to all three lists so stale responses cannot update records, errors, or loading state.
- Switched new-employee hire-date defaults to browser-local calendar fields.
- Cleared prior department/position page warnings after later saves or mutations refresh successfully.
- Implemented row and select-all checkbox state, indeterminate state, and selection reset when accepted records change in all three tables.
- Disabled the unconnected Feishu QR action and labeled it as unavailable.

## TDD Evidence

- Service red run: 6 new groups failed on missing password and enabled-hierarchy rules; green run passed 23/23 tests.
- Employee red runs: 7 account/date/permission/QR cases and 2 concurrency/selection cases failed for the expected missing behavior; green run passed 27/27 tests.
- Department/position red runs: permission cases and 6 concurrency/selection/warning cases failed for the expected missing behavior; green run passed 29/29 tests.

## Verification

- Focused organization tests: 5 files passed, 99 tests passed.
- Full frontend tests: 37 files passed, 422 tests passed.
- Production build: `vue-tsc --noEmit && vite build` passed; Vite transformed 1,749 modules.
- `git diff --check`: passed with no whitespace errors (Git emitted only expected LF-to-CRLF working-copy notices on Windows).
