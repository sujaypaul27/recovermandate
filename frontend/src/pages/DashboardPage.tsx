import { useState, useEffect, useRef } from "react";
import { motion, AnimatePresence, useMotionValue, useTransform } from "framer-motion";
import {
  AlertTriangle,
  TrendingUp,
  Clock,
  CheckCircle,
  ShieldAlert,
  Zap,
  XCircle,
  Bot,
  ArrowRight,
  IndianRupee,
  Activity,
  Filter,
  Send,
  PieChart,
  Sparkles,
  Play,
  Download,
} from "lucide-react";
import {
  fetchDashboardSummary,
  simulateFailure,
  simulatePaymentPaid,
  simulateFullFlow,
  exportRecoveryLedgerCsv,
  type DashboardSummary,
} from "../lib/api";
import { RazorpayMark } from "../components/RazorpayLogo";

// ─── Animation Variants ───────────────────────────────────────────
const stagger = { hidden: {}, show: { transition: { staggerChildren: 0.08 } } };
const fadeUp = {
  hidden: { opacity: 0, y: 16 },
  show: { opacity: 1, y: 0, transition: { type: "spring" as const, stiffness: 300, damping: 30 } },
};

// ─── Count-Up Components ──────────────────────────────────────────
function CountUp({ target, duration = 1200 }: { target: number; duration?: number }) {
  const [count, setCount] = useState(0);
  const rafRef = useRef<number>(0);

  useEffect(() => {
    const start = performance.now();
    const animate = (now: number) => {
      const elapsed = now - start;
      const progress = Math.min(elapsed / duration, 1);
      const eased = 1 - Math.pow(1 - progress, 3);
      setCount(Math.round(target * eased));
      if (progress < 1) rafRef.current = requestAnimationFrame(animate);
    };
    rafRef.current = requestAnimationFrame(animate);
    return () => cancelAnimationFrame(rafRef.current);
  }, [target, duration]);

  return <>{count.toLocaleString("en-IN")}</>;
}

function CountUpDecimal({ target, decimals = 1, duration = 1200 }: { target: number; decimals?: number; duration?: number }) {
  const [count, setCount] = useState(0);
  const rafRef = useRef<number>(0);

  useEffect(() => {
    const start = performance.now();
    const animate = (now: number) => {
      const elapsed = now - start;
      const progress = Math.min(elapsed / duration, 1);
      const eased = 1 - Math.pow(1 - progress, 3);
      setCount(target * eased);
      if (progress < 1) rafRef.current = requestAnimationFrame(animate);
    };
    rafRef.current = requestAnimationFrame(animate);
    return () => cancelAnimationFrame(rafRef.current);
  }, [target, duration]);

  return <>{count.toFixed(decimals)}</>;
}

// ─── Sparkline Component ──────────────────────────────────────────
function Sparkline({ colorClass, dataPoints }: { colorClass: string; dataPoints: number[] }) {
  const max = Math.max(...dataPoints, 1);
  const min = Math.min(...dataPoints, 0);
  const range = max - min || 1;

  const width = 100;
  const height = 30;

  const points = dataPoints
    .map((val, i) => {
      const x = (i / (Math.max(dataPoints.length - 1, 1))) * width;
      const y = height - ((val - min) / range) * height;
      return `${x},${y}`;
    })
    .join(" ");

  return (
    <svg width="100%" height="100%" viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none" className="opacity-40">
      <polyline
        points={points}
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        className={`animate-draw-line ${colorClass}`}
      />
    </svg>
  );
}

