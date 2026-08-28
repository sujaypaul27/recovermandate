# RecoverMandate Master Execution System

> **Generated**: 2026-08-28T23:00 IST  
> **Companion File**: `PROJECT_CONTEXT.md` (master source of truth)  
> **Instruction**: Copy each phase prompt sequentially into the AI agent. Each phase auto-ticks its status on completion.

---

## Architecture Summary & Production Hardening Decisions

RecoverMandate is being hardened from a working hackathon prototype into an enterprise-grade payment recovery platform. The current system successfully ingests Razorpay `payment.failed` webhooks, classifies failures deterministically, generates AI recovery drafts via Gemini, and presents a premium React dashboard with human-in-the-loop approval.

**Critical flaws identified and resolved in this execution plan:**
1. **No resilience on Gemini API** — raw RestTemplate with no retry/circuit breaker (experienced 503 crashes)
2. **PII leaked to external LLM** — customer names sent directly in Gemini prompts
3. **No replay protection** — webhooks accepted regardless of age
4. **No trace correlation** — impossible to follow a single transaction across pipeline stages
5. **No real-time updates** — manual Refresh button, no SSE streaming
6. **Monolith frontend** — 995-line App.tsx is a code review red flag
7. **Recovery pipeline incomplete** — "Approve" does nothing; no payment link, no dispatch
8. **pom.xml claims Java 17** — runtime is Java 21

---

## Phase Tracker

- [x] Phase 1: Webhook Hardening & Replay Protection
- [x] Phase 2: AI Resilience, Circuit Breaker & PII Redaction
- [x] Phase 3: Observability — Trace ID & Tamper-Proof Audit Chain
- [x] Phase 4: SSE Live Streaming & System Health Endpoint
- [x] Phase 5: Frontend Modularization & UX Enhancements
- [x] Phase 6: Payment Link Generation & Dispatch Pipeline
- [x] Phase 7: Smart Retry Engine & Bank Health Tracker
- [ ] Phase 8: Enhanced Dashboard & ROI Metrics

---

## Phase 1 — Webhook Hardening & Replay Protection
Status: [x] Completed

### Goal & Scope
Harden the webhook ingestion layer with replay protection (reject webhooks older than 5 minutes), fix the pom.xml Java version mismatch (17 → 21), and ensure webhook responses complete within 500ms. This phase touches only the ingestion pipeline and build config — zero impact on AI, frontend, or dispatch logic.

**Target Files:**
- `pom.xml` — Fix Java version
- `service/WebhookService.java` — Add timestamp replay guard
- `webhook/RazorpayWebhookController.java` — Verify response timing
- `application.yml` — Add replay window config property

### Executable Agent Prompt
```text
You are a Senior Java Backend Engineer working on RecoverMandate. Your task is Phase 1: Webhook Hardening & Replay Protection.

STEP 0 — STATE VERIFICATION:
Read the file `PROMPTS.md` in the workspace root. Check if "Phase 1" is already marked `[x]` in the Phase Tracker or if its Status line says `[x] Completed`. If Phase 1 is already completed, respond with "Phase 1 already completed. No action taken." and STOP immediately.

STEP 1 — PRE-IMPLEMENTATION INSPECTION:
Read and understand these files before making any changes:
- `c:\Users\Paul\Desktop\recovermandate\PROJECT_CONTEXT.md` (architecture context)
- `c:\Users\Paul\Desktop\recovermandate\pom.xml` (check current java.version property)
- `c:\Users\Paul\Desktop\recovermandate\src\main\java\com\recovermandate\service\WebhookService.java` (current webhook processing)
- `c:\Users\Paul\Desktop\recovermandate\src\main\java\com\recovermandate\webhook\RazorpayWebhookController.java` (webhook endpoint)
- `c:\Users\Paul\Desktop\recovermandate\src\main\resources\application.yml` (config properties)

STEP 2 — IMPLEMENTATION (zero stubs, zero TODOs):

2a. Fix pom.xml Java version:
- In `pom.xml`, change `<java.version>17</java.version>` to `<java.version>21</java.version>`.

2b. Add replay protection config to application.yml:
- Add under the `razorpay.webhook` section:
```yaml
razorpay:
  webhook:
    secret: ${RAZORPAY_WEBHOOK_SECRET:}
    replay-window-seconds: ${RAZORPAY_REPLAY_WINDOW:300}
```

2c. Add webhook timestamp replay guard to WebhookService.java:
- Add a new private method `isWebhookStale(JsonNode root)` that:
  1. Extracts `root.path("created_at").asLong(0)` — Razorpay sends this as epoch seconds.
  2. If the value is 0 (missing), return false (allow processing — don't break test mode payloads that may lack this field).
  3. Calculate age: `Instant.now().getEpochSecond() - webhookCreatedAt`.
  4. If age > the configured replay window (inject via `@Value("${razorpay.webhook.replay-window-seconds:300}")`), log a warning and return true.
  5. Otherwise return false.
- In `handleVerifiedEvent()`, call `isWebhookStale(root)` immediately after parsing the JSON root node (line ~51-52, after `objectMapper.readTree`). If stale:
  1. Log: `log.warn("Stale webhook rejected: age={}s, paymentId={}", age, extractPaymentId(root, paymentEntity))`.
  2. Call `auditService.log("WEBHOOK", 0L, "STALE_WEBHOOK_REJECTED", "SYSTEM", "Webhook rejected: age=" + age + "s exceeds replay window")`.
  3. Return null (do not process).
- Add the @Value injection field at the top of WebhookService class: `@Value("${razorpay.webhook.replay-window-seconds:300}") private int replayWindowSeconds;`
- Use `replayWindowSeconds` instead of hardcoding 300 in the stale check.

2d. Ensure fast webhook response in RazorpayWebhookController.java:
- Verify the controller returns `ResponseEntity.ok().build()` with no additional blocking work after calling `webhookService.handleVerifiedEvent()`. The current implementation already does this correctly — confirm and add a comment: `// Return 200 OK immediately — Razorpay requires response within 5s`.

