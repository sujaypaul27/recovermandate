import { useState, useEffect, useRef } from "react";
import { motion, AnimatePresence, useMotionValue, useTransform } from "framer-motion";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Toaster } from "@/components/ui/toaster";
import { useToast } from "@/hooks/use-toast";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { ScrollArea } from "@/components/ui/scroll-area";
import {
  Activity, AlertTriangle, CheckCircle, Clock, ShieldAlert,
  XCircle, Search, Zap, TrendingUp, Bot, User, Server, Inbox, RefreshCw,
  ChevronLeft, ChevronRight, Sparkles, ArrowRight, Sun, Moon, LayoutDashboard,
  FileX2, CheckSquare, List, ShieldCheck
} from "lucide-react";

import {
  fetchDashboardSummary,
  fetchPaymentEvents,
  fetchRecoveryActions,
  fetchAuditLogs,
  approveRecoveryAction,
  rejectRecoveryAction,
} from "./lib/api";

// ─── Animation Variants ───────────────────────────────────────────
const stagger = { hidden: {}, show: { transition: { staggerChildren: 0.08 } } };
const fadeUp = { hidden: { opacity: 0, y: 16 }, show: { opacity: 1, y: 0, transition: { type: "spring", stiffness: 300, damping: 30 } } };

// ─── Count-Up Hook ────────────────────────────────────────────────
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

// ─── Category Helpers ─────────────────────────────────────────────
function getCategoryClass(cat: string | null | undefined) {
  if (!cat) return "";
  const lower = cat.toLowerCase();
  if (lower.includes("insufficient")) return "category-insufficient_funds";
  if (lower.includes("technical"))     return "category-technical_decline";
  if (lower.includes("expired"))       return "category-expired_mandate";
  return "category-unknown";
}
function getCategoryLabel(cat: string | null | undefined) {
  if (!cat) return "PENDING";
  return cat.replace(/_/g, " ").replace(/\b\w/g, l => l.toUpperCase());
}

