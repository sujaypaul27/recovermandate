import { useState, useEffect } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useToast } from "@/hooks/use-toast";
import {
  CheckCircle,
  XCircle,
  Bot,
  User,
  ShieldCheck,
  Sliders,
  Cpu,
  ExternalLink,
  Copy,
  Check,
  Sparkles,
  TrendingUp,
  Info,
  ChevronDown,
  ChevronUp,
  Code2,
  Zap,
} from "lucide-react";
import {
  fetchRecoveryActions,
  approveAndDispatchRecoveryAction,
  rejectRecoveryAction,
  batchApproveRecoveryActions,
  type PageResponse,
  type RecoveryActionItem,
} from "../lib/api";
import { RazorpayMark, RazorpayBadge } from "../components/RazorpayLogo";
import { EmptyState } from "../components/EmptyState";
import { formatINR } from "../lib/formatters";

const stagger = { hidden: {}, show: { transition: { staggerChildren: 0.08 } } };
const fadeUp = {
  hidden: { opacity: 0, y: 16 },
  show: { opacity: 1, y: 0, transition: { type: "spring" as const, stiffness: 300, damping: 30 } },
};

const TypewriterText = ({ text }: { text: string }) => {
  const [displayedText, setDisplayedText] = useState("");

  useEffect(() => {
    let i = 0;
    setDisplayedText("");
    const interval = setInterval(() => {
      setDisplayedText(text.substring(0, i));
      i += 4;
      if (i > text.length) {
        setDisplayedText(text);
        clearInterval(interval);
      }
    }, 6);
    return () => clearInterval(interval);
  }, [text]);

  return <span>{displayedText}</span>;
};

// ─── Tone Strategy Metadata ───────────────────────────────────────
interface ToneStrategyConfig {
  label: string;
  badge: string;
  badgeColor: string;
  targetAudience: string;
  impactMetric: string;
  description: string;
}

const TONE_STRATEGIES: Record<"gentle" | "balanced" | "urgent", ToneStrategyConfig> = {
  gentle: {
    label: "Gentle (VIP & Soft Declines)",
    badge: "+14% Open Rate",
    badgeColor: "bg-emerald-500/15 text-emerald-300 border-emerald-500/30",
    targetAudience: "High-net-worth VIP customers & soft declines (insufficient funds).",
    impactMetric: "Zero customer irritation, maximizes goodwill and brand loyalty.",
    description: "Soft reminder framing the failure as a temporary bank glitch. Encourages quick review without alarming the subscriber.",
  },
  balanced: {
    label: "Balanced (Standard Recurring)",
    badge: "+28% Recovery Rate",
    badgeColor: "bg-[#3395FF]/15 text-[#93c5fd] border-[#3395FF]/30",
    targetAudience: "Default strategy for general recurring mandates and SaaS subscriptions.",
    impactMetric: "High direct conversion with professional clarity and clear CTA.",
    description: "Standard professional notice with failure explanation and direct Razorpay checkout link for immediate resolution.",
  },
  urgent: {
    label: "Urgent (Critical & Near-Expiry)",
    badge: "+42% Conversion Spike",
    badgeColor: "bg-rose-500/15 text-rose-300 border-rose-500/30",
    targetAudience: "Technical declines, recurring retries exhausted, or mandates near 48-hr grace period expiry.",
    impactMetric: "Prevents immediate involuntary churn before account suspension.",
    description: "High-urgency alert highlighting 48-hour subscription pause. Drives immediate settlement through Razorpay fast checkout.",
  },
};