STEP 3 — COMPILATION CHECK:
Run: `cd c:\Users\Paul\Desktop\recovermandate && mvnw.cmd clean compile -q` (or `mvn clean compile -q` if mvnw is not present).
If compilation fails, read the error output, fix the issue, and retry.

STEP 4 — TEST EXECUTION:
Run: `cd c:\Users\Paul\Desktop\recovermandate && mvnw.cmd test -q` (or `mvn test -q`).
If any test fails, analyze the failure and fix it. The replay guard should NOT break existing tests because test payloads without `created_at` should be allowed through (the 0-check in step 2c handles this).

STEP 5 — AUTOMATED PHASE TICKING:
After successful compilation AND tests:
1. Open `c:\Users\Paul\Desktop\recovermandate\PROMPTS.md`.
2. Change `- [ ] Phase 1: Webhook Hardening & Replay Protection` to `- [x] Phase 1: Webhook Hardening & Replay Protection` in the Phase Tracker section.
3. Change `Status: [ ] Pending` under the Phase 1 heading to `Status: [x] Completed`.
4. Open `c:\Users\Paul\Desktop\recovermandate\PROJECT_CONTEXT.md`.
5. In the "Master Phase Execution Tracker" table, change Phase 1's status from `⬜ Pending` to `✅ Completed`.

STEP 6 — COMPLETION REPORT:
Return a structured report:
- Files Modified: [list all files changed]
- Build Status: PASS/FAIL
- Tests Executed: [count] passed, [count] failed
- Key Changes: [1-line summary per file]
- Next Phase Readiness: Ready for Phase 2 (AI Resilience & PII Redaction)
```

---

## Phase 2 — AI Resilience, Circuit Breaker & PII Redaction
Status: [ ] Pending

### Goal & Scope
Wrap the Gemini API client in a Resilience4j circuit breaker with retry and time limiter. Create a deterministic `HeuristicFallbackEngine` that generates template-based drafts when Gemini is unavailable. Implement PII minimization by removing customer name from the Gemini prompt and adding a `draft_source` column to `RecoveryAction` to track whether the draft came from AI or heuristics.

**Target Files:**
- `pom.xml` — Add Resilience4j dependency
- `application.yml` — Add Resilience4j config
- `ai/GeminiClient.java` — Wrap with @CircuitBreaker, @Retry, @TimeLimiter; remove PII from prompt
- NEW: `ai/HeuristicFallbackEngine.java` — Deterministic template drafts
- `service/RecoveryActionService.java` — Pass draft_source to RecoveryAction
- `entity/RecoveryAction.java` — Add draft_source column

### Executable Agent Prompt
```text
You are a Senior Java Backend Engineer working on RecoverMandate. Your task is Phase 2: AI Resilience, Circuit Breaker & PII Redaction.

STEP 0 — STATE VERIFICATION:
Read `c:\Users\Paul\Desktop\recovermandate\PROMPTS.md`. Check if "Phase 2" is marked `[x]` in the Phase Tracker. If already completed, respond with "Phase 2 already completed. No action taken." and STOP.
Verify Phase 1 is marked `[x]`. If Phase 1 is NOT completed, respond with "Phase 1 must be completed first." and STOP.

STEP 1 — PRE-IMPLEMENTATION INSPECTION:
Read these files:
- `c:\Users\Paul\Desktop\recovermandate\PROJECT_CONTEXT.md`
- `c:\Users\Paul\Desktop\recovermandate\pom.xml`
- `c:\Users\Paul\Desktop\recovermandate\src\main\resources\application.yml`
- `c:\Users\Paul\Desktop\recovermandate\src\main\java\com\recovermandate\ai\GeminiClient.java`
- `c:\Users\Paul\Desktop\recovermandate\src\main\java\com\recovermandate\service\RecoveryActionService.java`
- `c:\Users\Paul\Desktop\recovermandate\src\main\java\com\recovermandate\entity\RecoveryAction.java`

STEP 2 — IMPLEMENTATION:

2a. Add Resilience4j dependency to pom.xml:
Add inside the <dependencies> section:
```xml
<!-- Resilience4j Circuit Breaker -->
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <version>2.2.0</version>
</dependency>
```

2b. Add Resilience4j configuration to application.yml:
Append at the end of application.yml:
```yaml
resilience4j:
  circuitbreaker:
    instances:
      geminiApi:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
        permittedNumberOfCallsInHalfOpenState: 3
        slidingWindowType: COUNT_BASED
  retry:
    instances:
      geminiApi:
        maxAttempts: 3
        waitDuration: 2s
        exponentialBackoffMultiplier: 2
        retryExceptions:
          - org.springframework.web.client.HttpServerErrorException
          - java.net.SocketTimeoutException
          - org.springframework.web.client.ResourceAccessException
  timelimiter:
    instances:
      geminiApi:
        timeoutDuration: 10s
```

2c. Create HeuristicFallbackEngine.java:
Create a new file at `c:\Users\Paul\Desktop\recovermandate\src\main\java\com\recovermandate\ai\HeuristicFallbackEngine.java`:

This service must:
- Be annotated with @Service and @Slf4j.
- Contain a private static final Map<String, String> TEMPLATES with keys: "insufficient_funds", "technical_decline", "expired_mandate", "unknown".
- Each template must be a complete, polite, professional email body (3-4 sentences) that:
  - Uses "Dear Customer" (never a real name — PII safe).
  - References the specific failure reason in plain language.
  - For insufficient_funds: asks to ensure adequate balance and retry.
  - For technical_decline: explains it was a temporary technical issue, automatic retry underway.
  - For expired_mandate: asks customer to re-authorize payment method.
  - For unknown: generic "issue processing payment" with link to update details.
  - Includes "Sincerely, The RecoverMandate Team" closing.
  - Embeds the formatted amount and currency using String.format placeholders.
- Have a public method `generateTemplate(String category, Long amountInPaise, String currency)` that:
  - Looks up the template by category (default to "unknown" if not found).
  - Formats the amount (amountInPaise / 100.0, 2 decimal places).
  - Returns the formatted string.
  - Logs at INFO level: "Heuristic fallback draft generated for category={}"