// ─── Hero Story Component ─────────────────────────────────────────
export function HeroStory() {
  return (
    <div className="glass-card rounded-2xl p-6 sm:p-8 relative overflow-hidden mb-8 border-[#3395FF]/30 bg-[#0C2340]/80 shadow-2xl">
      <div className="absolute -top-20 -right-20 p-8 opacity-10 pointer-events-none transform rotate-12">
        <Zap className="w-96 h-96 text-[#3395FF]" />
      </div>
      <div className="relative z-10 max-w-4xl">
        <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.6 }}>
          <div className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-[#02042B] text-[#3395FF] text-xs font-bold uppercase tracking-wider mb-3.5 border border-[#3395FF]/40 shadow-md">
            <RazorpayMark className="w-4 h-4" /> Powered by Razorpay Mandate Recovery Engine
          </div>
          <h2 className="text-3xl sm:text-4xl lg:text-5xl font-extrabold text-slate-900 dark:text-white mb-4 tracking-tight">
            Recover lost revenue from <br className="hidden sm:block" />
            <span className="text-transparent bg-clip-text bg-gradient-to-r from-[#3395FF] via-cyan-400 to-indigo-400">
              mandate & recurring payment failures.
            </span>
          </h2>
          <p className="text-slate-600 dark:text-slate-200 text-base sm:text-lg leading-relaxed mb-8 max-w-3xl font-medium">
            RecoverMandate connects directly to Razorpay webhooks. When a recurring mandate fails, our multi-layered AI
            categorizes the root cause, checks bank health, generates dynamic payment links, and initiates multi-channel recovery in under 5 minutes.
          </p>
        </motion.div>

        {/* Sequence Flow */}
        <motion.div variants={stagger} initial="hidden" animate="show" className="grid grid-cols-1 md:grid-cols-3 gap-4 relative">
          <motion.div variants={fadeUp} className="p-5 rounded-xl bg-white/70 dark:bg-slate-800/70 border border-slate-200 dark:border-slate-700/50 flex flex-col gap-3 shadow-md backdrop-blur-md">
            <div className="w-10 h-10 rounded-full bg-rose-100 dark:bg-rose-500/20 flex items-center justify-center">
              <XCircle className="w-5 h-5 text-rose-600 dark:text-rose-400" />
            </div>
            <div>
              <span className="font-bold text-slate-800 dark:text-white text-base block mb-1">1. Intercept & Classify</span>
              <span className="text-xs text-slate-500 dark:text-slate-400 leading-normal block">
                Razorpay emits `payment.failed`. We classify failure reason & check bank uptime.
              </span>
            </div>
          </motion.div>

          <motion.div variants={fadeUp} className="p-5 rounded-xl bg-white/70 dark:bg-slate-800/70 border border-slate-200 dark:border-slate-700/50 flex flex-col gap-3 shadow-md backdrop-blur-md">
            <ArrowRight className="w-6 h-6 text-slate-300 dark:text-slate-600 absolute -left-5 top-1/2 -translate-y-1/2 hidden md:block z-0" />
            <div className="w-10 h-10 rounded-full bg-purple-100 dark:bg-purple-500/20 flex items-center justify-center">
              <Bot className="w-5 h-5 text-purple-600 dark:text-purple-400" />
            </div>
            <div>
              <span className="font-bold text-slate-800 dark:text-white text-base block mb-1">2. AI Draft & Link Generation</span>
              <span className="text-xs text-slate-500 dark:text-slate-400 leading-normal block">
                Resilient Gemini / heuristic engine generates custom recovery drafts and Razorpay link.
              </span>
            </div>
          </motion.div>

          <motion.div variants={fadeUp} className="p-5 rounded-xl bg-white/70 dark:bg-slate-800/70 border border-emerald-500/30 flex flex-col gap-3 shadow-emerald-500/10 shadow-lg backdrop-blur-md">
            <ArrowRight className="w-6 h-6 text-slate-300 dark:text-slate-600 absolute -left-5 top-1/2 -translate-y-1/2 hidden md:block z-0" />
            <div className="w-10 h-10 rounded-full bg-emerald-100 dark:bg-emerald-500/20 flex items-center justify-center">
              <TrendingUp className="w-5 h-5 text-emerald-600 dark:text-emerald-400" />
            </div>
            <div>
              <span className="font-bold text-slate-800 dark:text-white text-base block mb-1">3. Approve & Recover</span>
              <span className="text-xs text-slate-500 dark:text-slate-400 leading-normal block">
                1-click approval dispatches recovery email. Revenue is salvaged within hours.
              </span>
            </div>
          </motion.div>
        </motion.div>
      </div>
    </div>
  );
}

