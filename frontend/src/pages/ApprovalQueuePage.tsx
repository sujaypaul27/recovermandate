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
  Mail,
  MessageSquare,
  Smartphone,
  CheckCheck,
  Edit3,
  RotateCcw,
  PenSquare,
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
import { getStatusConfig } from "../lib/statusFormatters";

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

  const pendingActions = [...(data?.content || [])].sort((a, b) => {
    const timeA = new Date(a.createdAt || 0).getTime();
    const timeB = new Date(b.createdAt || 0).getTime();
    if (timeB !== timeA) return timeB - timeA;
    return (b.id || 0) - (a.id || 0);
  });
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
  const [previewChannel, setPreviewChannel] = useState<"email" | "whatsapp" | "sms">("email");
  const [copiedLink, setCopiedLink] = useState(false);
  const [showExplainability, setShowExplainability] = useState(false);
  const [isEditing, setIsEditing] = useState(false);
  const [customDrafts, setCustomDrafts] = useState<Record<string, string>>({});
  const { toast } = useToast();

  const isHeuristic = action.draftSource === "HEURISTIC";
  const isDraftMode = action.status === "DRAFTED";
  const paymentLink =
    action.paymentLinkUrl || (action.id ? `https://rzp.io/l/plink_preview_act_${action.id}` : "https://rzp.io/l/plink_preview");

  // Extract amount from draft or default to standard ₹499.00
  const amountMatch = action.aiDraftMessage?.match(/₹[\d,]+(\.\d{2})?/);
  const formattedAmount = amountMatch ? amountMatch[0] : "₹499.00";

  // Dynamic Draft Generation per channel and tone
  const getBaseDraft = (channel: "email" | "whatsapp" | "sms", tone: "gentle" | "balanced" | "urgent") => {
    if (channel === "email") {
      if (tone === "gentle") {
        return `Hi ${action.customerName || "Valued Customer"},\n\nWe noticed a temporary issue processing your mandate for ${formattedAmount}. No worries — your subscription remains active.\n\nTap here to easily update your payment details or retry:\n${paymentLink}\n\nIf you no longer wish to continue your subscription, you can cancel anytime in your account settings.\n\nThank you for choosing us,\nCustomer Success Team`;
      } else if (tone === "urgent") {
        return `⚠️ ACTION REQUIRED: Mandate payment of ${formattedAmount} failed.\n\nYour subscription is in grace period and will automatically PAUSE in 48 hours unless resolved.\n\nPlease immediately complete payment via Razorpay secure checkout:\n${paymentLink}\n\nIf you no longer wish to maintain your subscription, you can manage or cancel your plan in account settings before the grace period ends.\n\nImmediate settlement required to prevent service cancellation.`;
      } else {
        if (action.aiDraftMessage && action.aiDraftMessage.trim() && !isHeuristic) {
          return action.aiDraftMessage.includes("http")
            ? action.aiDraftMessage
            : `${action.aiDraftMessage}\n\nSecurely retry or update payment method:\n${paymentLink}\n\nIf you no longer wish to continue your subscription, you can cancel anytime in your account settings or by contacting support.`;
        }
        return `Hello ${action.customerName || "Valued Customer"},\n\nYour mandate payment of ${formattedAmount} failed due to a bank processing issue. Securely retry or update your payment method:\n${paymentLink}\n\nIf you no longer wish to continue your subscription, you can cancel anytime in your account settings or by contacting support.\n\nBest regards,\nBilling & Subscriptions`;
      }
    } else if (channel === "whatsapp") {
      if (tone === "gentle") {
        return `Hi ${action.customerName || "there"} 👋 We noticed a small glitch processing your subscription renewal of ${formattedAmount}. Don't worry, your access is active! Tap below to update payment:\n${paymentLink}\n\n(If you wish to cancel instead, you can do so anytime in your account settings.)`;
      } else if (tone === "urgent") {
        return `🚨 URGENT: Your mandate payment of ${formattedAmount} failed. Service will be paused within 48 hours. Please complete immediate recovery payment here:\n${paymentLink}\n\nTo cancel your subscription instead, visit account settings before expiry.`;
      } else {
        return `Hello ${action.customerName || "Customer"}, your recurring mandate payment of ${formattedAmount} could not be processed. Please use this secure Razorpay link to restore active status:\n${paymentLink}\n\nTo cancel or update your plan, visit your account portal anytime.`;
      }
    } else {
      // SMS / DLT
      if (tone === "gentle") {
        return `Hi ${action.customerName || "Customer"}, your mandate payment of ${formattedAmount} had a transient bank delay. Update details here: ${paymentLink} (Cancel anytime in portal) - RM`;
      } else if (tone === "urgent") {
        return `ALERT: Mandate payment of ${formattedAmount} failed. Grace period expires in 48h. Pay: ${paymentLink} or cancel in settings to stop charges - RM`;
      } else {
        return `Your subscription mandate payment of ${formattedAmount} failed. Securely retry: ${paymentLink} (Cancel anytime in account settings) - RM`;
      }
    }
  };

  const activeDraftText = customDrafts[previewChannel] ?? getBaseDraft(previewChannel, selectedTone);
  const isCustomEdited = customDrafts[previewChannel] !== undefined;
  const currentStrategy = TONE_STRATEGIES[selectedTone];

  const handleCopyLink = () => {
    navigator.clipboard.writeText(paymentLink);
    setCopiedLink(true);
    toast({
      title: isDraftMode ? "Preview Link Copied" : "Razorpay Link Copied",
      description: isDraftMode
        ? `Copied preview URL. Live link will be dispatched to customer upon approval.`
        : `Copied ${paymentLink} to clipboard.`,
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
                <h3 className="text-lg font-bold text-slate-900 dark:text-white flex items-center gap-2">
                  <span>Strategy #{action.id}</span>
                  <span className="text-sm font-semibold text-[#93c5fd] font-sans">
                    · {action.customerName || (action.customerEmail ? action.customerEmail.split("@")[0] : "Customer")}
                  </span>
                </h3>
              </div>
              {(() => {
                const statusCfg = getStatusConfig(action.status);
                return (
                  <Badge className={`${statusCfg.badgeClass} text-xs font-bold uppercase tracking-wider`} title={statusCfg.description}>
                    {statusCfg.label}
                  </Badge>
                );
              })()}
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
            <p className="text-xs font-mono text-slate-400 mt-1.5 flex items-center gap-2 flex-wrap">
              <span>Customer: <strong className="text-slate-200">{action.customerName || "Customer"}</strong></span>
              <span>•</span>
              <span className="flex items-center gap-1.5">
                <span>To:</span>
                <strong className="text-cyan-300 font-mono">{action.customerEmail || "subscriber@example.com"}</strong>
                {action.customerEmail && !action.customerEmail.includes("@example.com") && (
                  <span className="text-[9px] bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 px-1.5 py-0.5 rounded font-sans font-bold uppercase tracking-wider">
                    ⚡ Live Target
                  </span>
                )}
              </span>
              <span>•</span>
              <span>Drafted: {new Date(action.createdAt).toLocaleTimeString()}</span>
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
          {/* Left: Dynamic AI Multi-Channel Draft Previews (7 cols) */}
          <div className="lg:col-span-7 bg-[#02042B] p-5 relative flex flex-col justify-between">
            <div className="absolute top-0 left-0 w-full h-1 bg-gradient-to-r from-[#3395FF] via-cyan-400 to-indigo-500" />
            
            <div>
              {/* Channel Selector Tabs & Inline Edit Toggle */}
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-4">
                <div className="flex items-center gap-1.5 bg-[#061530] p-1 rounded-xl border border-[#3395FF]/30 text-xs shadow-inner flex-wrap">
                  <button
                    onClick={() => setPreviewChannel("email")}
                    className={`px-3 py-1.5 rounded-lg font-bold flex items-center gap-1.5 transition-all ${
                      previewChannel === "email"
                        ? "bg-[#3395FF] text-white shadow-md shadow-[#3395FF]/30"
                        : "text-slate-400 hover:text-white"
                    }`}
                  >
                    <Mail className="w-3.5 h-3.5" />
                    <span>Email</span>
                    {customDrafts["email"] && (
                      <span className="w-1.5 h-1.5 rounded-full bg-amber-400" title="Custom edits active" />
                    )}
                  </button>
                  <button
                    onClick={() => setPreviewChannel("whatsapp")}
                    className={`px-3 py-1.5 rounded-lg font-bold flex items-center gap-1.5 transition-all ${
                      previewChannel === "whatsapp"
                        ? "bg-emerald-600 text-white shadow-md shadow-emerald-600/30"
                        : "text-slate-400 hover:text-white"
                    }`}
                  >
                    <MessageSquare className="w-3.5 h-3.5" />
                    <span>WhatsApp Business</span>
                    {customDrafts["whatsapp"] && (
                      <span className="w-1.5 h-1.5 rounded-full bg-amber-400" title="Custom edits active" />
                    )}
                  </button>
                  <button
                    onClick={() => setPreviewChannel("sms")}
                    className={`px-3 py-1.5 rounded-lg font-bold flex items-center gap-1.5 transition-all ${
                      previewChannel === "sms"
                        ? "bg-indigo-600 text-white shadow-md shadow-indigo-600/30"
                        : "text-slate-400 hover:text-white"
                    }`}
                  >
                    <Smartphone className="w-3.5 h-3.5" />
                    <span>SMS / DLT</span>
                    {customDrafts["sms"] && (
                      <span className="w-1.5 h-1.5 rounded-full bg-amber-400" title="Custom edits active" />
                    )}
                  </button>
                </div>

                <div className="flex items-center gap-2 self-start sm:self-auto flex-wrap">
                  {isCustomEdited && (
                    <span className="text-[10px] font-mono text-amber-300 bg-amber-500/15 px-2 py-0.5 rounded border border-amber-500/30 font-bold flex items-center gap-1">
                      <PenSquare className="w-3 h-3 text-amber-400" /> Manual Edit Applied
                    </span>
                  )}
                  {isEditing ? (
                    <div className="flex items-center gap-1.5">
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => {
                          setCustomDrafts((prev) => {
                            const copy = { ...prev };
                            delete copy[previewChannel];
                            return copy;
                          });
                          setIsEditing(false);
                        }}
                        className="h-7 px-2 text-[11px] border-slate-700 bg-slate-800/80 text-slate-300 hover:text-white"
                        title="Discard edits and restore original AI draft"
                      >
                        <RotateCcw className="w-3 h-3 mr-1 text-slate-400" /> Reset AI
                      </Button>
                      <Button
                        size="sm"
                        onClick={() => setIsEditing(false)}
                        className="h-7 px-2.5 text-[11px] bg-emerald-600 hover:bg-emerald-500 text-white font-bold shadow-sm"
                      >
                        <Check className="w-3 h-3 mr-1" /> Done
                      </Button>
                    </div>
                  ) : (
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => setIsEditing(true)}
                      className="h-7 px-2.5 text-[11px] border-[#3395FF]/40 bg-[#061530] text-[#93c5fd] hover:text-white hover:bg-[#3395FF]/20 font-semibold gap-1"
                    >
                      <Edit3 className="w-3 h-3 text-[#3395FF]" />
                      <span>{isCustomEdited ? "Edit Message" : "✏️ Edit Draft"}</span>
                    </Button>
                  )}
                </div>
              </div>

              {/* EMAIL CHANNEL VIEW */}
              {previewChannel === "email" && (
                <motion.div
                  initial={{ opacity: 0, y: 4 }}
                  animate={{ opacity: 1, y: 0 }}
                  className="rounded-xl bg-[#08182D] border border-slate-700/60 p-4 space-y-3 shadow-inner"
                >
                  <div className="border-b border-slate-700/60 pb-2.5 text-xs text-slate-400 space-y-1 font-sans">
                    <div className="flex items-center gap-2">
                      <span className="font-bold text-slate-300 w-14">From:</span>
                      <span className="text-slate-300 font-mono text-[11px]">billing@recovermandate.io (Verified SPF/DKIM)</span>
                    </div>
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="font-bold text-slate-300 w-14">To:</span>
                      <span className="text-[#93c5fd] font-mono text-[11px] font-semibold">
                        {action.customerName ? `${action.customerName} <${action.customerEmail || "customer@example.com"}>` : (action.customerEmail || "subscriber@example.com")}
                      </span>
                      {action.customerEmail && !action.customerEmail.includes("@example.com") && (
                        <span className="text-[9px] bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 px-1.5 py-0.2 rounded font-sans font-bold uppercase tracking-wider">
                          ⚡ Live Recipient
                        </span>
                      )}
                    </div>
                    <div className="flex items-center gap-2">
                      <span className="font-bold text-slate-300 w-14">Subject:</span>
                      <span className="text-white font-semibold">Action Required: Your Subscription Mandate Payment Failed</span>
                    </div>
                  </div>

                  {isEditing ? (
                    <div className="space-y-2">
                      <textarea
                        value={activeDraftText}
                        onChange={(e) =>
                          setCustomDrafts((prev) => ({ ...prev, [previewChannel]: e.target.value }))
                        }
                        rows={6}
                        placeholder="Write custom email dunning message..."
                        className="w-full text-xs font-mono leading-relaxed text-slate-100 bg-[#02042B] p-3 rounded-lg border border-[#3395FF]/50 focus:border-[#3395FF] focus:ring-1 focus:ring-[#3395FF] outline-none resize-y"
                      />
                      <div className="flex items-center justify-between text-[10px] text-slate-400 font-mono">
                        <span>💡 Tip: The payment link URL will be embedded in the checkout button below.</span>
                        <span>{activeDraftText.length} chars</span>
                      </div>
                    </div>
                  ) : (
                    <div className="text-sm leading-relaxed text-slate-200 font-mono whitespace-pre-wrap min-h-[110px] bg-[#02042B]/80 p-3 rounded-lg border border-slate-800">
                      {isCustomEdited ? <span>{activeDraftText}</span> : <TypewriterText text={activeDraftText} />}
                    </div>
                  )}

                  <div className="pt-2">
                    <a
                      href={paymentLink}
                      target="_blank"
                      rel="noreferrer"
                      className="w-full py-2.5 px-4 rounded-xl bg-[#3395FF] hover:bg-[#2582eb] text-white text-xs font-bold flex items-center justify-center gap-2 shadow-lg shadow-[#3395FF]/20 transition-colors"
                    >
                      <RazorpayMark className="w-4 h-4 text-white" />
                      <span>Pay Overdue {formattedAmount} via Razorpay Secure Checkout</span>
                      <ExternalLink className="w-3.5 h-3.5" />
                    </a>
                  </div>
                </motion.div>
              )}

              {/* WHATSAPP BUSINESS VIEW */}
              {previewChannel === "whatsapp" && (
                <motion.div
                  initial={{ opacity: 0, y: 4 }}
                  animate={{ opacity: 1, y: 0 }}
                  className="rounded-xl bg-[#0b141a] border border-emerald-500/30 overflow-hidden shadow-inner font-sans"
                >
                  {/* WhatsApp Header */}
                  <div className="bg-[#1f2c34] px-4 py-2.5 border-b border-slate-700/60 flex items-center gap-3">
                    <div className="w-8 h-8 rounded-full bg-emerald-600 flex items-center justify-center font-bold text-white text-xs shrink-0">
                      RM
                    </div>
                    <div>
                      <div className="flex items-center gap-1.5">
                        <span className="text-xs font-bold text-white">RecoverMandate Billing</span>
                        <CheckCheck className="w-3.5 h-3.5 text-emerald-400" />
                      </div>
                      <span className="text-[10px] text-emerald-400 font-medium">
                        To: {action.customerName || "Customer"} ({action.customerEmail || "Verified Subscriber"})
                      </span>
                    </div>
                  </div>

                  {/* WhatsApp Chat Body */}
                  <div className="p-4 space-y-3 bg-[radial-gradient(#1f2c34_1px,transparent_1px)] [background-size:16px_16px] bg-[#0b141a]">
                    <div className="max-w-[90%] bg-[#005c4b] text-white p-3.5 rounded-2xl rounded-tl-sm shadow-md space-y-2.5">
                      {isEditing ? (
                        <textarea
                          value={activeDraftText}
                          onChange={(e) =>
                            setCustomDrafts((prev) => ({ ...prev, [previewChannel]: e.target.value }))
                          }
                          rows={4}
                          placeholder="Type WhatsApp recovery message..."
                          className="w-full text-xs font-sans leading-relaxed text-white bg-[#004a3c] p-2.5 rounded-lg border border-emerald-400/40 focus:border-emerald-300 focus:ring-1 focus:ring-emerald-300 outline-none resize-y"
                        />
                      ) : (
                        <p className="text-xs leading-relaxed font-sans whitespace-pre-wrap">
                          {activeDraftText}
                        </p>
                      )}
                      <div className="flex items-center justify-end gap-1 text-[10px] text-emerald-200 font-mono">
                        <span>{activeDraftText.length} chars</span>
                        <span>· Just now</span>
                        <CheckCheck className="w-3.5 h-3.5 text-cyan-300" />
                      </div>
                    </div>

                    {/* WhatsApp Quick Reply Action Button */}
                    <div className="max-w-[90%]">
                      <a
                        href={paymentLink}
                        target="_blank"
                        rel="noreferrer"
                        className="w-full py-2.5 px-4 rounded-xl bg-[#1f2c34] hover:bg-[#2a3942] border border-emerald-500/40 text-emerald-300 hover:text-white text-xs font-bold flex items-center justify-center gap-2 shadow-md transition-colors"
                      >
                        <RazorpayMark className="w-4 h-4" />
                        <span>Pay Overdue {formattedAmount}</span>
                        <ExternalLink className="w-3 h-3 text-emerald-400" />
                      </a>
                    </div>
                  </div>
                </motion.div>
              )}

              {/* SMS / DLT VIEW */}
              {previewChannel === "sms" && (
                <motion.div
                  initial={{ opacity: 0, y: 4 }}
                  animate={{ opacity: 1, y: 0 }}
                  className="rounded-xl bg-[#08182D] border border-indigo-500/30 p-4 space-y-3 shadow-inner font-sans"
                >
                  <div className="flex items-center justify-between border-b border-slate-700/60 pb-2 text-xs">
                    <div className="flex items-center gap-2">
                      <span className="font-bold text-white">Sender ID:</span>
                      <span className="px-2 py-0.5 rounded bg-indigo-500/20 text-indigo-300 font-mono font-bold border border-indigo-500/30">
                        VM-RZPMND
                      </span>
                    </div>
                    <span className="text-[10px] font-mono text-emerald-400 bg-emerald-500/10 px-2 py-0.5 rounded border border-emerald-500/20">
                      TRAI DLT Template ID: 140716892019482
                    </span>
                  </div>

                  <div className="bg-[#02042B] p-4 rounded-2xl rounded-bl-sm border border-slate-800 text-xs leading-relaxed text-slate-200 space-y-2 font-mono">
                    {isEditing ? (
                      <textarea
                        value={activeDraftText}
                        onChange={(e) =>
                          setCustomDrafts((prev) => ({ ...prev, [previewChannel]: e.target.value }))
                        }
                        rows={3}
                        placeholder="Type SMS text..."
                        className="w-full text-xs font-mono leading-relaxed text-slate-100 bg-[#08182D] p-2.5 rounded-lg border border-indigo-500/50 focus:border-indigo-400 focus:ring-1 focus:ring-indigo-400 outline-none resize-y"
                      />
                    ) : (
                      <p>{activeDraftText}</p>
                    )}
                    <p className="text-blue-400 underline font-semibold break-all">{paymentLink}</p>
                  </div>

                  <div className="flex items-center justify-between text-[11px] text-slate-400 pt-1">
                    <span className="flex items-center gap-1.5 text-indigo-300 font-medium">
                      <ShieldCheck className="w-3.5 h-3.5" /> DLT Telemarketer Entity Verified
                    </span>
                    <span className="font-mono text-slate-400">
                      {activeDraftText.length + paymentLink.length + 1}/160 chars · {activeDraftText.length + paymentLink.length + 1 <= 160 ? "Single SMS" : "Multi-part SMS"}
                    </span>
                  </div>
                </motion.div>
              )}
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
                      <span className="text-[11px] font-bold text-white tracking-wide">
                        {isDraftMode ? "Razorpay Hosted Link (Draft Preview)" : "Razorpay Hosted Link"}
                      </span>
                      <span
                        className={`text-[9px] font-mono px-1.5 py-0.2 rounded border ${
                          isDraftMode
                            ? "bg-blue-500/20 text-blue-300 border-blue-500/30"
                            : "bg-emerald-500/20 text-emerald-300 border-emerald-500/30"
                        }`}
                      >
                        {isDraftMode ? "PREVIEW · ACTIVATES ON APPROVAL" : "256-BIT SSL LIVE"}
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
                    title="Open checkout link in new tab"
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
