# RecoverMandate — Project Context (Master Source of Truth)

> **Last Updated**: 2026-08-28T23:00 IST  
> **Architecture Owner**: Principal Architect (Autonomous)  
> **Status**: Phase execution system active — see `PROMPTS.md` for current progress

---

## 1. Target Production Architecture & System Boundaries

### 1.1 System Overview

RecoverMandate is an enterprise-grade payment failure observability, AI-driven diagnosis, and automated dunning/revenue recovery platform. It intercepts Razorpay subscription payment failures in real-time, classifies root causes deterministically, generates AI-powered recovery communications via Gemini, and orchestrates multi-channel customer outreach with one-click payment recovery links.

### 1.2 Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         EXTERNAL SYSTEMS                                │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                  │
│  │   Razorpay    │  │  Google      │  │   Email /    │                  │
│  │   Webhooks    │  │  Gemini API  │  │  WhatsApp    │                  │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘                  │
└─────────┼──────────────────┼──────────────────┼────────────────────────┘
          │                  │                  │
          │ HMAC-SHA256      │ Circuit Breaker  │ Dispatch
          │ + Replay Guard   │ + PII Redaction  │ Service
          ▼                  ▼                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    SPRING BOOT 3.3.3 (Java 21)                          │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │ SECURITY LAYER                                                    │   │
│  │ ApiKeyAuthFilter → RateLimitFilter (Bucket4j) → SecurityConfig   │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  ┌────────────────────┐  ┌────────────────────┐  ┌─────────────────┐   │
│  │ INGESTION          │  │ INTELLIGENCE       │  │ ACTION          │   │
│  │ WebhookController  │  │ FailureClassifier  │  │ RecoveryAction  │   │
│  │ WebhookService     │  │ GeminiClient       │  │ PaymentLinks    │   │
│  │ SignatureVerifier  │  │ HeuristicFallback  │  │ DispatchService │   │
│  │ Reconciliation     │  │ PiiRedaction       │  │ RetryScheduler  │   │
│  └────────┬───────────┘  └────────┬───────────┘  └───────┬─────────┘   │
│           └───────────────────────┴───────────────────────┘             │
│                                   │                                     │
│                          ┌────────▼────────┐                            │
│                          │  AUDIT LAYER    │                            │
│                          │  AuditService   │                            │
│                          │  TraceID / MDC  │                            │
│                          └────────┬────────┘                            │
│                                   │                                     │
│                          ┌────────▼────────┐                            │
│                          │  SSE STREAMING  │                            │
│                          │  SseController  │                            │
│                          └─────────────────┘                            │
└─────────────────────────────────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────┐    ┌──────────────────────────────────────────┐
│  PostgreSQL 15+     │    │  React 19 + Vite + Tailwind + Radix UI  │
│  8 core entities    │    │  Framer Motion + SSE EventSource        │
│  + new tables       │    │  Modular pages & components             │
└─────────────────────┘    └──────────────────────────────────────────┘
```

### 1.3 Tech Stack (Final)

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 21 |
| Backend Framework | Spring Boot | 3.3.3 |
| ORM | Hibernate / Spring Data JPA | 6.5.2 |
| Database | PostgreSQL | 15+ |
| Security | Spring Security + Bucket4j + HMAC-SHA256 | 6.3.3 / 8.10.1 |
| Resilience | Resilience4j (circuit breaker + retry + time limiter) | 2.2.0 |
| AI | Google Gemini API (gemini-3.5-flash-lite) | v1beta |
| Frontend | React 19 + TypeScript + Vite 8 | 19.2.8 / 8.2.0 |
| UI Framework | Tailwind CSS 3.4 + Radix UI + Framer Motion | 3.4.19 |
| Build | Maven (backend) + npm (frontend) | — |

---

## 2. Senior Engineering & Trade-Off Decisions

### Decision 1: Resilience4j over Spring Retry
**Choice**: Resilience4j circuit breaker + retry + time limiter wrapping GeminiClient.
**Rationale**: Spring Retry lacks circuit breaker state management. Resilience4j provides a state machine (CLOSED → OPEN → HALF_OPEN) that prevents cascading failures during prolonged Gemini outages. The circuit opens after 5/10 failures (50% threshold), waits 30s, then probes with 3 half-open calls.

### Decision 2: Heuristic Fallback over Queue-and-Retry-Later
**Choice**: Local deterministic template engine as synchronous fallback.
**Rationale**: Webhook processing pipeline MUST complete end-to-end in one request cycle. Queuing for async retry would leave incomplete PENDING_DRAFT state during demo. Heuristic fallback guarantees every failure produces a reviewable draft within the same HTTP transaction.

### Decision 3: PII Minimization at API Boundary over Regex Scrubbing
**Choice**: Never pass customer names, emails, or phone numbers to Gemini. Pass only: amount, currency, failure category, days since failure.
**Rationale**: Regex-based PII scrubbing is fragile. Data minimization at the API boundary is the only secure approach — if PII never enters the prompt string, it cannot leak.

### Decision 4: SSE over WebSocket
**Choice**: Server-Sent Events for real-time dashboard updates.
**Rationale**: SSE is unidirectional (server → client), auto-reconnects, works through ngrok. WebSocket is over-engineered for one-way push.

### Decision 5: Append-Only Audit with SHA-256 Hash Chain
**Choice**: Each audit row includes checksum = SHA256(previous_checksum + current_row_data).
**Rationale**: Creates tamper-evident chain. Any modification to a historical audit row breaks all subsequent checksums. Powerful compliance story for judges.

### Decision 6: Frontend Modularization — Tab SPA, No React Router
**Choice**: Extract App.tsx into 5 page components + shared hooks, keep single-page SPA.
**Rationale**: Adding React Router introduces routing complexity that risks breaking the demo. Tab-based navigation is functionally identical for dashboard products.

### Decision 7: pom.xml Java Version Correction
**Choice**: Update java.version from 17 to 21.
**Rationale**: Runtime is Java 21. Pom claims 17. Mismatch causes potential compilation issues.

---

## 3. Financial Safety & Idempotency Contracts

| Operation | Key | Enforcement | Duplicate Behavior |
|---|---|---|---|
| Webhook ingestion | razorpayPaymentId | UNIQUE DB constraint + app check | Return existing, log DUPLICATE_WEBHOOK_IGNORED |
| Failure classification | paymentEvent (OneToOne) | UNIQUE DB constraint + repo lookup | Return existing, no new audit |
| Recovery action | failureClassification (OneToOne) | UNIQUE DB constraint | DB rejects duplicate |
| Approve/Reject | status state check | Application state machine | IllegalStateException if not DRAFTED |

## 4. State Machine: RecoveryAction Lifecycle

```
  payment.failed     ┌──────────┐   AI OK    ┌────────┐
  ─────────────────▶ │PENDING   │──────────▶ │DRAFTED │
       webhook       │_DRAFT    │            │        │
                     └────┬─────┘            └──┬──┬──┘
                     AI FAIL               APPROVE REJECT
                          │                    │    │
                          ▼                    ▼    ▼
                     ┌─────────┐      ┌────────┐  ┌────────┐
                     │AI_DRAFT │      │APPROVED│  │REJECTED│
                     │_FAILED  │      └───┬────┘  └────────┘
                     └─────────┘          │
                                     DISPATCH
                                          │
                                          ▼
                                   ┌──────────┐
                                   │DISPATCHED│
                                   └────┬─────┘
                                   LINK PAID
                                        │
                                        ▼
                                   ┌──────────┐
                                   │RECOVERED │
                                   └──────────┘

  Validation Gate may also produce: BLOCKED
