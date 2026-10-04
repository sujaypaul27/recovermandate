# RecoverMandate

> **Autonomous AI-powered recovery platform for failed recurring payments and subscriptions on Indian banking rails.**

## Live Demo

A live evaluation deployment of RecoverMandate is accessible at [https://recovermandate.vercel.app](https://recovermandate.vercel.app). It runs as a self-contained sandbox on free-tier hosting (frontend on Vercel, backend Docker service on Render, and PostgreSQL on Supabase) with Demo Mode on, so some production integrations are simulated.

### Before You Try It

* **Instance Sleep & Cold Starts:** The backend runs on a free plan and sleeps after about 15 minutes without traffic. The first load can take 30 to 60 seconds while the container boots. While it sleeps, background schedulers are paused and the live-update stream reconnects automatically after the page wakes it.
* **Shared Database State:** All visitors share one database. Anyone can use the demo controls, including resetting the ledger, so data can disappear at any time. Do not enter real customer, bank, or payment data.

### How to Try the Demo

1. **Open the Dashboard:** Navigate to the **Overview & ROI** tab to view real-time recovery metrics and the top **Live Demo Simulator** bar.
2. **Run End-to-End Recovery:** Click the **"🚀 Full 5-Stage Recovery (1-Click)"** button to watch a simulated mandate failure step through ingestion, classification, AI drafting, link dispatch, and revenue recovery.
3. **Simulate a Specific Failure Category:** Select any scenario preset (such as **"Insufficient Funds"**, **"Technical Decline"**, or **"Expired Mandate"**) and click **"1. Simulate Failure (Step 1-3)"**.
4. **Review & Approve in the Queue:** Navigate to the **AI Approval Queue** tab, inspect the generated AI dunning draft, verify safety checks, and click **"Approve & Dispatch Link"**.
5. **Complete Customer Sandbox Checkout:** In the simulator banner or failure record, open the generated payment link (which routes to the hosted **Demo Sandbox Checkout** page) and click **"🧪 Simulate Demo Payment (₹499.00)"**.
6. **Verify the Cryptographic Audit Trail:** Switch to the **Audit Trail** tab to view the tamper-evident SHA-256 hash chain and click **"Verify Chain Integrity"**.

### What Works in the Live Demo vs. What is Simulated or Unavailable

| Feature | Status in Live Demo | Why | How to Enable in Your Own Clone |
|:---|:---|:---|:---|
| Simulated failure ingestion and full recovery flow | Works | Demo Mode is on (`DEMO_ENABLED=true`) and exposes the simulator | Works out of the box locally. |
| AI-drafted recovery emails (Gemini) | Works | A Gemini API key is configured; a rule-based fallback and a `"HEURISTIC DRAFT"` badge appear if Gemini is unavailable or rate-limited | Set `GEMINI_API_KEY` from Google AI Studio. |
| Deterministic classification, validation gate, audit ledger with SHA-256 chain | Works | Runs entirely inside the backend | Works out of the box. |
| Smart retry engine | Simulated | Retries run every minute against an internal bank-health model, with no real mandate re-presentment | Needs a real Razorpay recurring-charge integration with valid subscription IDs. |
| Bank / NPCI health | Internal data only | It is computed from the app's own recent failure events, not from an external NPCI feed | Works automatically from ingested failures. |
| Real email delivery | Not available (simulated) | `MAIL_ENABLED=false`, so no email leaves the system. Dispatch is recorded as sent with a `"(SIMULATED)"` tag in the Audit Trail and the action moves to `DISPATCHED`. Free hosts may also block outbound SMTP, and Gmail needs an app password | Set `MAIL_ENABLED=true` plus `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` (for Gmail: 2-Step Verification on and a 16-character App Password), and optionally `MAIL_FROM` and `MAIL_FROM_NAME`. |
| Real Razorpay payment links and checkout | Not available (sandbox) | `RAZORPAY_DRY_RUN=true` and no Razorpay keys, so payment links point to the built-in "Demo Sandbox Checkout" and no real card, UPI, or netbanking payment can be made | Set `RAZORPAY_DRY_RUN=false` with `RAZORPAY_KEY_ID` and `RAZORPAY_KEY_SECRET` (test keys are fine). |
| Inbound Razorpay webhooks | Not available | No webhook is registered and no webhook secret is set, so real `payment.failed` events never arrive; the 15-minute reconciliation job finds no credentials and exits cleanly | Register `https://<your-backend>/api/webhooks/razorpay` in the Razorpay dashboard, select the `payment.failed`, `payment.captured`, `subscription.charged`, and `payment_link.paid` events, and set `RAZORPAY_WEBHOOK_SECRET` to the same secret. |
| Operator login and multi-tenant isolation | Not available | The dashboard uses a single shared API key that is bundled into the public frontend, so anyone who opens browser developer tools can read it; there is no per-user authentication and all data belongs to one merchant namespace | For real use, add operator authentication and keep the API key server-side only. Turn Demo Mode off (leave `DEMO_ENABLED` unset in the prod profile). |

### Run Your Own Copy

To deploy or configure your own full production instance, follow [7. Environment Variables & Configuration](#7-environment-variables--configuration) and the [8. Quickstart Guide](#8-quickstart-guide). The live demo runs with the frontend on Vercel with root directory `frontend` and `VITE_API_BASE_URL` ending in `/api`, the backend on Render using the Dockerfile at the repository root with `SPRING_PROFILES_ACTIVE=prod`, and a Supabase PostgreSQL database through the session pooler. Set `CORS_ALLOWED_ORIGINS` and `APP_URL` to your frontend URL, and make sure `VITE_API_KEY` equals the backend `API_KEY`.

---

## 1. The Problem

Today, when a customer's recurring subscription or mandate debit fails, the payment gateway silently fires a failure webhook. Most merchant billing systems drop these events into an unmanaged queue, leaving subscriptions stuck in failure with zero automated recovery. Customers churn involuntarily without even realizing their payment failed, while merchants lose recurring revenue and overwhelm support teams with manual follow-ups.

---

## 2.  How This System Uses AI

Deterministic code owns every money-moving decision in RecoverMandate — retry scheduling, failure classification, escalation routing, and payment link dispatch are all executed by rule-based Java services with no AI in the loop. **Gemini is used for exactly one thing: drafting the wording of customer-facing recovery emails. No AI-generated draft is ever sent to a customer without passing a deterministic validation gate and receiving explicit human (or merchant Auto-Pilot policy) approval.** The `GeminiClient` sends only non-PII context (amount, currency, failure category, days since failure) to the Gemini API; customer names and emails never enter the prompt (Decision 3 in PROJECT_CONTEXT.md — PII minimization at the API boundary).

Every AI-generated draft passes through `RecoveryActionValidationService` before a human can approve it. This validation gate enforces three deterministic checks: (1) a **deny-list scan** rejecting any draft containing unauthorized offer language (`discount`, `refund`, `waiver`, `free`), (2) a **tone check** rejecting aggressive or threatening language (`sue`, `legal`, `penalty`, `final notice`, etc.), and (3) an **amount-match check** that parses all currency figures in the draft and hard-blocks if any mentioned amount diverges from the actual payment amount by more than ₹0.01. Drafts that fail any check transition to `BLOCKED` status — they cannot be approved or dispatched.

If Gemini is unavailable, rate-limited, or circuit-broken (Resilience4j opens after 5/10 failures, 50% threshold), the `HeuristicFallbackEngine` produces a rule-based template draft instead. The pipeline never blocks on the AI — every webhook produces a reviewable draft within the same request cycle.

---

## 3. Architecture & Recovery Flow

```
[ Razorpay Mandate Debit ]
           │
           ▼ (payment.failed Webhook)
┌────────────────────────────────────────────────────────┐
│             RecoverMandate Backend Platform            │
│                                                        │
│  1. HMAC-SHA256 Signature Verification & DLQ Safety    │
│  2. Deterministic Root-Cause Classification            │
│  3. Bank Health Engine & CBS Window Deferral           │
│  4. Gemini AI Email Drafting (Safety Guardrails)       │
│  5. Razorpay 1-Click Hosted Link Generation            │
│  6. Tamper-Proof Cryptographic Audit Ledger (SHA-256)  │
└────────────────────────────────────────────────────────┘
           │                                │
           ▼                                ▼
[ Multi-Channel Customer Dispatch ]   [ Smart Retry Engine ]
  (Email / Hosted Pay Link)             (AutoPay Re-presentment)
```

---

## 4. Results & Recovery Metrics

Recovery rate, mean time to resolve (MTTR), and total ₹ recovered are computed live by `DashboardService` and displayed on the Overview tab of the dashboard. **To see current numbers: start the app and open [http://localhost:5173](http://localhost:5173), then navigate to the Overview tab** — values are populated dynamically during each demo run from real pipeline data.

### Escalation Rules (from `MerchantSettings` & `RecoveryActionService`)

- **Auto-Pilot eligible categories:** `insufficient_funds`, `technical_decline` (configurable via `autoPilotAllowedCategories`)
- **Auto-dispatch threshold:** ≤ ₹2,500.00 (`autoPilotMaxAmount = 250000` paise) — drafts above this amount require human approval
- **Batch-approve:** operators can approve multiple drafted actions in one request, filtered by `maxAmount` ceiling (via `BatchApproveRequest.maxAmount`)
- **Auto-Pilot gate:** only fires for validated, unblocked drafts that match both the category allowlist AND the amount threshold; Auto-Pilot must be explicitly enabled by the merchant

### Stopping Criteria (from `RecoveryActionValidationService`)

- **Deny-list hard stop:** draft blocked if it contains any of: `discount`, `refund`, `waiver`, `free`
- **Aggressive tone hard stop:** draft blocked if it contains any of: `sue`, `legal`, `police`, `court`, `lawsuit`, `threat`, `penalty`, `immediately`, `failure to`, `suspend`, `terminate`, `final notice`, `consequences`
- **Amount-mismatch hard stop:** draft blocked if any currency figure in the text diverges from actual payment amount by > ₹0.01 (paise-to-rupee conversion: `actualAmount / 100.0`)
- **Empty draft hard stop:** null or blank draft messages are rejected before persistence
- **State machine enforcement:** only `DRAFTED` actions can be approved or rejected — any other status throws `IllegalStateException`

---

## 5. What Broke & How It Was Fixed

**1. Double-charge race condition between auto-retry and hosted payment links**
**What broke:** If a scheduled auto-retry succeeded while a customer was simultaneously paying via a hosted recovery link, the merchant would collect double payment.
**Root cause:** `RetryExecutionScheduler` had no awareness of active `PaymentLink` status, and vice versa.
**Fix:** Added bidirectional cancellation — on retry success, the scheduler checks `RecoveryAction` status and marks any `CREATED`/`DISPATCHED` payment link as `SUPERSEDED` via `RazorpayApiClient.cancelPaymentLink()`; on link payment, the webhook handler skips pending retries. (Commit `f473cdb`, verified in `RetryExecutionScheduler.java` lines 120–245 and `RetryExecutionSchedulerTest`.)

**2. Async webhook race condition — `@EventListener` + `@Async` wasn't commit-safe**
**What broke:** The classification/recovery pipeline fired on `PaymentFailedEvent` before the originating `PaymentEvent` row was committed, causing `EntityNotFoundException` on foreign key lookups in the async thread.
**Root cause:** Spring's `@EventListener` + `@Async` publishes the event during the transaction, not after commit. The async thread's `findById` query ran against an uncommitted row.
**Fix:** Replaced with `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Transactional(propagation = REQUIRES_NEW)`. Added a reflection-based regression test (`WebhookEventListenerTest.regressionGuard_verifyTransactionalEventListenerAnnotation`) that fails the build if any future refactor removes these annotations. (Verified in `WebhookEventListener.java` line 40–41, `WebhookEventListenerTest.java` lines 50–69.)

**3. Currency paise-vs-rupee mismatch in validation gate**
**What broke:** `RecoveryActionValidationService` compared AI-drafted rupee amounts (e.g. `₹499.00`) against the raw `amount` field, which stores values in paise (e.g. `49900`). Every draft was incorrectly blocked as an amount mismatch.
**Root cause:** Missing `/ 100.0` conversion before comparison.
**Fix:** Added `double expectedAmount = actualAmount / 100.0` in `validateDraft()` so the check compares rupee-to-rupee. (Verified in `RecoveryActionValidationService.java` line 54.)

**4. Audit hash-chain GENESIS reset corruption after Demo Reset**
**What broke:** After a "Demo Reset" (which truncates all operational tables), the in-memory `lastChecksum` diverged from the now-empty database. New audit entries chained from a stale checksum, making `verifyChain()` report tamper when none occurred.
**Root cause:** `resetGenesis()` in `AuditService` only reset the volatile field; `@PostConstruct init()` would restore a stale checksum from the latest DB row on restart, but mid-session resets had no equivalent re-initialization.
**Fix:** `DemoController.resetDemo()` now calls `auditService.resetGenesis()` immediately after table truncation, and `AuditService.resealChain()` was added to recompute the full hash chain from GENESIS for recovery scenarios. (Verified in `DemoController.java` line 331, `AuditService.java` lines 132–175.)

**5. Gemini hallucinating placeholder company names in drafts**
**What broke:** Gemini occasionally returned drafts containing `[Your Company Name]`, `[Merchant]`, or `[Your Organization Name]` despite explicit prompt instructions not to use placeholders. These slipped through to the approval queue.
**Root cause:** LLM instruction-following is probabilistic; the prompt alone couldn't guarantee zero placeholder tokens.
**Fix:** Added `sanitizeDraftText()` to `GeminiClient` — a post-processing pass with 6 regex replacements that catch common placeholder patterns (`[Company Name]`, `[Your Business Name]`, `[Merchant]`, etc.) and substitute the actual merchant's `businessDisplayName`. Also strips markdown fences, subject lines, and conversational preambles. (Verified in `GeminiClient.java` lines 149–181.)

---

## 6. Key Features

- **Bank Downtime Avoidance:** Indian PSU banks (SBI, PNB, Canara, BoB) run heavy nightly CBS batch settlements between 11:30 PM and 3:30 AM IST. RecoverMandate automatically defers retries during this window to preserve retry counts.
- **Human-in-the-Loop Review Queue:** High-value subscriptions or flagged recovery drafts are placed in an operator approval queue with single and batch approval workflows.
- **Real-Time Live Dashboard:** Interactive React dashboard connected via Server-Sent Events (SSE) showing live recovery yields, pipeline state transitions, and audit logs.
- **Built-in Demo Simulation:** Complete end-to-end sandbox simulator allowing users and hackathon judges to trigger failures, observe AI dunning, and test payment links without needing live credit cards or billing credentials.

---

## 7. Environment Variables & Configuration

Set these variables in your environment or an `.env` file before running the application (or run out-of-the-box using the built-in demo defaults):

> [!NOTE]
> **Zero Configuration Required for Local Demo Mode:**  
> The application includes working local defaults and a built-in sandbox simulator. If external API keys (Gemini, Razorpay, or Gmail) are left blank, RecoverMandate automatically activates offline fallback engines (heuristic templates, local preview links, and simulated email delivery) so you can test all features immediately.

> **Note on webhooks and ngrok:** This project includes a built-in demo simulator that replicates real Razorpay webhook payloads locally, so you can experience the full recovery pipeline (classification, retries, AI drafts, payment links) without exposing your machine to the internet. **You do not need ngrok to try the demo.**
>
> If you want to test genuinely live Razorpay webhook delivery (real payments hitting your local server instead of the simulator), you'll need a tool like [ngrok](https://ngrok.com) to expose your local port 8080 with a public HTTPS URL, which you then register in your Razorpay Dashboard's webhook settings. This is optional and only needed for live payment testing, not for running or judging the core project.

### Summary of Environment Variables

| Variable | What it's for | Where to get it | Required? |
|----------|---------------|---------------------|-----------|
| `DB_URL` | Connection string for your PostgreSQL database | Any Postgres instance (e.g. free Supabase project) | Yes |
| `DB_USERNAME` | Database login username | Same place as DB_URL | Yes |
| `DB_PASSWORD` | Database login password | Same place as DB_URL | Yes |
| `RAZORPAY_KEY_ID` | Identifies your Razorpay account for API calls | Razorpay Dashboard → Settings → API Keys (use Test Mode keys) | Yes, for live Razorpay features — the built-in demo simulator works without it |
| `RAZORPAY_KEY_SECRET` | Secret paired with the key ID above | Same place as RAZORPAY_KEY_ID | Same as above |
| `RAZORPAY_WEBHOOK_SECRET` | Verifies that incoming webhooks really came from Razorpay | Razorpay Dashboard → Webhooks → set up a webhook and copy its secret | Only needed for real Razorpay webhook delivery |
| `GEMINI_API_KEY` | Powers the AI-generated recovery emails | Google AI Studio (aistudio.google.com) — free tier available | No — falls back to rule-based templates if missing |
| `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD` | Sends real recovery emails via Gmail | A Gmail account + an "App Password" (not your normal Gmail password) generated in Google Account settings | No — emails are simulated/logged instead of sent if missing |
| `API_KEY` | Protects internal/demo endpoints from random access | You choose any string yourself | Yes, but has a working default for local testing |

---

### Detailed Setup: Getting Each Credential

#### 1. Database (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`)
- **What it's for:** Needed for the application to store payment events, failure classifications, customer records, retry schedules, and cryptographic audit logs.
- **Step-by-step instructions:**
  1. Sign up for a free account at [supabase.com](https://supabase.com).
  2. Create a new project and set a database password (remember this password).
  3. Once your project is created, navigate to **Project Settings** (gear icon) → **Database**.
  4. Under the **Connection string** section, select the **URI** tab and copy the connection string for `DB_URL` (format: `postgresql://postgres.[ref]:[password]@aws-0-[region].pooler.supabase.com:6543/postgres`).
  5. Use the user shown (typically `postgres`) for `DB_USERNAME`, and the password you chose during setup for `DB_PASSWORD`.
- **Alternative (Local PostgreSQL):** Any local PostgreSQL installation works as well:
  - `DB_URL`: `jdbc:postgresql://localhost:5432/recovermandate`
  - `DB_USERNAME`: `postgres`
  - `DB_PASSWORD`: `postgres`

#### 2. Razorpay API Keys (`RAZORPAY_KEY_ID` and `RAZORPAY_KEY_SECRET`)
- **What it's for:** Identifies your Razorpay account so RecoverMandate can call Razorpay's API to generate 1-click hosted payment links, cancel active links when an automated retry succeeds, and query live settlement status.
- **Step-by-step instructions:**
  1. Sign up or log in at [razorpay.com](https://razorpay.com).
  2. In your Razorpay Dashboard, make sure the toggle in the top-right is switched to **Test Mode** (never use live credentials for testing).
  3. Navigate to **Account & Settings** (or **Settings**) → **API Keys**.
  4. Click **Generate Test Key**.
  5. Copy the **Key Id** (starts with `rzp_test_...`) and **Key Secret** shown.
  6. Save the Key Secret immediately — Razorpay displays it only once and cannot show it again.
- **Note:** These keys are optional. If left blank, RecoverMandate automatically activates its local interactive checkout simulation engine (`/#/pay/...`), so you can test complete payment recovery without credentials.

#### 3. Razorpay Webhook Secret (`RAZORPAY_WEBHOOK_SECRET`)
- **What it's for:** Lets the application cryptographically verify (via HMAC-SHA256) that an incoming webhook really came from Razorpay and was not spoofed or intercepted by a third party.
- **Step-by-step instructions:**
  1. This is only needed if you are testing genuinely live webhooks routed to your local server via ngrok (see the note above).
  2. In your Razorpay Dashboard (Test Mode), navigate to **Account & Settings** → **Webhooks** → **Add New Webhook**.
  3. In the **Webhook URL** field, enter your public ngrok HTTPS URL followed by `/api/webhooks/razorpay` (e.g. `https://your-subdomain.ngrok-free.app/api/webhooks/razorpay`).
  4. In the **Secret** field, enter any secret string of your choice (e.g. `my_secure_webhook_secret_123`).
  5. Under **Active Events**, select `payment.failed` (and optionally `payment_link.paid`, `payment_link.expired`).
  6. Click **Create Webhook**, and copy that exact secret string into `RAZORPAY_WEBHOOK_SECRET`.

#### 4. Google Gemini API Key (`GEMINI_API_KEY`)
- **What it's for:** Powers AI-generated, personalized recovery emails tailored to the specific failure category instead of static templates.
- **Step-by-step instructions:**
  1. Go to [Google AI Studio](https://aistudio.google.com).
  2. Sign in with your Google account.
  3. Click **Get API Key** in the left sidebar.
  4. Click **Create API Key** (choose a Google Cloud project or let AI Studio create one for you).
  5. Copy the generated key and assign it to `GEMINI_API_KEY`.
- **Note:** This key is optional. If left blank, RecoverMandate automatically falls back to its built-in rule-based email templates (`HeuristicFallbackEngine`), so all recovery actions draft cleanly without an external API key.

#### 5. Email Dispatch Credentials (`SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, `MAIL_FROM`)
- **What it's for:** Lets the application actually send recovery emails to customers via Gmail rather than only simulating and logging them.
- **Step-by-step instructions:**
  1. Use any standard Gmail account.
  2. Go to your Google Account security center at [myaccount.google.com/security](https://myaccount.google.com/security).
  3. Under **How you sign in to Google**, enable **2-Step Verification** if it is not already turned on.
  4. Use the search bar in your Google Account settings to search for **App Passwords**.
  5. Create a new App Password (enter a name like `RecoverMandate`) and click **Create**.
  6. Google will display a 16-character passcode (e.g. `abcd efgh ijkl mnop`).
  7. Use your full Gmail address as `SPRING_MAIL_USERNAME` and `MAIL_FROM`.
  8. Use the generated 16-character passcode (without spaces) as `SPRING_MAIL_PASSWORD` (not your normal Gmail login password).
- **Note:** `SPRING_MAIL_HOST` (`smtp.gmail.com`) and `SPRING_MAIL_PORT` (`587`) already have default settings and do not need to be changed. If credentials are left blank, email delivery is simulated and logged locally without errors.

#### 6. Internal API Key (`API_KEY`)
- **What it's for:** Protects internal and demo endpoints (`/api/**`) from unauthorized access while allowing public access to customer checkout portals.
- **Step-by-step instructions:**
  1. You can choose any custom string yourself (e.g. `my-local-dev-key`).
  2. No external account or registration is required.
- **Note:** Has a working default (`default-dev-key`) if left unset for instant local testing.

---

## 8. Quickstart Guide

### Prerequisites
- **Java 21** or later
- **Maven 3.9+**
- **Node.js 18+** and **npm**
- **PostgreSQL** running locally on port 5432 (or a remote Supabase / Neon instance)

---

### Step 1: Start the Backend (Spring Boot)

1. Clone this repository:
   ```bash
   git clone https://github.com/your-username/recovermandate.git
   cd recovermandate
   ```

2. Run the Spring Boot application:
   ```bash
   mvn spring-boot:run
   ```
   The backend API will start on `http://localhost:8080`.

---

### Step 2: Start the Frontend (React + Vite)

1. Open a new terminal and navigate to the `frontend` folder:
   ```bash
   cd frontend
   ```

2. Install dependencies and start the development server:
   ```bash
   npm install
   npm run dev
   ```
   The interactive dashboard will open at `http://localhost:5173`.

---

## 9. Running the Test Suite

RecoverMandate includes a comprehensive test suite covering unit logic, failure classification, retry scheduling, signature verification, and full-stack integration flows.

Run all tests from the repository root:
```bash
mvn test
```

Expected result:
```
[INFO] Results:
[INFO] 
[INFO] Tests run: 241, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

---

## 10. Technology Stack

- **Backend:** Java 21, Spring Boot 3.3.3, Spring Data JPA, Hibernate, PostgreSQL, Resilience4j (Circuit Breaker & Retry), Bucket4j (Rate Limiting).
- **AI Engine:** Google Gemini 2.5 Flash Lite via Google Generative AI API with deterministic Heuristic Fallback Engine.
- **Payments Gateway:** Razorpay API v1 (Payment Links, Webhooks HMAC-SHA256, Subscriptions).
- **Frontend:** React 18, Vite, TypeScript, Tailwind CSS, Framer Motion, Lucide Icons, Server-Sent Events (SSE).

---

## 11. License

This project is open source and available under the [MIT License](LICENSE).
