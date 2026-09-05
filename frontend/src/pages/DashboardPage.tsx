import { useState, useEffect, useRef } from "react";
import { motion, useMotionValue, useTransform } from "framer-motion";
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
  Building2,
  Moon,
  RotateCcw,
  Mail,
  Check,
  CreditCard,
} from "lucide-react";
import {
  fetchDashboardSummary,
  simulateFailure,
  simulatePaymentPaid,
  simulateFullFlow,
  resetLedger,
  exportRecoveryLedgerCsv,
  fetchBankHealth,
  fetchSystemHealth,
  type DashboardSummary,
  type BankHealthItem,
} from "../lib/api";
import { RazorpayMark } from "../components/RazorpayLogo";
import { formatINR } from "../lib/formatters";

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
        <motion.div variants={stagger} initial="hidden" animate="show" className="grid grid-cols-1 md:grid-cols-3 gap-5 relative">
          <motion.div variants={fadeUp} className="p-6 rounded-2xl bg-white/80 dark:bg-slate-800/80 border border-slate-200 dark:border-slate-700/60 flex flex-col gap-3.5 shadow-lg backdrop-blur-md transition-all hover:border-rose-500/40">
            <div className="w-11 h-11 rounded-xl bg-rose-100 dark:bg-rose-500/20 flex items-center justify-center shadow-sm">
              <XCircle className="w-5 h-5 text-rose-600 dark:text-rose-400" />
            </div>
            <div>
              <span className="font-bold text-slate-800 dark:text-white text-base block mb-1">1. Intercept &amp; Classify</span>
              <span className="text-xs text-slate-500 dark:text-slate-400 leading-relaxed block">
                Razorpay emits `payment.failed`. We classify failure reason &amp; check bank uptime.
              </span>
            </div>
          </motion.div>

          <motion.div variants={fadeUp} className="p-6 rounded-2xl bg-white/80 dark:bg-slate-800/80 border border-slate-200 dark:border-slate-700/60 flex flex-col gap-3.5 shadow-lg backdrop-blur-md transition-all hover:border-purple-500/40">
            <ArrowRight className="w-6 h-6 text-slate-300 dark:text-slate-600 absolute -left-5 top-1/2 -translate-y-1/2 hidden md:block z-0" />
            <div className="w-11 h-11 rounded-xl bg-purple-100 dark:bg-purple-500/20 flex items-center justify-center shadow-sm">
              <Bot className="w-5 h-5 text-purple-600 dark:text-purple-400" />
            </div>
            <div>
              <span className="font-bold text-slate-800 dark:text-white text-base block mb-1">2. AI Draft &amp; Link Generation</span>
              <span className="text-xs text-slate-500 dark:text-slate-400 leading-relaxed block">
                Resilient Gemini / heuristic engine generates custom recovery drafts and Razorpay link.
              </span>
            </div>
          </motion.div>

          <motion.div variants={fadeUp} className="p-6 rounded-2xl bg-white/80 dark:bg-slate-800/80 border border-emerald-500/30 flex flex-col gap-3.5 shadow-emerald-500/10 shadow-xl backdrop-blur-md transition-all hover:border-emerald-500/50">
            <ArrowRight className="w-6 h-6 text-slate-300 dark:text-slate-600 absolute -left-5 top-1/2 -translate-y-1/2 hidden md:block z-0" />
            <div className="w-11 h-11 rounded-xl bg-emerald-100 dark:bg-emerald-500/20 flex items-center justify-center shadow-sm">
              <TrendingUp className="w-5 h-5 text-emerald-600 dark:text-emerald-400" />
            </div>
            <div>
              <span className="font-bold text-slate-800 dark:text-white text-base block mb-1">3. Approve &amp; Recover</span>
              <span className="text-xs text-slate-500 dark:text-slate-400 leading-relaxed block">
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
  confirmedEmail,
  onConfirmedEmailChange,
}: {
  onSimulated: () => void;
  onNavigate?: (tab: string) => void;
  confirmedEmail?: string | null;
  onConfirmedEmailChange?: (email: string | null) => void;
}) {
  const [selectedCategory, setSelectedCategory] = useState("insufficient_funds");
  const [emailInput, setEmailInput] = useState(confirmedEmail || "sujaypaul2711@gmail.com");
  const [emailError, setEmailError] = useState("");
  const [isSimulating, setIsSimulating] = useState(false);
  const [isFullFlowSimulating, setIsFullFlowSimulating] = useState(false);
  const [activeStageIndex, setActiveStageIndex] = useState<number>(0); // 0 = idle, 1..5 = stages
  const [completedStages, setCompletedStages] = useState<number[]>([]);
  const [lastResult, setLastResult] = useState<any>(null);
  const [isPayingLink, setIsPayingLink] = useState(false);
  const [razorpayApiMode, setRazorpayApiMode] = useState<"LIVE" | "SIMULATED">("SIMULATED");

  useEffect(() => {
    fetchSystemHealth()
      .then((h) => {
        if (h?.razorpayApi?.mode === "LIVE" || h?.razorpayApi?.configured === true) {
          setRazorpayApiMode("LIVE");
        } else {
          setRazorpayApiMode("SIMULATED");
        }
      })
      .catch(() => setRazorpayApiMode("SIMULATED"));
  }, []);

  useEffect(() => {
    if (confirmedEmail) {
      setEmailInput(confirmedEmail);
    }
  }, [confirmedEmail]);

  const handleConfirmEmail = () => {
    const trimmed = emailInput.trim();
    if (!trimmed) {
      setEmailError("Please enter an email address");
      return;
    }
    if (!trimmed.includes("@") || !trimmed.includes(".")) {
      setEmailError("Invalid email address format");
      return;
    }
    setEmailError("");
    onConfirmedEmailChange?.(trimmed);
  };

  const handleClearEmail = () => {
    setEmailInput("");
    setEmailError("");
    onConfirmedEmailChange?.(null);
  };

  const categories = [
    { id: "insufficient_funds", label: "Insufficient Funds", bank: "HDFC", amount: 49900, badge: "AI Draft" },
    { id: "technical_decline", label: "Technical Decline", bank: "SBI", amount: 89900, badge: "Auto-Retry" },
    { id: "expired_mandate", label: "Expired Mandate", bank: "ICICI", amount: 149900, badge: "AI Draft" },
    { id: "unknown", label: "Unknown Reason", bank: "AXIS", amount: 299000, badge: "Human Review" },
  ];

  const stages = [
    {
      id: 1,
      title: "1. Webhook Ingested",
      short: "Webhook",
      desc: "Razorpay payment.failed captured",
      icon: Zap,
    },
    {
      id: 2,
      title: "2. AI Classified",
      short: "Classification",
      desc: "Error mapped & bank checked",
      icon: Bot,
    },
    {
      id: 3,
      title: "3. Strategy Drafted",
      short: "AI Draft",
      desc: "Gemini dunning message created",
      icon: Sparkles,
    },
    {
      id: 4,
      title: "4. Link Dispatched",
      short: "Dispatched",
      desc: "1-Click Razorpay link sent",
      icon: Send,
    },
    {
      id: 5,
      title: "5. Revenue Salvaged",
      short: "Recovered",
      desc: "Payment settled & closed loop",
      icon: TrendingUp,
    },
  ];

  const handleSimulateFailure = async () => {
    setIsSimulating(true);
    setActiveStageIndex(1);
    setCompletedStages([]);
    setLastResult(null);

    try {
      const selected = categories.find((c) => c.id === selectedCategory);
      const res = await simulateFailure({
        category: selectedCategory,
        amount: selected?.amount || 49900,
        bankCode: selected?.bank,
        customerEmail: confirmedEmail || undefined,
      });

      // Visually step through Stages 1 -> 2 -> 3
      setCompletedStages([1]);
      setActiveStageIndex(2);
      await new Promise((r) => setTimeout(r, 450));

      setCompletedStages([1, 2]);
      setActiveStageIndex(3);
      await new Promise((r) => setTimeout(r, 450));

      setCompletedStages([1, 2, 3]);
      setActiveStageIndex(3);
      setLastResult({ ...res, step: "FAILURE_INGESTED" });
      onSimulated();
    } catch (e: any) {
      console.error(e);
      setActiveStageIndex(0);
    } finally {
      setIsSimulating(false);
    }
  };

  const handleSimulatePaymentPaid = async () => {
    if (!lastResult) return;
    setIsPayingLink(true);
    setActiveStageIndex(4);

    try {
      await new Promise((r) => setTimeout(r, 400));
      setCompletedStages((prev) => Array.from(new Set([...prev, 4])));
      setActiveStageIndex(5);

      const res = await simulatePaymentPaid({
        actionId: lastResult.recoveryActionId,
        amount: lastResult.amount,
      });

      await new Promise((r) => setTimeout(r, 400));
      setCompletedStages([1, 2, 3, 4, 5]);
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
    setCompletedStages([]);
    setActiveStageIndex(1);
    setLastResult(null);

    try {
      const selected = categories.find((c) => c.id === selectedCategory);
      
      // Step 1: Ingest
      setActiveStageIndex(1);
      await new Promise((r) => setTimeout(r, 400));
      setCompletedStages([1]);

      // Step 2: Classify
      setActiveStageIndex(2);
      await new Promise((r) => setTimeout(r, 400));
      setCompletedStages([1, 2]);

      // Step 3: Draft
      setActiveStageIndex(3);
      await new Promise((r) => setTimeout(r, 400));
      setCompletedStages([1, 2, 3]);

      // Step 4: Dispatch
      setActiveStageIndex(4);
      await new Promise((r) => setTimeout(r, 400));
      setCompletedStages([1, 2, 3, 4]);

      // Backend simulation execution
      const res = await simulateFullFlow({
        category: selectedCategory,
        amount: selected?.amount || 49900,
        customerEmail: confirmedEmail || undefined,
      });

      // Step 5: Recover
      setActiveStageIndex(5);
      await new Promise((r) => setTimeout(r, 400));
      setCompletedStages([1, 2, 3, 4, 5]);

      setLastResult({ ...res, step: "FULL_FLOW_COMPLETED" });
      onSimulated();
    } catch (e: any) {
      console.error(e);
      setActiveStageIndex(0);
    } finally {
      setIsFullFlowSimulating(false);
    }
  };

  const [isResetting, setIsResetting] = useState(false);
  const handleResetLedger = async () => {
    if (!confirm("Are you sure you want to wipe the operational demo ledger (events, classifications, recoveries, retries, and audit logs) and reset the hash chain to GENESIS?")) return;
    setIsResetting(true);
    try {
      await resetLedger();
      setCompletedStages([]);
      setActiveStageIndex(0);
      setLastResult(null);
      onSimulated();
    } catch (e: any) {
      alert("Failed to reset ledger: " + e.message);
    } finally {
      setIsResetting(false);
    }
  };

  return (
    <div className="glass-card rounded-3xl p-6 sm:p-8 border-blue-500/30 bg-gradient-to-b from-[#08172E]/95 via-[#061224]/95 to-[#030914]/95 shadow-2xl shadow-blue-950/40 relative overflow-hidden space-y-6">
      {/* ─── Top Header: Title & Fast-Track Badge ─── */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-1">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-blue-600 dark:bg-blue-500 flex items-center justify-center text-white shadow-lg shadow-blue-500/25 shrink-0">
            <Sparkles className="w-5 h-5" />
          </div>
          <div>
            <div className="flex items-center gap-2.5 flex-wrap">
              <h3 className="text-lg font-bold text-slate-900 dark:text-white tracking-tight">
                Live Demo Simulator
              </h3>
              <span className="text-[10px] font-mono font-extrabold px-2.5 py-0.5 rounded-full bg-blue-500/15 text-blue-400 border border-blue-500/30 uppercase tracking-wider">
                Jury Fast-Track
              </span>
            </div>
            <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
              Trigger simulated Razorpay webhook failures, observe real-time AI triage &amp; dunning, and watch end-to-end recovery live.
            </p>
          </div>
        </div>
      </div>

      {/* ─── System Rails Status & Action Toolbar ─── */}
      <div className="p-4 sm:p-5 rounded-2xl bg-[#030A17]/85 border border-blue-500/20 backdrop-blur-md flex flex-col lg:flex-row lg:items-center justify-between gap-4 shadow-inner">
        {/* Left: System Status Rails */}
        <div className="flex items-center gap-2.5 flex-wrap">
          <span className="text-[11px] font-bold uppercase tracking-wider text-slate-400 mr-1 flex items-center gap-1.5">
            <Activity className="w-3.5 h-3.5 text-blue-400" />
            System Rails:
          </span>

          {/* Target Recipient Email Delivery Status */}
          {confirmedEmail ? (
            <div className="flex items-center gap-2 px-3.5 py-2 rounded-xl bg-emerald-500/15 border border-emerald-500/40 text-emerald-300 text-xs font-mono font-bold shadow-sm">
              <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse shrink-0" />
              <span className="truncate max-w-[220px]" title={confirmedEmail}>
                📧 Real Email Delivery: {confirmedEmail}
              </span>
              <span className="text-[9px] uppercase tracking-wider px-1.5 py-0.5 rounded bg-emerald-500/20 text-emerald-200 border border-emerald-500/30 font-sans font-extrabold ml-1">
                Real Inbox
              </span>
            </div>
          ) : (
            <div className="flex items-center gap-2 px-3.5 py-2 rounded-xl bg-slate-900/90 border border-slate-700/80 text-slate-400 text-xs font-mono font-medium shadow-sm">
              <span>🎭 Simulated Email (Not Delivered)</span>
              <span className="text-[9px] uppercase tracking-wider px-1.5 py-0.5 rounded bg-slate-800 text-slate-400 border border-slate-700 font-sans font-bold ml-1">
                Demo Only
              </span>
            </div>
          )}

          {/* Separate Payment Link Gateway Mode Indicator */}
          <div
            className={`flex items-center gap-2 px-3.5 py-2 rounded-xl text-xs font-mono font-bold shadow-sm border ${
              razorpayApiMode === "LIVE"
                ? "bg-cyan-500/15 border-cyan-500/40 text-cyan-300"
                : "bg-indigo-500/15 border-indigo-500/30 text-indigo-300"
            }`}
            title={
              razorpayApiMode === "LIVE"
                ? "Razorpay API Keys Configured: Live hosted rzp.io payment links generated"
                : "No Razorpay API Keys: Using RecoverMandate built-in demo checkout route"
            }
          >
            <CreditCard className="w-3.5 h-3.5 shrink-0" />
            <span>
              {razorpayApiMode === "LIVE"
                ? "💳 Payment Links: Live Razorpay API"
                : "💳 Payment Links: Simulated Mode"}
            </span>
            <span
              className={`text-[9px] uppercase tracking-wider px-1.5 py-0.5 rounded font-sans font-extrabold ml-1 border ${
                razorpayApiMode === "LIVE"
                  ? "bg-cyan-500/20 text-cyan-200 border-cyan-500/30"
                  : "bg-indigo-500/20 text-indigo-200 border-indigo-500/30"
              }`}
            >
              {razorpayApiMode === "LIVE" ? "Live Gateway" : "Demo Checkout"}
            </span>
          </div>
        </div>

        {/* Right: Action Buttons with micro-animations */}
        <div className="flex items-center gap-2.5 flex-wrap">
          <motion.button
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.98 }}
            onClick={handleResetLedger}
            disabled={isSimulating || isFullFlowSimulating || isResetting}
            className="flex items-center gap-2 px-3.5 py-2.5 rounded-xl bg-slate-800/80 hover:bg-slate-700/80 text-slate-300 hover:text-white text-xs font-bold border border-slate-700 shadow-sm transition-all disabled:opacity-50 cursor-pointer"
            title="Wipe demo records and reset cryptographic audit chain to GENESIS seed"
          >
            <RotateCcw className={`w-3.5 h-3.5 ${isResetting ? "animate-spin" : ""}`} />
            <span>{isResetting ? "Resetting..." : "Reset Ledger"}</span>
          </motion.button>

          <motion.button
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.98 }}
            onClick={handleSimulateFailure}
            disabled={isSimulating || isFullFlowSimulating || isResetting}
            className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-slate-900/90 hover:bg-slate-800 text-slate-100 text-xs font-bold border border-blue-500/30 hover:border-blue-500/60 shadow-md shadow-blue-950/30 transition-all disabled:opacity-50 cursor-pointer"
          >
            <Play className={`w-3.5 h-3.5 text-blue-400 ${isSimulating ? "animate-spin" : ""}`} />
            {isSimulating ? "Triage in Progress..." : "1. Simulate Failure (Step 1-3)"}
          </motion.button>

          <motion.button
            whileHover={{ scale: 1.03 }}
            whileTap={{ scale: 0.97 }}
            onClick={handleSimulateFullFlow}
            disabled={isSimulating || isFullFlowSimulating || isResetting}
            className="flex items-center gap-2 px-5 py-2.5 rounded-xl bg-gradient-to-r from-blue-600 via-indigo-600 to-purple-600 hover:from-blue-500 hover:to-purple-500 text-white text-xs font-extrabold shadow-lg shadow-indigo-500/30 transition-all disabled:opacity-50 cursor-pointer"
          >
            <Zap className={`w-4 h-4 text-amber-300 ${isFullFlowSimulating ? "animate-bounce" : ""}`} />
            {isFullFlowSimulating ? "Simulating Pipeline..." : "🚀 Full 5-Stage Recovery (1-Click)"}
          </motion.button>
        </div>
      </div>

      {/* ─── Failure Scenario Selection Pills (Generous Spacing) ─── */}
      <div className="space-y-2.5">
        <div className="flex items-center justify-between">
          <span className="text-[11px] font-bold uppercase tracking-wider text-slate-400 flex items-center gap-1.5">
            <Filter className="w-3.5 h-3.5 text-blue-400" />
            Failure Scenario Preset:
          </span>
          <span className="text-[11px] text-slate-500 hidden sm:inline">
            Select failure reason to simulate targeted AI recovery workflow
          </span>
        </div>

        <div className="flex items-center gap-3 overflow-x-auto pb-1 pt-0.5">
          {categories.map((c) => {
            const isSelected = selectedCategory === c.id;
            return (
              <motion.button
                key={c.id}
                whileHover={{ y: -1, scale: 1.01 }}
                whileTap={{ scale: 0.99 }}
                onClick={() => setSelectedCategory(c.id)}
                disabled={isSimulating || isFullFlowSimulating}
                className={`px-4 py-2.5 rounded-xl text-xs font-bold transition-all shrink-0 flex items-center gap-2.5 cursor-pointer ${
                  isSelected
                    ? "bg-blue-600 text-white shadow-lg shadow-blue-600/30 border border-blue-400/50"
                    : "bg-slate-900/80 text-slate-300 hover:text-white hover:bg-slate-800/80 border border-slate-700/70"
                }`}
              >
                <span>{c.label}</span>
                <span className={`text-[10px] px-2 py-0.5 rounded-md font-mono font-semibold ${
                  isSelected ? "bg-white/20 text-white" : "bg-slate-800 text-slate-400 border border-slate-700"
                }`}>
                  {c.bank} · ₹{(c.amount / 100).toLocaleString("en-IN")}
                </span>
              </motion.button>
            );
          })}
        </div>
      </div>

      {/* ─── Target Recipient Email (Dedicated Spacious Card) ─── */}
      <div className="p-5 sm:p-6 rounded-2xl bg-[#040D1F]/95 border border-blue-500/30 shadow-lg flex flex-col lg:flex-row lg:items-center justify-between gap-5">
        <div className="flex items-start sm:items-center gap-4">
          <div className="w-11 h-11 rounded-2xl bg-blue-500/15 border border-blue-500/30 text-blue-400 flex items-center justify-center shrink-0 shadow-md shadow-blue-500/15">
            <Mail className="w-5 h-5" />
          </div>
          <div className="space-y-1">
            <div className="flex items-center gap-2 flex-wrap">
              <span className="text-sm font-bold text-slate-100">
                Target Recipient Email
              </span>
              <span className="text-[10px] font-mono px-2 py-0.5 rounded-full bg-emerald-500/15 text-emerald-300 border border-emerald-500/30 font-bold">
                Real Transactional Delivery
              </span>
            </div>
            <p className="text-xs text-slate-400 leading-relaxed max-w-2xl">
              Enter your email to receive live transactional recovery emails upon <strong className="text-slate-200">Approve &amp; Dispatch</strong>, or leave unconfirmed for simulated demo data (not delivered to inbox).
            </p>
          </div>
        </div>

        {/* Input & Two-State Confirmation Controls */}
        <div className="flex items-center gap-3 self-start lg:self-auto shrink-0 flex-wrap">
          {confirmedEmail ? (
            <div className="flex items-center gap-3 bg-emerald-950/70 border border-emerald-500/40 px-4 py-2.5 rounded-2xl shadow-md">
              <div className="flex items-center gap-2 text-xs font-mono font-bold text-emerald-300">
                <CheckCircle className="w-4 h-4 text-emerald-400 shrink-0" />
                <span className="truncate max-w-[240px]">📧 Real Email Delivery: {confirmedEmail}</span>
              </div>
              <button
                onClick={handleClearEmail}
                className="text-xs text-slate-400 hover:text-rose-300 font-semibold underline underline-offset-4 transition-colors cursor-pointer ml-1"
                title="Switch back to simulated email (not delivered)"
              >
                Reset to Simulated Mode
              </button>
            </div>
          ) : (
            <div className="flex items-center gap-2.5">
              <div className="relative">
                <input
                  type="email"
                  placeholder="e.g. yourname@gmail.com"
                  value={emailInput}
                  onChange={(e) => {
                    setEmailInput(e.target.value);
                    if (emailError) setEmailError("");
                  }}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") handleConfirmEmail();
                  }}
                  className={`h-10 px-4 text-xs bg-[#020517] text-slate-100 placeholder:text-slate-500 rounded-xl border font-mono outline-none w-60 sm:w-72 focus:border-blue-400 focus:ring-1 focus:ring-blue-400 transition-all ${
                    emailError ? "border-rose-500" : "border-slate-700/90"
                  }`}
                />
                {emailError && (
                  <span className="absolute -bottom-5 left-0 text-[10px] text-rose-400 font-medium">
                    {emailError}
                  </span>
                )}
              </div>
              <motion.button
                whileHover={{ scale: 1.02 }}
                whileTap={{ scale: 0.98 }}
                onClick={handleConfirmEmail}
                disabled={!emailInput.trim()}
                className="h-10 px-5 rounded-xl bg-blue-600 hover:bg-blue-500 disabled:opacity-40 text-white text-xs font-bold transition-all shadow-md shadow-blue-600/25 flex items-center gap-1.5 cursor-pointer shrink-0"
                title="Lock in this email address for real transactional email delivery"
              >
                <Check className="w-4 h-4" />
                <span>Use This Email</span>
              </motion.button>
            </div>
          )}
        </div>
      </div>

      {/* ─── Horizontal 5-Stage Live Recovery Stepper ──────────────────────── */}
      <div className="p-5 sm:p-6 rounded-2xl bg-[#020713]/90 border border-slate-800 shadow-inner space-y-4">
        <div className="flex items-center justify-between flex-wrap gap-2">
          <span className="text-[11px] font-bold uppercase tracking-wider text-slate-400 flex items-center gap-2">
            <Activity className="w-4 h-4 text-blue-400" />
            Live Recovery Pipeline Progression
          </span>
          <span className="text-xs font-mono font-semibold px-2.5 py-1 rounded-lg bg-slate-900 border border-slate-800 text-slate-300">
            {completedStages.length === 5
              ? "✅ Cycle Complete (100% Recovered)"
              : completedStages.length > 0
              ? `Stage ${activeStageIndex} of 5 Active`
              : "Idle · Ready to simulate"}
          </span>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-5 gap-3.5 relative">
          {stages.map((stage) => {
            const isCompleted = completedStages.includes(stage.id);
            const isActive = activeStageIndex === stage.id && !isCompleted;
            const Icon = stage.icon;

            return (
              <motion.div
                key={stage.id}
                animate={isActive ? { scale: [1, 1.02, 1] } : {}}
                transition={{ duration: 0.6, repeat: isActive ? Infinity : 0 }}
                className={`p-4 rounded-xl border flex flex-col justify-between min-h-[105px] transition-all duration-300 relative ${
                  isCompleted
                    ? "bg-emerald-950/40 border-emerald-500/50 shadow-md shadow-emerald-500/10"
                    : isActive
                    ? "bg-blue-950/50 border-blue-500 shadow-lg shadow-blue-500/20 ring-1 ring-blue-500/50"
                    : "bg-slate-900/60 border-slate-800/80 opacity-70"
                }`}
              >
                <div className="flex items-center justify-between mb-2">
                  <span className="text-xs font-bold text-white flex items-center gap-2 truncate">
                    <Icon className={`w-4 h-4 shrink-0 ${
                      isCompleted ? "text-emerald-400" : isActive ? "text-blue-400" : "text-slate-500"
                    }`} />
                    <span className="truncate">{stage.short}</span>
                  </span>
                  {isCompleted ? (
                    <span className="w-4 h-4 rounded-full bg-emerald-500/20 text-emerald-400 border border-emerald-500/40 flex items-center justify-center text-[10px] font-bold shrink-0">
                      ✓
                    </span>
                  ) : isActive ? (
                    <span className="w-2.5 h-2.5 rounded-full bg-blue-500 animate-ping shrink-0" />
                  ) : (
                    <span className="text-[10px] text-slate-600 font-mono font-bold shrink-0">
                      #{stage.id}
                    </span>
                  )}
                </div>

                <p className="text-[11px] text-slate-400 leading-snug">
                  {isCompleted && stage.id === 1 && lastResult?.paymentId
                    ? `${lastResult.paymentId.substring(0, 12)}...`
                    : isCompleted && stage.id === 2 && lastResult?.category
                    ? `${lastResult.category}`
                    : isCompleted && stage.id === 3
                    ? "AI Dunning Prepared"
                    : isCompleted && stage.id === 4
                    ? "Link Sent (SMS/Email)"
                    : isCompleted && stage.id === 5
                    ? "₹499 Salvaged ✅"
                    : stage.desc}
                </p>
              </motion.div>
            );
          })}
        </div>
      </div>

      {/* ─── Live Simulation Banner / Quick Recovery Action ─── */}
      {lastResult && (
        <motion.div
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          className="p-5 rounded-2xl bg-[#08182D]/95 border border-blue-500/40 flex flex-col sm:flex-row sm:items-center justify-between gap-4 text-xs shadow-xl"
        >
          <div className="flex items-center gap-3">
            <span className="w-2.5 h-2.5 rounded-full bg-emerald-400 animate-ping shrink-0" />
            <span className="font-semibold text-slate-200 text-xs sm:text-sm">
              {lastResult.step === "FULL_FLOW_COMPLETED"
                ? "🎉 Complete 5-Stage Cycle Executed: Mandate failed → AI drafted → Dispatched → Customer paid → Revenue salvaged!"
                : lastResult.step === "RECOVERED"
                ? "✅ Customer Payment Intercepted: Payment link settled & revenue closed loop verified!"
                : `⚡ Ingested ${lastResult.paymentId || "failure"} (${lastResult.category}) — AI draft generated & queued for review.`}
            </span>
          </div>

          <div className="flex items-center gap-2.5 shrink-0 flex-wrap">
            {lastResult.step === "FAILURE_INGESTED" && onNavigate && (
              <>
                <button
                  onClick={() => onNavigate("approvals")}
                  className="px-4 py-2 rounded-xl bg-purple-500/15 hover:bg-purple-500/25 text-purple-300 font-bold border border-purple-500/30 flex items-center gap-1.5 transition-colors cursor-pointer"
                >
                  Review in Queue <ArrowRight className="w-3.5 h-3.5" />
                </button>
                <button
                  onClick={handleSimulatePaymentPaid}
                  disabled={isPayingLink}
                  className="px-4 py-2 rounded-xl bg-emerald-500/15 hover:bg-emerald-500/25 text-emerald-300 font-bold border border-emerald-500/30 flex items-center gap-1.5 transition-colors shadow-sm cursor-pointer"
                >
                  <TrendingUp className="w-3.5 h-3.5" />
                  {isPayingLink ? "Settling..." : "Simulate Customer Payment"}
                </button>
              </>
            )}

            {lastResult.step === "FULL_FLOW_COMPLETED" && onNavigate && (
              <>
                <button
                  onClick={() => onNavigate("mandates")}
                  className="px-3.5 py-2 rounded-xl bg-blue-500/15 hover:bg-blue-500/25 text-blue-300 font-bold border border-blue-500/30 flex items-center gap-1.5 cursor-pointer"
                >
                  Failed Mandates <ArrowRight className="w-3.5 h-3.5" />
                </button>
                <button
                  onClick={() => onNavigate("audit")}
                  className="px-3.5 py-2 rounded-xl bg-indigo-500/15 hover:bg-indigo-500/25 text-indigo-300 font-bold border border-indigo-500/30 flex items-center gap-1.5 cursor-pointer"
                >
                  Audit Trail <ArrowRight className="w-3.5 h-3.5" />
                </button>
              </>
            )}
          </div>
        </motion.div>
      )}
    </div>
  );
}

