export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080/api";
export const API_KEY = import.meta.env.VITE_API_KEY || "default-dev-key";

const getHeaders = (additionalHeaders: Record<string, string> = {}) => ({
  "X-API-Key": API_KEY,
  ...additionalHeaders,
});

export interface DashboardSummary {
  recoveredAmount: number;
  failedCount: number;
  pendingApprovalsCount: number;
  blockedDraftsCount: number;
  totalPaymentsProcessed: number;
  successfulPaymentsCount: number;
  successRate: number;
  avgResolutionTimeMinutes: number;
  failuresByCategory: Record<string, number>;
  draftsGenerated: number;
  draftsApproved: number;
  messagesDispatched: number;
  paymentsRecovered: number;
  recoveryRate: number;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

export interface RetryScheduleItem {
  id: number;
  attemptNumber: number;
  scheduledAt: string;
  executedAt?: string;
  status: "PENDING" | "SUCCESS" | "FAILED" | "SKIPPED";
  scheduleReason?: string;
  failureCategory?: string;
  razorpayRetryPaymentId?: string;
}

export interface PaymentEventItem {
  id: number;
  razorpayPaymentId: string;
  razorpaySubscriptionId?: string;
  customerEmail?: string;
  customerPhone?: string;
  eventType: string;
  amount: number;
  currency?: string;
  status?: string;
  failureReasonCode?: string;
  failureCategory?: string;
  autoRecoverable?: boolean;
  retryCount?: number;
  createdAt?: string;
  classificationCategory?: string;
  classificationStatus?: string;
  errorReason?: string;
  retrySchedules?: RetryScheduleItem[];
}

export interface RecoveryActionItem {
  id: number;
  failureClassificationId?: number;
  paymentEventId?: number;
  razorpayPaymentId?: string;
  category?: string;
  rawErrorCode?: string;
  bank?: string;
  autoRecoverable?: boolean;
  matchedRule?: string;
  amount?: number;
  customerEmail?: string;
  aiDraftMessage?: string;
  draftSource?: string;
  paymentLinkUrl?: string;
  status: string;
  approvedBy?: string;
  approvedAt?: string;
  sentAt?: string;
  createdAt: string;
  actor: string;
  tone?: string;
}

export interface AuditLogItem {
  id: number;
  entityType: string;
  entityId: number;
  action: string;
  actor: string;
  details: string;
  traceId?: string;
  timestamp: string;
  checksum: string;
  aiModelUsed?: string;
}

export async function fetchDashboardSummary(): Promise<DashboardSummary> {
  const res = await fetch(`${API_BASE_URL}/dashboard/summary`, {
    headers: getHeaders(),
  });
  if (!res.ok) throw new Error("Failed to fetch dashboard summary");
  return res.json();
}

export async function fetchPaymentEvents(page = 0, size = 20, status?: string): Promise<PageResponse<PaymentEventItem>> {
  const url = new URL(`${API_BASE_URL}/payment-events`);
  url.searchParams.append("page", page.toString());
  url.searchParams.append("size", size.toString());
  if (status) {
    url.searchParams.append("status", status);
  }
  const res = await fetch(url.toString(), { headers: getHeaders() });
  if (!res.ok) throw new Error("Failed to fetch payment events");
  return res.json();
}

export async function fetchRecoveryActions(page = 0, size = 20, status?: string): Promise<PageResponse<RecoveryActionItem>> {
  const url = new URL(`${API_BASE_URL}/recovery-actions`);
  url.searchParams.append("page", page.toString());
  url.searchParams.append("size", size.toString());
  if (status) {
    url.searchParams.append("status", status);
  }
  const res = await fetch(url.toString(), { headers: getHeaders() });
  if (!res.ok) throw new Error("Failed to fetch recovery actions");
  return res.json();
}

export async function fetchAuditLogs(page = 0, size = 20): Promise<PageResponse<AuditLogItem>> {
  const url = new URL(`${API_BASE_URL}/audit-log`);
  url.searchParams.append("page", page.toString());
  url.searchParams.append("size", size.toString());
  const res = await fetch(url.toString(), { headers: getHeaders() });
  if (!res.ok) throw new Error("Failed to fetch audit logs");
  return res.json();
}

export async function approveRecoveryAction(id: number) {
  const res = await fetch(`${API_BASE_URL}/recovery-actions/${id}/approve`, {
    method: "POST",
    headers: getHeaders(),
  });
  if (!res.ok) {
    if (res.status === 404) throw new Error("Recovery Action not found");
    if (res.status === 409) throw new Error("Recovery Action is not in DRAFTED state (Conflict)");
    throw new Error("Failed to approve recovery action");
  }
}

export async function approveAndDispatchRecoveryAction(id: number, tone?: string, message?: string) {
  const res = await fetch(`${API_BASE_URL}/recovery-actions/${id}/approve-and-dispatch`, {
    method: "POST",
    headers: getHeaders({
      "Content-Type": "application/json",
    }),
    body: JSON.stringify({
      tone: tone || "balanced",
      message: message || null,
      approvedBy: "HUMAN",
    }),
  });
  if (!res.ok) {
    if (res.status === 404) throw new Error("Recovery Action not found");
    if (res.status === 409) throw new Error("Recovery Action is not in DRAFTED state (Conflict)");
    throw new Error("Failed to approve and dispatch recovery action");
  }
}

export async function rejectRecoveryAction(id: number, reason: string) {
  const res = await fetch(`${API_BASE_URL}/recovery-actions/${id}/reject`, {
    method: "POST",
    headers: getHeaders({
      "Content-Type": "application/json",
    }),
    body: JSON.stringify({ reason }),
  });
  if (!res.ok) {
    if (res.status === 400) throw new Error("Validation Error: Reason is required");
    if (res.status === 404) throw new Error("Recovery Action not found");
    if (res.status === 409) throw new Error("Recovery Action is not in DRAFTED state (Conflict)");
    throw new Error("Failed to reject recovery action");
  }
}

export async function fetchSystemHealth() {
  try {
    const res = await fetch(`${API_BASE_URL}/health/detailed`, {
      headers: getHeaders(),
    });
    if (!res.ok) throw new Error(`Health check returned status ${res.status}`);
    return await res.json();
  } catch (error) {
    return {
      status: "DEGRADED",
      geminiApi: { status: "UNKNOWN", model: "gemini-3.5-flash-lite" },
      database: { status: "UNKNOWN" },
      timestamp: new Date().toISOString(),
    };
  }
}

export async function searchGlobal(query: string) {
  if (!query || !query.trim()) return [];
  try {
    const res = await fetch(`${API_BASE_URL}/search?q=${encodeURIComponent(query.trim())}`, {
      headers: getHeaders(),
    });
    if (!res.ok) {
      // Endpoint may not exist yet or return 404 — fallback safely
      return [];
    }
    return await res.json();
  } catch {
    return [];
  }
}

export interface AuditChainVerification {
  valid: boolean;
  chainLength: number;
  brokenAtId: number | null;
  message: string;
}

export async function verifyAuditChain(): Promise<AuditChainVerification> {
  const res = await fetch(`${API_BASE_URL}/audit-log/verify-chain`, {
    headers: getHeaders(),
  });
  if (!res.ok) throw new Error("Failed to verify cryptographic audit chain");
  return res.json();
}

export async function simulateFailure(params?: {
  category?: string;
  amount?: number;
  customerName?: string;
  customerEmail?: string;
  bankCode?: string;
}) {
  const res = await fetch(`${API_BASE_URL}/demo/simulate-failure`, {
    method: "POST",
    headers: getHeaders({ "Content-Type": "application/json" }),
    body: JSON.stringify(params || {}),
  });
  if (!res.ok) throw new Error("Failed to simulate mandate failure");
  return res.json();
}

export async function simulatePaymentPaid(params?: {
  paymentLinkId?: string;
  actionId?: number;
  amount?: number;
}) {
  const url = new URL(`${API_BASE_URL}/demo/simulate-payment-paid`);
  if (params?.paymentLinkId) url.searchParams.append("paymentLinkId", params.paymentLinkId);
  if (params?.actionId) url.searchParams.append("actionId", params.actionId.toString());
  if (params?.amount) url.searchParams.append("amount", params.amount.toString());

  const res = await fetch(url.toString(), {
    method: "POST",
    headers: getHeaders(),
  });
  if (!res.ok) throw new Error("Failed to simulate payment link payment");
  return res.json();
}

export async function simulateFullFlow(params?: { category?: string; amount?: number }) {
  const res = await fetch(`${API_BASE_URL}/demo/simulate-full-flow`, {
    method: "POST",
    headers: getHeaders({ "Content-Type": "application/json" }),
    body: JSON.stringify(params || {}),
  });
  if (!res.ok) throw new Error("Failed to simulate end-to-end recovery flow");
  return res.json();
}

export async function exportRecoveryLedgerCsv(): Promise<void> {
  const res = await fetch(`${API_BASE_URL}/dashboard/export-csv`, {
    headers: getHeaders(),
  });
  if (!res.ok) throw new Error("Failed to export recovery ledger CSV");
  const blob = await res.blob();
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = "recovermandate-recovery-ledger.csv";
  document.body.appendChild(a);
  a.click();
  window.URL.revokeObjectURL(url);
  document.body.removeChild(a);
}

export interface MerchantSettings {
  defaultTone: string;
  autoPilotEnabled: boolean;
  autoPilotMaxAmount: number; // in paise
  autoPilotAllowedCategories: string;
  businessDisplayName: string;
  updatedAt?: string;
}

export async function fetchMerchantSettings(): Promise<MerchantSettings> {
  const res = await fetch(`${API_BASE_URL}/settings`, {
    headers: getHeaders(),
  });
  if (!res.ok) throw new Error("Failed to fetch merchant settings");
  return res.json();
}

export async function updateMerchantSettings(settings: Partial<MerchantSettings>): Promise<MerchantSettings> {
  const res = await fetch(`${API_BASE_URL}/settings`, {
    method: "PUT",
    headers: getHeaders({ "Content-Type": "application/json" }),
    body: JSON.stringify(settings),
  });
  if (!res.ok) throw new Error("Failed to update merchant settings");
  return res.json();
}

export interface BatchApproveResponse {
  totalRequested: number;
  successful: number;
  failed: number;
  approvedActionIds: number[];
  errors: { actionId: number; errorMessage: string }[];
}

export async function batchApproveRecoveryActions(params: {
  actionIds?: number[];
  maxAmount?: number;
  tone?: string;
  approvedBy?: string;
}): Promise<BatchApproveResponse> {
  const res = await fetch(`${API_BASE_URL}/recovery-actions/batch-approve`, {
    method: "POST",
    headers: getHeaders({ "Content-Type": "application/json" }),
    body: JSON.stringify(params),
  });
  if (!res.ok) throw new Error("Failed to execute batch approval");
  return res.json();
}

export interface WebhookDlqItem {
  id: number;
  payload: string;
  headers?: string;
  errorMessage?: string;
  status: "REJECTED" | "REPLAYED";
  createdAt: string;
  replayedAt?: string;
}

export async function fetchWebhookDlq(): Promise<WebhookDlqItem[]> {
  const res = await fetch(`${API_BASE_URL}/webhooks/dlq`, {
    headers: getHeaders(),
  });
  if (!res.ok) throw new Error("Failed to fetch webhook DLQ items");
  return res.json();
}

export async function replayWebhookDlq(id: number): Promise<{ message: string; eventId?: number; status: string; dlqId: number }> {
  const res = await fetch(`${API_BASE_URL}/webhooks/dlq/${id}/replay`, {
    method: "POST",
    headers: getHeaders(),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "Replay failed" }));
    throw new Error(err.error || "Failed to replay webhook");
  }
  return res.json();
}