2d. Modify GeminiClient.java — PII Redaction & Circuit Breaker:
- Add `@CircuitBreaker(name = "geminiApi", fallbackMethod = "generateDraftFallback")` and `@Retry(name = "geminiApi")` annotations to the `generateDraft()` method.
- Inject `HeuristicFallbackEngine` via constructor.
- CRITICAL PII FIX: In the `generateDraft()` method, change the prompt construction to:
  - Replace `customerName` parameter usage with the literal string "Customer" in the prompt.
  - Remove the customer name from the prompt entirely. The prompt should say "Dear Customer" and never reference any real name.
  - Keep amount, currency, failureCategory, and daysSinceFailure (these are not PII).
- Add the fallback method:
```java
private String generateDraftFallback(String customerName, Long amount, String currency, String failureCategory, int daysSinceFailure, Throwable t) {
    log.warn("Gemini API circuit breaker activated, using heuristic fallback. Cause: {}", t.getMessage());
    return heuristicFallbackEngine.generateTemplate(failureCategory, amount, currency);
}
```
- The method signature of `generateDraftFallback` MUST exactly match `generateDraft` parameters plus a `Throwable t` parameter at the end. This is a Resilience4j requirement.
- Add a second return value mechanism: add a new method `public boolean isLastDraftFromFallback()` that returns a thread-local or instance boolean set to true in the fallback, false in the main method. Alternatively, simpler approach: have `generateDraft` return the draft string and let RecoveryActionService determine the source by checking if the circuit breaker is open. SIMPLEST approach: change `generateDraft` to return null on failure (current behavior), and have the fallback return the heuristic template. RecoveryActionService can then check: if GeminiClient returned a value AND the circuit breaker was invoked as fallback, mark as HEURISTIC. BUT this is complex — instead, use this pattern:
  - Make GeminiClient store a `private volatile String lastDraftSource = "AI"` field.
  - In `generateDraft()`, set `this.lastDraftSource = "AI"` at the start.
  - In `generateDraftFallback()`, set `this.lastDraftSource = "HEURISTIC"` before returning.
  - Add a getter: `public String getLastDraftSource() { return lastDraftSource; }`
- Enable Spring's @EnableRetry/@EnableCircuitBreaker: Add `@EnableAspectJAutoProxy` to `RecoverMandateApplication.java` if not already present. Resilience4j Spring Boot 3 starter auto-configures — no additional annotation needed on the main class.

2e. Modify RecoveryAction.java — Add draft_source column:
- Add a new field: `@Column(name = "draft_source") private String draftSource;` — values: "AI" or "HEURISTIC".
- This field is nullable (existing rows will have null, which is fine).

2f. Modify RecoveryActionService.java — Record draft source:
- After calling `geminiClient.generateDraft(...)`, capture the source: `String draftSource = geminiClient.getLastDraftSource();`
- If `draftMessage` is still null after fallback (both AI and heuristic failed — extremely unlikely but handle it), log and return as before.
- When building the RecoveryAction, add `.draftSource(draftSource)`.
- In the audit log for AI_DRAFT_GENERATED, include the source: `"AI draft generated via " + draftSource`.

STEP 3 — COMPILATION CHECK:
Run: `cd c:\Users\Paul\Desktop\recovermandate && mvnw.cmd clean compile -q` (or `mvn clean compile -q`).
Fix any compilation errors.

STEP 4 — TEST EXECUTION:
Run: `cd c:\Users\Paul\Desktop\recovermandate && mvnw.cmd test -q` (or `mvn test -q`).
Fix any test failures. Note: existing tests mock GeminiClient, so the circuit breaker annotations should not affect them. If tests fail because of Resilience4j context issues, ensure test classes are not loading the full application context unnecessarily.

STEP 5 — AUTOMATED PHASE TICKING:
After successful build and tests:
1. In `PROMPTS.md`: Change `- [ ] Phase 2:` to `- [x] Phase 2:` in the Phase Tracker. Change `Status: [ ] Pending` to `Status: [x] Completed` under the Phase 2 heading.
2. In `PROJECT_CONTEXT.md`: Change Phase 2 status from `⬜ Pending` to `✅ Completed`.

STEP 6 — COMPLETION REPORT:
Return: Files Modified, Build Status, Tests Executed, Key Changes, Next Phase Readiness.
```

---

## Phase 3 — Observability: Trace ID & Tamper-Proof Audit Chain
Status: [x] Completed

### Goal & Scope
Implement end-to-end trace correlation using UUID trace IDs propagated via SLF4J MDC across the webhook → classification → drafting → audit pipeline. Add SHA-256 hash chaining to audit logs for tamper detection. Add trace_id and checksum columns to existing tables.

**Target Files:**
- `entity/PaymentEvent.java` — Add trace_id column
- `entity/AuditLog.java` — Add trace_id, checksum, ai_model_used, ai_prompt_hash columns
- `audit/AuditService.java` — Implement hash chain computation
- `service/WebhookService.java` — Generate and propagate trace_id via MDC
- `service/FailureClassificationService.java` — Read trace_id from MDC
- `service/RecoveryActionService.java` — Read trace_id from MDC
- NEW: `config/MdcFilter.java` — Clean up MDC after request

### Executable Agent Prompt
```text
You are a Senior Java Backend Engineer working on RecoverMandate. Your task is Phase 3: Observability — Trace ID & Tamper-Proof Audit Chain.

STEP 0 — STATE VERIFICATION:
Read `c:\Users\Paul\Desktop\recovermandate\PROMPTS.md`. Check if "Phase 3" is marked `[x]`. If completed, STOP. Verify Phase 2 is `[x]`. If not, STOP with "Phase 2 must be completed first."

STEP 1 — PRE-IMPLEMENTATION INSPECTION:
Read: PROJECT_CONTEXT.md, entity/PaymentEvent.java, entity/AuditLog.java, audit/AuditService.java, service/WebhookService.java, service/FailureClassificationService.java, service/RecoveryActionService.java.

STEP 2 — IMPLEMENTATION:

2a. Add trace_id to PaymentEvent.java:
- Add: `@Column(name = "trace_id") private UUID traceId;`
- Add import: `java.util.UUID`

