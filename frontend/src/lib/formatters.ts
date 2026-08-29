/**
 * Shared formatting utilities for currency, dates, and times across RecoverMandate.
 */

/**
 * Formats an amount in paise into Indian Rupee (INR) currency string.
 * Example: 250000 -> "₹2,500.00", 99900 -> "₹999.00"
 */
export function formatINR(paise: number | undefined | null, includeDecimals = true): string {
  if (paise == null || isNaN(paise)) {
    return includeDecimals ? "₹0.00" : "₹0";
  }

  const rupees = paise / 100;
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    minimumFractionDigits: includeDecimals ? 2 : 0,
    maximumFractionDigits: includeDecimals ? 2 : 0,
  }).format(rupees);
}

/**
 * Formats a timestamp into a human-readable Indian Standard Time (IST) string.
 * Example: "2026-08-29T16:05:00Z" -> "29 Aug 2026, 09:35 PM IST"
 */
export function formatDateIST(dateInput: string | number | Date | undefined | null): string {
  if (!dateInput) return "N/A";

  try {
    const d = new Date(dateInput);
    if (isNaN(d.getTime())) return "Invalid Date";

    const formatted = new Intl.DateTimeFormat("en-IN", {
      timeZone: "Asia/Kolkata",
      day: "2-digit",
      month: "short",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
      hour12: true,
    }).format(d);

    return `${formatted} IST`;
  } catch {
    return String(dateInput);
  }
}

/**
 * Formats a timestamp into a compact IST time string.
 * Example: "2026-08-29T16:05:00Z" -> "09:35:00 PM"
 */
export function formatTimeIST(dateInput: string | number | Date | undefined | null): string {
  if (!dateInput) return "N/A";

  try {
    const d = new Date(dateInput);
    if (isNaN(d.getTime())) return "Invalid Date";

    return new Intl.DateTimeFormat("en-IN", {
      timeZone: "Asia/Kolkata",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
      hour12: true,
    }).format(d);
  } catch {
    return String(dateInput);
  }
}

/**
 * Returns human-readable relative time or fallback to formatted date.
 */
export function formatRelativeTime(dateInput: string | number | Date | undefined | null): string {
  if (!dateInput) return "N/A";

  try {
    const d = new Date(dateInput);
    const diffMs = Date.now() - d.getTime();
    const diffMins = Math.floor(diffMs / (1000 * 60));
    const diffHours = Math.floor(diffMins / 60);
    const diffDays = Math.floor(diffHours / 24);

    if (diffMins < 1) return "Just now";
    if (diffMins < 60) return `${diffMins}m ago`;
    if (diffHours < 24) return `${diffHours}h ago`;
    if (diffDays < 7) return `${diffDays}d ago`;
    return formatDateIST(dateInput);
  } catch {
    return String(dateInput);
  }
}
