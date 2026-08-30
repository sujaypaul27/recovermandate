import { useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import {
  Smartphone,
  Server,
  CreditCard,
  Building2,
  AlertOctagon,
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  Sparkles,
  Bot,
  ArrowRight,
  Database,
  Info,
} from "lucide-react";

interface TransactionFlowDiagramProps {
  failurePoint?: string;
  category?: string;
  failureReasonCode?: string;
  diagnosis?: string;
  autoRecoverable?: boolean;
}

export function TransactionFlowDiagram({
  failurePoint = "bank",
  category,
  failureReasonCode,
  diagnosis,
  autoRecoverable,
}: TransactionFlowDiagramProps) {
  const [showDiagnosis, setShowDiagnosis] = useState(true);

  // Normalize failure point to one of the 4 nodes
  const getFailingNodeKey = () => {
    const cat = (category || "").toLowerCase();
    const reason = (failureReasonCode || "").toLowerCase();
    const fp = (failurePoint || "").toLowerCase();

    if (cat.includes("insufficient") || fp.includes("customer") || reason.includes("balance")) {
      return "customer";
    }
    if (cat.includes("expired") || fp.includes("merchant") || reason.includes("auth")) {
      return "merchant";
    }
    if (fp.includes("gateway") || reason.includes("gateway") || reason.includes("timeout")) {
      return "gateway";
    }
    return "bank"; // Default to issuer / NPCI
  };

  const failingKey = getFailingNodeKey();

  const nodes = [
    {
      key: "customer",
      label: "Customer Account",
      sub: "UPI / Mandate",
      icon: Smartphone,
      failureDescription: "Payer bank account lacked sufficient liquidity or UPI user authorization failed.",
      healthyDescription: "Customer mandate authorization was active and presented correctly.",
    },
    {
      key: "merchant",
      label: "Merchant Backend",
      sub: "Subscription Sync",
      icon: Server,
      failureDescription: "Merchant subscription authorization expired or recurring schedule misconfigured.",
      healthyDescription: "Merchant backend instruction sent with valid HMAC signature and plan ID.",
    },
    {
      key: "gateway",
      label: "Razorpay Gateway",
      sub: "Webhook Dispatch",
      icon: CreditCard,
      failureDescription: "Gateway bridge timeout, throttled API, or unrecoverable gateway processing fault.",
      healthyDescription: "Razorpay payment gateway received and routed the recurring debit webhook properly.",
    },
    {
      key: "bank",
      label: "NPCI / Issuer Bank",
      sub: "Core Banking Switch",
      icon: Building2,
      failureDescription: "Issuer Bank Core Banking Switch (CBS) maintenance, downtime, or NPCI network drop.",
      healthyDescription: "Inter-bank clearing and NPCI network switch passed the debit packet.",
    },
  ];

  return (
    <div className="rounded-xl bg-slate-900/90 border border-slate-700/60 p-4 sm:p-5 text-white shadow-2xl backdrop-blur-md">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 mb-4">
        <div className="flex items-center gap-2.5">
          <div className="w-7 h-7 rounded-lg bg-blue-500/20 flex items-center justify-center border border-blue-500/40 shrink-0">
            <Sparkles className="w-4 h-4 text-blue-400" />
          </div>
          <div>
            <div className="flex items-center gap-2 flex-wrap">
              <h4 className="text-xs font-bold uppercase tracking-wider text-slate-200">
                Transaction Interception Flow
              </h4>
              <span
                className="text-[10px] font-mono px-2 py-0.5 rounded-full bg-[#3395FF]/10 text-[#93c5fd] border border-[#3395FF]/20 flex items-center gap-1 cursor-help"
                title="Dynamic root-cause isolation derived from Razorpay webhook error codes and domain classification rules"
              >
                <Database className="w-3 h-3" /> Live Signal Derived
              </span>
            </div>
            <p className="text-[11px] text-slate-400 mt-0.5">
              Topological root-cause isolation dynamically mapped from webhook error payload
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2 self-start sm:self-auto">
          {autoRecoverable !== undefined && (
            <span
              className={`text-[10px] uppercase font-bold px-2.5 py-0.5 rounded-full border ${
                autoRecoverable
                  ? "bg-emerald-500/15 text-emerald-300 border-emerald-500/30"
                  : "bg-rose-500/15 text-rose-300 border-rose-500/30"
              }`}
            >
              {autoRecoverable ? "Auto-Recoverable" : "Action Required"}
            </span>
          )}
        </div>
      </div>

      {/* Nodes visual chain */}
      <div className="grid grid-cols-1 sm:grid-cols-4 gap-2.5 relative items-center py-2">
        {nodes.map((node, index) => {
          const isFailing = node.key === failingKey;
          const Icon = node.icon;
          const tooltip = isFailing
            ? `Point of Failure: ${node.failureDescription} (Derived from error reason: '${failureReasonCode || category || "DECLINED"}')`
            : `Healthy: ${node.healthyDescription}`;

          return (
            <div key={node.key} className="relative flex flex-col items-center group">
              <motion.div
                initial={{ scale: 0.95 }}
                animate={{ scale: isFailing ? 1.02 : 1 }}
                title={tooltip}
                className={`w-full rounded-xl p-3.5 flex flex-col items-center text-center transition-all duration-300 border relative cursor-help ${
                  isFailing
                    ? "bg-rose-950/40 border-rose-500 shadow-lg shadow-rose-500/20 ring-2 ring-rose-500/40"
                    : "bg-slate-800/60 border-slate-700/60 hover:border-slate-600"
                }`}
              >
                <div
                  className={`w-9 h-9 rounded-full flex items-center justify-center mb-2 ${
                    isFailing
                      ? "bg-rose-500/20 text-rose-400 animate-pulse"
                      : "bg-slate-700/50 text-slate-300"
                  }`}
                >
                  <Icon className="w-4 h-4" />
                </div>

                <span className="text-xs font-bold text-slate-200 block truncate max-w-full">
                  {node.label}
                </span>
                <span className="text-[10px] text-slate-400 mt-0.5 block truncate max-w-full font-mono">
                  {node.sub}
                </span>

                {isFailing ? (
                  <div className="mt-2.5 flex items-center gap-1 text-[10px] font-bold text-rose-300 bg-rose-500/20 px-2 py-0.5 rounded-full border border-rose-500/30">
                    <AlertOctagon className="w-3 h-3 text-rose-400" /> Point of Failure
                  </div>
                ) : (
                  <div className="mt-2.5 flex items-center gap-1 text-[10px] font-medium text-emerald-300/90">
                    <CheckCircle2 className="w-3 h-3 text-emerald-400" /> Healthy
                  </div>
                )}
              </motion.div>

              {/* Connecting Arrow for Desktop */}
              {index < nodes.length - 1 && (
                <div className="hidden sm:flex absolute -right-3.5 top-1/2 -translate-y-1/2 z-10 pointer-events-none">
                  <ArrowRight className="w-4 h-4 text-slate-600" />
                </div>
              )}
            </div>
          );
        })}
      </div>

      {/* Expandable Diagnosis Drawer */}
      <div className="mt-4 pt-3 border-t border-slate-800">
        <button
          onClick={() => setShowDiagnosis(!showDiagnosis)}
          className="w-full flex items-center justify-between text-xs font-semibold text-slate-300 hover:text-white py-1"
        >
          <div className="flex items-center gap-2">
            <Bot className="w-4 h-4 text-purple-400" />
            <span>AI Diagnostic Synthesis & Recovery Protocol</span>
          </div>
          {showDiagnosis ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
        </button>

        <AnimatePresence>
          {showDiagnosis && (
            <motion.div
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: "auto" }}
              exit={{ opacity: 0, height: 0 }}
              className="overflow-hidden mt-2"
            >
              <div className="p-3.5 rounded-lg bg-slate-950/80 border border-slate-800 text-xs font-mono text-slate-300 space-y-2.5">
                <div className="flex justify-between items-center text-[11px] text-slate-400 pb-2 border-b border-slate-800/80 flex-wrap gap-2">
                  <span>Category: <strong className="text-blue-400">{category || "UNKNOWN"}</strong></span>
                  <span>Webhook Reason: <strong className="text-amber-400">{failureReasonCode || "BAD_REQUEST_ERROR"}</strong></span>
                  <span className="text-[10px] text-slate-500 font-sans italic flex items-center gap-1">
                    <Info className="w-3 h-3" /> Rule-Engine Mapped
                  </span>
                </div>
                <p className="leading-relaxed text-slate-300 font-sans text-xs">
                  {diagnosis ||
                    `Payment failure intercepted at the ${failingKey.toUpperCase()} tier due to '${category || "Technical Decline"}'. Intelligent retry and customer drafting pipeline initialized based on real webhook error reason.`}
                </p>

                {/* Indian Banking Rail Heuristic Strategy */}
                <div className="pt-2 border-t border-slate-800/80 flex flex-col sm:flex-row sm:items-center justify-between gap-2 text-[11px]">
                  <div className="flex items-center gap-1.5 text-[#3395FF]">
                    <Sparkles className="w-3.5 h-3.5 shrink-0" />
                    <span className="font-bold">Smart Retry Strategy (Indian Banking Rails):</span>
                  </div>
                  <span className="text-slate-300 font-mono text-[10px]">
                    {category?.toLowerCase().includes("insufficient")
                      ? "Salary Credit Liquidity Window (10:00 AM IST)"
                      : "Avoiding PSU CBS (11:30 PM–3:30 AM) & Peak UPI (7:00–9:30 PM)"}
                  </span>
                </div>
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </div>
  );
}
