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

> **Note on webhooks and ngrok:** This project includes a built-in demo simulator that replicates real Razorpay webhook payloads locally, so you can experience the full recovery pipeline (classification, retries, AI drafts, payment links) without exposing your machine to the internet. **You do not need ngrok to try the demo.**
>
> If you want to test genuinely live Razorpay webhook delivery (real payments hitting your local server instead of the simulator), you'll need a tool like [ngrok](https://ngrok.com) to expose your local port 8080 with a public HTTPS URL, which you then register in your Razorpay Dashboard's webhook settings. This is optional and only needed for live payment testing, not for running or judging the core project.

### Summary of Environment Variables

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
