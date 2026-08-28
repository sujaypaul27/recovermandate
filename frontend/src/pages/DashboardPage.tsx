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
} from "lucide-react";
import { fetchDashboardSummary } from "../lib/api";

// ─── Animation Variants ───────────────────────────────────────────
const stagger = { hidden: {}, show: { transition: { staggerChildren: 0.08 } } };
const fadeUp = {
  hidden: { opacity: 0, y: 16 },
  show: { opacity: 1, y: 0, transition: { type: "spring" as const, stiffness: 300, damping: 30 } },
};

// ─── Count-Up Hooks ────────────────────────────────────────────────
function useCountUp(target: number, duration = 1200) {
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
  return count;
}

function useCountUpPercent(target: number, duration = 1200) {
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
  return count;
}

// ─── Sparkline Component ──────────────────────────────────────────
function Sparkline({ colorClass, dataPoints }: { colorClass: string; dataPoints: number[] }) {
  const max = Math.max(...dataPoints, 1);
  const min = Math.min(...dataPoints, 0);
  const range = max - min;

  const width = 100;
  const height = 30;

  const points = dataPoints
    .map((val, i) => {
      const x = (i / (dataPoints.length - 1)) * width;
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
    <div className="glass-card rounded-2xl p-6 sm:p-8 relative overflow-hidden mb-8 border-blue-500/20">
      <div className="absolute -top-20 -right-20 p-8 opacity-10 pointer-events-none transform rotate-12">
        <Zap className="w-96 h-96 text-blue-500" />
      </div>
      <div className="relative z-10 max-w-4xl">
        <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.6 }}>
          <h2 className="text-3xl sm:text-4xl lg:text-5xl font-extrabold text-slate-900 dark:text-white mb-4 tracking-tight">
            Stop losing revenue to <br className="hidden sm:block" />
            <span className="text-transparent bg-clip-text bg-gradient-to-r from-blue-600 to-cyan-500 dark:from-blue-500 dark:to-cyan-400">
              generic payment failures.
            </span>
          </h2>
          <p className="text-slate-600 dark:text-slate-300 text-lg leading-relaxed mb-8 max-w-3xl">
            RecoverMandate connects directly to your Razorpay webhooks. When a mandate fails, our AI engine instantly
            categorizes the root cause and drafts highly personalized recovery actions for your team to approve with one click.
          </p>
        </motion.div>

        {/* Animated Sequence Diagram */}
        <motion.div variants={stagger} initial="hidden" animate="show" className="grid grid-cols-1 md:grid-cols-3 gap-4 relative">
          {/* Step 1 */}
          <motion.div variants={fadeUp} className="p-5 rounded-xl bg-white/60 dark:bg-slate-800/60 border border-slate-200 dark:border-slate-700/50 flex flex-col gap-3 relative z-10 shadow-lg">
            <div className="w-10 h-10 rounded-full bg-rose-100 dark:bg-rose-500/20 flex items-center justify-center">
              <XCircle className="w-5 h-5 text-rose-600 dark:text-rose-500" />
            </div>
            <div>
              <span className="font-bold text-slate-800 dark:text-white text-base block mb-1">1. Payment Fails</span>
              <span className="text-sm text-slate-500 dark:text-slate-400 leading-tight block">
                Razorpay emits webhook `payment.failed`. We catch it instantly.
              </span>
            </div>
          </motion.div>

          {/* Step 2 */}
          <motion.div variants={fadeUp} className="p-5 rounded-xl bg-white/60 dark:bg-slate-800/60 border border-slate-200 dark:border-slate-700/50 flex flex-col gap-3 relative z-10 shadow-lg">
            <ArrowRight className="w-6 h-6 text-slate-300 dark:text-slate-600 absolute -left-5 top-1/2 -translate-y-1/2 hidden md:block z-0" />
            <div className="w-10 h-10 rounded-full bg-purple-100 dark:bg-purple-500/20 flex items-center justify-center">
              <Bot className="w-5 h-5 text-purple-600 dark:text-purple-500" />
            </div>
            <div>
              <span className="font-bold text-slate-800 dark:text-white text-base block mb-1">2. AI Interception</span>
              <span className="text-sm text-slate-500 dark:text-slate-400 leading-tight block">
                Gemini analyzes error codes and drafts tailored recovery comms.
              </span>
            </div>
          </motion.div>

          {/* Step 3 */}
          <motion.div variants={fadeUp} className="p-5 rounded-xl bg-white/60 dark:bg-slate-800/60 border border-emerald-500/30 dark:border-emerald-500/30 flex flex-col gap-3 relative z-10 shadow-emerald-500/10 shadow-xl">
            <ArrowRight className="w-6 h-6 text-slate-300 dark:text-slate-600 absolute -left-5 top-1/2 -translate-y-1/2 hidden md:block z-0" />
            <div className="w-10 h-10 rounded-full bg-emerald-100 dark:bg-emerald-500/20 flex items-center justify-center">
              <TrendingUp className="w-5 h-5 text-emerald-600 dark:text-emerald-500" />
            </div>
            <div>
              <span className="font-bold text-slate-800 dark:text-white text-base block mb-1">3. Revenue Saved</span>
              <span className="text-sm text-slate-500 dark:text-emerald-100/70 leading-tight block">
                1-click human approval dispatches the fix. Revenue secured.
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
  value,
  type,
  icon,
  glowClass,
  accentColor,
  tooltip,
  sparklineData,
}: {
  title: string;
  value: number;
  type: "integer" | "percent";
  icon: React.ReactNode;
  glowClass: string;
  accentColor: string;
  tooltip?: string;
  sparklineData: number[];
}) {
  const animatedInt = useCountUp(type === "integer" ? value : 0);
  const animatedPct = useCountUpPercent(type === "percent" ? value : 0);

  const x = useMotionValue(0);
  const y = useMotionValue(0);
  const rotateX = useTransform(y, [-100, 100], [7, -7]);
  const rotateY = useTransform(x, [-100, 100], [-7, 7]);

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

          <div className="flex items-end justify-between">
            <div className="text-4xl font-extrabold text-slate-900 dark:text-white tabular-nums tracking-tight">
              {type === "integer" ? animatedInt : `${animatedPct.toFixed(1)}%`}
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
export function DashboardPage({ isEnabled }: { isEnabled: boolean }) {
  const [data, setData] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    fetchDashboardSummary()
      .then(setData)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <GlassSkeletons count={4} type="card" />;
  if (error) return <ErrorState message={error} />;

  const safeData = {
    failedMandatesToday: data?.failedMandatesToday ?? 0,
    autoRecoverableRate: data?.autoRecoverableRate ?? 0,
    pendingApprovals: data?.pendingApprovals ?? 0,
    recoverySuccessRate: data?.recoverySuccessRate ?? 0,
  };

  const displayData = isEnabled
    ? safeData
    : {
        failedMandatesToday: safeData.failedMandatesToday,
        autoRecoverableRate: 0,
        pendingApprovals: 0,
        recoverySuccessRate: 0,
      };

  return (
    <div className="space-y-8">
      <HeroStory />

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
                  <p className="font-semibold text-slate-800 dark:text-slate-200 text-sm">Standard Mode Active</p>
                  <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
                    Toggle RecoverMandate on to activate AI intelligence and recovery flows.
                  </p>
                </div>
              </div>
            </motion.div>
          )}
        </AnimatePresence>

        {/* KPI Cards */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 sm:gap-6">
          <motion.div variants={fadeUp}>
            <KPICard
              title="Failed Today"
              value={displayData.failedMandatesToday}
              type="integer"
              icon={<AlertTriangle className={`w-5 h-5 ${isEnabled ? "text-rose-600 dark:text-rose-400" : "text-slate-400"}`} />}
              glowClass={isEnabled ? "glow-rose" : "opacity-75 saturate-0"}
              accentColor={isEnabled ? "from-rose-500 to-rose-400" : "from-slate-400 to-slate-500"}
              tooltip="Total number of payment failures intercepted by webhooks today."
              sparklineData={[10, 15, 8, 25, 20, 30, displayData.failedMandatesToday]}
            />
          </motion.div>
          <motion.div variants={fadeUp}>
            <KPICard
              title="Auto-Recoverable"
              value={displayData.autoRecoverableRate * 100}
              type="percent"
              icon={<TrendingUp className={`w-5 h-5 ${isEnabled ? "text-blue-600 dark:text-blue-400" : "text-slate-400"}`} />}
              glowClass={isEnabled ? "glow-blue" : "opacity-75 saturate-0"}
              accentColor={isEnabled ? "from-blue-600 to-cyan-500" : "from-slate-400 to-slate-500"}
              tooltip="Failures where AI determined the issue is technical/soft-decline and can be automatically recovered."
              sparklineData={isEnabled ? [0, 20, 50, 45, 80, 95, displayData.autoRecoverableRate * 100] : [0, 0, 0, 0, 0, 0, 0]}
            />
          </motion.div>
          <motion.div variants={fadeUp}>
            <KPICard
              title="Pending Approvals"
              value={displayData.pendingApprovals}
              type="integer"
              icon={<Clock className={`w-5 h-5 ${isEnabled ? "text-amber-600 dark:text-amber-400" : "text-slate-400"}`} />}
              glowClass={isEnabled ? "glow-amber" : "opacity-75 saturate-0"}
              accentColor={isEnabled ? "from-amber-500 to-amber-400" : "from-slate-400 to-slate-500"}
              tooltip="AI-drafted recovery actions waiting for human approval."
              sparklineData={isEnabled ? [5, 2, 8, 3, 10, 4, displayData.pendingApprovals] : [0, 0, 0, 0, 0, 0, 0]}
            />
          </motion.div>
          <motion.div variants={fadeUp}>
            <KPICard
              title="Recovery Success"
              value={displayData.recoverySuccessRate * 100}
              type="percent"
              icon={<CheckCircle className={`w-5 h-5 ${isEnabled ? "text-emerald-600 dark:text-emerald-400" : "text-slate-400"}`} />}
              glowClass={isEnabled ? "glow-emerald" : "opacity-75 saturate-0"}
              accentColor={isEnabled ? "from-emerald-500 to-emerald-400" : "from-slate-400 to-slate-500"}
              tooltip="Percentage of executed recovery actions that resulted in a successful payment retry."
              sparklineData={isEnabled ? [60, 65, 75, 70, 85, 90, displayData.recoverySuccessRate * 100] : [0, 0, 0, 0, 0, 0, 0]}
            />
          </motion.div>
        </div>
      </motion.div>
    </div>
  );
}