// ═══════════════════════════════════════════════════════════════════
//  SPARKLINE COMPONENT
// ═══════════════════════════════════════════════════════════════════
function Sparkline({ colorClass, dataPoints }: { colorClass: string, dataPoints: number[] }) {
  const max = Math.max(...dataPoints, 1);
  const min = Math.min(...dataPoints, 0);
  const range = max - min;
  
  const width = 100;
  const height = 30;
  
  const points = dataPoints.map((val, i) => {
    const x = (i / (dataPoints.length - 1)) * width;
    const y = height - ((val - min) / range) * height;
    return `${x},${y}`;
  }).join(" ");

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

// ═══════════════════════════════════════════════════════════════════
//  STORYTELLING HERO COMPONENT
// ═══════════════════════════════════════════════════════════════════
function HeroStory() {
  return (
    <div className="glass-card rounded-2xl p-6 sm:p-8 relative overflow-hidden mb-8 border-blue-500/20">
      <div className="absolute -top-20 -right-20 p-8 opacity-10 pointer-events-none transform rotate-12">
        <Zap className="w-96 h-96 text-blue-500" />
      </div>
      <div className="relative z-10 max-w-4xl">
        <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.6 }}>
          <h2 className="text-3xl sm:text-4xl lg:text-5xl font-extrabold text-slate-900 dark:text-white mb-4 tracking-tight">
            Stop losing revenue to <br className="hidden sm:block"/>
            <span className="text-transparent bg-clip-text bg-gradient-to-r from-blue-600 to-cyan-500 dark:from-blue-500 dark:to-cyan-400">
              generic payment failures.
            </span>
          </h2>
          <p className="text-slate-600 dark:text-slate-300 text-lg leading-relaxed mb-8 max-w-3xl">
            RecoverMandate connects directly to your Razorpay webhooks. When a mandate fails, our AI engine instantly categorizes the root cause and drafts highly personalized recovery actions for your team to approve with one click.
          </p>
        </motion.div>
        
        {/* Animated Sequence Diagram */}
        <motion.div 
          variants={stagger}
          initial="hidden"
          animate="show"
          className="grid grid-cols-1 md:grid-cols-3 gap-4 relative"
        >
          {/* Step 1 */}
          <motion.div variants={fadeUp} className="p-5 rounded-xl bg-white/60 dark:bg-slate-800/60 border border-slate-200 dark:border-slate-700/50 flex flex-col gap-3 relative z-10 shadow-lg">
            <div className="w-10 h-10 rounded-full bg-rose-100 dark:bg-rose-500/20 flex items-center justify-center">
              <XCircle className="w-5 h-5 text-rose-600 dark:text-rose-500" />
            </div>
            <div>
              <span className="font-bold text-slate-800 dark:text-white text-base block mb-1">1. Payment Fails</span>
              <span className="text-sm text-slate-500 dark:text-slate-400 leading-tight block">Razorpay emits webhook `payment.failed`. We catch it instantly.</span>
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
              <span className="text-sm text-slate-500 dark:text-slate-400 leading-tight block">Gemini analyzes error codes and drafts tailored recovery comms.</span>
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
              <span className="text-sm text-slate-500 dark:text-emerald-100/70 leading-tight block">1-click human approval dispatches the fix. Revenue secured.</span>
            </div>
          </motion.div>
        </motion.div>
      </div>
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════════
//  ROOT APP
// ═══════════════════════════════════════════════════════════════════
export default function App() {
  const [isRecoverMandateEnabled, setIsRecoverMandateEnabled] = useState(true);
  const [theme, setTheme] = useState<"dark" | "light">("dark");
  const [activeTab, setActiveTab] = useState("dashboard");

  useEffect(() => {
    if (theme === "dark") {
      document.documentElement.classList.add("dark");
    } else {
      document.documentElement.classList.remove("dark");
    }
  }, [theme]);

  const navItems = [
    { id: "dashboard", label: "Overview", icon: LayoutDashboard },
    { id: "mandates", label: "Failed Mandates", icon: FileX2 },
    { id: "approvals", label: "Approval Queue", icon: CheckSquare },
    { id: "audit", label: "Audit Log", icon: List },
  ];

  return (
    <>
      {/* Background Layer */}
      <div className="gradient-mesh-bg" aria-hidden="true">
        <div className="ambient-blob ambient-blob-1" />
        <div className="ambient-blob ambient-blob-2" />
        <div className="ambient-blob ambient-blob-3" />
      </div>

      <div className="app-layout text-slate-900 dark:text-slate-100 antialiased">
        
        {/* Sidebar Navigation */}
        <aside className="app-sidebar flex-col justify-between p-4">
          <div>
            {/* Branding */}
            <div className="flex items-center gap-3 px-2 py-4 mb-6">
              <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-blue-500 to-cyan-400 flex items-center justify-center shadow-lg shadow-blue-500/20 shrink-0">
                <Zap className="w-5 h-5 text-white" />
              </div>
              <div className="hidden lg:block">
                <h1 className="text-xl font-bold tracking-tight text-slate-900 dark:text-white leading-none">
                  RecoverMandate
                </h1>
                <div className="flex items-center gap-1 mt-1">
                  <span className="text-[10px] uppercase font-semibold tracking-wider text-slate-500">Powered by</span>
                  <span className="font-bold text-slate-800 dark:text-white tracking-wide text-xs">Razorpay</span>
                </div>
              </div>
            </div>

            {/* Nav Menu */}
            <nav className="space-y-1 flex lg:flex-col lg:space-y-2 overflow-x-auto lg:overflow-visible">
              {navItems.map((item) => (
                <button
                  key={item.id}
                  onClick={() => setActiveTab(item.id)}
                  className={`flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all shrink-0 ${
                    activeTab === item.id 
                    ? "bg-blue-600/10 text-blue-700 dark:text-blue-400 border-l-2 border-blue-600 dark:border-blue-500 shadow-sm"
                    : "text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800/50 hover:text-slate-900 dark:hover:text-slate-200 border-l-2 border-transparent"
                  }`}
                >
                  <item.icon className="w-5 h-5 shrink-0" />
                  <span className="hidden lg:inline-block">{item.label}</span>
                </button>
              ))}
            </nav>
          </div>

          <div className="hidden lg:block mt-auto">
            {/* Theme Toggle & User */}
            <div className="p-4 rounded-xl bg-slate-100 dark:bg-slate-800/40 border border-slate-200 dark:border-slate-700/50 flex items-center justify-between">
              <div className="flex items-center gap-3">
                <div className="w-8 h-8 rounded-full bg-blue-100 dark:bg-blue-900/50 flex items-center justify-center">
                  <User className="w-4 h-4 text-blue-600 dark:text-blue-400" />
                </div>
                <div className="hidden xl:block">
                  <p className="text-sm font-semibold text-slate-900 dark:text-white leading-none">Admin</p>
                  <p className="text-xs text-slate-500 mt-1">Razorpay Co.</p>
                </div>
              </div>
              <Button 
                variant="ghost" 
                size="icon" 
                onClick={() => setTheme(t => t === "dark" ? "light" : "dark")}
                className="text-slate-500 hover:bg-white dark:hover:bg-slate-700 hover:text-slate-900 dark:text-slate-400 dark:hover:text-white h-8 w-8 rounded-full shadow-sm"
              >
                {theme === "dark" ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />}
              </Button>
            </div>
          </div>
        </aside>

        {/* Main Content Area */}
        <main className="app-main p-4 sm:p-6 lg:p-8 xl:p-10">
          <div className="max-w-6xl mx-auto space-y-8">
            
            {/* Header / Context Actions */}
            <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
              <div>
                <h2 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-white">
                  {navItems.find(i => i.id === activeTab)?.label}
                </h2>
                <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
                  {activeTab === 'dashboard' && 'Monitor and recover failed mandate payments.'}
                  {activeTab === 'mandates' && 'Live feed of caught Razorpay webhook events.'}
                  {activeTab === 'approvals' && 'Review AI-generated recovery strategies.'}
                  {activeTab === 'audit' && 'Cryptographically immutable system log.'}
                </p>
              </div>

              {/* God Mode Slider (Only on Dashboard) */}
              <AnimatePresence>
                {activeTab === "dashboard" && (
                  <motion.div 
                    initial={{ opacity: 0, scale: 0.95 }}
                    animate={{ opacity: 1, scale: 1 }}
                    exit={{ opacity: 0, scale: 0.95 }}
                    className="flex items-center gap-3 glass-card rounded-full p-1.5 border-slate-200 dark:border-slate-700/50"
                  >
                    <span className={`text-xs font-semibold pl-3 transition-colors ${!isRecoverMandateEnabled ? 'text-slate-900 dark:text-white' : 'text-slate-400'}`}>
                      Standard
                    </span>
                    <button
                      onClick={() => setIsRecoverMandateEnabled(!isRecoverMandateEnabled)}
                      className="relative w-14 h-7 rounded-full bg-slate-200 dark:bg-slate-800 transition-colors shadow-inner outline-none focus:ring-2 focus:ring-blue-500"
                    >
                      <motion.div
                        className={`absolute top-1 left-1 w-5 h-5 rounded-full shadow-md flex items-center justify-center ${isRecoverMandateEnabled ? 'bg-gradient-to-br from-blue-500 to-cyan-400' : 'bg-slate-400 dark:bg-slate-500'}`}
                        animate={{ x: isRecoverMandateEnabled ? 28 : 0 }}
                        transition={{ type: "spring", stiffness: 500, damping: 30 }}
                      >
                        {isRecoverMandateEnabled && <Sparkles className="w-3 h-3 text-white" />}
                      </motion.div>
                    </button>
                    <span className={`text-xs font-semibold pr-3 transition-colors flex items-center gap-1 ${isRecoverMandateEnabled ? 'text-transparent bg-clip-text bg-gradient-to-r from-blue-600 to-cyan-500 dark:from-blue-400 dark:to-cyan-300' : 'text-slate-400'}`}>
                      RecoverMandate
                    </span>
                  </motion.div>
                )}
              </AnimatePresence>
            </div>

            {/* View Content */}
            <AnimatePresence mode="wait">
              <motion.div
                key={activeTab}
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -10 }}
                transition={{ duration: 0.2 }}
              >
                {activeTab === "dashboard" && (
                  <>
                    <HeroStory />
                    <DashboardTab isEnabled={isRecoverMandateEnabled} />
                  </>
                )}
                {activeTab === "mandates" && <MandatesTab />}
                {activeTab === "approvals" && <ApprovalsTab />}
                {activeTab === "audit" && <AuditTab />}
              </motion.div>
            </AnimatePresence>

          </div>
        </main>
      </div>
      <Toaster />
    </>
  );
}