```

---

## 5. Master Phase Execution Tracker

| # | Phase | Status | Description |
|---|---|---|---|
| 1 | Webhook Hardening & Replay Protection | ✅ Completed | Timestamp guard, pom.xml Java 21, response optimization |
| 2 | AI Resilience, Circuit Breaker & PII Redaction | ✅ Completed | Resilience4j, heuristic fallback, PII minimization |
| 3 | Observability: Trace ID & Tamper-Proof Audit | ✅ Completed | UUID correlation, SHA-256 chain, MDC logging |
| 4 | SSE Live Streaming & System Health | ✅ Completed | EventSource, degraded state banners, health API |
| 5 | Frontend Modularization & UX Polish | ✅ Completed | Extract App.tsx, Cmd+K search, flow diagram, tone slider |
| 6 | Payment Link Generation & Dispatch Pipeline | ✅ Completed | Razorpay Payment Links API, dispatch service |
| 7 | Smart Retry Engine & Bank Health Tracker | ✅ Completed | Category-based backoff, issuer failure rate monitoring |
| 8 | Enhanced Dashboard & ROI Metrics | ⬜ Pending | Recovery funnel, MTTR, success rate, category breakdown |

---

## 6. Completed Legacy Phases

All 14 original development phases are complete (Core Domain → Award-Winning UX). See legacy PROJECT_CONTEXT.md for full history.

---

## 7. Key File Index

### Backend (Java)
| File | Purpose |
|---|---|
| `webhook/RazorpayWebhookController.java` | POST /api/webhooks/razorpay |
| `webhook/RazorpaySignatureVerifier.java` | HMAC-SHA256 verification |
| `service/WebhookService.java` | Core webhook pipeline (365 lines) |
| `service/FailureClassificationService.java` | Deterministic classifier |
| `service/RecoveryActionService.java` | AI draft + approve/reject |
| `service/RecoveryActionValidationService.java` | Deny-list, tone, amount checks |
| `ai/GeminiClient.java` | Gemini API REST client |
| `audit/AuditService.java` | REQUIRES_NEW audit logging |
| `security/SecurityConfig.java` | Spring Security filter chain |
| `security/ApiKeyAuthFilter.java` | X-API-Key authentication |
| `security/RateLimitFilter.java` | Bucket4j rate limiting |

### Frontend (React/TypeScript)
| File | Purpose |
|---|---|
| `frontend/src/App.tsx` | Monolith (995 lines — refactor target) |
| `frontend/src/lib/api.ts` | Backend API client |
| `frontend/src/components/ui/` | 13 Radix UI wrappers |

### Tests (14 files)
Service tests (4), Controller tests (4), Webhook tests (2), Integration tests (4)
