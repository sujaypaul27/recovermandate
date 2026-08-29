import { useState, useEffect } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { Zap } from "lucide-react";
import { fetchSystemHealth } from "../lib/api";
import { RazorpayMark } from "./RazorpayLogo";

interface HealthStatus {
  status: string;
  geminiApi?: {
    status: string;
    circuitBreakerState?: string;
    model?: string;
    fallbackEngine?: string;
  };
  database?: {
    status: string;
  };
  timestamp?: string;
}

export function SystemHealthBanner() {
  const [health, setHealth] = useState<HealthStatus | null>(null);
  const [dismissed, setDismissed] = useState(false);

  const checkHealth = async () => {
    try {
      const data = await fetchSystemHealth();
      setHealth(data);
    } catch {
      setHealth({
        status: "DEGRADED",
        geminiApi: { status: "UNKNOWN" },
        database: { status: "UNKNOWN" },
      });
    }
  };

  useEffect(() => {
    checkHealth();
    const interval = setInterval(checkHealth, 30000);
    return () => clearInterval(interval);
  }, []);

  const isCircuitOpen =
    health?.geminiApi?.circuitBreakerState === "OPEN" ||
    health?.geminiApi?.circuitBreakerState === "HALF_OPEN" ||
    health?.geminiApi?.status === "DEGRADED";

  const isDegraded = health?.status === "DEGRADED" || isCircuitOpen;

  if (!isDegraded || dismissed) {
    return null;
  }

  return (
    <AnimatePresence>
      <motion.div
        initial={{ opacity: 0, y: -20, height: 0 }}
        animate={{ opacity: 1, y: 0, height: "auto" }}
        exit={{ opacity: 0, y: -20, height: 0 }}
        transition={{ duration: 0.3 }}
        className="w-full bg-[#02042B] border-b border-amber-500/40 backdrop-blur-md px-4 py-2.5 text-amber-200 text-xs sm:text-sm font-medium flex items-center justify-between shadow-lg z-50"
      >
        <div className="flex items-center gap-2.5 max-w-5xl mx-auto w-full">
          <div className="w-6 h-6 rounded-full bg-amber-500/20 flex items-center justify-center shrink-0 border border-amber-500/30">
            <Zap className="w-3.5 h-3.5 text-amber-400 animate-pulse" />
          </div>
          <div className="flex-1 flex flex-col sm:flex-row sm:items-center gap-1 sm:gap-3">
            <span className="font-bold tracking-tight text-white">
              ⚡ AI Engine Degraded — Using Heuristic Fallback Templates
            </span>
            <span className="text-[11px] text-amber-300/80 font-mono">
              Resilience4j Circuit Breaker: {health?.geminiApi?.circuitBreakerState || "ACTIVE_FALLBACK"}
            </span>
          </div>
          <button
            onClick={() => setDismissed(true)}
            className="text-amber-300 hover:text-white px-2.5 py-0.5 text-xs rounded-lg hover:bg-amber-500/20 transition-colors border border-amber-500/20"
          >
            Dismiss
          </button>
        </div>
      </motion.div>
    </AnimatePresence>
  );
}

export function SystemHealthStatusDot() {
  const [health, setHealth] = useState<HealthStatus | null>(null);

  useEffect(() => {
    fetchSystemHealth().then(setHealth).catch(() => setHealth({ status: "DEGRADED" }));
  }, []);

  const isHealthy = health?.status === "UP" && health?.geminiApi?.status !== "DEGRADED";

  return (
    <div className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-[#02042B] border border-[#3395FF]/30 text-[11px] shadow-sm">
      <RazorpayMark className="w-3.5 h-3.5" />
      <span
        className={`w-2 h-2 rounded-full ${
          isHealthy ? "bg-emerald-400 shadow-emerald-500/50 shadow-sm animate-pulse" : "bg-amber-400 animate-ping"
        }`}
      />
      <span className="font-semibold text-slate-200">
        {isHealthy ? "Razorpay Gateway UP" : "Degraded (Fallback Active)"}
      </span>
    </div>
  );
}