// ═══════════════════════════════════════════════════════════════════
//  DASHBOARD TAB
// ═══════════════════════════════════════════════════════════════════
function DashboardTab({ isEnabled }: { isEnabled: boolean }) {
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

  const displayData = isEnabled ? safeData : {
    failedMandatesToday: safeData.failedMandatesToday,
    autoRecoverableRate: 0,
    pendingApprovals: 0,
    recoverySuccessRate: 0,
  };

  return (
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
                <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">Toggle RecoverMandate on to activate AI intelligence and recovery flows.</p>
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
            icon={<AlertTriangle className={`w-5 h-5 ${isEnabled ? 'text-rose-600 dark:text-rose-400' : 'text-slate-400'}`} />}
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
            icon={<TrendingUp className={`w-5 h-5 ${isEnabled ? 'text-blue-600 dark:text-blue-400' : 'text-slate-400'}`} />}
            glowClass={isEnabled ? "glow-blue" : "opacity-75 saturate-0"}
            accentColor={isEnabled ? "from-blue-600 to-cyan-500" : "from-slate-400 to-slate-500"}
            tooltip="Failures where AI determined the issue is technical/soft-decline and can be automatically recovered."
            sparklineData={isEnabled ? [0, 20, 50, 45, 80, 95, displayData.autoRecoverableRate * 100] : [0,0,0,0,0,0,0]}
          />
        </motion.div>
        <motion.div variants={fadeUp}>
          <KPICard
            title="Pending Approvals"
            value={displayData.pendingApprovals}
            type="integer"
            icon={<Clock className={`w-5 h-5 ${isEnabled ? 'text-amber-600 dark:text-amber-400' : 'text-slate-400'}`} />}
            glowClass={isEnabled ? "glow-amber" : "opacity-75 saturate-0"}
            accentColor={isEnabled ? "from-amber-500 to-amber-400" : "from-slate-400 to-slate-500"}
            tooltip="AI-drafted recovery actions waiting for human approval."
            sparklineData={isEnabled ? [5, 2, 8, 3, 10, 4, displayData.pendingApprovals] : [0,0,0,0,0,0,0]}
          />
        </motion.div>
        <motion.div variants={fadeUp}>
          <KPICard
            title="Recovery Success"
            value={displayData.recoverySuccessRate * 100}
            type="percent"
            icon={<CheckCircle className={`w-5 h-5 ${isEnabled ? 'text-emerald-600 dark:text-emerald-400' : 'text-slate-400'}`} />}
            glowClass={isEnabled ? "glow-emerald" : "opacity-75 saturate-0"}
            accentColor={isEnabled ? "from-emerald-500 to-emerald-400" : "from-slate-400 to-slate-500"}
            tooltip="Percentage of executed recovery actions that resulted in a successful payment retry."
            sparklineData={isEnabled ? [60, 65, 75, 70, 85, 90, displayData.recoverySuccessRate * 100] : [0,0,0,0,0,0,0]}
          />
        </motion.div>
      </div>
    </motion.div>
  );
}

