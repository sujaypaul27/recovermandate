import { useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Clock,
  XCircle,
  CheckCircle2,
  RotateCw,
  Ban,
  Zap,
  AlertTriangle,
} from "lucide-react";
import {
  triggerRetryNow,
  cancelRetry,
  type RetryScheduleItem,
} from "../lib/api";
import { formatDateIST, formatINR } from "../lib/formatters";
import { RetryEligibilityLegend } from "./RetryEligibilityLegend";

interface SmartRetryTimelineProps {
  schedules?: RetryScheduleItem[];
  paymentEventId: number;
  amount?: number;
  onUpdate?: () => void;
}

export function SmartRetryTimeline({
  schedules = [],
  paymentEventId,
  amount,
  onUpdate,
}: SmartRetryTimelineProps) {
  // Deduplicate schedules by attemptNumber to guarantee clean rendering
  const uniqueSchedules = Array.from(
    new Map(
      (schedules || []).map((s) => [s.attemptNumber ?? s.id, s])
    ).values()
  ).sort((a, b) => (a.attemptNumber || 0) - (b.attemptNumber || 0));

  const [actingId, setActingId] = useState<number | null>(null);
  const [actionMessage, setActionMessage] = useState<string | null>(null);
  const [confirmTrigger, setConfirmTrigger] = useState<{
    id: number;
    attemptNumber: number;
    scheduledAt: string;
    amount?: number;
    reason?: string;
  } | null>(null);
  const [confirmCancel, setConfirmCancel] = useState<{
    id: number;
    attemptNumber: number;
    scheduledAt: string;
  } | null>(null);

  const executeTrigger = async (id: number) => {
    setActingId(id);
    setActionMessage(null);
    try {
      const res = await triggerRetryNow(id);
      setActionMessage(
        `Retry attempt #${res.attemptNumber} executed immediately! Outcome: ${res.status}${
          res.razorpayRetryPaymentId ? ` (${res.razorpayRetryPaymentId})` : ""
        }`
      );
      if (onUpdate) onUpdate();
    } catch (e: any) {
      alert(`Failed to trigger retry: ${e.message}`);
    } finally {
      setActingId(null);
    }
  };

  const executeCancel = async (id: number) => {
    setActingId(id);
    setActionMessage(null);
    try {
      const res = await cancelRetry(id);
      setActionMessage(`Retry attempt #${res.attemptNumber} marked as SKIPPED.`);
      if (onUpdate) onUpdate();
    } catch (e: any) {
      alert(`Failed to cancel retry: ${e.message}`);
    } finally {
      setActingId(null);
    }
  };

  const getStatusBadge = (status: string) => {
    switch (status?.toUpperCase()) {
      case "SUCCESS":
        return (
          <Badge className="bg-emerald-500/15 text-emerald-400 border-emerald-500/30 text-[10px] font-bold uppercase tracking-wider flex items-center gap-1">
            <CheckCircle2 className="w-3 h-3" /> Recovered via Auto-Retry
          </Badge>
        );
      case "FAILED":
        return (
          <Badge className="bg-rose-500/15 text-rose-400 border-rose-500/30 text-[10px] font-bold uppercase tracking-wider flex items-center gap-1">
            <XCircle className="w-3 h-3" /> Retry Failed
          </Badge>
        );
      case "SKIPPED":
        return (
          <Badge className="bg-slate-500/15 text-slate-400 border-slate-500/30 text-[10px] font-bold uppercase tracking-wider flex items-center gap-1">
            <Ban className="w-3 h-3" /> Guard Skipped
          </Badge>
        );
      case "DEFERRED":
        return (
          <Badge className="bg-orange-500/15 text-orange-300 border-orange-500/30 text-[10px] font-bold uppercase tracking-wider flex items-center gap-1">
            <Clock className="w-3 h-3 text-orange-400" /> Deferred (Bank Outage)
          </Badge>
        );
      default: // PENDING
        return (
          <Badge className="bg-amber-500/15 text-amber-300 border-amber-500/30 text-[10px] font-bold uppercase tracking-wider flex items-center gap-1">
            <Clock className="w-3 h-3 animate-pulse text-amber-400" /> Pending Window
          </Badge>
        );
    }
  };

  const formatReason = (reason?: string) => {
    if (!reason) return "Standard Algorithmic Backoff";
    const lower = reason.toLowerCase();
    if (lower.includes("bank_outage") || lower.includes("bank_down")) {
      return "Deferred: Issuer Bank Outage / CBS Maintenance";
    }
    if (lower.includes("cbs") || lower.includes("psu")) {
      return "Avoiding PSU CBS Batch Window (11:30 PM–3:30 AM)";
    }
    if (lower.includes("salary")) {
      return "Targeting Salary Credit Window (9:00 AM–11:00 AM)";
    }
    if (lower.includes("peak") || lower.includes("upi")) {
      return "Avoiding Peak UPI Traffic Window (7:00 PM–9:30 PM)";
    }
    if (lower.includes("superseded")) {
      return "Superseded by Customer Link Settlement";
    }
    if (lower.includes("cancelled_by_support")) {
      return "Manual Support Operator Override";
    }
    if (lower.includes("bank") && lower.includes("down")) {
      return "Halted due to Issuer Downtime Guard";
    }
    return reason.replace(/_/g, " ");
  };

  return (
    <div className="rounded-xl bg-[#08182D] border border-[#3395FF]/30 p-4 space-y-3 shadow-lg">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-[#3395FF]/20 pb-2.5">
        <div className="flex items-center gap-2">
          <RotateCw className="w-4 h-4 text-[#3395FF]" />
          <h4 className="text-xs font-extrabold uppercase tracking-wider text-white">
            Smart Retry Engine Schedule Timeline
          </h4>
          <span className="text-[10px] px-2 py-0.5 rounded-full bg-[#3395FF]/20 text-[#93c5fd] font-mono font-bold">
            Banking Rail Heuristics
          </span>
        </div>
        <div className="flex items-center gap-2 flex-wrap">
          <span
            className="text-[11px] font-mono text-slate-300 bg-slate-900/80 px-2 py-0.5 rounded border border-slate-700/80 flex items-center gap-1.5 cursor-help shadow-sm"
            title="Internal Ledger Event ID: System reference key linking this automated retry plan to the intercepted payment failure event (internal tracking reference)."
          >
            <span className="text-[#3395FF] font-bold">Ledger Ref:</span>
            <span>Event #{paymentEventId}</span>
            <span className="text-[9px] text-slate-400 font-sans italic hidden sm:inline">(Internal Ref)</span>
          </span>
          <span className="text-[11px] text-slate-400 font-mono">
            · {uniqueSchedules.length} Retry Windows
          </span>
        </div>
      </div>

      {actionMessage && (
        <div className="p-2.5 rounded-lg bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 text-xs font-semibold flex items-center justify-between">
          <span>{actionMessage}</span>
          <button onClick={() => setActionMessage(null)} className="text-slate-400 hover:text-white">
            ✕
          </button>
        </div>
      )}

      {uniqueSchedules.length === 0 ? (
        <div className="py-6 text-center text-xs text-slate-400 font-medium">
          No automated retry schedule records associated with this payment event.
        </div>
      ) : (
        <div className="space-y-2.5">
          {uniqueSchedules.map((schedule, idx) => {
            const isPending = schedule.status === "PENDING";
            const isActing = actingId === schedule.id;

            return (
              <div
                key={schedule.id}
                className={`p-3 rounded-lg border transition-colors flex flex-col sm:flex-row sm:items-center justify-between gap-3 ${
                  isPending
                    ? "bg-[#0C2340]/90 border-amber-500/30"
                    : schedule.status === "SUCCESS"
                    ? "bg-[#0C2340]/70 border-emerald-500/30"
                    : "bg-[#0C2340]/50 border-slate-700/60"
                }`}
              >
                <div className="space-y-1">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="text-xs font-mono font-bold text-white">
                      Attempt #{schedule.attemptNumber || idx + 1}
                    </span>
                    {getStatusBadge(schedule.status)}
                    <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-[#02042B] text-slate-300 border border-slate-700">
                      {formatReason(schedule.scheduleReason)}
                    </span>
                  </div>

                  <div className="text-[11px] font-mono text-slate-400 flex items-center gap-2 flex-wrap">
                    <span>Scheduled: {formatDateIST(schedule.scheduledAt)}</span>
                    {schedule.executedAt && (
                      <>
                        <span>•</span>
                        <span>Executed: {formatDateIST(schedule.executedAt)}</span>
                      </>
                    )}
                    {schedule.razorpayRetryPaymentId && (
                      <>
                        <span>•</span>
                        <span className="text-emerald-400 font-bold">
                          Payment ID: {schedule.razorpayRetryPaymentId}
                        </span>
                      </>
                    )}
                  </div>
                </div>

                {/* Support Agent Overrides */}
                {isPending && (
                  <div className="flex items-center gap-2 shrink-0 self-end sm:self-center">
                    <Button
                      size="sm"
                      onClick={(e) => {
                        e.stopPropagation();
                        setConfirmTrigger({
                          id: schedule.id,
                          attemptNumber: schedule.attemptNumber || idx + 1,
                          scheduledAt: schedule.scheduledAt,
                          amount: amount,
                          reason: schedule.scheduleReason,
                        });
                      }}
                      disabled={isActing}
                      className="h-7 text-xs font-bold bg-[#3395FF] hover:bg-[#2582eb] text-white gap-1 px-2.5 shadow-md shadow-[#3395FF]/20"
                    >
                      <Zap className={`w-3 h-3 ${isActing ? "animate-spin" : ""}`} />
                      {isActing ? "Executing..." : "Trigger Now"}
                    </Button>
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={(e) => {
                        e.stopPropagation();
                        setConfirmCancel({
                          id: schedule.id,
                          attemptNumber: schedule.attemptNumber || idx + 1,
                          scheduledAt: schedule.scheduledAt,
                        });
                      }}
                      disabled={isActing}
                      className="h-7 text-xs font-bold border-rose-500/40 text-rose-300 hover:bg-rose-950/40 px-2.5"
                    >
                      <XCircle className="w-3 h-3 mr-1" />
                      Cancel Retry
                    </Button>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}

      {/* Banking Rail Retry Policy & Category Eligibility Legend */}
      <RetryEligibilityLegend className="mt-3" defaultExpanded={false} />

      {/* Confirmation Modal for Immediate Retry Trigger */}
      <AnimatePresence>
        {confirmTrigger && (
          <div
            className="fixed inset-0 z-50 bg-black/70 backdrop-blur-sm flex items-center justify-center p-4"
            onClick={() => setConfirmTrigger(null)}
          >
            <motion.div
              initial={{ scale: 0.95, opacity: 0, y: 10 }}
              animate={{ scale: 1, opacity: 1, y: 0 }}
              exit={{ scale: 0.95, opacity: 0, y: 10 }}
              onClick={(e) => e.stopPropagation()}
              className="bg-[#0C2340] border border-[#3395FF]/40 rounded-2xl p-6 max-w-md w-full shadow-2xl space-y-4 text-left"
            >
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-amber-500/20 flex items-center justify-center border border-amber-500/30 text-amber-400 shrink-0">
                  <AlertTriangle className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="text-sm font-bold text-white">Execute Immediate Mandate Retry?</h3>
                  <p className="text-xs text-slate-400">Manual Financial Action · Banking Rails Authorization</p>
                </div>
              </div>

              <p className="text-xs text-slate-300 leading-relaxed">
                This will immediately initiate an auto-debit charge against the customer's registered mandate{" "}
                {confirmTrigger.amount ? (
                  <strong className="text-emerald-400 font-bold">{formatINR(confirmTrigger.amount)}</strong>
                ) : (
                  "for the overdue invoice amount"
                )}{" "}
                via the Razorpay Recurring Gateway.
              </p>

              <div className="p-3 rounded-lg bg-[#02042B] border border-slate-700/80 text-[11px] font-mono text-slate-300 space-y-1.5">
                <div className="flex justify-between">
                  <span className="text-slate-400">Ledger Reference:</span>
                  <span className="font-bold text-white">Event #{paymentEventId}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-400">Target Attempt:</span>
                  <span className="text-[#3395FF] font-bold">Attempt #{confirmTrigger.attemptNumber}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-400">Authorized Debit:</span>
                  <span className="text-emerald-400 font-bold font-mono">
                    {confirmTrigger.amount ? formatINR(confirmTrigger.amount) : "Invoice Amount"}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-400">Scheduled Time:</span>
                  <span className="text-slate-200">{formatDateIST(confirmTrigger.scheduledAt)}</span>
                </div>
                {confirmTrigger.reason && (
                  <div className="flex justify-between">
                    <span className="text-slate-400">Strategy Window:</span>
                    <span className="text-slate-300 truncate max-w-[200px]">{formatReason(confirmTrigger.reason)}</span>
                  </div>
                )}
              </div>

              <div className="flex items-center justify-end gap-2.5 pt-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setConfirmTrigger(null)}
                  className="text-xs border-slate-700 text-slate-300 hover:bg-slate-800"
                >
                  Cancel
                </Button>
                <Button
                  size="sm"
                  onClick={() => {
                    const id = confirmTrigger.id;
                    setConfirmTrigger(null);
                    executeTrigger(id);
                  }}
                  className="text-xs font-bold bg-[#3395FF] hover:bg-[#2582eb] text-white shadow-md shadow-[#3395FF]/30 gap-1.5"
                >
                  <Zap className="w-3.5 h-3.5" />
                  Confirm & Charge Now
                </Button>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      {/* Confirmation Modal for Cancel Retry */}
      <AnimatePresence>
        {confirmCancel && (
          <div
            className="fixed inset-0 z-50 bg-black/70 backdrop-blur-sm flex items-center justify-center p-4"
            onClick={() => setConfirmCancel(null)}
          >
            <motion.div
              initial={{ scale: 0.95, opacity: 0, y: 10 }}
              animate={{ scale: 1, opacity: 1, y: 0 }}
              exit={{ scale: 0.95, opacity: 0, y: 10 }}
              onClick={(e) => e.stopPropagation()}
              className="bg-[#0C2340] border border-rose-500/40 rounded-2xl p-6 max-w-md w-full shadow-2xl space-y-4 text-left"
            >
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-rose-500/20 flex items-center justify-center border border-rose-500/30 text-rose-400 shrink-0">
                  <Ban className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="text-sm font-bold text-white">Cancel Scheduled Retry Window?</h3>
                  <p className="text-xs text-slate-400">Bypass Automated Banking Rails Attempt</p>
                </div>
              </div>

              <p className="text-xs text-slate-300 leading-relaxed">
                Are you sure you want to cancel scheduled retry{" "}
                <strong className="text-white font-bold">Attempt #{confirmCancel.attemptNumber}</strong>? This attempt will be
                marked as <code className="text-slate-200 bg-slate-800 px-1 py-0.5 rounded font-mono text-[11px]">SKIPPED</code> and will not debit the customer's account.
              </p>

              <div className="flex items-center justify-end gap-2.5 pt-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setConfirmCancel(null)}
                  className="text-xs border-slate-700 text-slate-300 hover:bg-slate-800"
                >
                  Keep Scheduled
                </Button>
                <Button
                  size="sm"
                  onClick={() => {
                    const id = confirmCancel.id;
                    setConfirmCancel(null);
                    executeCancel(id);
                  }}
                  className="text-xs font-bold bg-rose-600 hover:bg-rose-700 text-white shadow-md shadow-rose-600/30 gap-1.5"
                >
                  <XCircle className="w-3.5 h-3.5" />
                  Confirm Cancellation
                </Button>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>
    </div>
  );
}
