/**
 * Human-readable status mapping and audit log summaries for non-technical users
 * (merchants, support agents, reviewers).
 */

export interface StatusInfo {
  label: string;
  description: string;
  badgeClass: string;
}

export interface AuditActionInfo {
  label: string;
  plainDescription: string;
  category: "ingestion" | "ai" | "approval" | "dispatch" | "retry" | "payment" | "dlq" | "system";
  badgeClass: string;
}

export const STATUS_MAP: Record<string, StatusInfo> = {
  UNCLASSIFIED: {
    label: "New — Awaiting Classification",
    description: "Failure event intercepted, queued for rule engine triage",
    badgeClass: "bg-slate-500/15 text-slate-400 border-slate-500/30",
  },
  PENDING_DRAFT: {
    label: "Analyzing Failure",
    description: "Classifier triaging failure code against bank downtime & dunning rules",
    badgeClass: "bg-cyan-500/15 text-cyan-300 border-cyan-500/30",
  },
  DRAFTED: {
    label: "AI Draft Ready for Review",
    description: "Recovery message generated and waiting for human approval or Auto-Pilot",
    badgeClass: "bg-amber-500/15 text-amber-300 border-amber-500/30",
  },
  APPROVED: {
    label: "Approved — Queued for Send",
    description: "Recovery draft approved by operator, link generated for dispatch",
    badgeClass: "bg-indigo-500/15 text-indigo-300 border-indigo-500/30",
  },
  DISPATCHED: {
    label: "Recovery Message Sent",
    description: "Razorpay payment link sent to customer via Email/WhatsApp/SMS",
    badgeClass: "bg-blue-500/15 text-blue-300 border-blue-500/30",
  },
  RECOVERED: {
    label: "Payment Recovered ✅",
    description: "Customer completed payment or mandate re-authorized successfully",
    badgeClass: "bg-emerald-500/15 text-emerald-300 border-emerald-500/30 font-bold",
  },
  AUTO_RECOVERED: {
    label: "Auto-Recovered (Smart Retries) ✅",
    description: "Payment auto-debited successfully during optimal banking window",
    badgeClass: "bg-emerald-500/15 text-emerald-300 border-emerald-500/30 font-bold",
  },
  BLOCKED: {
    label: "AI Draft Blocked (Safety Filter)",
    description: "Draft contained unsafe terms or amount mismatch and was blocked by guardrails",
    badgeClass: "bg-rose-500/15 text-rose-300 border-rose-500/30",
  },
  REJECTED: {
    label: "Draft Rejected",
    description: "Support operator rejected draft with feedback for model retraining",
    badgeClass: "bg-slate-500/15 text-slate-400 border-slate-500/30",
  },
  EXPIRED: {
    label: "Payment Link Expired",
    description: "Hosted recovery link reached expiration time without payment",
    badgeClass: "bg-amber-500/15 text-amber-400 border-amber-500/30",
  },
  SUPERSEDED: {
    label: "Superseded by Mandate Retry",
    description: "Payment link deactivated because auto-retry succeeded first",
    badgeClass: "bg-purple-500/15 text-purple-300 border-purple-500/30",
  },
  SUPERSEDED_BY_LINK_PAYMENT: {
    label: "Superseded by Link Payment",
    description: "Pending retries cancelled because customer paid via link first",
    badgeClass: "bg-purple-500/15 text-purple-300 border-purple-500/30",
  },
  PENDING: {
    label: "Scheduled (Awaiting Window)",
    description: "Queued for automatic re-attempt at calculated optimal timestamp",
    badgeClass: "bg-amber-500/15 text-amber-300 border-amber-500/30",
  },
  SUCCESS: {
    label: "Executed Successfully ✅",
    description: "Transaction successfully debited and settled",
    badgeClass: "bg-emerald-500/15 text-emerald-300 border-emerald-500/30",
  },
  SKIPPED: {
    label: "Skipped / Cancelled",
    description: "Retry attempt bypassed to avoid duplicate charge",
    badgeClass: "bg-slate-500/15 text-slate-400 border-slate-500/30",
  },
  FAILED: {
    label: "Failed Attempt",
    description: "Transaction attempt declined by issuer bank",
    badgeClass: "bg-rose-500/15 text-rose-300 border-rose-500/30",
  },
  DEFERRED: {
    label: "Deferred (Bank Outage)",
    description: "Retry postponed to preserve attempt quota while issuer bank is down",
    badgeClass: "bg-orange-500/15 text-orange-300 border-orange-500/30",
  },
};

