import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Clock,
  XCircle,
  CheckCircle2,
  RotateCw,
  Ban,
  Zap,
} from "lucide-react";
import {
  triggerRetryNow,
  cancelRetry,
  type RetryScheduleItem,
} from "../lib/api";
import { formatDateIST } from "../lib/formatters";

interface SmartRetryTimelineProps {
  schedules?: RetryScheduleItem[];
  paymentEventId: number;
  onUpdate?: () => void;
}

export function SmartRetryTimeline({
  schedules = [],
  paymentEventId,
  onUpdate,
}: SmartRetryTimelineProps) {
  const [actingId, setActingId] = useState<number | null>(null);
  const [actionMessage, setActionMessage] = useState<string | null>(null);

  const handleTrigger = async (id: number) => {
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

  const handleCancel = async (id: number) => {
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
        <span className="text-[11px] text-slate-400 font-mono">
          Event ID #{paymentEventId} · {schedules.length} Retry Windows
        </span>
      </div>

      {actionMessage && (
        <div className="p-2.5 rounded-lg bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 text-xs font-semibold flex items-center justify-between">
          <span>{actionMessage}</span>
          <button onClick={() => setActionMessage(null)} className="text-slate-400 hover:text-white">
            ✕
          </button>
        </div>
      )}

      {schedules.length === 0 ? (
        <div className="py-6 text-center text-xs text-slate-400 font-medium">
          No automated retry schedule records associated with this payment event.
        </div>
      ) : (
        <div className="space-y-2.5">
          {schedules.map((schedule, idx) => {
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
                        handleTrigger(schedule.id);
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
                        handleCancel(schedule.id);
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
    </div>
  );
}