2b. Enhance AuditLog.java with new columns:
- Add: `@Column(name = "trace_id") private UUID traceId;`
- Add: `@Column(name = "checksum", length = 64) private String checksum;`
- Add: `@Column(name = "ai_model_used", length = 50) private String aiModelUsed;`
- Add: `@Column(name = "ai_prompt_hash", length = 64) private String aiPromptHash;`
- All new fields are nullable (backward compatible with existing rows).

2c. Implement SHA-256 hash chain in AuditService.java:
- Add a private field: `private volatile String lastChecksum = "GENESIS";` — this is the seed for the chain.
- Modify the `log()` method signature to accept two optional parameters: `String aiModelUsed` and `String aiPromptHash`. Use method overloading: keep the existing 5-parameter method (calls the new 7-parameter method with nulls), and add a new method with 7 parameters.
- In the new log method:
  1. Read trace_id from SLF4J MDC: `String traceIdStr = org.slf4j.MDC.get("traceId"); UUID traceId = traceIdStr != null ? UUID.fromString(traceIdStr) : null;`
  2. Compute the checksum: concatenate `lastChecksum + "|" + entityType + "|" + entityId + "|" + action + "|" + actor + "|" + Instant.now().toString()`, then SHA-256 hash it.
  3. Create a private helper: `private String sha256(String input)` using `java.security.MessageDigest.getInstance("SHA-256")` and `HexFormat.of().formatHex(digest)`.
  4. Set the new fields on the AuditLog builder: `.traceId(traceId).checksum(checksum).aiModelUsed(aiModelUsed).aiPromptHash(aiPromptHash)`.
  5. After save, update `this.lastChecksum = checksum`.
- The lastChecksum field introduces a single-threaded bottleneck for audit writes. This is acceptable for our throughput (webhook volume is low). Add a comment explaining this trade-off.

2d. Generate and propagate trace_id in WebhookService.java:
- At the very start of `handleVerifiedEvent()`, before the try block:
  1. Generate: `UUID traceId = UUID.randomUUID();`
  2. Set MDC: `org.slf4j.MDC.put("traceId", traceId.toString());`
- In the try block, set `traceId` on the PaymentEvent builder: `.traceId(traceId)`.
- In the finally block (add a finally block wrapping the try-catch): `org.slf4j.MDC.remove("traceId");` — critical to prevent MDC leaking to other requests on the same thread.

2e. Create MdcFilter.java for HTTP request cleanup:
Create `c:\Users\Paul\Desktop\recovermandate\src\main\java\com\recovermandate\config\MdcFilter.java`:
- Extends `OncePerRequestFilter`.
- In `doFilterInternal`: if no traceId exists in MDC, generate one and put it. After `filterChain.doFilter()`, clear all MDC entries in a finally block.
- Annotate with `@Component` and `@Order(Ordered.HIGHEST_PRECEDENCE)`.

STEP 3 — COMPILATION: Run `mvnw.cmd clean compile -q`. Fix errors.
STEP 4 — TESTS: Run `mvnw.cmd test -q`. The new nullable columns should not break existing tests. If AuditService tests fail due to the new checksum logic, update them to verify checksum is non-null.
STEP 5 — PHASE TICKING: Update PROMPTS.md (Phase 3 → [x]) and PROJECT_CONTEXT.md (Phase 3 → ✅ Completed).
STEP 6 — REPORT: Files Modified, Build Status, Tests, Key Changes, Next Phase Readiness.
```

---

## Phase 4 — SSE Live Streaming & System Health Endpoint
Status: [x] Completed

### Goal & Scope
Implement Server-Sent Events (SSE) so the frontend receives real-time event notifications without manual Refresh. Create a detailed health endpoint that reports Gemini API, database, and scheduler status. This enables the "degraded state banner" in the frontend.

**Target Files:**
- NEW: `controller/SseController.java` — SSE endpoint
- NEW: `service/SseService.java` — Emitter management
- NEW: `controller/SystemHealthController.java` — Detailed health API
- `service/WebhookService.java` — Emit SSE events on webhook ingestion
- `service/RecoveryActionService.java` — Emit SSE events on draft/approve
- `security/ApiKeyAuthFilter.java` — Allow SSE endpoint
- `security/SecurityConfig.java` — Permit SSE path

### Executable Agent Prompt
```text
You are a Senior Java Backend Engineer working on RecoverMandate. Your task is Phase 4: SSE Live Streaming & System Health Endpoint.

STEP 0 — STATE VERIFICATION:
Read PROMPTS.md. If Phase 4 is `[x]`, STOP. If Phase 3 is not `[x]`, STOP.

STEP 1 — READ: PROJECT_CONTEXT.md, WebhookService.java, RecoveryActionService.java, SecurityConfig.java, ApiKeyAuthFilter.java.

STEP 2 — IMPLEMENTATION:

2a. Create SseService.java at `src/main/java/com/recovermandate/service/SseService.java`:
- @Service, @Slf4j
- Maintain a `private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();`
- `addEmitter(SseEmitter emitter)`: Add to list. Set onCompletion, onTimeout, onError callbacks that remove the emitter from the list.
- `broadcast(String eventType, Object data)`: Iterate all emitters, send `SseEmitter.event().name(eventType).data(data, MediaType.APPLICATION_JSON)`. Catch exceptions and remove dead emitters. Use try-catch per emitter — never let one dead emitter kill the loop.
- All methods must be synchronized on the emitters list or use CopyOnWriteArrayList (already thread-safe for iteration).

2b. Create SseController.java at `src/main/java/com/recovermandate/controller/SseController.java`:
- @RestController, @RequestMapping("/api/stream")
- GET "/events" with `produces = MediaType.TEXT_EVENT_STREAM_VALUE`.
- Create `SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);` — infinite timeout since the client manages reconnection.
- Call `sseService.addEmitter(emitter)`.
- Send an initial "connected" event immediately so the client knows the stream is active.
- Return the emitter.