export function ApprovalQueuePage({ refreshTrigger }: { refreshTrigger?: number }) {
  const [data, setData] = useState<PageResponse<RecoveryActionItem> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [showBatchModal, setShowBatchModal] = useState(false);
  const [isBatchApproving, setIsBatchApproving] = useState(false);
  const [batchTone, setBatchTone] = useState<"gentle" | "balanced" | "urgent">("balanced");
  const { toast } = useToast();

  const load = () => {
    setLoading(true);
    fetchRecoveryActions(0, 50, "DRAFTED")
      .then(setData)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, [refreshTrigger]);

  const pendingActions = data?.content || [];
  const safeActions = pendingActions.filter((a) => !a.amount || a.amount <= 250000);
  const totalSafeValuePaise = safeActions.reduce(
    (acc, a) => acc + (a.amount != null ? a.amount : 49900),
    0
  );

  const handleApprove = async (id: number, tone?: string, message?: string) => {
    try {
      await approveAndDispatchRecoveryAction(id, tone, message);
      toast({
        title: "Razorpay Recovery Dispatched",
        description: `Payment link generated & ${tone ? `[${tone.toUpperCase()}]` : ""} recovery notification dispatched to customer.`,
      });
      load();
    } catch (e: any) {
      toast({ title: "Error", description: e.message, variant: "destructive" });
    }
  };

  const handleBatchApprove = async () => {
    setIsBatchApproving(true);
    try {
      const resp = await batchApproveRecoveryActions({
        actionIds: safeActions.map((a) => a.id),
        tone: batchTone,
        approvedBy: "HUMAN_BATCH",
      });
      toast({
        title: "Batch Recovery Dispatched",
        description: `Successfully approved & dispatched ${resp.successful} of ${resp.totalRequested} recovery actions.`,
      });
      setShowBatchModal(false);
      load();
    } catch (e: any) {
      toast({
        title: "Batch Approval Failed",
        description: e.message || "Error processing batch request",
        variant: "destructive",
      });
    } finally {
      setIsBatchApproving(false);
    }
  };

  if (error) {
    return (
      <div className="glass-card rounded-2xl p-8 text-center text-rose-400 space-y-3 bg-[#0C2340]/80 border-rose-500/30">
        <p className="font-bold">Failed to load recovery actions: {error}</p>
        <Button onClick={load} variant="outline" size="sm" className="border-[#3395FF]/40 text-white">
          Retry
        </Button>
      </div>
    );
  }

  return (
    <motion.div variants={stagger} initial="hidden" animate="show" className="space-y-6">
      <div className="flex flex-col sm:flex-row justify-between sm:items-end gap-3 mb-2">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <h2 className="text-xl font-bold text-slate-900 dark:text-white">AI Recovery Approval Queue</h2>
            <RazorpayBadge text="Razorpay Links Enabled" subtext="Automated Dispatch" />
          </div>
          <p className="text-sm text-slate-500 dark:text-slate-400">
            Review AI-drafted recovery communications, adjust tone parameters in real-time, and dispatch Razorpay hosted checkout links.
          </p>
        </div>

        {safeActions.length > 0 && (
          <div className="flex items-center gap-2">
            <Button
              onClick={() => setShowBatchModal(true)}
              className="bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-500 hover:to-teal-500 text-white text-xs font-bold gap-2 shadow-lg shadow-emerald-500/20"
            >
              <Zap className="w-3.5 h-3.5 text-amber-300" />
              Approve All Safe (&lt; ₹2,500) — {safeActions.length} Actions ({formatINR(totalSafeValuePaise, false)})
            </Button>
          </div>
        )}
      </div>

      {/* Batch Approval Confirmation Modal */}
      <AnimatePresence>
        {showBatchModal && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-6">
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={() => setShowBatchModal(false)}
              className="fixed inset-0 bg-slate-950/80 backdrop-blur-md"
            />
            <motion.div
              initial={{ opacity: 0, scale: 0.95, y: 15 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.95, y: 15 }}
              className="relative w-full max-w-lg rounded-2xl bg-[#0C2340] border border-emerald-500/40 shadow-2xl p-6 z-10 text-white space-y-5"
            >
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-emerald-500/20 text-emerald-400 flex items-center justify-center border border-emerald-500/30">
                  <Zap className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="text-base font-bold text-white">Batch Approve &amp; Dispatch Safe Actions</h3>
                  <p className="text-xs text-slate-300">Execute 1-click batch dunning for all low-risk pending recovery actions</p>
                </div>
              </div>

              <div className="p-4 rounded-xl bg-[#02042B] border border-slate-700/60 space-y-2 text-xs font-mono">
                <div className="flex justify-between text-slate-300 pb-2 border-b border-slate-800">
                  <span>Qualifying Actions:</span>
                  <span className="text-emerald-400 font-bold">{safeActions.length} Pending Mandates</span>
                </div>
                <div className="flex justify-between text-slate-300 pb-2 border-b border-slate-800">
                  <span>Total Recoverable Value:</span>
                  <span className="text-emerald-400 font-bold">{formatINR(totalSafeValuePaise)}</span>
                </div>
                <div className="flex justify-between text-slate-300">
                  <span>Safety Criteria:</span>
                  <span className="text-blue-400 font-bold">Amount &lt;= ₹2,500 · PII Redacted</span>
                </div>
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-300">Select Batch Dunning Tone</label>
                <div className="grid grid-cols-3 gap-2 bg-[#02042B] p-1.5 rounded-xl border border-slate-700 text-xs font-bold">
                  {(["gentle", "balanced", "urgent"] as const).map((t) => (
                    <button
                      key={t}
                      type="button"
                      onClick={() => setBatchTone(t)}
                      className={`py-1.5 rounded-lg capitalize transition-all ${
                        batchTone === t
                          ? "bg-emerald-600 text-white shadow-sm font-extrabold"
                          : "text-slate-400 hover:text-white"
                      }`}
                    >
                      {t}
                    </button>
                  ))}
                </div>
              </div>

              <div className="flex items-center justify-end gap-3 pt-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setShowBatchModal(false)}
                  className="border-slate-700 hover:bg-slate-800 text-slate-300 text-xs"
                >
                  Cancel
                </Button>
                <Button
                  size="sm"
                  onClick={handleBatchApprove}
                  disabled={isBatchApproving}
                  className="bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-bold gap-1.5 shadow-lg shadow-emerald-600/30"
                >
                  <Zap className={`w-3.5 h-3.5 ${isBatchApproving ? "animate-spin" : ""}`} />
                  {isBatchApproving ? "Dispatching..." : `Confirm & Dispatch (${safeActions.length})`}
                </Button>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      {loading ? (
        <div className="space-y-4">
          {Array.from({ length: 2 }).map((_, i) => (
            <div key={i} className="skeleton-shimmer h-64 w-full rounded-2xl" />
          ))}
        </div>
      ) : !data || data.content.length === 0 ? (
        <div className="glass-card rounded-2xl overflow-hidden shadow-xl">
          <EmptyState
            variant="shield"
            title="Approval Queue Clear"
            description="All AI-generated recovery drafts have been reviewed, dispatched, or automatically resolved via Auto-Pilot."
            badgeText="Zero Pending Drafts"
          />
        </div>
      ) : (
        data.content.map((action: RecoveryActionItem) => (
          <motion.div key={action.id} variants={fadeUp}>
            <ApprovalCard
              action={action}
              onApprove={(tone, message) => handleApprove(action.id, tone, message)}
              onReload={load}
            />
          </motion.div>
        ))
      )}
    </motion.div>
  );
}

function ApprovalCard({
  action,
  onApprove,
  onReload,
}: {
  action: RecoveryActionItem;
  onApprove: (tone: string, message?: string) => void;
  onReload: () => void;
}) {
  const [rejectReason, setRejectReason] = useState("");
  const [isRejecting, setIsRejecting] = useState(false);
  const [selectedTone, setSelectedTone] = useState<"gentle" | "balanced" | "urgent">("balanced");
  const [copiedLink, setCopiedLink] = useState(false);
  const [showExplainability, setShowExplainability] = useState(false);
  const { toast } = useToast();

  const isHeuristic = action.draftSource === "HEURISTIC";
  const paymentLink =
    action.paymentLinkUrl || `https://rzp.io/simulated/pay_rec_${action.id || 101}`;

  // Extract amount from draft or default to standard ₹499.00
  const amountMatch = action.aiDraftMessage?.match(/₹[\d,]+(\.\d{2})?/);
  const formattedAmount = amountMatch ? amountMatch[0] : "₹499.00";

  // Dynamic Draft Rewriting based on selected tone
  const getComputedDraft = () => {
    if (selectedTone === "gentle") {
      return `Hi Valued Customer,\n\nWe noticed a temporary issue processing your mandate for ${formattedAmount}. No worries — your subscription remains active.\n\nTap here to easily update your payment details or retry:\n${paymentLink}\n\nThank you for choosing us,\nCustomer Success Team`;
    } else if (selectedTone === "urgent") {
      return `⚠️ ACTION REQUIRED: Mandate payment of ${formattedAmount} failed.\n\nYour subscription is in grace period and will automatically PAUSE in 48 hours unless resolved.\n\nPlease immediately complete payment via Razorpay secure checkout:\n${paymentLink}\n\nImmediate settlement required to prevent service cancellation.`;
    } else {
      // Balanced (Standard Professional)
      if (action.aiDraftMessage && action.aiDraftMessage.trim() && !isHeuristic) {
        return action.aiDraftMessage.includes("http")
          ? action.aiDraftMessage
          : `${action.aiDraftMessage}\n\nSecurely retry or update payment method:\n${paymentLink}`;
      }
      return `Hello Valued Customer,\n\nYour mandate payment of ${formattedAmount} failed due to a bank processing issue. Securely retry or update your payment method:\n${paymentLink}\n\nBest regards,\nBilling & Subscriptions`;
    }
  };

  const activeDraftText = getComputedDraft();
  const currentStrategy = TONE_STRATEGIES[selectedTone];

  const handleCopyLink = () => {
    navigator.clipboard.writeText(paymentLink);
    setCopiedLink(true);
    toast({
      title: "Razorpay Link Copied",
      description: `Copied ${paymentLink} to clipboard.`,
    });
    setTimeout(() => setCopiedLink(false), 2000);
  };

  const handleReject = async () => {
    if (!rejectReason.trim()) {
      toast({
        title: "Reason Required",
        description: "Please provide feedback to train the AI model.",
        variant: "destructive",
      });
      return;
    }
    try {
      await rejectRecoveryAction(action.id, rejectReason);
      toast({ title: "Draft Rejected", description: "Feedback recorded for model retraining." });
      onReload();
    } catch (e: any) {
      toast({ title: "Error", description: e.message, variant: "destructive" });
    }
  };

  return (
    <div className="glass-card rounded-2xl overflow-hidden shadow-2xl border-[#3395FF]/30 bg-[#0C2340]/80">
      <div className="p-6">
        {/* Header */}
        <div className="flex flex-col sm:flex-row justify-between items-start gap-4 mb-6">
          <div>
            <div className="flex items-center gap-3 flex-wrap">
              <div className="flex items-center gap-2">
                <div className="w-7 h-7 rounded-lg bg-[#02042B] border border-[#3395FF]/40 flex items-center justify-center">
                  <RazorpayMark className="w-4 h-4" />
                </div>
                <h3 className="text-lg font-bold text-slate-900 dark:text-white">
                  Strategy #{action.id}
                </h3>
              </div>
              <Badge className="bg-amber-500/15 text-amber-300 border border-amber-500/30 text-xs font-bold uppercase tracking-wider">
                Needs Review
              </Badge>
              {isHeuristic ? (
                <Badge className="bg-purple-500/15 text-purple-300 border border-purple-500/30 text-xs font-semibold flex items-center gap-1">
                  <Cpu className="w-3 h-3" /> Heuristic Engine
                </Badge>
              ) : (
                <Badge className="bg-[#3395FF]/15 text-[#93c5fd] border border-[#3395FF]/30 text-xs font-semibold flex items-center gap-1">
                  <Bot className="w-3 h-3 text-[#3395FF]" /> Gemini 3.5 Flash
                </Badge>
              )}
            </div>
            <p className="text-xs font-mono text-slate-400 mt-1.5 flex items-center gap-2">
              <span>Drafted: {new Date(action.createdAt).toLocaleString()}</span>
              <span>•</span>
              <span className="text-[#3395FF] font-semibold">Razorpay Mandate Intercept</span>
            </p>
          </div>

          <div className="flex items-center gap-2 flex-wrap">
            {/* Tone Selector Pills */}
            <div className="flex items-center bg-[#02042B] p-1 rounded-xl border border-[#3395FF]/30 text-xs shadow-inner">
              <Sliders className="w-3.5 h-3.5 text-[#3395FF] mx-2 hidden sm:block" />
              <button
                onClick={() => setSelectedTone("gentle")}
                className={`px-3 py-1.5 rounded-lg font-bold transition-all ${
                  selectedTone === "gentle"
                    ? "bg-emerald-600 text-white shadow-md shadow-emerald-600/30"
                    : "text-slate-400 hover:text-white"
                }`}
              >
                Gentle
              </button>
              <button
                onClick={() => setSelectedTone("balanced")}
                className={`px-3 py-1.5 rounded-lg font-bold transition-all ${
                  selectedTone === "balanced"
                    ? "bg-[#3395FF] text-white shadow-md shadow-[#3395FF]/30"
                    : "text-slate-400 hover:text-white"
                }`}
              >
                Balanced
              </button>
              <button
                onClick={() => setSelectedTone("urgent")}
                className={`px-3 py-1.5 rounded-lg font-bold transition-all ${
                  selectedTone === "urgent"
                    ? "bg-rose-600 text-white shadow-md shadow-rose-600/30"
                    : "text-slate-400 hover:text-white"
                }`}
              >
                Urgent
              </button>
            </div>

            <Badge className="bg-[#3395FF]/10 text-[#93c5fd] border border-[#3395FF]/30 text-xs font-semibold flex items-center gap-1.5 shadow-sm">
              <ShieldCheck className="w-3.5 h-3.5 text-[#3395FF]" /> Tone: {selectedTone.toUpperCase()}
            </Badge>
          </div>
        </div>

        {/* AI Tone Strategy & Behavioral Impact Card */}
        <motion.div
          key={selectedTone}
          initial={{ opacity: 0, y: -6 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.2 }}
          className="mb-4 p-4 rounded-xl bg-[#02042B]/90 border border-[#3395FF]/30 shadow-inner"
        >
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 mb-2">
            <div className="flex items-center gap-2">
              <Sparkles className="w-4 h-4 text-[#3395FF]" />
              <span className="text-xs font-extrabold uppercase tracking-wider text-white">
                AI Tone Strategy & Behavioral Impact: {currentStrategy.label}
              </span>
            </div>
            <span className={`text-[11px] font-bold px-2.5 py-0.5 rounded-full border ${currentStrategy.badgeColor} flex items-center gap-1`}>
              <TrendingUp className="w-3 h-3" /> {currentStrategy.badge}
            </span>
          </div>
          <p className="text-xs text-slate-300 leading-relaxed font-medium mb-1">
            <strong className="text-white">Target Audience:</strong> {currentStrategy.targetAudience}
          </p>
          <p className="text-xs text-[#93c5fd] leading-relaxed">
            <strong className="text-white">Expected Outcome:</strong> {currentStrategy.impactMetric} {currentStrategy.description}
          </p>
        </motion.div>

        {/* AI Decision Explainability & Triage Reasoning Drawer */}
        <div className="mb-6">
          <button
            onClick={() => setShowExplainability(!showExplainability)}
            className="w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl bg-[#02042B]/90 hover:bg-[#02042B] border border-[#3395FF]/30 text-xs font-semibold text-slate-300 hover:text-white transition-colors shadow-sm"
          >
            <div className="flex items-center gap-2">
              <Info className="w-4 h-4 text-[#3395FF]" />
              <span>AI Decision Explainability & Deterministic Classifier Reasoning</span>
              <span className="text-[10px] px-2 py-0.5 rounded-full bg-[#3395FF]/20 text-[#93c5fd] font-mono font-bold">
                Rule Engine
              </span>
            </div>
            <div className="flex items-center gap-1.5 text-xs text-[#93c5fd] font-medium">
              <span>{showExplainability ? "Hide Telemetry" : "Inspect Decision Rules"}</span>
              {showExplainability ? <ChevronUp className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5 text-[#3395FF]" />}
            </div>
          </button>

          <AnimatePresence>
            {showExplainability && (
              <motion.div
                initial={{ opacity: 0, height: 0 }}
                animate={{ opacity: 1, height: "auto" }}
                exit={{ opacity: 0, height: 0 }}
                transition={{ duration: 0.2 }}
                className="overflow-hidden mt-2.5"
              >
                <div className="p-4 rounded-xl bg-[#02042B] border border-[#3395FF]/40 space-y-3 shadow-inner">
                  <div className="flex items-center justify-between border-b border-[#3395FF]/20 pb-2.5">
                    <span className="text-[11px] font-extrabold uppercase tracking-wider text-white flex items-center gap-1.5 font-mono">
                      <Code2 className="w-3.5 h-3.5 text-[#3395FF]" /> Triage Telemetry & Rule Match
                    </span>
                    <span className="text-[10px] font-mono text-emerald-400 bg-emerald-500/10 px-2 py-0.5 rounded border border-emerald-500/20">
                      Audit Verified
                    </span>
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-2.5 text-xs font-mono">
                    <div className="p-2.5 rounded-lg bg-[#0C2340]/90 border border-slate-700/60">
                      <span className="text-[10px] uppercase text-slate-400 block mb-0.5">Raw Error Code</span>
                      <span className="text-amber-300 font-bold break-all">
                        {action.rawErrorCode || "BAD_REQUEST_ERROR"}
                      </span>
                    </div>

                    <div className="p-2.5 rounded-lg bg-[#0C2340]/90 border border-slate-700/60">
                      <span className="text-[10px] uppercase text-slate-400 block mb-0.5">Issuer Bank</span>
                      <span className="text-[#3395FF] font-bold">
                        {action.bank ? `${action.bank} Bank` : "HDFC Bank (UPI AutoPay)"}
                      </span>
                    </div>

                    <div className="p-2.5 rounded-lg bg-[#0C2340]/90 border border-slate-700/60">
                      <span className="text-[10px] uppercase text-slate-400 block mb-0.5">Category</span>
                      <span className="text-white font-bold capitalize">
                        {(action.category || "insufficient_funds").replace(/_/g, " ")}
                      </span>
                    </div>

                    <div className="p-2.5 rounded-lg bg-[#0C2340]/90 border border-slate-700/60">
                      <span className="text-[10px] uppercase text-slate-400 block mb-0.5">Auto-Recoverable</span>
                      <span className={action.autoRecoverable ? "text-emerald-400 font-bold" : "text-slate-300 font-bold"}>
                        {action.autoRecoverable ? "YES (Auto-Retry Candidate)" : "NO (Customer Dunning Required)"}
                      </span>
                    </div>
                  </div>

                  <div className="p-3 rounded-lg bg-[#0C2340]/90 border border-slate-700/60 text-xs font-mono">
                    <div className="flex justify-between items-center text-[10px] text-slate-400 mb-1.5">
                      <span className="uppercase font-bold text-slate-300">Deterministic Classifier Rule Matched:</span>
                      <span className="text-[#93c5fd]">Zero-Hallucination Safe</span>
                    </div>
                    <p className="text-slate-200 leading-relaxed font-sans text-xs">
                      <code className="text-emerald-400 bg-slate-950 px-2 py-0.5 rounded text-[11px] font-mono mr-1.5 border border-emerald-500/20">
                        {action.matchedRule || `exact match ${action.rawErrorCode || "BAD_REQUEST_ERROR"} -> ${action.category || "insufficient_funds"}`}
                      </code>
                      — Strategy drafted via <span className="text-white font-semibold">{isHeuristic ? "Heuristic Template Engine" : "Gemini 3.5 Flash"}</span> with verified PII redaction and cryptographic audit logging.
                    </p>
                  </div>
                </div>
              </motion.div>
            )}
          </AnimatePresence>
        </div>

        {/* Split Interface - "The Code Editor" feel */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-0 border border-[#3395FF]/30 rounded-xl overflow-hidden shadow-lg">
          {/* Left: Dynamic AI Draft (7 cols) */}
          <div className="lg:col-span-7 bg-[#02042B] p-5 relative flex flex-col justify-between">
            <div className="absolute top-0 left-0 w-full h-1 bg-gradient-to-r from-[#3395FF] via-cyan-400 to-indigo-500" />
            <div>
              <div className="flex items-center justify-between mb-4">
                <h4 className="text-xs font-bold uppercase tracking-widest text-[#93c5fd] flex items-center gap-2">
                  <Bot className="w-4 h-4 text-[#3395FF]" />
                  {isHeuristic ? "Heuristic Strategy Draft" : "Gemini 3.5 Flash Draft"}{" "}
                  <span className="text-slate-400 font-normal">({selectedTone})</span>
                </h4>
                <span className="text-[10px] font-mono text-emerald-400 bg-emerald-500/10 px-2 py-0.5 rounded border border-emerald-500/20">
                  Live Preview
                </span>
              </div>

              <div className="text-sm leading-relaxed text-slate-200 font-mono whitespace-pre-wrap pl-3 border-l-2 border-[#3395FF]/40 min-h-[140px] bg-[#061530]/40 p-3 rounded-r-lg">
                <TypewriterText text={activeDraftText} />
              </div>
            </div>

            {/* Embedded Razorpay Link Preview */}
            <div className="mt-4 pt-3 border-t border-[#3395FF]/20">
              <div className="p-3 rounded-lg bg-[#0C2340] border border-[#3395FF]/30 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
                <div className="flex items-center gap-2.5 min-w-0">
                  <div className="w-7 h-7 rounded-md bg-[#02042B] flex items-center justify-center p-1 border border-[#3395FF]/40 shrink-0">
                    <RazorpayMark className="w-4 h-4" />
                  </div>
                  <div className="min-w-0">
                    <div className="flex items-center gap-1.5">
                      <span className="text-[11px] font-bold text-white tracking-wide">Razorpay Hosted Link</span>
                      <span className="text-[9px] font-mono px-1.5 py-0.2 rounded bg-emerald-500/20 text-emerald-300 border border-emerald-500/30">
                        256-BIT SSL
                      </span>
                    </div>
                    <span className="text-xs font-mono text-[#93c5fd] truncate block">
                      {paymentLink}
                    </span>
                  </div>
                </div>

                <div className="flex items-center gap-2 shrink-0">
                  <button
                    onClick={handleCopyLink}
                    className="px-2.5 py-1.5 rounded-lg bg-[#02042B] hover:bg-[#3395FF]/20 border border-[#3395FF]/40 text-xs font-semibold text-white flex items-center gap-1.5 transition-colors"
                  >
                    {copiedLink ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5 text-[#3395FF]" />}
                    <span>{copiedLink ? "Copied" : "Copy"}</span>
                  </button>
                  <a
                    href={paymentLink}
                    target="_blank"
                    rel="noreferrer"
                    className="p-1.5 rounded-lg bg-[#02042B] hover:bg-[#3395FF]/20 border border-[#3395FF]/40 text-[#3395FF] hover:text-white transition-colors"
                    title="Open test payment link in new tab"
                  >
                    <ExternalLink className="w-3.5 h-3.5" />
                  </a>
                </div>
              </div>
            </div>
          </div>

          {/* Right: Actions & Dispatch (5 cols) */}
          <div className="lg:col-span-5 bg-[#0C2340]/90 p-5 flex flex-col justify-between border-t lg:border-t-0 lg:border-l border-[#3395FF]/30">
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <h4 className="text-xs font-bold uppercase tracking-widest text-[#93c5fd] flex items-center gap-2">
                  <User className="w-4 h-4 text-[#3395FF]" /> Human Decision
                </h4>
                <span className="text-[10px] text-slate-400 font-mono">Stage 3 of 5</span>
              </div>
              <p className="text-xs text-slate-300 leading-relaxed">
                Review the selected tone and message. Approving will automatically generate the <strong>Razorpay Payment Link</strong> and dispatch the recovery communication via email/SMS.
              </p>

              <div className="p-3 rounded-lg bg-[#02042B]/80 border border-[#3395FF]/20 space-y-1.5 text-xs text-slate-300 font-mono">
                <div className="flex justify-between">
                  <span className="text-slate-400">Payment Gateway:</span>
                  <span className="text-white font-bold">Razorpay API v1</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-400">Link Expiry:</span>
                  <span className="text-emerald-400 font-bold">48 Hours</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-400">Selected Tone:</span>
                  <span className="text-[#3395FF] font-bold capitalize">{selectedTone}</span>
                </div>
              </div>
            </div>

            <div className="flex flex-col gap-3 mt-6">
              <Button
                onClick={() => onApprove(selectedTone, activeDraftText)}
                className="w-full bg-[#3395FF] hover:bg-[#2582eb] text-white font-bold shadow-lg shadow-[#3395FF]/30 py-6 text-base group transition-all"
              >
                <CheckCircle className="w-5 h-5 mr-2 group-hover:scale-110 transition-transform" /> Approve & Dispatch Link
              </Button>

              <AnimatePresence mode="wait">
                {!isRejecting ? (
                  <motion.div key="reject-btn" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
                    <Button
                      onClick={() => setIsRejecting(true)}
                      variant="outline"
                      className="w-full border-rose-500/40 text-rose-400 hover:bg-rose-950/40 font-semibold"
                    >
                      <XCircle className="w-4 h-4 mr-2" /> Reject Draft
                    </Button>
                  </motion.div>
                ) : (
                  <motion.div
                    key="reject-form"
                    initial={{ opacity: 0, height: 0 }}
                    animate={{ opacity: 1, height: "auto" }}
                    exit={{ opacity: 0, height: 0 }}
                    className="flex flex-col gap-2 overflow-hidden"
                  >
                    <Input
                      value={rejectReason}
                      onChange={(e) => setRejectReason(e.target.value)}
                      placeholder="Feedback reason (trains AI)..."
                      className="bg-[#02042B] border-rose-500/40 text-white text-xs focus-visible:ring-rose-500"
                      autoFocus
                    />
                    <div className="flex gap-2">
                      <Button onClick={handleReject} variant="destructive" className="flex-1 font-bold text-xs">
                        Confirm
                      </Button>
                      <Button
                        onClick={() => setIsRejecting(false)}
                        variant="outline"
                        className="flex-1 bg-[#02042B] border-slate-700 text-slate-300 text-xs"
                      >
                        Cancel
                      </Button>
                    </div>
                  </motion.div>
                )}
              </AnimatePresence>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
