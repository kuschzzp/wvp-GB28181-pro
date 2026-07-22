# Exception Prompt Experience Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use inline execution in this session. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make expected business failures show actionable messages instead of generic "系统异常".

**Architecture:** Add a first-class business exception handled before the generic exception fallback. Keep internal failures as server errors, and update the frontend request interceptor so both HTTP errors and wrapped `code != 0` responses surface one consistent message.

**Tech Stack:** Spring Boot `@RestControllerAdvice`, `WVPResult`, Vue 2, Element UI, axios.

## Global Constraints

- Do not expose internal system faults, stack traces, cache/database names, or resource identifiers through generic fallbacks.
- Preserve existing `WVPResult` response shape.
- Preserve `suppressGlobalError` for pages that intentionally handle errors locally.
- Do not commit or push.

---

### Task 1: Backend Business Error Contract

**Files:**
- Create: `src/main/java/com/genersoft/iot/vmp/conf/exception/BusinessException.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/conf/GlobalExceptionHandler.java`
- Test: `src/test/java/com/genersoft/iot/vmp/auth/security/SecurityStatusCodeTest.java`

- [ ] Add `BusinessException` with `code`, `message`, and HTTP status.
- [ ] Handle `BusinessException` before generic exceptions.
- [ ] Return explicit `IllegalArgumentException` messages as 400 when safe.
- [ ] Extract readable validation messages for invalid request bodies.
- [ ] Keep unexpected exceptions as 500 "系统异常".

### Task 2: System Management Rule Failures

**Files:**
- Modify: `src/main/java/com/genersoft/iot/vmp/auth/department/impl/DepartmentTreeServiceImpl.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/auth/menu/impl/SystemMenuServiceImpl.java`
- Test: existing department/menu service or controller tests

- [ ] Convert expected delete blockers to `BusinessException`.
- [ ] Keep write failures and inconsistent tree/closure data as `IllegalStateException`.
- [ ] Add tests for department-with-children and menu-with-children responses.

### Task 3: Frontend Request Messaging

**Files:**
- Modify: `web/src/utils/request.js`
- Test: add or update frontend unit test if the project has a local test harness.

- [ ] Build a normalized `Error` object with `code`, `status`, and `message`.
- [ ] Show `data.msg` for HTTP errors.
- [ ] Show `res.msg` for wrapped `code != 0` responses.
- [ ] Keep 401 re-login and 403 permission behavior.
- [ ] Preserve `suppressGlobalError`.

### Task 4: Verification And Deployment

**Files:**
- No source files expected beyond tasks above.

- [ ] Run focused backend tests.
- [ ] Run available frontend lint/test or at least static syntax check for `request.js`.
- [ ] Build Docker image using the current optimized Docker flow.
- [ ] Redeploy the VM stack on `10.211.55.27`.
- [ ] Verify deleting a department with child nodes returns the readable message.