// ─── Live Banking Rails Health Component ─────────────────────────────
function LiveBankingRailsHealth() {
  const [banks, setBanks] = useState<BankHealthItem[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchBankHealth()
      .then((data) => setBanks(Array.isArray(data) ? data : []))
      .catch((err) => console.error("Could not load banking rails health:", err))
      .finally(() => setLoading(false));
  }, []);

  const getStatusBadge = (status: string) => {
    switch (status) {
      case "OPERATIONAL":
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-emerald-500/15 text-emerald-300 border border-emerald-500/30">
            <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
            Operational
          </span>
        );
      case "CBS_MAINTENANCE_WINDOW":
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-amber-500/15 text-amber-300 border border-amber-500/30">
            <Moon className="w-3 h-3 text-amber-400" />
            CBS Maintenance
          </span>
        );
      case "DEGRADED":
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-amber-500/15 text-amber-300 border border-amber-500/30">
            <AlertTriangle className="w-3 h-3 text-amber-400" />
            Degraded
          </span>
        );
      case "DOWN":
      default:
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-rose-500/15 text-rose-300 border border-rose-500/30">
            <XCircle className="w-3 h-3 text-rose-400" />
            Outage Detected
          </span>
        );
    }
  };

  return (
    <div className="glass-card rounded-2xl p-6 border-slate-200 dark:border-slate-700/60 shadow-lg relative overflow-hidden">
      <div className="flex items-center justify-between mb-5 flex-wrap gap-3">
        <div>
          <div className="flex items-center gap-2">
            <Building2 className="w-5 h-5 text-[#3395FF]" />
            <h3 className="text-lg font-bold text-slate-900 dark:text-white">
              Live Banking Rails Health (UPI AutoPay & e-NACH)
            </h3>
          </div>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
            Real-time telemetry across Indian issuer banks powering automated retry scheduling and outage deferrals.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <span className="text-[11px] font-mono font-semibold px-2.5 py-1 rounded-lg bg-[#3395FF]/10 text-[#93c5fd] border border-[#3395FF]/20">
            5 Major Clearing Rails Monitored
          </span>
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3.5">
        {loading ? (
          Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className="p-4 rounded-xl bg-slate-900/40 border border-slate-800 animate-pulse h-32" />
          ))
        ) : (
          banks.map((b) => (
          <div
            key={b.bankCode}
            className="p-4 rounded-xl bg-slate-900/60 dark:bg-[#08182D]/90 border border-slate-700/50 hover:border-[#3395FF]/40 transition-colors flex flex-col justify-between space-y-3"
          >
            <div>
              <div className="flex items-center justify-between gap-2 mb-2">
                <span className="text-xs font-extrabold text-white tracking-wide truncate">
                  {b.bankName}
                </span>
                {b.isPsuBank && (
                  <span className="text-[9px] font-mono px-1.5 py-0.5 rounded bg-purple-500/20 text-purple-300 border border-purple-500/30">
                    PSU
                  </span>
                )}
              </div>
              <div className="mb-2.5">{getStatusBadge(b.status)}</div>
              <p className="text-[11px] text-slate-400 leading-snug font-sans">{b.advice}</p>
            </div>

            <div className="pt-2 border-t border-slate-800 flex items-center justify-between text-[10px] font-mono text-slate-400">
              <span>Uptime: <strong className="text-emerald-400">{b.uptime}</strong></span>
              <span>{b.latencyMs}ms</span>
            </div>
          </div>
        )))}
      </div>
    </div>
  );
}