2c. Create SystemHealthController.java at `src/main/java/com/recovermandate/controller/SystemHealthController.java`:
- @RestController, @RequestMapping("/api/health")
- GET "/detailed" returns a Map with:
  - "status": "UP" or "DEGRADED"
  - "geminiApi": { "status": check if Resilience4j circuit breaker for geminiApi is OPEN/CLOSED/HALF_OPEN, "model": "gemini-3.5-flash-lite" }
  - "database": { "status": "UP" — do a simple `SELECT 1` via JdbcTemplate or entityManager }
  - "timestamp": Instant.now()
- Inject `io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry` to check circuit breaker state.
- If the circuitbreaker registry bean is not available (e.g., in tests), wrap in try-catch and default to "UNKNOWN".

2d. Emit SSE events from WebhookService:
- Inject `SseService` into WebhookService constructor.
- After saving the PaymentEvent (after the `paymentEventRepository.save()` call), broadcast: `sseService.broadcast("webhook.received", Map.of("id", savedEvent.getId(), "paymentId", razorpayPaymentId, "eventType", eventType, "amount", amount != null ? amount : 0, "timestamp", Instant.now().toString()))`.
- After classification completes, broadcast: `sseService.broadcast("classification.complete", Map.of("eventId", savedEvent.getId(), "category", classification.getCategory()))`.

2e. Emit SSE events from RecoveryActionService:
- Inject `SseService`.
- After saving a DRAFTED recovery action, broadcast: `sseService.broadcast("draft.generated", Map.of("actionId", savedAction.getId(), "status", status, "draftSource", draftSource != null ? draftSource : "AI"))`.
- After approving, broadcast: `sseService.broadcast("action.approved", Map.of("actionId", actionId))`.

2f. Update security to allow SSE endpoint:
- In `ApiKeyAuthFilter.java`, add the SSE path to the skip list: after the existing webhook path check, add: `if (path.startsWith("/api/stream/")) { filterChain.doFilter(request, response); return; }`.
  Wait — SSE should still require API key authentication. The frontend sends the API key. But EventSource API does not support custom headers. Solution: Accept API key as query parameter for SSE only.
  - Add: `if (path.startsWith("/api/stream/")) { String queryApiKey = request.getParameter("apiKey"); if (queryApiKey != null && expectedApiKey.equals(queryApiKey)) { filterChain.doFilter(request, response); return; } }`.
  - This allows: `/api/stream/events?apiKey=default-dev-key`.
- In `SecurityConfig.java`, no changes needed — the filter chain already permits all and delegates to the ApiKeyAuthFilter.

2g. Update HealthController.java:
- The existing `/api/health` endpoint returns basic status. Keep it as-is. The new `SystemHealthController` adds `/api/health/detailed`.
- WAIT — there's a conflict: `HealthController` is mapped to `/api/health` and `SystemHealthController` would also be at `/api/health`. Solution: Add the detailed endpoint directly to the existing `HealthController.java` as a second @GetMapping("/detailed") method instead of creating a new controller. Inject CircuitBreakerRegistry (optional, use @Autowired(required = false)) and DataSource to check DB connectivity.

STEP 3 — COMPILATION: `mvnw.cmd clean compile -q`. Fix errors.
STEP 4 — TESTS: `mvnw.cmd test -q`. SSE and health changes should not break existing tests since they're additive. If WebhookServiceTest fails because SseService is a new required dependency, add a @Mock for SseService in the test.
STEP 5 — PHASE TICKING: PROMPTS.md Phase 4 → [x], PROJECT_CONTEXT.md Phase 4 → ✅.
STEP 6 — REPORT.
```

---

## Phase 5 — Frontend Modularization & UX Enhancements
Status: [x] Completed

### Goal & Scope
Break the monolithic 995-line App.tsx into modular page components and hooks. Add SSE integration via a `useEventSource` hook, implement a Cmd+K search palette, add a transaction flow diagram component, tone adjuster slider, and degraded state banners.

**Target Files:**
- `frontend/src/App.tsx` — Refactor into shell with imports
- NEW: `frontend/src/pages/DashboardPage.tsx`
- NEW: `frontend/src/pages/FailedMandatesPage.tsx`
- NEW: `frontend/src/pages/ApprovalQueuePage.tsx`
- NEW: `frontend/src/pages/AuditLogPage.tsx`
- NEW: `frontend/src/hooks/useEventSource.ts` — SSE client
- NEW: `frontend/src/components/CommandPalette.tsx` — Cmd+K search
- NEW: `frontend/src/components/TransactionFlowDiagram.tsx`
- NEW: `frontend/src/components/SystemHealthBanner.tsx`
- `frontend/src/lib/api.ts` — Add search and health endpoints

### Executable Agent Prompt
```text
You are a Senior Frontend Engineer working on RecoverMandate. Your task is Phase 5: Frontend Modularization & UX Enhancements.

STEP 0 — STATE VERIFICATION:
Read PROMPTS.md. If Phase 5 is `[x]`, STOP. If Phase 4 is not `[x]`, STOP.

STEP 1 — READ: PROJECT_CONTEXT.md, frontend/src/App.tsx (all 995 lines), frontend/src/lib/api.ts, frontend/src/hooks/use-toast.ts, frontend/package.json.

STEP 2 — IMPLEMENTATION:

2a. Create useEventSource hook at `frontend/src/hooks/useEventSource.ts`:
- Accept `url: string` parameter.
- On mount, create `new EventSource(url)`.
- Listen to events: "webhook.received", "classification.complete", "draft.generated", "action.approved".
- On each event, parse JSON data and call an `onEvent(type, data)` callback passed as second parameter.
- Auto-reconnect on error with 3-second delay.
- Clean up on unmount.
- Export the hook.

2b. Create SystemHealthBanner component at `frontend/src/components/SystemHealthBanner.tsx`:
- Fetch `/api/health/detailed` every 30 seconds.
- If geminiApi.status is "OPEN" (circuit breaker open), show a yellow banner: "⚡ AI Engine Degraded — Using Heuristic Fallback Templates"
- If status is "DEGRADED", show a yellow banner.
- If all healthy, show nothing (or a small green dot in the sidebar).
- Use Framer Motion for banner slide-in/out animation.

