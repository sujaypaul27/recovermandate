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
**Phase 12: Final Review (Pending)**

## Next Step
Perform final system checks and wrap up.

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

### Phase 8: Edge Cases & Reconciliation ✅
- [x] Scheduled job for missed webhook reconciliation
- [x] Duplicate webhook handling (idempotency tests)
- [x] Halt subscriptions on consecutive failures

### Phase 9: Security Hardening ✅
- [x] Spring Security API Key Auth on `/api/**` endpoints
- [x] CORS hardening driven by environment variables
- [x] Bucket4j rate limiting for webhooks and approval endpoints
- [x] Prevent data exposure in `GlobalExceptionHandler`

### Phase 10: Threat Model ✅
- [x] Documented attack surfaces and defenses in `THREAT_MODEL.md`

### Phase 11: Frontend Impact Toggle ✅
- [x] "With RecoverMandate" vs "Without RecoverMandate" toggle on Dashboard
- [x] Switch between static mock data (lost revenue) and live API data

### Phase 12: UI Polish Pass (Production-Grade Visual Upgrade) ✅
- [x] Razorpay-blue brand palette, Inter font, deep navy gradient mesh background
- [x] Glassmorphism surfaces (backdrop-blur, subtle borders, soft shadows) for all cards
- [x] Ambient gradient blobs with CSS-only slow-float animations
- [x] Framer Motion: staggered reveals, spring-based transitions, animated count-up KPIs
- [x] Category severity color coding (rose/amber/slate/purple) on Failed Mandates table
- [x] Side-by-side approval layout (AI draft left, human decision right)
- [x] Audit trail timeline with distinct actor icons/colors (SYSTEM/AI/HUMAN)
- [x] Shimmer skeleton loading states, guided empty states, premium error state
- [x] Fully responsive mobile layouts (stacked cards, collapsible table → cards)
### Phase 13: Recruiter-Grade UI Upgrade (Hero, 3D, Themes) ✅
- [x] Full Dark / Light Mode Theme Toggle (Sun/Moon switch)
- [x] Interactive Storytelling Hero Section with diagrammatic flow
- [x] CSS 3D parallax hover-tilt effect on KPI cards
- [x] Custom zero-dependency tooltips for complex metrics
- [x] Razorpay co-branding SVG logo in navbar
### Phase 14: Award-Winning UX/UI Transformation (Fintech Studio) ✅
- [x] Converted to professional Sidebar App Shell layout.
- [x] Integrated Storytelling Hero sequence with animated flow (Fail -> AI -> Recovered).
- [x] Added bespoke SVG animated sparklines to KPI cards.
- [x] Built a "Typewriter" generative AI effect for the approval queue split-pane.
- [x] Added multi-layered inset shadows to glassmorphism.
- [x] Replaced simple button toggle with a draggable/clickable Impact Slider.
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