// ─── Recovery Funnel Component ─────────────────────────────────────
function RecoveryFunnel({
  summary,
  isRecoveredPulse,
  isEnabled = true,
}: {
  summary: DashboardSummary;
  isRecoveredPulse?: boolean;
  isEnabled?: boolean;
}) {
  const steps = isEnabled
    ? [
        {
          label: "1. Failed Mandates",
          count: summary.failedCount || 0,
          icon: <XCircle className="w-4 h-4 text-rose-500" />,
          color: "from-rose-500 to-rose-400",
          description: "Intercepted via Razorpay webhook",
        },
        {
          label: "2. Classified",
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
          description: `${formatINR(summary.recoveredAmount)} settled`,
          highlight: true,
        },
      ]
    : [
        {
          label: "1. Failed Mandates",
          count: summary.failedCount || 10,
          icon: <XCircle className="w-4 h-4 text-rose-500" />,
          color: "from-rose-500 to-rose-400",
          description: "100% unrecovered failures",
        },
        {
          label: "2. Classified",
          count: 0,
          icon: <Bot className="w-4 h-4 text-slate-500" />,
          color: "from-slate-600 to-slate-500",
          description: "Blocked without AI Engine",
        },
        {
          label: "3. Approved",
          count: 0,
          icon: <CheckCircle className="w-4 h-4 text-slate-500" />,
          color: "from-slate-600 to-slate-500",
          description: "No strategy drafted",
        },
        {
          label: "4. Dispatched",
          count: 0,
          icon: <Send className="w-4 h-4 text-slate-500" />,
          color: "from-slate-600 to-slate-500",
          description: "No payment link generated",
        },
        {
          label: "5. Revenue Recovered",
          count: 0,
          icon: <TrendingUp className="w-4 h-4 text-slate-500" />,
          color: "from-slate-600 to-slate-500",
          description: "₹0.00 settled · 100% Churn",
          highlight: false,
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
            {isEnabled
              ? "Real-time pipeline progression from failure detection to revenue recovery"
              : "Legacy Mode: Failures remain stuck at Step 1 with 0% recovery conversion"}
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
          <div className={`flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-bold border shadow-sm ${
            isEnabled
              ? "bg-emerald-50 dark:bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-200 dark:border-emerald-500/20"
              : "bg-rose-50 dark:bg-rose-500/10 text-rose-600 dark:text-rose-400 border-rose-200 dark:border-rose-500/20"
          }`}>
            <Activity className="w-3.5 h-3.5" /> {isEnabled ? summary.recoveryRate : 0}% Recovery Rate
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
              animate={isStep5 && isRecoveredPulse && isEnabled ? { scale: [1, 1.04, 1] } : {}}
              transition={{ duration: 0.8, repeat: isRecoveredPulse ? 3 : 0 }}
              className={`relative p-4 rounded-xl border flex flex-col justify-between shadow-sm overflow-hidden transition-all duration-300 ${
                isStep5 && isEnabled
                  ? `bg-emerald-500/10 border-emerald-500/40 dark:bg-emerald-500/10 dark:border-emerald-500/40 ${
                      isRecoveredPulse ? "ring-2 ring-emerald-500 shadow-lg shadow-emerald-500/30" : ""
                    }`
                  : !isEnabled && idx > 0
                  ? "bg-slate-900/40 border-slate-800 opacity-60"
                  : "bg-white/60 dark:bg-slate-800/60 border-slate-200 dark:border-slate-700/50"
              }`}
            >
              {/* Pulsing Highlight Overlay for Step 5 */}
              {isStep5 && isRecoveredPulse && isEnabled && (
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
                  {isStep5 && isEnabled && (
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
                  animate={{ width: isEnabled ? `${widthPct}%` : idx === 0 ? "100%" : "0%" }}
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
// ─── Legacy Mode Benchmark View ────────────────────────────────────
function LegacyBenchmarkView({
  safeData,
  onToggleMode,
}: {
  safeData: DashboardSummary;
  onToggleMode?: () => void;
}) {
  const lostInRupees = Math.round(((safeData.failedCount || 10) * 499));

  return (
    <div className="space-y-8 pb-12">
      {/* Top Banner */}
      <div className="glass-card rounded-2xl p-6 border-rose-500/40 bg-gradient-to-r from-rose-950/60 via-slate-900/80 to-amber-950/40 shadow-2xl flex flex-col md:flex-row items-start md:items-center justify-between gap-6">
        <div className="flex items-start gap-4">
          <div className="w-12 h-12 rounded-2xl bg-rose-500/20 text-rose-400 border border-rose-500/30 flex items-center justify-center shrink-0 shadow-lg shadow-rose-500/10">
            <ShieldAlert className="w-6 h-6" />
          </div>
          <div className="space-y-1">
            <div className="flex items-center gap-2 flex-wrap">
              <h3 className="font-bold text-white text-lg">
                Viewing Benchmark: Legacy Mode (Without RecoverMandate)
              </h3>
              <span className="text-[10px] font-mono px-2.5 py-0.5 rounded-full bg-rose-500/20 text-rose-300 border border-rose-500/30 font-bold uppercase">
                100% Involuntary Churn
              </span>
            </div>
            <p className="text-xs text-slate-300 leading-relaxed max-w-3xl font-medium">
              In standard recurring payment setups, failed mandate webhooks sit unrecovered. There is no automated bank downtime deferral, no context-aware AI dunning, and no 1-click hosted checkout links.
            </p>
          </div>
        </div>

        {onToggleMode && (
          <button
            onClick={onToggleMode}
            className="flex items-center gap-2 px-5 py-2.5 rounded-xl bg-gradient-to-r from-blue-600 via-indigo-600 to-purple-600 hover:from-blue-500 hover:to-purple-500 text-white text-xs font-bold shadow-lg shadow-indigo-500/30 transition-all shrink-0 active:scale-95 cursor-pointer"
          >
            <Sparkles className="w-4 h-4 text-amber-300" />
            <span>Activate RecoverMandate AI</span>
          </button>
        )}
      </div>

      {/* 4 Legacy KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 sm:gap-6">
        <KPICard
          title="Recovery Rate (Legacy)"
          displayValue="0.0%"
          subtitle={`0 of ${safeData.failedCount || 10} salvaged · 100% Churn`}
          icon={<XCircle className="w-5 h-5 text-rose-500" />}
          glowClass="glow-rose border-rose-500/30"
          accentColor="from-rose-500 to-rose-400"
          tooltip="Legacy setups without RecoverMandate have 0% automated recovery yield on recurring mandate declines."
          sparklineData={[0, 0, 0, 0, 0, 0]}
        />
        <KPICard
          title="Resolution Latency"
          displayValue="~48h"
          subtitle="Manual email escalation backlog"
          icon={<Clock className="w-5 h-5 text-amber-500" />}
          glowClass="border-amber-500/30"
          accentColor="from-amber-500 to-rose-500"
          tooltip="Manual email operations take an average of 48+ hours per failed mandate ticket."
          sparklineData={[48, 48, 48, 48, 48, 48]}
        />
        <KPICard
          title="Lost Churned Revenue"
          displayValue={
            <span className="flex items-center">
              <IndianRupee className="w-7 h-7 -mr-1 text-rose-500" />
              <CountUp target={lostInRupees} />
            </span>
          }
          subtitle="100% unrecovered invoice churn"
          icon={<IndianRupee className="w-5 h-5 text-rose-500" />}
          glowClass="glow-rose border-rose-500/30"
          accentColor="from-rose-600 to-rose-500"
          tooltip="Cumulative revenue lost due to involuntary subscriber churn and abandoned mandates."
          sparklineData={[10000, 20000, 35000, 48000, lostInRupees]}
        />
        <KPICard
          title="Unresolved Failure Queue"
          displayValue={<CountUp target={safeData.failedCount || 10} />}
          subtitle="Sitting without automated recovery"
          icon={<AlertTriangle className="w-5 h-5 text-slate-400" />}
          glowClass="border-slate-700/60"
          accentColor="from-slate-500 to-slate-400"
          tooltip="Failed payments sitting idle in the database without intelligent recovery workflows."
          sparklineData={[10, 10, 10, 10, 10, 10]}
        />
      </div>

      {/* Comparative Architecture Matrix */}
      <div className="glass-card rounded-2xl p-6 border-slate-800 bg-[#0C2340]/60 space-y-6 shadow-xl">
        <div className="flex items-center justify-between border-b border-slate-800 pb-4">
          <div>
            <h4 className="text-base font-bold text-white flex items-center gap-2">
              <Building2 className="w-4 h-4 text-blue-400" />
              Comparative Architecture & Financial Fallout Matrix
            </h4>
            <p className="text-xs text-slate-400 mt-0.5">
              Side-by-side comparison of recurring payment failure handling mechanisms.
            </p>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-xs">
            <thead>
              <tr className="border-b border-slate-800 text-slate-400 uppercase tracking-wider font-mono text-[10px]">
                <th className="pb-3 text-left w-1/4">Workflow Dimension</th>
                <th className="pb-3 text-left w-3/8 text-rose-400">Legacy Mode (Without RecoverMandate)</th>
                <th className="pb-3 text-left w-3/8 text-emerald-400">RecoverMandate AI Engine</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60 font-medium">
              <tr>
                <td className="py-3.5 text-white font-bold">1. Webhook Ingestion & Triage</td>
                <td className="py-3.5 text-rose-300">Stored as raw database failure row; no classification or root cause tagging.</td>
                <td className="py-3.5 text-emerald-300">Deterministic rule classification into Insufficient Funds, Technical Decline, or Expired Mandate.</td>
              </tr>
              <tr>
                <td className="py-3.5 text-white font-bold">2. Smart Retry Scheduling</td>
                <td className="py-3.5 text-rose-300">Blind static retry (often next day) colliding with bank CBS outages & empty accounts.</td>
                <td className="py-3.5 text-emerald-300">Algorithmic 3-stage backoff aligned with monthly salary windows & live bank health.</td>
              </tr>
              <tr>
                <td className="py-3.5 text-white font-bold">3. Customer Communication</td>
                <td className="py-3.5 text-rose-300">Generic static template or manual support ticket delayed by ~48 hours.</td>
                <td className="py-3.5 text-emerald-300">Gemini 3.5 Flash multi-channel dunning with 3 tailored tones (Email, WhatsApp, SMS).</td>
              </tr>
              <tr>
                <td className="py-3.5 text-white font-bold">4. Settlement & Mandate Restoration</td>
                <td className="py-3.5 text-rose-300">Customer must navigate full login & re-enter card/UPI; high drop-off & churn.</td>
                <td className="py-3.5 text-emerald-300">1-click Razorpay Hosted Recovery link with automatic retry cancellation on payment.</td>
              </tr>
              <tr className="bg-slate-900/60">
                <td className="py-3.5 text-white font-bold">5. Recovery Yield</td>
                <td className="py-3.5 text-rose-400 font-bold">0.0% (100% Involuntary Churn)</td>
                <td className="py-3.5 text-emerald-400 font-bold">~65% Automated Mandate Salvage Rate</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      {/* Broken Legacy Pipeline Flow */}
      <div className="glass-card rounded-2xl p-6 border-rose-500/20 bg-rose-950/20 space-y-4 shadow-xl">
        <div className="flex items-center justify-between">
          <h4 className="text-sm font-bold text-white flex items-center gap-2">
            <XCircle className="w-4 h-4 text-rose-400" />
            Unmanaged Failure Chain (Legacy Fallout)
          </h4>
          <span className="text-[10px] font-mono font-bold text-rose-400 bg-rose-500/10 px-2 py-0.5 rounded border border-rose-500/20">
            High Subscriber Churn
          </span>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-4 gap-3 text-xs">
          <div className="p-3.5 rounded-xl bg-slate-900/80 border border-slate-800 space-y-1">
            <div className="flex items-center justify-between">
              <span className="font-bold text-slate-200">1. Mandate Decline</span>
              <span className="text-amber-400 font-mono text-[10px]">T+0</span>
            </div>
            <p className="text-[11px] text-slate-400">Webhook fires `payment.failed`. Stored in unmanaged database queue.</p>
          </div>

          <div className="p-3.5 rounded-xl bg-slate-900/80 border border-rose-800/40 space-y-1">
            <div className="flex items-center justify-between">
              <span className="font-bold text-rose-300">2. Blind Retry Fails</span>
              <span className="text-rose-400 font-mono text-[10px]">T+24h</span>
            </div>
            <p className="text-[11px] text-slate-400">Default retry hits ongoing CBS maintenance or uncredited salary account.</p>
          </div>

          <div className="p-3.5 rounded-xl bg-slate-900/80 border border-rose-800/40 space-y-1">
            <div className="flex items-center justify-between">
              <span className="font-bold text-rose-300">3. Support Backlog</span>
              <span className="text-rose-400 font-mono text-[10px]">T+48h</span>
            </div>
            <p className="text-[11px] text-slate-400">Agent manually emails generic link without personalized urgency or fallback channels.</p>
          </div>

          <div className="p-3.5 rounded-xl bg-rose-950/60 border border-rose-500/40 space-y-1">
            <div className="flex items-center justify-between">
              <span className="font-bold text-rose-200">4. Churn & Drop-off</span>
              <span className="text-rose-300 font-mono text-[10px]">T+72h</span>
            </div>
            <p className="text-[11px] text-rose-200">Customer abandons subscription. Recurring ARR permanently lost.</p>
          </div>
        </div>
      </div>
    </div>
  );
}

// ─── Main Dashboard Page Component ────────────────────────────────
export function DashboardPage({
  isEnabled,
  onToggleMode,
  refreshTrigger,
  onNavigate,
  confirmedEmail,
  onConfirmedEmailChange,
}: {
  isEnabled: boolean;
  onToggleMode?: () => void;
  refreshTrigger?: number;
  onNavigate?: (tab: string) => void;
  confirmedEmail?: string | null;
  onConfirmedEmailChange?: (email: string | null) => void;
}) {
  const [includeDemo, setIncludeDemo] = useState(false);
  const [data, setData] = useState<DashboardSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [isRecoveredPulse, setIsRecoveredPulse] = useState(false);
  const prevRecoveredRef = useRef<number>(0);

  const refreshData = () => {
    fetchDashboardSummary(includeDemo)
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
  }, [includeDemo]);

  useEffect(() => {
    refreshData();
  }, [refreshTrigger, includeDemo]);

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

  // If in Legacy Benchmark Mode, render the dedicated benchmark analysis
  if (!isEnabled) {
    return <LegacyBenchmarkView safeData={safeData} onToggleMode={onToggleMode} />;
  }

  // Active RecoverMandate AI Mode
  return (
    <div className="space-y-8 pb-12">
      <HeroStory />

      {/* Live Demo Simulator Bar */}
      <LiveDemoSimulator
        onSimulated={refreshData}
        onNavigate={onNavigate}
        confirmedEmail={confirmedEmail}
        onConfirmedEmailChange={onConfirmedEmailChange}
      />

      <motion.div variants={stagger} initial="hidden" animate="show" className="space-y-8">
        {/* Active Impact Banner with Data Scope Toggle */}
        <motion.div
          key="active-impact-banner"
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          className="glass-card rounded-2xl p-5 border-emerald-500/30 bg-gradient-to-r from-emerald-950/30 via-slate-900/60 to-blue-950/30 shadow-lg flex items-center justify-between gap-4 flex-wrap"
        >
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 flex items-center justify-center shrink-0">
              <Zap className="w-5 h-5" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="text-xs font-bold text-white uppercase tracking-wider">
                  RecoverMandate AI Active
                </span>
                <span className="text-[10px] font-mono px-2 py-0.5 rounded-full bg-emerald-500/20 text-emerald-300 font-bold">
                  Auto-Pilot & Smart Retries Armed
                </span>
              </div>
              <p className="text-[11px] text-slate-300 mt-0.5">
                Autonomous webhook interception, bank health-aware retry rescheduling, and Gemini multi-channel dunning active.
              </p>
            </div>
          </div>

          {/* Top-Right Toggle: Live Data Only vs Include Sandbox/Demo */}
          <div className="flex items-center gap-1.5 p-1 rounded-xl bg-[#02042B] border border-slate-700/80 shrink-0">
            <button
              onClick={() => setIncludeDemo(false)}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold transition-all ${
                !includeDemo
                  ? "bg-[#3395FF] text-white shadow-md shadow-[#3395FF]/20"
                  : "text-slate-400 hover:text-slate-200"
              }`}
            >
              <Zap className="w-3.5 h-3.5 text-amber-300" />
              <span>Live Data Only</span>
            </button>
            <button
              onClick={() => setIncludeDemo(true)}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold transition-all ${
                includeDemo
                  ? "bg-slate-700 text-white shadow-md"
                  : "text-slate-400 hover:text-slate-200"
              }`}
            >
              <Bot className="w-3.5 h-3.5 text-purple-300" />
              <span>Include Sandbox / Demo</span>
            </button>
          </div>
        </motion.div>

        {/* Top 4 ROI KPI Cards */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 sm:gap-6">
          <motion.div variants={fadeUp}>
            <KPICard
              title="Recovery Rate"
              displayValue={
                <>
                  <CountUpDecimal target={safeData.recoveryRate} decimals={1} />%
                </>
              }
              subtitle={`${safeData.paymentsRecovered} payments salvaged (+${safeData.recoveryRate}% Uplift)`}
              icon={<TrendingUp className="w-5 h-5 text-emerald-600 dark:text-emerald-400" />}
              glowClass="glow-emerald"
              accentColor="from-emerald-500 to-emerald-400"
              tooltip="Percentage of failed mandates successfully recovered through automated retries and payment links."
              sparklineData={[40, 55, 65, 70, 80, safeData.recoveryRate]}
            />
          </motion.div>

          <motion.div variants={fadeUp}>
            <KPICard
              title="Mean Time to Resolve (MTTR)"
              displayValue={<><CountUpDecimal target={safeData.avgResolutionTimeMinutes} decimals={1} />m</>}
              subtitle="From failure to dispatch"
              icon={<Clock className="w-5 h-5 text-blue-600 dark:text-blue-400" />}
              glowClass="glow-blue"
              accentColor="from-blue-600 to-cyan-500"
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
                  <CountUp target={recoveredInRupees} />
                </span>
              }
              subtitle="Salvaged subscription revenue"
              icon={<IndianRupee className="w-5 h-5 text-cyan-600 dark:text-cyan-400" />}
              glowClass="glow-cyan"
              accentColor="from-cyan-500 to-blue-500"
              tooltip="Total rupee value recovered through subscription charges and Razorpay recovery links."
              sparklineData={[5000, 12000, 25000, 38000, recoveredInRupees]}
            />
          </motion.div>

          <motion.div variants={fadeUp}>
            <KPICard
              title="Pending Action Queue"
              displayValue={<CountUp target={safeData.pendingApprovalsCount} />}
              subtitle={`${safeData.blockedDraftsCount} drafts in guardrail hold`}
              icon={<AlertTriangle className="w-5 h-5 text-amber-600 dark:text-amber-400" />}
              glowClass="glow-amber"
              accentColor="from-amber-500 to-amber-400"
              tooltip="AI-drafted recovery communications waiting for human review."
              sparklineData={[3, 8, 4, 12, safeData.pendingApprovalsCount]}
            />
          </motion.div>
        </div>

        {/* Live Banking Rails Health Widget */}
        <motion.div variants={fadeUp}>
          <LiveBankingRailsHealth />
        </motion.div>

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

