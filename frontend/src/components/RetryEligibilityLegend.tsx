import { useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  CheckCircle2,
  XCircle,
  AlertTriangle,
  Info,
  ChevronDown,
  ChevronUp,
  Cpu,
} from "lucide-react";

interface RetryEligibilityLegendProps {
  defaultExpanded?: boolean;
  compact?: boolean;
  className?: string;
}

interface EligibilityCategory {
  key: string;
  name: string;
  badgeLabel: string;
  badgeVariant: "eligible" | "ineligible" | "limited";
  reason: string;
  actionSummary: string;
}

const CATEGORIES: EligibilityCategory[] = [
  {
    key: "insufficient_funds",
    name: "Insufficient Funds",
    badgeLabel: "Auto-Retry Eligible",
    badgeVariant: "eligible",
    reason: "Funds may become available over the billing cycle.",
    actionSummary: "Optimally timed across 9 AM salary credit windows & non-peak hours.",
  },
  {
    key: "technical_decline",
    name: "Technical Decline",
    badgeLabel: "Auto-Retry Eligible",
    badgeVariant: "eligible",
    reason: "Temporary issuer bank CBS downtime or gateway glitch.",
    actionSummary: "Exponential backoff retries execute once banking rail health clears.",
  },
  {
    key: "expired_mandate",
    name: "Expired Mandate",
    badgeLabel: "NOT Eligible",
    badgeVariant: "ineligible",
    reason: "Mandate authentication expired or card replaced.",
    actionSummary: "Auto-retries blocked; requires customer re-auth via Mandate Swap link.",
  },
  {
    key: "unknown",
    name: "Unknown / Unclassified",
    badgeLabel: "Limited (Max 2 Attempts)",
    badgeVariant: "limited",
    reason: "Unclassified bank decline or generic error code.",
    actionSummary: "Guarded by a strict 2-attempt backoff limit to avoid merchant penalties.",
  },
];

export function RetryEligibilityLegend({
  defaultExpanded = false,
  className = "",
}: RetryEligibilityLegendProps) {
  const [isExpanded, setIsExpanded] = useState(defaultExpanded);

  const getBadge = (variant: EligibilityCategory["badgeVariant"], label: string) => {
    switch (variant) {
      case "eligible":
        return (
          <Badge className="bg-emerald-500/20 text-emerald-300 border-emerald-500/40 text-[10px] font-bold uppercase tracking-wider flex items-center gap-1">
            <CheckCircle2 className="w-3 h-3 text-emerald-400" />
            {label}
          </Badge>
        );
      case "ineligible":
        return (
          <Badge className="bg-rose-500/20 text-rose-300 border-rose-500/40 text-[10px] font-bold uppercase tracking-wider flex items-center gap-1">
            <XCircle className="w-3 h-3 text-rose-400" />
            {label}
          </Badge>
        );
      case "limited":
        return (
          <Badge className="bg-amber-500/20 text-amber-300 border-amber-500/40 text-[10px] font-bold uppercase tracking-wider flex items-center gap-1">
            <AlertTriangle className="w-3 h-3 text-amber-400" />
            {label}
          </Badge>
        );
    }
  };

  return (
    <div
      className={`rounded-xl border border-[#3395FF]/30 bg-[#061530]/90 backdrop-blur-md shadow-lg transition-all ${className}`}
    >
      {/* Header Bar */}
      <div
        onClick={() => setIsExpanded(!isExpanded)}
        className="px-4 py-3 flex items-center justify-between cursor-pointer select-none hover:bg-[#3395FF]/10 transition-colors rounded-xl"
      >
        <div className="flex items-center gap-2.5">
          <div className="w-6 h-6 rounded-md bg-[#02042B] border border-[#3395FF]/40 flex items-center justify-center text-[#3395FF]">
            <Cpu className="w-3.5 h-3.5" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h4 className="text-xs font-bold text-white tracking-wide uppercase">
                Auto-Retry Category Eligibility Matrix
              </h4>
              <span className="text-[10px] font-mono px-2 py-0.2 rounded-full bg-[#3395FF]/20 text-[#93c5fd] border border-[#3395FF]/30 hidden sm:inline-block">
                Smart Engine Policy
              </span>
            </div>
            <p className="text-[11px] text-slate-400">
              Rules governing which failed mandates are automatically retried vs routed to human intervention.
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <Button
            variant="ghost"
            size="sm"
            className="h-7 px-2 text-xs text-[#93c5fd] hover:text-white hover:bg-[#3395FF]/20"
          >
            {isExpanded ? (
              <>
                <span className="hidden sm:inline mr-1 text-[11px]">Hide Rules</span>
                <ChevronUp className="w-4 h-4" />
              </>
            ) : (
              <>
                <span className="hidden sm:inline mr-1 text-[11px]">View Rules</span>
                <ChevronDown className="w-4 h-4" />
              </>
            )}
          </Button>
        </div>
      </div>

      {/* Collapsible Content */}
      <AnimatePresence initial={false}>
        {isExpanded && (
          <motion.div
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: "auto" }}
            exit={{ opacity: 0, height: 0 }}
            transition={{ duration: 0.2 }}
            className="overflow-hidden border-t border-[#3395FF]/20"
          >
            <div className="p-4 grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-3 bg-[#02042B]/60">
              {CATEGORIES.map((cat) => (
                <div
                  key={cat.key}
                  className={`p-3 rounded-lg border flex flex-col justify-between space-y-2.5 transition-all ${
                    cat.badgeVariant === "eligible"
                      ? "bg-[#0C2340]/90 border-emerald-500/30 hover:border-emerald-500/50"
                      : cat.badgeVariant === "ineligible"
                      ? "bg-[#0C2340]/90 border-rose-500/30 hover:border-rose-500/50"
                      : "bg-[#0C2340]/90 border-amber-500/30 hover:border-amber-500/50"
                  }`}
                >
                  <div className="space-y-1.5">
                    <div className="flex items-center justify-between gap-1 flex-wrap">
                      <span className="font-mono text-[11px] font-bold text-white tracking-tight">
                        {cat.key}
                      </span>
                      {getBadge(cat.badgeVariant, cat.badgeLabel)}
                    </div>
                    <p className="text-xs font-semibold text-slate-200">{cat.name}</p>
                    <p className="text-[11px] text-slate-300 leading-snug">
                      {cat.reason}
                    </p>
                  </div>

                  <div className="pt-2 border-t border-slate-700/60 text-[10px] font-mono text-slate-400">
                    <span className="text-[#93c5fd] font-sans font-medium">Policy: </span>
                    {cat.actionSummary}
                  </div>
                </div>
              ))}
            </div>

            {/* Footer Guidance */}
            <div className="px-4 py-2 bg-[#02042B] border-t border-[#3395FF]/10 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-2 text-[10px] text-slate-400 font-mono">
              <span className="flex items-center gap-1 text-[#93c5fd]">
                <Info className="w-3.5 h-3.5 text-[#3395FF]" />
                Deterministic Guardrails: Ineligible mandates automatically bypass the retry scheduler and route directly to Approval Queue.
              </span>
              <span className="text-slate-500">NPCI / RBI e-Mandate Guard v2.4</span>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