// ─── KPI Card Component ───────────────────────────────────────────
function KPICard({
  title,
  displayValue,
  subtitle,
  icon,
  glowClass,
  accentColor,
  tooltip,
  sparklineData,
}: {
  title: string;
  displayValue: string | React.ReactNode;
  subtitle?: string;
  icon: React.ReactNode;
  glowClass: string;
  accentColor: string;
  tooltip?: string;
  sparklineData: number[];
}) {
  const x = useMotionValue(0);
  const y = useMotionValue(0);
  const rotateX = useTransform(y, [-100, 100], [6, -6]);
  const rotateY = useTransform(x, [-100, 100], [-6, 6]);

  const handleMouseMove = (e: React.MouseEvent<HTMLDivElement>) => {
    const rect = e.currentTarget.getBoundingClientRect();
    x.set(e.clientX - rect.left - rect.width / 2);
    y.set(e.clientY - rect.top - rect.height / 2);
  };

  const handleMouseLeave = () => {
    x.set(0);
    y.set(0);
  };

  return (
    <motion.div
      className="perspective-1000 w-full h-full"
      onMouseMove={handleMouseMove}
      onMouseLeave={handleMouseLeave}
      style={{ rotateX, rotateY, zIndex: 10 }}
      whileHover={{ scale: 1.02 }}
      transition={{ type: "spring", stiffness: 300, damping: 20 }}
    >
      <div className={`glass-card preserve-3d w-full h-full rounded-2xl ${glowClass} relative group`}>
        <div className={`h-1.5 w-full bg-gradient-to-r ${accentColor} absolute top-0 left-0 right-0 rounded-t-2xl`} />

        <div className="p-5 sm:p-6 h-full flex flex-col justify-between" style={{ transform: "translateZ(20px)" }}>
          <div className="flex items-center justify-between mb-2">
            <div className={`flex items-center gap-1.5 ${tooltip ? "has-tooltip relative" : ""}`}>
              <span className={`text-xs font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400 ${tooltip ? "cursor-help border-b border-dashed border-slate-400" : ""}`}>
                {title}
              </span>
              {tooltip && <div className="custom-tooltip">{tooltip}</div>}
            </div>
            <div className="w-10 h-10 rounded-xl bg-white dark:bg-slate-800/80 flex items-center justify-center shadow-sm border border-slate-100 dark:border-slate-700">
              {icon}
            </div>
          </div>

          <div className="flex items-end justify-between mt-2">
            <div>
              <div className="text-3xl sm:text-4xl font-extrabold text-slate-900 dark:text-white tabular-nums tracking-tight">
                {displayValue}
              </div>
              {subtitle && (
                <div className="text-xs text-slate-500 dark:text-slate-400 mt-1 font-medium">
                  {subtitle}
                </div>
              )}
            </div>

            <div className="w-20 h-8 relative opacity-0 group-hover:opacity-100 transition-opacity duration-300 hidden sm:block">
              <Sparkline colorClass={`text-${accentColor.split("-")[1]}-500`} dataPoints={sparklineData} />
            </div>
          </div>
        </div>
      </div>
    </motion.div>
  );
}

