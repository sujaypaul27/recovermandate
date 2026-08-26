# RecoverMandate — Project Context

## Overview
RecoverMandate is a mandate payment recovery system that ingests Razorpay webhook events,
classifies payment failures deterministically, and will eventually use AI (Gemini) to
recommend recovery actions for failed mandates.

## Architecture
- **Spring Boot 3.3.3** (Java 17) with Spring Data JPA
- **PostgreSQL** for persistence
- Razorpay HMAC-SHA256 webhook signature verification
- Layered architecture: Controller → Service → Repository → Entity

## Current Task
**Phase 8: Edge Cases (Pending)**

## Next Step
Handle edge cases and further refine the integration.

---

## Phase Checklist

### Phase 1: Core Domain Model ✅
- [x] Entity classes: Merchant, Customer, Plan, Subscription, PaymentEvent,
      FailureClassification, RecoveryAction, AuditLog
- [x] JPA repositories with finder methods
- [x] Database schema via Hibernate ddl-auto

### Phase 2: Webhook Ingestion Pipeline ✅
- [x] RazorpaySignatureVerifier — HMAC-SHA256 with constant-time comparison
- [x] RazorpayWebhookController — thin controller, delegates to WebhookService
- [x] WebhookService.handleVerifiedEvent — parses Razorpay JSON, extracts payment/
      subscription/customer/merchant/plan data, creates or finds entities

### Phase 3: Idempotency + Audit Trail ✅
- [x] Duplicate webhook detection by razorpayPaymentId
- [x] AuditService with REQUIRES_NEW propagation for independent audit logging
- [x] Audit entries for: WEBHOOK_INGESTED, DUPLICATE_WEBHOOK_IGNORED,
      INVALID_SIGNATURE, WEBHOOK_PROCESSING_FAILED
- [x] Full unit test coverage for all webhook/audit/idempotency paths

### Phase 4: Deterministic Failure Classifier ✅
- [x] FailureClassificationService.classify() — pure deterministic Java, zero AI
- [x] Exhaustive error_code mapping (BAD_REQUEST_ERROR → insufficient_funds,
      GATEWAY_ERROR/SERVER_ERROR → technical_decline, expired/mandate substring →
      expired_mandate, everything else → unknown)
- [x] Case-insensitive matching, null/blank/whitespace normalization
- [x] Idempotency guard — duplicate classification on same PaymentEvent is a no-op
      (no second row, no second AuditLog entry)
- [x] Structured audit reasoning (raw_error_code, category, auto_recoverable, matched rule)

### Phase 5: Gemini AI Integration + Validation Gate ✅
- [x] Integrate Gemini AI for recovery action recommendation
- [x] Human-in-the-loop validation gate
- [x] AI-recommended actions persisted as RecoveryAction entities
- [x] Audit trail for AI decisions
- [x] Unit and integration tests

### Phase 6: Backend APIs for Dashboard & Recovery Flow ✅
- [x] Global exception handler for safe API error responses
- [x] Response/Request DTOs to hide internal entity details
- [x] Repository queries with pagination, filtering, and aggregation
- [x] Dashboard summary endpoint
- [x] Payment events and audit log paginated endpoints
- [x] Recovery action approve/reject endpoints with idempotent state transitions
- [x] Controller and Service level testing

### Phase 7: Frontend UI Implementation ✅
- [x] Dashboard overview with KPI cards
- [x] Failed mandates table with pagination and filtering
- [x] Approval queue for recovery actions
- [x] Audit trail timeline
- [x] Error handling and loading states

---

## Key Design Decisions
- **auto_recoverable = true** only for `technical_decline`; false for all others including unknown
- **Unknown is the safe terminal state** — classifier never throws for bad/missing input
- FailureClassification has a **unique constraint** on payment_event_id (OneToOne) as DB-level backstop
- AuditService uses **REQUIRES_NEW propagation** so audit logs survive transaction rollbacks
- Webhook idempotency is two-layered: PaymentEvent dedup by razorpayPaymentId +
  FailureClassification dedup by paymentEvent

---

## Security Review — 2026-08-26

### Finding 1 (MEDIUM → FIXED): `calculateHmacSha256` was public
- **File**: `RazorpaySignatureVerifier.java:77`
- **Issue**: The method that computes HMACs using the webhook secret was `public`, exposing
  it to any code with a reference to the bean. No legitimate external caller exists.
- **Fix**: Reduced visibility to package-private. Tests are in the same package — no breakage.

### Finding 2 (LOW — no fix needed): RuntimeException wraps `e.getMessage()` in audit log
- **File**: `WebhookService.java:130-132`
- **Issue**: On processing failure, `e.getMessage()` is written to the audit_logs table and
  the exception is rethrown as RuntimeException. Potential concern: stack trace leakage.
- **Assessment**: Spring Boot 3.x defaults (`server.error.include-message=never`,
  `server.error.include-stacktrace=never`) prevent the message/trace from reaching HTTP
  responses. The RuntimeException message itself is a static string. The audit DB entry
  is not user-facing. **No action needed** while defaults are preserved.

### Confirmed Clean
| Area | Result |
|---|---|
| **Hardcoded secrets** | None found. All secrets use `${ENV_VAR:default}` pattern. Git history checked — no Supabase password ever committed to tracked files. |
| **Signature bypass paths** | Null payload, null/blank signature, null/blank secret all return `false`. `MessageDigest.isEqual` used for constant-time comparison. |
| **SQL injection** | Zero `@Query`, `nativeQuery`, `EntityManager`, or raw SQL anywhere. All repositories use Spring Data derived queries only. |
| **Audit log forgery/bypass** | `AuditService.log()` is the only write path. Actor field defaults to `"SYSTEM"` if null. All code paths that modify state call audit. REQUIRES_NEW propagation ensures audit survives transaction rollbacks. |
| **CORS** | Limited to `localhost:5173` and `localhost:3000` (dev origins). Acceptable for dev; should be restricted for production. |
| **Payload injection via error_code** | `error_code` flows through `determineCategory()` (string comparison only, never interpolated into SQL/queries), stored via JPA parameterized queries. No injection vector. |
| **Malformed JSON** | Jackson `readTree` throws on malformed JSON → caught in the catch block → audit logged → RuntimeException rethrown → 500 response. Pipeline does not crash silently. |
| **.gitignore** | `.env`, `.env.local`, `application-local.yml`, `frontend/.env` all excluded. |