export function getStatusConfig(rawStatus?: string): StatusInfo {
  if (!rawStatus) {
    return STATUS_MAP.UNCLASSIFIED;
  }
  const key = rawStatus.toUpperCase().trim();
  return (
    STATUS_MAP[key] || {
      label: rawStatus.replace(/_/g, " "),
      description: "State transition recorded in RecoverMandate engine",
      badgeClass: "bg-slate-500/15 text-slate-300 border-slate-500/30",
    }
  );
}

export const AUDIT_ACTION_MAP: Record<string, AuditActionInfo> = {
  WEBHOOK_INGESTED: {
    label: "Webhook Ingested",
    plainDescription: "System received and verified a payment failure event notification from Razorpay",
    category: "ingestion",
    badgeClass: "bg-blue-500/15 text-blue-300 border-blue-500/30",
  },
  DUPLICATE_WEBHOOK_SKIPPED: {
    label: "Duplicate Webhook Skipped",
    plainDescription: "Duplicate webhook payload detected and safely ignored (idempotent protection)",
    category: "ingestion",
    badgeClass: "bg-slate-500/15 text-slate-400 border-slate-500/30",
  },
  FAILURE_CLASSIFIED: {
    label: "Failure Classified",
    plainDescription: "AI & rule engine classified error code, bank context, and determined recoverability",
    category: "ai",
    badgeClass: "bg-purple-500/15 text-purple-300 border-purple-500/30",
  },
  AI_DRAFT_GENERATED: {
    label: "AI Draft Generated",
    plainDescription: "AI crafted a personalized dunning message tailored to customer & failure context",
    category: "ai",
    badgeClass: "bg-purple-500/15 text-purple-300 border-purple-500/30",
  },
  AI_DRAFT_BLOCKED: {
    label: "AI Draft Blocked",
    plainDescription: "Automated guardrails blocked draft due to policy or amount verification check",
    category: "ai",
    badgeClass: "bg-rose-500/15 text-rose-300 border-rose-500/30",
  },
  AI_DRAFT_FAILED: {
    label: "AI Draft Fallback",
    plainDescription: "AI service unreachable; system generated a deterministic heuristic fallback draft",
    category: "ai",
    badgeClass: "bg-amber-500/15 text-amber-300 border-amber-500/30",
  },
  RECOVERY_ACTION_APPROVED: {
    label: "Action Approved",
    plainDescription: "Support agent reviewed and approved the recovery message draft",
    category: "approval",
    badgeClass: "bg-indigo-500/15 text-indigo-300 border-indigo-500/30",
  },
  RECOVERY_ACTION_REJECTED: {
    label: "Action Rejected",
    plainDescription: "Support agent rejected the recovery draft and logged feedback for model tuning",
    category: "approval",
    badgeClass: "bg-slate-500/15 text-slate-400 border-slate-500/30",
  },
  AUTO_PILOT_DISPATCHED: {
    label: "Auto-Pilot Dispatched",
    plainDescription: "Auto-Pilot verified safety criteria and automatically approved & dispatched recovery",
    category: "dispatch",
    badgeClass: "bg-emerald-500/15 text-emerald-300 border-emerald-500/30",
  },
  RECOVERY_DISPATCHED: {
    label: "Recovery Dispatched",
    plainDescription: "Recovery notification with Razorpay payment link sent to customer (Email/SMS/WhatsApp)",
    category: "dispatch",
    badgeClass: "bg-blue-500/15 text-blue-300 border-blue-500/30",
  },
  PAYMENT_LINK_CREATED: {
    label: "Payment Link Created",
    plainDescription: "Razorpay 1-Click Hosted Recovery Payment Link generated and linked to mandate",
    category: "payment",
    badgeClass: "bg-cyan-500/15 text-cyan-300 border-cyan-500/30",
  },
  PAYMENT_LINK_PAID: {
    label: "Payment Link Paid",
    plainDescription: "Customer successfully paid invoice via hosted link — mandate recovered",
    category: "payment",
    badgeClass: "bg-emerald-500/15 text-emerald-300 border-emerald-500/30",
  },
  PAYMENT_RECOVERED: {
    label: "Payment Recovered",
    plainDescription: "Customer successfully paid overdue amount — recurring mandate restored",
    category: "payment",
    badgeClass: "bg-emerald-500/15 text-emerald-300 border-emerald-500/30",
  },
  PAYMENT_LINK_EXPIRED: {
    label: "Payment Link Expired",
    plainDescription: "Razorpay recovery link reached expiration time without customer completing checkout",
    category: "payment",
    badgeClass: "bg-amber-500/15 text-amber-400 border-amber-500/30",
  },
  PAYMENT_LINK_SUPERSEDED: {
    label: "Payment Link Superseded",
    plainDescription: "Payment link cancelled via Razorpay API because mandate auto-retry succeeded first",
    category: "payment",
    badgeClass: "bg-purple-500/15 text-purple-300 border-purple-500/30",
  },
  RETRY_SCHEDULED: {
    label: "Retry Scheduled",
    plainDescription: "Smart Retry Engine scheduled an auto-debit attempt avoiding PSU CBS & downtime windows",
    category: "retry",
    badgeClass: "bg-cyan-500/15 text-cyan-300 border-cyan-500/30",
  },
  RETRY_EXECUTED: {
    label: "Retry Executed",
    plainDescription: "Scheduled automatic auto-debit retry was executed on the banking rails",
    category: "retry",
    badgeClass: "bg-blue-500/15 text-blue-300 border-blue-500/30",
  },
  RETRY_DEFERRED_BANK_OUTAGE: {
    label: "Retry Deferred (Bank Down)",
    plainDescription: "Retry attempt deferred by 60 min because issuer bank is undergoing maintenance",
    category: "retry",
    badgeClass: "bg-orange-500/15 text-orange-300 border-orange-500/30",
  },
  RETRY_CANCELLED_ALREADY_PAID: {
    label: "Retry Cancelled (Already Paid)",
    plainDescription: "Scheduled retry cancelled to prevent double-charging (customer already paid via link)",
    category: "retry",
    badgeClass: "bg-purple-500/15 text-purple-300 border-purple-500/30",
  },
  RETRY_CANCELLED_MAX_ATTEMPTS: {
    label: "Retry Max Limit Reached",
    plainDescription: "Retry series concluded after reaching max configured retry attempts",
    category: "retry",
    badgeClass: "bg-slate-500/15 text-slate-400 border-slate-500/30",
  },
  RETRY_MANUALLY_TRIGGERED: {
    label: "Manual Retry Triggered",
    plainDescription: "Support agent manually triggered an immediate retry attempt for this mandate",
    category: "retry",
    badgeClass: "bg-amber-500/15 text-amber-300 border-amber-500/30",
  },
  RETRY_MANUALLY_CANCELLED: {
    label: "Manual Retry Cancelled",
    plainDescription: "Support agent manually cancelled a pending retry schedule",
    category: "retry",
    badgeClass: "bg-slate-500/15 text-slate-400 border-slate-500/30",
  },
  DLQ_EVENT_STORED: {
    label: "Quarantined in DLQ",
    plainDescription: "Malformed or signature-failed webhook safely stored in DLQ for forensic inspection",
    category: "dlq",
    badgeClass: "bg-rose-500/15 text-rose-300 border-rose-500/30",
  },
  DLQ_EVENT_REPLAYED: {
    label: "DLQ Event Replayed",
    plainDescription: "Support agent replayed a quarantined Dead Letter Queue webhook into the pipeline",
    category: "dlq",
    badgeClass: "bg-emerald-500/15 text-emerald-300 border-emerald-500/30",
  },
  MERCHANT_SETTINGS_UPDATED: {
    label: "Settings Updated",
    plainDescription: "Merchant updated auto-pilot thresholds, tone preferences, or business branding",
    category: "system",
    badgeClass: "bg-slate-500/15 text-slate-300 border-slate-500/30",
  },
};

export function getAuditActionConfig(rawAction?: string, rawReasoning?: string): AuditActionInfo {
  if (!rawAction) {
    return {
      label: "System Event",
      plainDescription: rawReasoning || "System recorded an audit trail event",
      category: "system",
      badgeClass: "bg-slate-500/15 text-slate-400 border-slate-500/30",
    };
  }
  const key = rawAction.toUpperCase().trim();
  const found = AUDIT_ACTION_MAP[key];
  if (found) {
    return found;
  }
  return {
    label: rawAction.replace(/_/g, " "),
    plainDescription: rawReasoning || `System recorded state change: ${rawAction}`,
    category: "system",
    badgeClass: "bg-slate-500/15 text-slate-300 border-slate-500/30",
  };
}
