# RecoverMandate

> **Autonomous AI-powered recovery platform for failed recurring payments and subscriptions on Indian banking rails.**

---

## 1. The Problem

Today, when a customer's recurring subscription or mandate debit fails, the payment gateway silently fires a failure webhook. Most merchant billing systems drop these events into an unmanaged queue, leaving subscriptions stuck in failure with zero automated recovery. Customers churn involuntarily without even realizing their payment failed, while merchants lose recurring revenue and overwhelm support teams with manual follow-ups.

---

## 2. What RecoverMandate Does

RecoverMandate acts as an automated, intelligent recovery layer between your payment gateway (Razorpay) and your subscription billing system. When a recurring mandate fails:

1. **Instant Webhook Ingestion:** Securely ingests Razorpay `payment.failed` webhooks with HMAC-SHA256 signature verification and fast replay protection.
2. **Deterministic Classification:** Automatically diagnoses the root cause into standardized categories:
   - **Technical Decline:** Temporary bank server errors or network drops.
   - **Insufficient Funds:** Low balance on debit day.
   - **Expired Mandate:** Expired card or cancelled AutoPay authorization.
3. **Smart Retry Scheduling:** Avoids futile retries by consulting real-time Indian banking rail status. Retries are scheduled away from Core Banking Solution (CBS) nightly maintenance windows (11:30 PM – 3:30 AM IST) and timed for morning liquidity settlement windows (10:00 AM IST).
4. **Context-Aware AI Dunning:** Uses Google Gemini (with an offline rule-based fallback) to generate polite, brand-safe customer recovery emails without aggressive threats or unauthorized refund promises.
5. **1-Click Hosted Recovery Links:** Automatically creates official Razorpay checkout payment links so subscribers can settle declines in seconds with UPI, cards, or netbanking.
6. **Closed-Loop Cancellation:** If an automated retry succeeds, any active recovery payment link is superseded immediately to prevent accidental double-charging.
7. **Cryptographic Audit Ledger:** Every webhook, classification, draft, and dispatch is sealed into a tamper-evident SHA-256 hash-chained ledger.

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

## 4. Key Features

- **Bank Downtime Avoidance:** Indian PSU banks (SBI, PNB, Canara, BoB) run heavy nightly CBS batch settlements between 11:30 PM and 3:30 AM IST. RecoverMandate automatically defers retries during this window to preserve retry counts.
- **Human-in-the-Loop Review Queue:** High-value subscriptions or flagged recovery drafts are placed in an operator approval queue with single and batch approval workflows.
- **Real-Time Live Dashboard:** Interactive React dashboard connected via Server-Sent Events (SSE) showing live recovery yields, pipeline state transitions, and audit logs.
- **Built-in Demo Simulation:** Complete end-to-end sandbox simulator allowing users and hackathon judges to trigger failures, observe AI dunning, and test payment links without needing live credit cards or billing credentials.

---

## 5. Environment Variables & Configuration

Set these variables in your environment or an `.env` file before running the application (or run out-of-the-box using the built-in demo defaults):

> [!NOTE]
> **Zero Configuration Required for Local Demo Mode:**  
> The application includes working local defaults and a built-in sandbox simulator. If external API keys (Gemini, Razorpay, or Gmail) are left blank, RecoverMandate automatically activates offline fallback engines (heuristic templates, local preview links, and simulated email delivery) so you can test all features immediately.

| Variable | What it's for | Where to get it | Required? |
|----------|---------------|------------------|-----------|
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

## 6. Quickstart Guide

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

## 7. Running the Test Suite

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

## 8. Technology Stack

- **Backend:** Java 21, Spring Boot 3.3.3, Spring Data JPA, Hibernate, PostgreSQL, Resilience4j (Circuit Breaker & Retry), Bucket4j (Rate Limiting).
- **AI Engine:** Google Gemini 2.5 Flash Lite via Google Generative AI API with deterministic Heuristic Fallback Engine.
- **Payments Gateway:** Razorpay API v1 (Payment Links, Webhooks HMAC-SHA256, Subscriptions).
- **Frontend:** React 18, Vite, TypeScript, Tailwind CSS, Framer Motion, Lucide Icons, Server-Sent Events (SSE).

---

## 9. License

This project is open source and available under the [MIT License](LICENSE).
