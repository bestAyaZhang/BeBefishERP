# Task 2 report

Implemented full Feishu directory sync and durable background tasks, sharing the identity merge path with login.

## Changes

- HTTP client paginates root recursive departments and each department's direct users (including root users), page size 50, open IDs throughout. Business status, page shape, missing/repeated tokens are validated. Typed profiles preserve unknown activation, primary-department orders, employee type, job title and join date. Logs emit bounded request IDs without credentials or bodies.
- Shared identity merge checks union/open disagreement, rejects conflicting or ambiguous normalized mobile bindings, and uses stable `FS-U-` SHA-256-derived codes on missing/colliding employee numbers. `FS-D-` codes are equally stable. Directory imports include nonformal employees; login retains formal-only policy.
- Employee and user rows are locked/refreshed before merge, preserving manual status, position and password during concurrent admin edits. A mobile match is revalidated after obtaining its row lock. LOCAL roles and login metadata are untouched. Explicit negative Feishu state is committed before login rejection.
- Parent-first department import never creates root. Root and department users are deduplicated, primary/fallback departments restricted to imported departments, and leaders backfilled after people. Every department/employee update owns a short transaction; one bad record does not roll back successful records. Missing people are never marked resigned.
- Task service injects a bounded single-worker executor, coalesces manual/login starts through a single-instance mutex plus persisted task query, throttles successful/partial login-triggered runs for 30 minutes, persists scheduling/API failures, and fails pending/running tasks older than two hours on application startup. No self-invoked async method.
- REST POST returns 202 task; latest is nullable; task lookup is tenant scoped. Start requires organization:sync; reads require organization:view. Login queues work without waiting and cannot fail due to scheduling errors.

## RED / GREEN evidence

- `/tmp/task2-red.log`: missing directory API methods failed compilation as expected.
- `/tmp/task2-http-green-merge-red.log`: HTTP fixture tests passed; old random fallback employee number failed stable-code expectation.
- `/tmp/task2-merge-red.log`: missing shared directory merge contract.
- `/tmp/task2-task-red.log`: missing sync/task service contracts.
- `/tmp/task2-api-red.log`: REST 404 and absent login scheduling hook.
- `/tmp/task2-lock-red.log`: removing the employee row lock reproduced overwriting a concurrent manual disable (expected DISABLED, got ACTIVE). Restored lock passed.
- `/tmp/task2-mobile-race-red.log`: concurrent manual mobile change initially allowed binding the wrong employee. Revalidation under lock closes the race.
- `/tmp/task2-final-focused.log`: 34 focused tests passed before the final mobile-race regression.
- `/tmp/task2-final-backend.log`: final full backend suite **287 tests, 0 failures, 0 errors, 0 skips**, BUILD SUCCESS, Java 21 / real MySQL test database. Includes 35 focused tests with the added race regression.
- `git diff --check` passed. Changed Java files formatted with google-java-format AOSP style.

The suite logs expected pre-existing failure-injection errors (audit failure, API exception, storage 401, duplicate inventory key), standard Spring test security warnings, and the intentional scheduling-failure warning. None are test failures.

## Integration notes / limits

- Task JSON: id, triggerType, status, startedAt, finishedAt, departmentsCreated, departmentsUpdated, employeesCreated, employeesUpdated, recordsSkipped, recordsFailed, warningMessage, errorMessage. Counts are published at completion; running status is available while work executes.
- Manual start while Feishu is disabled/unconfigured still creates an inspectable task that finishes failed with a safe configuration/permission/network message.
- Synchronization is intentionally single-instance. Multi-instance task locking remains future work per the design.
- Official API routes/models were checked against the supplied larksuite SDK files and official endpoint pages: https://open.feishu.cn/document/server-docs/contact-v3/department/children and https://open.feishu.cn/document/server-docs/contact-v3/user/find_by_department .
- No real tenant credentials were available; real external tenant acceptance remains unverified. Tests use real HTTP fixtures and real MySQL. No push or deployment.

## Review round 1: eventual orphan recovery

- Confirmed P1: startup-only recovery retained a fresh orphan indefinitely after a quick restart.
- Recovery now runs under the start mutex on startup, start attempts, latest polling and individual task polling. Once an orphan crosses the two-hour cutoff, the existing page sees failed and can retry without another process restart or login.
- Locally queued/running task IDs remain tracked until the runnable exits; recovery excludes them even after the cutoff. Executor rejection releases ownership. Timeout therefore cannot expire a live worker and create overlapping work in the supported single-instance deployment.
- RED: `/tmp/task2-recovery-red.log` records four intended failures for pending/running orphans, individual polling/start recovery and erroneously expiring locally queued work.
- GREEN: `/tmp/task2-recovery-green.log` has 12 passing lifecycle/API/login tests. Controlled queues and a mutable clock cover a previous process, quick replacement before cutoff, later GET/latest/start reconciliation, successful retry, and queued/running live-work exclusion.
- Final backend suite: `/tmp/task2-recovery-full.log`, **291 tests, 0 failures/errors/skips**, BUILD SUCCESS. `git diff --check` passes. No schema, merge, authentication policy or frontend changes in this fix.