// ─── Category Breakdown Bar Chart Component ────────────────────────
function CategoryBreakdownChart({ data }: { data: Record<string, number> }) {
  const categories = [
    { key: "insufficient_funds", label: "Insufficient Funds", color: "bg-rose-500", text: "text-rose-500" },
    { key: "technical_decline", label: "Technical / Bank Decline", color: "bg-blue-500", text: "text-blue-500" },
    { key: "expired_mandate", label: "Expired Mandate", color: "bg-amber-500", text: "text-amber-500" },
    { key: "unknown", label: "Other / Unknown", color: "bg-purple-500", text: "text-purple-500" },
  ];

  const total = Object.values(data).reduce((acc, curr) => acc + curr, 0) || 1;

  return (
    <div className="glass-card rounded-2xl p-6 border-slate-200 dark:border-slate-700/60 shadow-lg">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h3 className="text-lg font-bold text-slate-900 dark:text-white flex items-center gap-2">
            <PieChart className="w-5 h-5 text-blue-500" />
            Failure Reasons Breakdown
          </h3>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
            Root-cause distribution across all intercepted mandate failures
          </p>
        </div>
        <span className="text-xs font-semibold px-2.5 py-1 rounded-full bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 border border-slate-200 dark:border-slate-700">
          {total} Total Failures
        </span>
      </div>

      <div className="space-y-4">
        {categories.map((cat) => {
          const count = data[cat.key] || 0;
          const percentage = Math.round((count / total) * 100);

          return (
            <div key={cat.key} className="space-y-1.5">
              <div className="flex justify-between text-xs font-semibold">
                <span className="text-slate-700 dark:text-slate-300 flex items-center gap-2">
                  <span className={`w-2.5 h-2.5 rounded-full ${cat.color}`} />
                  {cat.label}
                </span>
                <span className="text-slate-500 dark:text-slate-400 tabular-nums">
                  {count} failures ({percentage}%)
                </span>
              </div>

              <div className="w-full h-3 bg-slate-100 dark:bg-slate-800 rounded-full overflow-hidden p-0.5 border border-slate-200/50 dark:border-slate-700/50">
                <motion.div
                  initial={{ width: 0 }}
                  animate={{ width: `${Math.max(percentage, count > 0 ? 3 : 0)}%` }}
                  transition={{ duration: 1, ease: "easeOut" }}
                  className={`h-full rounded-full ${cat.color}`}
                />
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

// ─── Live Demo Simulator Component ────────────────────────────────
function LiveDemoSimulator({
  onSimulated,
  onNavigate,
}: {
  onSimulated: () => void;
  onNavigate?: (tab: string) => void;
}) {
  const [selectedCategory, setSelectedCategory] = useState("insufficient_funds");
  const [isSimulating, setIsSimulating] = useState(false);
  const [isFullFlowSimulating, setIsFullFlowSimulating] = useState(false);
  const [lastResult, setLastResult] = useState<any>(null);
  const [isPayingLink, setIsPayingLink] = useState(false);

  const categories = [
    { id: "insufficient_funds", label: "Insufficient Funds", bank: "HDFC", amount: 49900, badge: "AI Draft" },
    { id: "technical_decline", label: "Technical Decline", bank: "SBI", amount: 89900, badge: "Auto-Retry" },
    { id: "expired_mandate", label: "Expired Mandate", bank: "ICICI", amount: 149900, badge: "AI Draft" },
    { id: "unknown", label: "Unknown Reason", bank: "AXIS", amount: 299000, badge: "Human Review" },
  ];

  const handleSimulateFailure = async () => {
    setIsSimulating(true);
    try {
      const selected = categories.find((c) => c.id === selectedCategory);
      const res = await simulateFailure({
        category: selectedCategory,
        amount: selected?.amount || 49900,
        bankCode: selected?.bank,
      });
      setLastResult({ ...res, step: "FAILURE_INGESTED" });
      onSimulated();
    } catch (e: any) {
      console.error(e);
    } finally {
      setIsSimulating(false);
    }
  };

  const handleSimulatePaymentPaid = async () => {
    if (!lastResult) return;
    setIsPayingLink(true);
    try {
      const res = await simulatePaymentPaid({
        actionId: lastResult.recoveryActionId,
        amount: lastResult.amount,
      });
      setLastResult((prev: any) => ({ ...prev, ...res, step: "RECOVERED" }));
      onSimulated();
    } catch (e: any) {
      console.error(e);
    } finally {
      setIsPayingLink(false);
    }
  };

  const handleSimulateFullFlow = async () => {
    setIsFullFlowSimulating(true);
    try {
      const selected = categories.find((c) => c.id === selectedCategory);
      const res = await simulateFullFlow({
        category: selectedCategory,
        amount: selected?.amount || 49900,
      });
      setLastResult({ ...res, step: "FULL_FLOW_COMPLETED" });
      onSimulated();
    } catch (e: any) {
      console.error(e);
    } finally {
      setIsFullFlowSimulating(false);
    }
  };

  return (
    <div className="glass-card rounded-2xl p-6 border-blue-500/30 bg-gradient-to-r from-blue-500/5 via-purple-500/5 to-cyan-500/5 shadow-xl relative overflow-hidden">
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 mb-4">
        <div>
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg bg-blue-600 dark:bg-blue-500 flex items-center justify-center text-white shadow-md shadow-blue-500/20">
              <Sparkles className="w-4 h-4" />
            </div>
            <h3 className="text-base font-bold text-slate-900 dark:text-white">
              Live Demo Simulator <span className="text-xs font-semibold text-blue-500 uppercase tracking-wider ml-1">Jury Fast-Track</span>
            </h3>
          </div>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
            Simulate real Razorpay mandate failures, observe real-time AI drafting, and test closed-loop recovery without mock credentials.
          </p>
        </div>

        {/* Action Buttons */}
        <div className="flex items-center gap-2.5 flex-wrap">
          <button
            onClick={handleSimulateFailure}
            disabled={isSimulating || isFullFlowSimulating}
            className="flex items-center gap-2 px-4 py-2 rounded-xl bg-white dark:bg-slate-800 hover:bg-slate-100 dark:hover:bg-slate-700 text-slate-800 dark:text-slate-100 text-xs font-bold border border-slate-300 dark:border-slate-700 shadow-sm transition-all active:scale-95 disabled:opacity-50"
          >
            <Play className={`w-3.5 h-3.5 text-blue-500 ${isSimulating ? "animate-spin" : ""}`} />
            {isSimulating ? "Injecting Webhook..." : "1. Simulate Failure"}
          </button>

          <button
            onClick={handleSimulateFullFlow}
            disabled={isSimulating || isFullFlowSimulating}
            className="flex items-center gap-2 px-4 py-2 rounded-xl bg-gradient-to-r from-blue-600 via-indigo-600 to-purple-600 hover:from-blue-500 hover:to-purple-500 text-white text-xs font-bold shadow-md shadow-indigo-500/25 transition-all active:scale-95 disabled:opacity-50"
          >
            <Zap className={`w-3.5 h-3.5 text-amber-300 ${isFullFlowSimulating ? "animate-bounce" : ""}`} />
            {isFullFlowSimulating ? "Simulating End-to-End..." : "🚀 Full 5-Stage Recovery (1-Click)"}
          </button>
        </div>
      </div>

      {/* Category Pills */}
      <div className="flex items-center gap-2 overflow-x-auto pb-1">
        <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider mr-1 shrink-0">Failure Scenario:</span>
        {categories.map((c) => {
          const isSelected = selectedCategory === c.id;
          return (
            <button
              key={c.id}
              onClick={() => setSelectedCategory(c.id)}
              className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all shrink-0 flex items-center gap-1.5 ${
                isSelected
                  ? "bg-blue-600 text-white shadow-sm shadow-blue-600/30"
                  : "bg-white/70 dark:bg-slate-800/70 text-slate-600 dark:text-slate-300 hover:bg-white dark:hover:bg-slate-800 border border-slate-200 dark:border-slate-700/60"
              }`}
            >
              <span>{c.label}</span>
              <span className={`text-[10px] px-1.5 py-0.5 rounded-md font-mono ${
                isSelected ? "bg-white/20 text-white" : "bg-slate-100 dark:bg-slate-700 text-slate-500 dark:text-slate-400"
              }`}>
                {c.bank} · ₹{(c.amount / 100).toLocaleString("en-IN")}
              </span>
            </button>
          );
        })}
      </div>

      {/* Live Simulation Banner / Quick Recovery Action */}
      {lastResult && (
        <motion.div
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          className="mt-3.5 pt-3.5 border-t border-blue-500/20 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs"
        >
          <div className="flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-emerald-500 animate-ping" />
            <span className="font-semibold text-slate-800 dark:text-slate-200">
              {lastResult.step === "FULL_FLOW_COMPLETED"
                ? "🎉 Complete 5-Stage Cycle Executed: Mandate failed → AI drafted → Dispatched → Customer paid → Revenue salvaged!"
                : lastResult.step === "RECOVERED"
                ? "✅ Customer Payment Intercepted: Payment link settled & revenue closed loop verified!"
                : `⚡ Ingested ${lastResult.paymentId || "failure"} (${lastResult.category}) — AI draft generated & queued for human review.`}
            </span>
          </div>

          <div className="flex items-center gap-2 shrink-0">
            {lastResult.step === "FAILURE_INGESTED" && onNavigate && (
              <>
                <button
                  onClick={() => onNavigate("approvals")}
                  className="px-3 py-1 rounded-lg bg-purple-500/10 hover:bg-purple-500/20 text-purple-600 dark:text-purple-400 font-bold border border-purple-500/30 flex items-center gap-1"
                >
                  Review in Queue <ArrowRight className="w-3 h-3" />
                </button>
                <button
                  onClick={handleSimulatePaymentPaid}
                  disabled={isPayingLink}
                  className="px-3 py-1 rounded-lg bg-emerald-500/10 hover:bg-emerald-500/20 text-emerald-600 dark:text-emerald-400 font-bold border border-emerald-500/30 flex items-center gap-1"
                >
                  <TrendingUp className="w-3 h-3" />
                  {isPayingLink ? "Simulating..." : "Simulate Customer Payment"}
                </button>
              </>
            )}
          </div>
        </motion.div>
      )}
    </div>
  );
}

// ─── Recovery Funnel Component ─────────────────────────────────────
function RecoveryFunnel({
  summary,
  isRecoveredPulse,
}: {
  summary: DashboardSummary;
  isRecoveredPulse?: boolean;
}) {
  const steps = [
    {
      label: "1. Mandates Failed",
      count: summary.failedCount || 0,
      icon: <XCircle className="w-4 h-4 text-rose-500" />,
      color: "from-rose-500 to-rose-400",
      description: "Intercepted by webhook",
    },
    {
      label: "2. AI Drafted",
      count: summary.draftsGenerated || summary.failedCount || 0,
      icon: <Bot className="w-4 h-4 text-purple-500" />,
      color: "from-purple-500 to-purple-400",
      description: "Root cause categorized",
    },
    {
      label: "3. Approved",
      count: summary.draftsApproved || 0,
      icon: <CheckCircle className="w-4 h-4 text-amber-500" />,
      color: "from-amber-500 to-amber-400",
      description: "Human reviewed & signed",
    },
    {
      label: "4. Dispatched",
      count: summary.messagesDispatched || summary.draftsApproved || 0,
      icon: <Send className="w-4 h-4 text-blue-500" />,
      color: "from-blue-500 to-cyan-400",
      description: "Razorpay link sent to customer",
    },
    {
      label: "5. Revenue Recovered",
      count: summary.paymentsRecovered || 0,
      icon: <TrendingUp className="w-4 h-4 text-emerald-500" />,
      color: "from-emerald-500 to-emerald-400",
      description: `₹${Math.round(summary.recoveredAmount / 100).toLocaleString("en-IN")} settled`,
      highlight: true,
    },
  ];

  const maxVal = Math.max(...steps.map((s) => s.count), 1);
  const [isExporting, setIsExporting] = useState(false);

  const handleExportCsv = async () => {
    setIsExporting(true);
    try {
      await exportRecoveryLedgerCsv();
    } catch (e: any) {
      console.error("Failed to export recovery ledger CSV:", e);
    } finally {
      setIsExporting(false);
    }
  };

  return (
    <div className="glass-card rounded-2xl p-6 border-slate-200 dark:border-slate-700/60 shadow-lg relative overflow-hidden">
      <div className="flex items-center justify-between mb-6 flex-wrap gap-3">
        <div>
          <h3 className="text-lg font-bold text-slate-900 dark:text-white flex items-center gap-2">
            <Filter className="w-5 h-5 text-emerald-500" />
            End-to-End Recovery Funnel
          </h3>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
            Real-time pipeline progression from failure detection to revenue recovery
          </p>
        </div>
        <div className="flex items-center gap-2.5 flex-wrap">
          <button
            onClick={handleExportCsv}
            disabled={isExporting}
            className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-full bg-slate-100 dark:bg-slate-800 hover:bg-slate-200 dark:hover:bg-slate-700 text-slate-700 dark:text-slate-200 text-xs font-bold border border-slate-200 dark:border-slate-700 shadow-sm transition-all active:scale-95 disabled:opacity-50"
            title="Download full recovery ledger for accounting reconciliation (CSV)"
          >
            <Download className={`w-3.5 h-3.5 text-blue-500 ${isExporting ? "animate-bounce" : ""}`} />
            <span>{isExporting ? "Exporting..." : "Export Recovery Ledger (.CSV)"}</span>
          </button>
          <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-emerald-50 dark:bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 text-xs font-bold border border-emerald-200 dark:border-emerald-500/20 shadow-sm">
            <Activity className="w-3.5 h-3.5" /> {summary.recoveryRate}% Recovery Rate
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3">
        {steps.map((step, idx) => {
          const widthPct = Math.max(15, Math.round((step.count / maxVal) * 100));
          const isStep5 = idx === 4;

          return (
            <motion.div
              key={step.label}
              animate={isStep5 && isRecoveredPulse ? { scale: [1, 1.04, 1] } : {}}
              transition={{ duration: 0.8, repeat: isRecoveredPulse ? 3 : 0 }}
              className={`relative p-4 rounded-xl border flex flex-col justify-between shadow-sm overflow-hidden transition-all duration-300 ${
                isStep5
                  ? `bg-emerald-500/10 border-emerald-500/40 dark:bg-emerald-500/10 dark:border-emerald-500/40 ${
                      isRecoveredPulse ? "ring-2 ring-emerald-500 shadow-lg shadow-emerald-500/30" : ""
                    }`
                  : "bg-white/60 dark:bg-slate-800/60 border-slate-200 dark:border-slate-700/50"
              }`}
            >
              {/* Pulsing Highlight Overlay for Step 5 */}
              {isStep5 && isRecoveredPulse && (
                <div className="absolute inset-0 pointer-events-none bg-gradient-to-r from-emerald-400/20 via-teal-400/20 to-emerald-400/20 animate-pulse" />
              )}

              <div className="flex items-center justify-between mb-2 relative z-10">
                <span className="text-xs font-bold text-slate-700 dark:text-slate-200 flex items-center gap-1.5">
                  {step.icon}
                  {step.label}
                </span>
                <span className="text-xs font-semibold text-slate-400">Step {idx + 1}</span>
              </div>

              <div className="my-2 relative z-10">
                <div className="text-2xl font-extrabold text-slate-900 dark:text-white tabular-nums tracking-tight flex items-baseline gap-1.5">
                  {step.count}
                  {isStep5 && (
                    <span className="text-xs font-bold text-emerald-600 dark:text-emerald-400">
                      salvaged
                    </span>
                  )}
                </div>
                <div className="text-[11px] text-slate-500 dark:text-slate-400 mt-0.5 font-medium">
                  {step.description}
                </div>
              </div>

              <div className="w-full h-2 bg-slate-100 dark:bg-slate-700/50 rounded-full overflow-hidden mt-3 relative z-10">
                <motion.div
                  initial={{ width: 0 }}
                  animate={{ width: `${widthPct}%` }}
                  transition={{ duration: 1, delay: idx * 0.1 }}
                  className={`h-full rounded-full bg-gradient-to-r ${step.color}`}
                />
              </div>
            </motion.div>
          );
        })}
      </div>
    </div>
  );
}

// ─── Skeletons & Error ────────────────────────────────────────────
function GlassSkeletons({ count, type }: { count: number; type: "card" | "row" }) {
  return (
    <div className={type === "card" ? "grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4" : "space-y-4"}>
      {Array.from({ length: count }).map((_, i) => (
        <div key={i} className={`skeleton-shimmer ${type === "card" ? "h-36 w-full rounded-2xl" : "h-20 w-full rounded-xl"}`} />
      ))}
    </div>
  );
}

function ErrorState({ message }: { message: string }) {
  return (
    <motion.div initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }} className="glass-card rounded-2xl p-10 flex flex-col items-center justify-center text-center space-y-4 border-rose-500/20 shadow-xl">
      <div className="w-16 h-16 rounded-full bg-rose-100 dark:bg-rose-500/10 flex items-center justify-center mb-2">
        <AlertTriangle className="w-8 h-8 text-rose-600 dark:text-rose-400" />
      </div>
      <h3 className="font-bold text-slate-900 dark:text-white text-xl">System Interruption</h3>
      <p className="text-sm text-slate-600 dark:text-slate-400 max-w-md font-medium">{message}</p>
    </motion.div>
  );
}

// ─── Main Dashboard Page Component ────────────────────────────────
export function DashboardPage({
  isEnabled,
  refreshTrigger,
  onNavigate,
}: {
  isEnabled: boolean;
  refreshTrigger?: number;
  onNavigate?: (tab: string) => void;
}) {
  const [data, setData] = useState<DashboardSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [isRecoveredPulse, setIsRecoveredPulse] = useState(false);
  const prevRecoveredRef = useRef<number>(0);

  const refreshData = () => {
    fetchDashboardSummary()
      .then((res) => {
        if (res && res.paymentsRecovered > prevRecoveredRef.current) {
          setIsRecoveredPulse(true);
          setTimeout(() => setIsRecoveredPulse(false), 4000);
        }
        if (res) {
          prevRecoveredRef.current = res.paymentsRecovered;
        }
        setData(res);
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    refreshData();
    const interval = setInterval(refreshData, 10000);
    return () => clearInterval(interval);
  }, []);

  useEffect(() => {
    refreshData();
  }, [refreshTrigger]);

  if (loading) return <GlassSkeletons count={4} type="card" />;
  if (error) return <ErrorState message={error} />;

  const safeData: DashboardSummary = data || {
    recoveredAmount: 0,
    failedCount: 0,
    pendingApprovalsCount: 0,
    blockedDraftsCount: 0,
    totalPaymentsProcessed: 0,
    successfulPaymentsCount: 0,
    successRate: 0.0,
    avgResolutionTimeMinutes: 4.2,
    failuresByCategory: {
      insufficient_funds: 0,
      technical_decline: 0,
      expired_mandate: 0,
      unknown: 0,
    },
    draftsGenerated: 0,
    draftsApproved: 0,
    messagesDispatched: 0,
    paymentsRecovered: 0,
    recoveryRate: 0.0,
  };

  const recoveredInRupees = Math.round(safeData.recoveredAmount / 100);

  return (
    <div className="space-y-8 pb-12">
      <HeroStory />

      {/* Live Demo Simulator Bar */}
      <LiveDemoSimulator onSimulated={refreshData} onNavigate={onNavigate} />

      <motion.div variants={stagger} initial="hidden" animate="show" className="space-y-8">
        {/* Impact Banner */}
        <AnimatePresence mode="wait">
          {!isEnabled && (
            <motion.div
              key="impact-banner"
              initial={{ opacity: 0, height: 0, marginBottom: 0 }}
              animate={{ opacity: 1, height: "auto", marginBottom: 32 }}
              exit={{ opacity: 0, height: 0, marginBottom: 0 }}
              className="overflow-hidden"
            >
              <div className="glass-card rounded-xl p-5 border-slate-300 dark:border-slate-700 bg-slate-100 dark:bg-slate-800/50 flex items-center gap-4 shadow-inner">
                <div className="w-10 h-10 rounded-full bg-slate-200 dark:bg-slate-700 flex items-center justify-center flex-shrink-0">
                  <ShieldAlert className="w-5 h-5 text-slate-500" />
                </div>
                <div className="flex-1">
                  <p className="font-semibold text-slate-800 dark:text-slate-200 text-sm">Standard Protection Mode</p>
                  <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
                    Toggle RecoverMandate AI switch in the header to activate intelligent dunning and automated recovery flows.
                  </p>
                </div>
              </div>
            </motion.div>
          )}
        </AnimatePresence>

        {/* Top 4 ROI KPI Cards */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 sm:gap-6">
          <motion.div variants={fadeUp}>
            <KPICard
              title="Recovery Rate"
              displayValue={
                <>
                  <CountUpDecimal target={isEnabled ? safeData.recoveryRate : 0} decimals={1} />%
                </>
              }
              subtitle={`${safeData.paymentsRecovered} payments salvaged`}
              icon={<TrendingUp className={`w-5 h-5 ${isEnabled ? "text-emerald-600 dark:text-emerald-400" : "text-slate-400"}`} />}
              glowClass={isEnabled ? "glow-emerald" : "opacity-75 saturate-0"}
              accentColor={isEnabled ? "from-emerald-500 to-emerald-400" : "from-slate-400 to-slate-500"}
              tooltip="Percentage of failed mandates successfully recovered through automated retries and payment links."
              sparklineData={isEnabled ? [40, 55, 65, 70, 80, safeData.recoveryRate] : [0, 0, 0, 0, 0, 0]}
            />
          </motion.div>

          <motion.div variants={fadeUp}>
            <KPICard
              title="Mean Time to Resolve (MTTR)"
              displayValue={
                <>
                  <CountUpDecimal target={safeData.avgResolutionTimeMinutes} decimals={1} />m
                </>
              }
              subtitle="From failure to dispatch"
              icon={<Clock className={`w-5 h-5 ${isEnabled ? "text-blue-600 dark:text-blue-400" : "text-slate-400"}`} />}
              glowClass={isEnabled ? "glow-blue" : "opacity-75 saturate-0"}
              accentColor={isEnabled ? "from-blue-600 to-cyan-500" : "from-slate-400 to-slate-500"}
              tooltip="Average duration in minutes from webhook failure ingestion to recovery action approval & dispatch."
              sparklineData={[12, 10, 8, 6, 5, 4.2]}
            />
          </motion.div>

          <motion.div variants={fadeUp}>
            <KPICard
              title="Total Revenue Recovered"
              displayValue={
                <span className="flex items-center">
                  <IndianRupee className="w-7 h-7 -mr-1" />
                  <CountUp target={isEnabled ? recoveredInRupees : 0} />
                </span>
              }
              subtitle="Recovered subscription revenue"
              icon={<IndianRupee className={`w-5 h-5 ${isEnabled ? "text-cyan-600 dark:text-cyan-400" : "text-slate-400"}`} />}
              glowClass={isEnabled ? "glow-cyan" : "opacity-75 saturate-0"}
              accentColor={isEnabled ? "from-cyan-500 to-blue-500" : "from-slate-400 to-slate-500"}
              tooltip="Total rupee value recovered through subscription charges and Razorpay recovery links."
              sparklineData={isEnabled ? [5000, 12000, 25000, 38000, recoveredInRupees] : [0, 0, 0, 0, 0]}
            />
          </motion.div>

          <motion.div variants={fadeUp}>
            <KPICard
              title="Pending Action Queue"
              displayValue={<CountUp target={isEnabled ? safeData.pendingApprovalsCount : 0} />}
              subtitle={`${safeData.blockedDraftsCount} drafts in guardrail hold`}
              icon={<AlertTriangle className={`w-5 h-5 ${isEnabled ? "text-amber-600 dark:text-amber-400" : "text-slate-400"}`} />}
              glowClass={isEnabled ? "glow-amber" : "opacity-75 saturate-0"}
              accentColor={isEnabled ? "from-amber-500 to-amber-400" : "from-slate-400 to-slate-500"}
              tooltip="AI-drafted recovery communications waiting for human review."
              sparklineData={isEnabled ? [3, 8, 4, 12, safeData.pendingApprovalsCount] : [0, 0, 0, 0, 0]}
            />
          </motion.div>
        </div>

        {/* Recovery Funnel */}
        <motion.div variants={fadeUp}>
          <RecoveryFunnel summary={safeData} isRecoveredPulse={isRecoveredPulse} />
        </motion.div>

        {/* Category Breakdown */}
        <motion.div variants={fadeUp}>
          <CategoryBreakdownChart data={safeData.failuresByCategory || {}} />
        </motion.div>
      </motion.div>
    </div>
  );
}