function KPICard({
  title, value, type, icon, glowClass, accentColor, tooltip, sparklineData
}: {
  title: string; value: number; type: "integer" | "percent";
  icon: React.ReactNode; glowClass: string; accentColor: string;
  tooltip?: string; sparklineData: number[];
}) {
  const animatedInt = useCountUp(type === "integer" ? value : 0);
  const animatedPct = useCountUpPercent(type === "percent" ? value : 0);

  // 3D Parallax logic
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
      className={`perspective-1000 w-full h-full`}
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
            <div className={`flex items-center gap-1.5 ${tooltip ? 'has-tooltip relative' : ''}`}>
              <span className={`text-xs font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400 ${tooltip ? 'cursor-help border-b border-dashed border-slate-400' : ''}`}>{title}</span>
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
            
            {/* Sparkline Container */}
            <div className="w-20 h-8 relative opacity-0 group-hover:opacity-100 transition-opacity duration-300 hidden sm:block">
               <Sparkline colorClass={`text-${accentColor.split('-')[1]}-500`} dataPoints={sparklineData} />
            </div>
          </div>
        </div>
      </div>
    </motion.div>
  );
}

// ═══════════════════════════════════════════════════════════════════
//  FAILED MANDATES TAB
// ═══════════════════════════════════════════════════════════════════
function MandatesTab() {
  const [data, setData] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [page, setPage] = useState(0);

  const load = () => {
    setLoading(true);
    fetchPaymentEvents(page, 10)
      .then(setData)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, [page]);

  if (error) return <ErrorState message={error} />;

  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.4 }}>
      <div className="glass-card rounded-2xl overflow-hidden shadow-xl">
        <div className="p-6 border-b border-slate-200 dark:border-slate-800 flex justify-between items-center bg-white/40 dark:bg-transparent">
          <div>
            <h2 className="text-lg font-bold text-slate-900 dark:text-white">Failed Mandates Log</h2>
            <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">Real-time feed of ingested webhook events.</p>
          </div>
          <Button variant="outline" size="sm" onClick={load} className="hidden sm:flex dark:border-slate-700">
            <RefreshCw className="w-4 h-4 mr-2" /> Refresh
          </Button>
        </div>
        <div className="p-0">
          {loading ? (
            <div className="p-6"><GlassSkeletons count={5} type="row" /></div>
          ) : data.content.length === 0 ? (
            <EmptyState message="No failed mandates found." subtitle="Payment failures from Razorpay webhooks will appear here." />
          ) : (
            <>
              {/* Desktop Table */}
              <div className="hidden md:block overflow-x-auto">
                <Table>
                  <TableHeader>
                    <TableRow className="border-slate-200 dark:border-slate-800 hover:bg-transparent">
                      <TableHead className="text-xs uppercase tracking-wider text-slate-500 dark:text-slate-400 font-bold">Payment ID</TableHead>
                      <TableHead className="text-xs uppercase tracking-wider text-slate-500 dark:text-slate-400 font-bold">Amount</TableHead>
                      <TableHead className="text-xs uppercase tracking-wider text-slate-500 dark:text-slate-400 font-bold">Category</TableHead>
                      <TableHead className="text-xs uppercase tracking-wider text-slate-500 dark:text-slate-400 font-bold">Recoverable</TableHead>
                      <TableHead className="text-xs uppercase tracking-wider text-slate-500 dark:text-slate-400 font-bold text-right">Status</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {data.content.map((item: any, i: number) => (
                      <motion.tr
                        key={item.id}
                        initial={{ opacity: 0, x: -10 }}
                        animate={{ opacity: 1, x: 0 }}
                        transition={{ delay: i * 0.04 }}
                        className="border-slate-200 dark:border-slate-800/50 hover:bg-slate-50 dark:hover:bg-slate-800/30 transition-all duration-200 group cursor-pointer"
                      >
                        <TableCell className="font-mono text-sm text-slate-700 dark:text-slate-300 group-hover:text-blue-600 dark:group-hover:text-blue-400 transition-colors">{item.razorpayPaymentId}</TableCell>
                        <TableCell className="font-bold text-slate-900 dark:text-white">₹{(item.amount / 100).toFixed(2)}</TableCell>
                        <TableCell>
                          <Badge variant="outline" className={`text-xs font-semibold border ${getCategoryClass(item.classificationCategory)}`}>
                            {getCategoryLabel(item.classificationCategory)}
                          </Badge>
                        </TableCell>
                        <TableCell>
                          {item.autoRecoverable ? (
                            <span className="inline-flex items-center gap-1.5 text-xs font-bold text-emerald-600 dark:text-emerald-400 bg-emerald-100 dark:bg-emerald-500/10 px-2 py-1 rounded-md">
                              <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
                              Yes
                            </span>
                          ) : (
                            <span className="text-xs font-medium text-slate-500 bg-slate-100 dark:bg-slate-800 px-2 py-1 rounded-md">No</span>
                          )}
                        </TableCell>
                        <TableCell className="text-right">
                          <span className="text-xs font-medium text-slate-500 dark:text-slate-400 uppercase tracking-wider bg-slate-100 dark:bg-slate-800/60 px-2 py-1 rounded border border-slate-200 dark:border-slate-700">{item.classificationStatus || "UNCLASSIFIED"}</span>
                        </TableCell>
                      </motion.tr>
                    ))}
                  </TableBody>
                </Table>
              </div>

              {/* Mobile Cards */}
              <div className="md:hidden p-4 space-y-3">
                {data.content.map((item: any) => (
                  <div key={item.id} className="p-4 rounded-xl bg-white dark:bg-slate-800/30 border border-slate-200 dark:border-slate-700/50 shadow-sm space-y-3">
                    <div className="flex justify-between items-center">
                      <span className="font-mono text-xs text-blue-600 dark:text-blue-400 font-medium">{item.razorpayPaymentId}</span>
                      <span className="font-bold text-slate-900 dark:text-white text-lg">₹{(item.amount / 100).toFixed(2)}</span>
                    </div>
                    <div className="flex justify-between items-center">
                      <Badge variant="outline" className={`text-[10px] font-bold border ${getCategoryClass(item.classificationCategory)}`}>
                        {getCategoryLabel(item.classificationCategory)}
                      </Badge>
                      {item.autoRecoverable ? (
                        <span className="inline-flex items-center gap-1 text-xs font-bold text-emerald-600 dark:text-emerald-400">
                          <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" /> Recoverable
                        </span>
                      ) : (
                        <span className="text-xs text-slate-500 font-medium">Terminal</span>
                      )}
                    </div>
                  </div>
                ))}
              </div>

              <div className="p-4 border-t border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-transparent">
                <PaginationControls page={page} setPage={setPage} totalPages={data.totalPages} />
              </div>
            </>
          )}
        </div>
      </div>
    </motion.div>
  );
}