2c. Create CommandPalette component at `frontend/src/components/CommandPalette.tsx`:
- Listen for Ctrl+K / Cmd+K keyboard shortcut.
- Show a modal overlay with a search input.
- On typing, debounce 300ms, then call the backend search API (add to api.ts: `GET /api/search?q={query}`).
- Display results grouped by type (Payment Events, Audit Logs).
- On selecting a result, switch to the relevant tab and highlight the item.
- Dismiss with Escape key or clicking outside.
- Style with glassmorphism matching the existing dark theme.

2d. Create TransactionFlowDiagram component at `frontend/src/components/TransactionFlowDiagram.tsx`:
- Visual node flow: [Customer App] → [Merchant API] → [Razorpay Gateway] → [NPCI / Issuer Bank]
- Accept a `failurePoint` prop (string) that determines which node glows red.
- Accept a `diagnosis` prop (string) for the AI-generated diagnosis text.
- Use SVG or CSS flexbox with connecting arrows.
- The failing node pulses with a red glow animation.
- Below the failing node, show an expandable diagnosis panel with the AI text.
- Use Framer Motion for entrance animations.

2e. Refactor App.tsx into modular pages:
- Extract the Dashboard/Overview tab content into `pages/DashboardPage.tsx`.
- Extract the Failed Mandates tab into `pages/FailedMandatesPage.tsx`.
- Extract the Approval Queue tab into `pages/ApprovalQueuePage.tsx`.
- Extract the Audit Log tab into `pages/AuditLogPage.tsx`.
- Keep the App.tsx as the shell: sidebar navigation, theme toggle, SSE connection via useEventSource, SystemHealthBanner, CommandPalette.
- Pass shared state (toast, theme, SSE events) down as props or via React context.
- CRITICAL: Do not break existing functionality. Every feature visible in the current App.tsx must work identically after refactoring.
- Integrate the TransactionFlowDiagram into FailedMandatesPage — show it when a payment event row is expanded/clicked.

2f. Add API functions to api.ts:
- `fetchSystemHealth()`: GET /api/health/detailed with API key header.
- `searchGlobal(query: string)`: GET /api/search?q={query} with API key header. Note: this endpoint may not exist yet on the backend — create a stub response handler that returns empty results if the endpoint returns 404. The backend search endpoint will be built in a later phase.

2g. Integrate SSE into the app shell:
- In App.tsx, connect to `${API_BASE_URL.replace('/api', '')}/api/stream/events?apiKey=${API_KEY}` using the useEventSource hook.
- On "webhook.received" events, show a toast notification and add a pulsing blue dot to the "Failed Mandates" nav item.
- On "draft.generated" events, show a toast and add a pulsing badge count to "Approval Queue" nav item.
- On "action.approved", show a success toast.
- Auto-refresh the current page's data when relevant SSE events arrive.

STEP 3 — BUILD CHECK: `cd c:\Users\Paul\Desktop\recovermandate\frontend && npm run build`. Fix TypeScript and build errors.
STEP 4 — VERIFY: `cd c:\Users\Paul\Desktop\recovermandate\frontend && npm run dev` (start dev server, verify no console errors).
STEP 5 — PHASE TICKING: PROMPTS.md Phase 5 → [x], PROJECT_CONTEXT.md Phase 5 → ✅.
STEP 6 — REPORT.
```

---

## Phase 6 — Payment Link Generation & Dispatch Pipeline
Status: [x] Completed

### Goal & Scope
Complete the recovery pipeline end-to-end: when a recovery action is approved, auto-generate a Razorpay Payment Link and dispatch the recovery message to the customer via email (simulated for hackathon). Create the payment_links and dispatch_logs tables.

**Target Files:**
- NEW: `entity/PaymentLink.java` — JPA entity
- NEW: `entity/DispatchLog.java` — JPA entity
- NEW: `repository/PaymentLinkRepository.java`
- NEW: `repository/DispatchLogRepository.java`
- NEW: `service/PaymentLinkService.java` — Razorpay Payment Links API
- NEW: `service/DispatchService.java` — Multi-channel dispatch orchestrator
- `service/RecoveryActionService.java` — Wire approve → link → dispatch
- `controller/RecoveryActionController.java` — Add approve-and-dispatch endpoint
- `client/RazorpayApiClient.java` — Add createPaymentLink method
- `application.yml` — Add Razorpay API key config

### Executable Agent Prompt
```text
You are a Senior Java Backend Engineer working on RecoverMandate. Your task is Phase 6: Payment Link Generation & Dispatch Pipeline.

STEP 0 — STATE VERIFICATION:
Read PROMPTS.md. If Phase 6 is `[x]`, STOP. If Phase 5 is not `[x]`, STOP.

STEP 1 — READ: PROJECT_CONTEXT.md, RecoveryActionService.java, RecoveryActionController.java, RazorpayApiClient.java, application.yml, entity/RecoveryAction.java.

STEP 2 — IMPLEMENTATION:

2a. Create PaymentLink entity at `entity/PaymentLink.java`:
- Fields: id (Long, auto-generated), recoveryAction (OneToOne FK to RecoveryAction), razorpayLinkId (String), shortUrl (String), amount (Long, in paise), currency (String, default "INR"), expireBy (Instant), status (String — CREATED/PAID/EXPIRED), createdAt (Instant), paidAt (Instant nullable).
- Table name: "payment_links".

2b. Create DispatchLog entity at `entity/DispatchLog.java`:
- Fields: id (Long), recoveryAction (ManyToOne FK), channel (String — EMAIL/WHATSAPP/SMS), recipient (String), status (String — SENT/DELIVERED/FAILED), providerMessageId (String nullable), sentAt (Instant), deliveredAt (Instant nullable), errorDetail (String nullable).
- Table name: "dispatch_logs".

2c. Create repositories for both entities.

2d. Add Razorpay API key config to application.yml:
```yaml
razorpay:
  api:
    key:
      id: ${RAZORPAY_KEY_ID:}
      secret: ${RAZORPAY_KEY_SECRET:}
    base-url: ${RAZORPAY_API_BASE_URL:https://api.razorpay.com/v1}
```