export async function triggerRetryNow(retryId: number): Promise<RetryScheduleItem> {
  const res = await fetch(`${API_BASE_URL}/retries/${retryId}/trigger-now`, {
    method: "POST",
    headers: getHeaders(),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "Failed to trigger retry" }));
    throw new Error(err.error || "Failed to trigger retry");
  }
  return res.json();
}

export async function cancelRetry(retryId: number): Promise<RetryScheduleItem> {
  const res = await fetch(`${API_BASE_URL}/retries/${retryId}/cancel`, {
    method: "POST",
    headers: getHeaders(),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "Failed to cancel retry" }));
    throw new Error(err.error || "Failed to cancel retry");
  }
  return res.json();
}

export interface CheckoutDetails {
  linkId: string;
  paymentEventId?: number;
  amount: number;
  currency: string;
  customerName: string;
  customerEmail: string;
  merchantName: string;
  planName: string;
  failureCategory?: string;
  failureReason?: string;
  aiExplanation?: string;
  status: "CREATED" | "PAID" | "EXPIRED" | "SUPERSEDED";
  expireBy?: string;
  shortUrl?: string;
}

export async function fetchCheckoutDetails(linkId: string): Promise<CheckoutDetails> {
  const res = await fetch(`${API_BASE_URL}/checkout/${linkId}`, {
    headers: getHeaders(),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "Checkout not found" }));
    throw new Error(err.error || "Failed to load checkout details");
  }
  return res.json();
}

export async function simulateCheckoutPayment(linkId: string): Promise<{
  status: string;
  paymentId: string;
  message: string;
  amount: number;
  paidAt: string;
}> {
  const res = await fetch(`${API_BASE_URL}/checkout/${linkId}/pay`, {
    method: "POST",
    headers: getHeaders(),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: "Payment failed" }));
    throw new Error(err.error || "Failed to simulate payment");
  }
  return res.json();
}