// ═══════════════════════════════════════════════════════════════════
//  APPROVAL QUEUE TAB (Typewriter text for AI)
// ═══════════════════════════════════════════════════════════════════
const TypewriterText = ({ text }: { text: string }) => {
  const [displayedText, setDisplayedText] = useState("");
  
  useEffect(() => {
    let i = 0;
    setDisplayedText("");
    const interval = setInterval(() => {
      setDisplayedText(text.substring(0, i));
      i += 3; // speed
      if (i > text.length) clearInterval(interval);
    }, 10);
    return () => clearInterval(interval);
  }, [text]);

  return <span>{displayedText}</span>;
};

function ApprovalsTab() {
  const [data, setData] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const { toast } = useToast();

  const load = () => {
    setLoading(true);
    fetchRecoveryActions(0, 50, "DRAFTED")
      .then(setData)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, []);

  const handleApprove = async (id: number) => {
    try {
      await approveRecoveryAction(id);
      toast({ title: "Approved", description: "Recovery action dispatched successfully." });
      load();
    } catch (e: any) {
      toast({ title: "Error", description: e.message, variant: "destructive" });
    }
  };

  if (error) return <ErrorState message={error} />;

  return (
    <motion.div variants={stagger} initial="hidden" animate="show" className="space-y-6">
      <div className="flex justify-between items-end mb-2">
        <div>
          <h2 className="text-xl font-bold text-slate-900 dark:text-white">Action Required</h2>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">Review AI-drafted communications before dispatch.</p>
        </div>
      </div>
      
      {loading ? (
        <GlassSkeletons count={2} type="card" />
      ) : data?.content.length === 0 ? (
        <EmptyState
          message="Inbox Zero!"
          subtitle="No pending approvals. Go grab a coffee."
          icon={<CheckCircle className="w-12 h-12 text-emerald-500 mb-2" />}
        />
      ) : (
        data?.content.map((action: any) => (
          <motion.div key={action.id} variants={fadeUp}>
            <ApprovalCard action={action} onApprove={() => handleApprove(action.id)} onReload={load} />
          </motion.div>
        ))
      )}
    </motion.div>
  );
}