2e. Add createPaymentLink to RazorpayApiClient.java:
- Method: `public Map<String, String> createPaymentLink(Long amountInPaise, String currency, String customerEmail, String customerName, String description, Instant expireBy)`
- If keyId or keySecret is blank, log a warning and return a SIMULATED response: `Map.of("id", "plink_sim_" + UUID.randomUUID(), "short_url", "https://rzp.io/simulated/" + UUID.randomUUID())`. This ensures the demo works without real Razorpay API credentials.
- If credentials exist, POST to `{baseUrl}/payment_links` with:
  - Basic Auth header (keyId:keySecret base64 encoded)
  - JSON body: { amount, currency, description, customer: { name, email }, expire_by (epoch seconds), notify: { email: false, sms: false }, callback_url: "", callback_method: "" }
  - Parse response: extract "id" and "short_url"
  - Return as Map<String, String> with keys "id" and "short_url"
- Wrap in try-catch, log errors, throw RuntimeException on failure.

2f. Create PaymentLinkService.java:
- Inject: RazorpayApiClient, PaymentLinkRepository, AuditService.
- Method: `createLinkForRecoveryAction(RecoveryAction action)`:
  1. Extract customer email and name from action → failureClassification → paymentEvent → subscription → customer.
  2. Extract amount from paymentEvent.
  3. Call razorpayApiClient.createPaymentLink(...) with 48-hour expiry.
  4. Save PaymentLink entity with status CREATED.
  5. Update RecoveryAction with payment_link_url (add this field to RecoveryAction entity: `@Column(name = "payment_link_url") private String paymentLinkUrl;`).
  6. Audit log: "PAYMENT_LINK_CREATED".
  7. Return the PaymentLink entity.

2g. Create DispatchService.java:
- For hackathon: simulate email dispatch (log it, save to dispatch_logs with status SENT).
- Method: `dispatchRecovery(RecoveryAction action, String paymentLinkUrl)`:
  1. Get customer email from the action chain.
  2. Compose the final message: action.getAiDraftMessage() + "\n\nPay now: " + paymentLinkUrl.
  3. Log: "Dispatching recovery email to {} with payment link {}" (mask email in log — show only first 3 chars + domain).
  4. Save DispatchLog with channel EMAIL, status SENT, sentAt now.
  5. Audit log: "RECOVERY_DISPATCHED".
  6. Broadcast SSE: "recovery.dispatched".

