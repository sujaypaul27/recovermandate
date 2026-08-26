export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080/api";
const API_KEY = import.meta.env.VITE_API_KEY || "default-dev-key";

const getHeaders = (additionalHeaders: Record<string, string> = {}) => ({
  "X-API-Key": API_KEY,
  ...additionalHeaders,
});

export async function fetchDashboardSummary() {
  const res = await fetch(`${API_BASE_URL}/dashboard/summary`, {
    headers: getHeaders(),
  });
  if (!res.ok) throw new Error("Failed to fetch dashboard summary");
  return res.json();
}

export async function fetchPaymentEvents(page = 0, size = 20, status?: string) {
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

export async function fetchRecoveryActions(page = 0, size = 20, status?: string) {
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

export async function fetchAuditLogs(page = 0, size = 20) {
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