function ApprovalCard({ action, onApprove, onReload }: { action: any, onApprove: () => void, onReload: () => void }) {
  const [rejectReason, setRejectReason] = useState("");
  const [isRejecting, setIsRejecting] = useState(false);
  const { toast } = useToast();

  const handleReject = async () => {
    if (!rejectReason.trim()) {
      toast({ title: "Required", description: "Please provide a reason to train the AI.", variant: "destructive" });
      return;
    }
    try {
      await rejectRecoveryAction(action.id, rejectReason);
      toast({ title: "Rejected", description: "Feedback sent to model." });
      onReload();
    } catch (e: any) {
      toast({ title: "Error", description: e.message, variant: "destructive" });
    }
  };

  return (
    <div className="glass-card rounded-2xl overflow-hidden shadow-xl border-blue-500/10">
      <div className="p-6">
        {/* Header */}
        <div className="flex flex-col sm:flex-row justify-between items-start gap-4 mb-6">
          <div>
            <div className="flex items-center gap-3">
              <h3 className="text-lg font-bold text-slate-900 dark:text-white">Strategy #{action.id}</h3>
              <Badge className="bg-amber-100 text-amber-700 dark:bg-amber-500/10 dark:text-amber-400 border border-amber-500/20 text-xs font-bold uppercase tracking-wider">
                Needs Review
              </Badge>
            </div>
            <p className="text-xs font-mono text-slate-500 mt-1.5">
              Drafted: {new Date(action.createdAt).toLocaleString()}
            </p>
          </div>
          <div className="flex gap-2 flex-wrap">
            <Badge className="bg-emerald-50 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-400 border border-emerald-500/20 text-xs font-semibold flex items-center gap-1.5 shadow-sm">
              <ShieldCheck className="w-3 h-3" /> Tone: Verified
            </Badge>
          </div>
        </div>

        {/* Split Interface - "The Code Editor" feel */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-0 border border-slate-200 dark:border-slate-700/60 rounded-xl overflow-hidden shadow-sm">
          
          {/* Left: AI Draft (7 cols) */}
          <div className="lg:col-span-7 bg-white dark:bg-[#0a0a0a] p-5 relative">
            <div className="absolute top-0 left-0 w-full h-1 bg-gradient-to-r from-purple-500 to-blue-500" />
            <h4 className="text-xs font-bold uppercase tracking-widest text-slate-400 flex items-center gap-2 mb-4">
              <Bot className="w-4 h-4 text-purple-500" /> Gemini Generated Draft
            </h4>
            <div className="text-sm leading-relaxed text-slate-800 dark:text-slate-300 font-mono whitespace-pre-wrap pl-2 border-l-2 border-slate-100 dark:border-slate-800 min-h-[120px]">
              <TypewriterText text={action.aiDraftMessage} />
            </div>
          </div>

          {/* Right: Actions (5 cols) */}
          <div className="lg:col-span-5 bg-slate-50 dark:bg-slate-900/50 p-5 flex flex-col justify-between border-t lg:border-t-0 lg:border-l border-slate-200 dark:border-slate-700/60">
            <div className="space-y-3">
              <h4 className="text-xs font-bold uppercase tracking-widest text-slate-400 flex items-center gap-2">
                <User className="w-4 h-4 text-blue-500" /> Human Decision
              </h4>
              <p className="text-sm text-slate-600 dark:text-slate-400">
                Review the AI draft. If accurate, approve to dispatch the recovery email immediately.
              </p>
            </div>

            <div className="flex flex-col gap-3 mt-6">
              <Button
                onClick={onApprove}
                className="w-full bg-blue-600 hover:bg-blue-700 text-white font-bold shadow-lg shadow-blue-600/20 py-6 text-base group"
              >
                <CheckCircle className="w-5 h-5 mr-2 group-hover:scale-110 transition-transform" /> Approve & Dispatch
              </Button>
              
              <AnimatePresence mode="wait">
                {!isRejecting ? (
                  <motion.div key="reject-btn" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
                    <Button
                      onClick={() => setIsRejecting(true)}
                      variant="outline"
                      className="w-full border-rose-200 text-rose-600 hover:bg-rose-50 dark:border-rose-900/50 dark:text-rose-400 dark:hover:bg-rose-950/30 font-semibold"
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
                      placeholder="Why? (Improves AI model)"
                      className="bg-white dark:bg-slate-950 border-slate-300 dark:border-slate-700 text-sm focus-visible:ring-rose-500"
                      autoFocus
                    />
                    <div className="flex gap-2">
                      <Button onClick={handleReject} variant="destructive" className="flex-1 font-bold shadow-lg shadow-rose-600/20">Confirm</Button>
                      <Button onClick={() => setIsRejecting(false)} variant="outline" className="flex-1 bg-white dark:bg-slate-900 border-slate-300 dark:border-slate-700 text-slate-700 dark:text-slate-300">Cancel</Button>
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

// ═══════════════════════════════════════════════════════════════════
//  AUDIT TRAIL TAB
// ═══════════════════════════════════════════════════════════════════
function AuditTab() {
  const [data, setData] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [page, setPage] = useState(0);

  useEffect(() => {
    setLoading(true);
    fetchAuditLogs(page, 15)
      .then(setData)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, [page]);

  if (error) return <ErrorState message={error} />;

  const getActorConfig = (actor: string) => {
    switch (actor) {
      case "SYSTEM":
        return { icon: <Server className="w-4 h-4" />, bg: "bg-blue-100 dark:bg-blue-500/15", text: "text-blue-700 dark:text-blue-400", label: "System" };
      case "HUMAN":
        return { icon: <User className="w-4 h-4" />, bg: "bg-amber-100 dark:bg-amber-500/15", text: "text-amber-700 dark:text-amber-400", label: "Human" };
      case "AI":
        return { icon: <Bot className="w-4 h-4" />, bg: "bg-purple-100 dark:bg-purple-500/15", text: "text-purple-700 dark:text-purple-400", label: "AI Model" };
      default:
        return { icon: <Activity className="w-4 h-4" />, bg: "bg-slate-100 dark:bg-slate-500/15", text: "text-slate-700 dark:text-slate-400", label: actor };
    }
  };

  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.4 }}>
      <div className="glass-card rounded-2xl overflow-hidden shadow-xl">
        <div className="p-6 border-b border-slate-200 dark:border-slate-800 bg-white/40 dark:bg-transparent">
          <h2 className="text-lg font-bold text-slate-900 dark:text-white">Immutable Audit Log</h2>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">Cryptographically secure timeline of all state transitions.</p>
        </div>
        <div className="p-6">
          {loading ? (
            <GlassSkeletons count={6} type="row" />
          ) : data.content.length === 0 ? (
            <EmptyState message="No audit logs found" icon={<List className="w-10 h-10 text-slate-400" />} />
          ) : (
            <ScrollArea className="h-[600px] pr-4">
              <motion.div variants={stagger} initial="hidden" animate="show" className="space-y-4 timeline-line ml-2">
                {data.content.map((log: any) => {
                  const actorConfig = getActorConfig(log.actor);
                  return (
                    <motion.div key={log.id} variants={fadeUp} className="flex gap-5 relative group">
                      <div className="relative z-10 flex-shrink-0 mt-1">
                        <div className={`w-10 h-10 rounded-full shadow-sm border border-white dark:border-slate-800 ${actorConfig.bg} flex items-center justify-center ${actorConfig.text} group-hover:scale-110 transition-transform duration-300`}>
                          {actorConfig.icon}
                        </div>
                      </div>
                      <div className="flex-1 min-w-0 bg-white/80 dark:bg-slate-800/40 border border-slate-200 dark:border-slate-700/50 p-4 rounded-xl shadow-sm hover:shadow-md transition-shadow">
                        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 mb-2">
                          <div className="flex items-center gap-2 flex-wrap">
                            <span className="font-bold text-sm text-slate-900 dark:text-white">{log.action}</span>
                            <Badge variant="secondary" className="text-[10px] bg-slate-100 text-slate-600 dark:bg-slate-900 dark:text-slate-400 border-slate-300 dark:border-slate-700/50 font-mono font-bold tracking-wider uppercase">
                              {log.entityType} #{log.entityId}
                            </Badge>
                            <Badge className={`text-[10px] ${actorConfig.bg} ${actorConfig.text} border-transparent font-bold uppercase tracking-wider`}>
                              {actorConfig.label}
                            </Badge>
                          </div>
                          <span className="text-[11px] font-bold text-slate-400 dark:text-slate-500 font-mono tabular-nums shrink-0">
                            {new Date(log.timestamp).toLocaleString()}
                          </span>
                        </div>
                        <p className="text-sm text-slate-600 dark:text-slate-300 leading-relaxed font-medium">{log.details}</p>
                      </div>
                    </motion.div>
                  );
                })}
              </motion.div>
            </ScrollArea>
          )}
          {!loading && <div className="mt-6"><PaginationControls page={page} setPage={setPage} totalPages={data?.totalPages || 0} /></div>}
        </div>
      </div>
    </motion.div>
  );
}

// ═══════════════════════════════════════════════════════════════════
//  SHARED UTILS
// ═══════════════════════════════════════════════════════════════════

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
      <Button onClick={() => window.location.reload()} className="mt-4 bg-slate-900 hover:bg-slate-800 text-white dark:bg-slate-100 dark:hover:bg-white dark:text-slate-900 font-bold rounded-full px-6">
        <RefreshCw className="w-4 h-4 mr-2" /> Reconnect
      </Button>
    </motion.div>
  );
}

function EmptyState({ message, subtitle, icon }: { message: string; subtitle?: string; icon?: React.ReactNode }) {
  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="py-20 flex flex-col items-center justify-center text-center space-y-4">
      {icon || <div className="w-16 h-16 rounded-full bg-slate-100 dark:bg-slate-800/50 flex items-center justify-center mb-2"><Search className="w-8 h-8 text-slate-400" /></div>}
      <p className="font-bold text-lg text-slate-900 dark:text-white">{message}</p>
      {subtitle && <p className="text-sm font-medium text-slate-500 dark:text-slate-400 max-w-sm">{subtitle}</p>}
    </motion.div>
  );
}

function PaginationControls({ page, setPage, totalPages }: { page: number; setPage: (p: number) => void; totalPages: number }) {
  if (totalPages <= 1) return null;
  return (
    <div className="flex items-center justify-between">
      <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
        Page {page + 1} of {totalPages}
      </span>
      <div className="flex items-center gap-2">
        <Button variant="outline" size="sm" onClick={() => setPage(Math.max(0, page - 1))} disabled={page === 0} className="rounded-lg h-9 w-9 p-0 shadow-sm border-slate-300 dark:border-slate-700 text-slate-600 dark:text-slate-300">
          <ChevronLeft className="w-4 h-4" />
        </Button>
        <Button variant="outline" size="sm" onClick={() => setPage(Math.min(totalPages - 1, page + 1))} disabled={page >= totalPages - 1} className="rounded-lg h-9 w-9 p-0 shadow-sm border-slate-300 dark:border-slate-700 text-slate-600 dark:text-slate-300">
          <ChevronRight className="w-4 h-4" />
        </Button>
      </div>
    </div>
  );
}