2h. Wire approve-and-dispatch in RecoveryActionService:
- Inject PaymentLinkService and DispatchService.
- Add new method `approveAndDispatch(Long actionId, String approvedBy)`:
  1. Call `approveAction(actionId, approvedBy)` (existing).
  2. Fetch the action again (it's now APPROVED).
  3. Call `paymentLinkService.createLinkForRecoveryAction(action)`.
  4. Call `dispatchService.dispatchRecovery(action, paymentLink.getShortUrl())`.
  5. Update action status to "DISPATCHED", set sentAt.
  6. Audit log: "ACTION_DISPATCHED".

2i. Add approve-and-dispatch endpoint to RecoveryActionController:
- `@PostMapping("/{id}/approve-and-dispatch")`
- Calls `recoveryActionService.approveAndDispatch(id, "HUMAN")`.
- Returns ResponseEntity.ok().

STEP 3 — COMPILATION: `mvnw.cmd clean compile -q`.
STEP 4 — TESTS: `mvnw.cmd test -q`.
STEP 5 — PHASE TICKING: PROMPTS.md Phase 6 → [x], PROJECT_CONTEXT.md Phase 6 → ✅.
STEP 6 — REPORT.
```

---

## Phase 7 — Smart Retry Engine & Bank Health Tracker
Status: [x] Completed

### Goal & Scope
Implement algorithmic retry scheduling with category-specific backoff strategies and a bank health monitoring module that tracks failure rates per issuer. The retry engine checks bank health before executing retries to avoid wasting attempts during bank outages.

**Target Files:**
- NEW: `entity/RetrySchedule.java`
- NEW: `entity/BankHealthSnapshot.java`
- NEW: `repository/RetryScheduleRepository.java`
- NEW: `repository/BankHealthSnapshotRepository.java`
- NEW: `service/RetrySchedulerService.java`
- NEW: `service/BankHealthService.java`
- NEW: `scheduler/RetryExecutionScheduler.java`
- NEW: `scheduler/BankHealthScheduler.java`
- `service/WebhookService.java` — Create retry schedules on failure

### Executable Agent Prompt
```text
You are a Senior Java Backend Engineer working on RecoverMandate. Your task is Phase 7: Smart Retry Engine & Bank Health Tracker.

STEP 0 — STATE VERIFICATION: Read PROMPTS.md. If Phase 7 `[x]`, STOP. If Phase 6 not `[x]`, STOP.

STEP 1 — READ: PROJECT_CONTEXT.md, FailureClassificationService.java (category constants), WebhookService.java, scheduler/WebhookReconciliationScheduler.java.

STEP 2 — IMPLEMENTATION:

2a. Create RetrySchedule entity:
- Fields: id, paymentEventId (FK), failureCategory, attemptNumber (int), scheduledAt (Instant), executedAt (Instant nullable), result (String: PENDING/SUCCESS/FAILED/SKIPPED), razorpayRetryPaymentId (String nullable), createdAt.

2b. Create BankHealthSnapshot entity:
- Fields: id, bankCode (String, e.g. "HDFC"), windowStart (Instant), windowEnd (Instant), totalAttempts (int), failedAttempts (int), failureRate (double), status (String: HEALTHY/DEGRADED/DOWN), createdAt.

2c. Create repositories for both.

2d. Create RetrySchedulerService:
- Method: `scheduleRetries(PaymentEvent event, FailureClassification classification)`:
  - Based on category, create retry schedule entries:
    - insufficient_funds: 3 retries at Day 1, Day 3, Day 7
    - technical_decline: 3 retries at 5min, 30min, 2hr
    - expired_mandate: 0 retries (no point retrying expired mandates)
    - unknown: 2 retries at 1hr, 24hr
  - Save all RetrySchedule rows with status PENDING.
  - Audit log.

2e. Create BankHealthService:
- Method: `computeHealthSnapshots()`: Query payment_events from last 30 minutes, group by a extracted bank_code (parse from error description or raw_payload — if not available, use "UNKNOWN"), compute failure rates, save snapshots.
- Method: `getBankHealth(String bankCode)`: Return latest snapshot for the bank. If failure_rate > 80%, return DOWN. If > 40%, DEGRADED. Else HEALTHY.

2f. Create RetryExecutionScheduler:
- @Scheduled(fixedDelay = 60000) — every 1 minute.
- Find all RetrySchedule where result=PENDING and scheduledAt <= now, limit 10.
- For each: check bank health, if DOWN → skip (mark SKIPPED). If HEALTHY/DEGRADED → attempt retry via Razorpay API (simulated: just mark as SUCCESS for demo). Audit log each attempt.

2g. Create BankHealthScheduler:
- @Scheduled(fixedDelay = 300000) — every 5 minutes.
- Calls bankHealthService.computeHealthSnapshots().

2h. Wire retry scheduling into WebhookService:
- After failure classification completes (in the payment.failed block), call `retrySchedulerService.scheduleRetries(savedEvent, classification)`.

STEP 3 — COMPILATION: `mvnw.cmd clean compile -q`.
STEP 4 — TESTS: `mvnw.cmd test -q`.
STEP 5 — PHASE TICKING: PROMPTS.md Phase 7 → [x], PROJECT_CONTEXT.md Phase 7 → ✅.
STEP 6 — REPORT.
```

---

## Phase 8 — Enhanced Dashboard & ROI Metrics
Status: [ ] Pending

### Goal & Scope
Expand the dashboard with enterprise-grade ROI metrics: recovery rate, MTTR, category breakdown, daily time series, and a recovery funnel. Update both backend DTO and frontend to display these metrics prominently.

**Target Files:**
- `dto/DashboardSummaryResponse.java` — Expand with new fields
- `service/DashboardService.java` — Compute new metrics
- `repository/PaymentEventRepository.java` — Add aggregation queries
- `repository/RecoveryActionRepository.java` — Add metric queries
- `frontend/src/pages/DashboardPage.tsx` — Render new metrics
- `frontend/src/lib/api.ts` — Handle new response shape

### Executable Agent Prompt
```text
You are a Full-Stack Engineer working on RecoverMandate. Your task is Phase 8: Enhanced Dashboard & ROI Metrics.

STEP 0 — STATE VERIFICATION: Read PROMPTS.md. If Phase 8 `[x]`, STOP. If Phase 7 not `[x]`, STOP.

STEP 1 — READ: PROJECT_CONTEXT.md, DashboardService.java, DashboardSummaryResponse.java, DashboardController.java, PaymentEventRepository.java, RecoveryActionRepository.java, frontend/src/pages/DashboardPage.tsx (or App.tsx if not yet extracted), frontend/src/lib/api.ts.

STEP 2 — BACKEND IMPLEMENTATION:

2a. Expand DashboardSummaryResponse:
- Add: totalPaymentsProcessed (long), successfulPaymentsCount (long), successRate (double, percentage), avgResolutionTimeMinutes (double), failuresByCategory (Map<String, Long>), draftsGenerated (long), draftsApproved (long), messagesDispatched (long), paymentsRecovered (long), recoveryRate (double).

2b. Add repository query methods:
- PaymentEventRepository: `countByEventType("subscription.charged")` for successful payments, `@Query` for GROUP BY fc.category counts.
- RecoveryActionRepository: `countByStatus("APPROVED")`, `countByStatus("DISPATCHED")`, count by status "RECOVERED".

2c. Enhance DashboardService.getSummary():
- Compute all new metrics using repository queries.
- successRate = successfulPayments / totalPayments * 100.
- recoveryRate = recovered / failed * 100.
- avgResolutionTimeMinutes: Compute average time between PaymentEvent.receivedAt and RecoveryAction.approvedAt (or sentAt) for resolved actions. Use a @Query for this.

STEP 3 — FRONTEND IMPLEMENTATION:

3a. Update DashboardPage (or the dashboard section of App.tsx):
- Display new KPI cards: Recovery Rate (gauge), MTTR, Success Rate, Total Recovered Revenue.
- Add a category breakdown bar chart (use inline SVG bars — no external charting library needed).
- Add a recovery funnel visualization: Failed → Drafted → Approved → Dispatched → Recovered, with animated count-up numbers and percentage drop-off between stages.
- All new cards should use the existing glassmorphism style, Framer Motion animations, and sparkline components.

3b. Update api.ts to handle new response shape.

STEP 4 — BUILD: Backend: `mvnw.cmd clean compile -q`. Frontend: `cd frontend && npm run build`.
STEP 5 — TESTS: `mvnw.cmd test -q`.
STEP 6 — PHASE TICKING: PROMPTS.md Phase 8 → [x], PROJECT_CONTEXT.md Phase 8 → ✅.
STEP 7 — REPORT.
```

---

## Final Notes

### Execution Rules
1. **Execute phases in strict order** (1 → 2 → 3 → ... → 8). Each phase depends on the previous.
2. **Each phase is self-contained** — copy the Executable Agent Prompt into an AI coding agent.
3. **Phase ticking is mandatory** — the agent must update both PROMPTS.md and PROJECT_CONTEXT.md.
4. **No stubs or TODOs** — every phase produces production-grade, compilable, tested code.
5. **If a phase fails compilation**, the agent must fix it before ticking.

### Time Estimates
| Phase | Estimated Time | Judge Impact |
|---|---|---|
| Phase 1 | 30 min | Security credibility |
| Phase 2 | 1.5 hrs | Resilience story (we experienced the 503 crash live) |
| Phase 3 | 1 hr | Enterprise observability |
| Phase 4 | 1.5 hrs | Makes demo feel alive |
| Phase 5 | 2.5 hrs | Visual wow factor + code quality |
| Phase 6 | 2 hrs | Completes the revenue recovery pipeline |
| Phase 7 | 1.5 hrs | Intelligent dunning differentiation |
| Phase 8 | 1.5 hrs | ROI storytelling for judges |
| **Total** | **~12 hrs** | |
