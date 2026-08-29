import { useState, useEffect, useCallback } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { Toaster } from "@/components/ui/toaster";
import { useToast } from "@/hooks/use-toast";
import {
  LayoutDashboard,
  FileX2,
  CheckSquare,
  List,
  Sun,
  Moon,
  User,
  Sparkles,
  Search,
} from "lucide-react";

import { API_BASE_URL, API_KEY } from "./lib/api";
import { useEventSource } from "./hooks/useEventSource";
import { SystemHealthBanner, SystemHealthStatusDot } from "./components/SystemHealthBanner";
import { CommandPalette } from "./components/CommandPalette";
import { RazorpayMark } from "./components/RazorpayLogo";
import { ErrorBoundary } from "./components/ErrorBoundary";

import { DashboardPage } from "./pages/DashboardPage";
import { FailedMandatesPage } from "./pages/FailedMandatesPage";
import { ApprovalQueuePage } from "./pages/ApprovalQueuePage";
import { AuditLogPage } from "./pages/AuditLogPage";

export default function App() {
  const [isRecoverMandateEnabled, setIsRecoverMandateEnabled] = useState(true);
  const [theme, setTheme] = useState<"dark" | "light">("dark");
  const [activeTab, setActiveTab] = useState("dashboard");
  const [isCommandPaletteOpen, setIsCommandPaletteOpen] = useState(false);

  // SSE Live Notification States
  const [hasNewFailedMandate, setHasNewFailedMandate] = useState(false);
  const [pendingDraftCount, setPendingDraftCount] = useState(0);
  const [refreshTrigger, setRefreshTrigger] = useState(0);

  const { toast } = useToast();

  const isMac =
    typeof window !== "undefined" &&
    (/Mac|iPod|iPhone|iPad/.test(navigator.platform || "") || /Mac/i.test(navigator.userAgent || ""));

  // Dynamic document title update per tab
  useEffect(() => {
    const titles: Record<string, string> = {
      dashboard: "Overview & ROI | RecoverMandate",
      mandates: "Failed Mandates Log | RecoverMandate",
      approvals: "AI Approval Queue | RecoverMandate",
      audit: "Cryptographic Audit Trail | RecoverMandate",
    };
    document.title = titles[activeTab] || "RecoverMandate — Payment Recovery Intelligence";
  }, [activeTab]);

  // Apply theme class to document
  useEffect(() => {
    if (theme === "dark") {
      document.documentElement.classList.add("dark");
    } else {
      document.documentElement.classList.remove("dark");
    }
  }, [theme]);

  // Global keyboard shortcut for Command Palette (Ctrl+K / Cmd+K)
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setIsCommandPaletteOpen((prev) => !prev);
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, []);

  // SSE Event Handler Callback
  const handleSseEvent = useCallback(
    (eventType: string, data: any) => {
      console.log("[SSE Event Received]", eventType, data);
      setRefreshTrigger((prev) => prev + 1);

      if (eventType === "webhook.received") {
        setHasNewFailedMandate(true);
        toast({
          title: "Payment Failure Intercepted",
          description: `Captured ${data.paymentId || "webhook"} (₹${((data.amount || 0) / 100).toFixed(2)}) via Razorpay.`,
        });
      } else if (eventType === "draft.generated") {
        setPendingDraftCount((prev) => prev + 1);
        toast({
          title: "AI Strategy Drafted",
          description: `Gemini generated recovery action #${data.actionId || ""} (${data.draftSource || "AI"}).`,
        });
      } else if (eventType === "action.approved") {
        toast({
          title: "Recovery Approved",
          description: `Strategy #${data.actionId} approved. Recovery link generated.`,
        });
      } else if (eventType === "recovery.dispatched") {
        toast({
          title: "Payment Link Dispatched",
          description: `Razorpay recovery link dispatched to customer for action #${data.actionId}.`,
        });
      } else if (eventType === "recovery.completed") {
        toast({
          title: "🎉 Mandate Saved & Revenue Recovered!",
          description: `Customer completed payment of ₹${((data.amount || 0) / 100).toLocaleString("en-IN")}. Revenue recovered!`,
        });
      }
    },
    [toast]
  );

  // Connect to SSE Stream with API Key query parameter
  // NOTE [ARCHITECTURAL LIMITATION]: Passing the API key in the query parameter (?apiKey=...) is an
  // intentional design choice dictated by the W3C EventSource API specification, which does not allow
  // custom HTTP request headers (such as 'X-API-Key') in standard browser EventSource implementations.
  // This is a known browser API limitation rather than a security gap; ApiKeyAuthFilter supports both
  // 'X-API-Key' headers and the 'apiKey' query parameter for this endpoint. In high-security production
  // environments requiring strict header-only transport, an upgrade to WebSockets or ephemeral short-lived
  // handshake tokens is the recommended path (out of scope for standard SSE streaming).
  const sseUrl = `${API_BASE_URL.replace("/api", "")}/api/stream/events?apiKey=${encodeURIComponent(API_KEY)}`;
  useEventSource(sseUrl, handleSseEvent);

  const navItems = [
    { id: "dashboard", label: "Overview", icon: LayoutDashboard },
    {
      id: "mandates",
      label: "Failed Mandates",
      icon: FileX2,
      badge: hasNewFailedMandate ? "dot" : null,
    },
    {
      id: "approvals",
      label: "Approval Queue",
      icon: CheckSquare,
      badge: pendingDraftCount > 0 ? `${pendingDraftCount}` : null,
    },
    { id: "audit", label: "Audit Log", icon: List },
  ];

  const handleTabChange = (tabId: string) => {
    setActiveTab(tabId);
    if (tabId === "mandates") setHasNewFailedMandate(false);
    if (tabId === "approvals") setPendingDraftCount(0);
  };

  return (
    <>
      {/* Degraded State Alert Banner */}
      <SystemHealthBanner />

      {/* Global Command Palette */}
      <CommandPalette
        isOpen={isCommandPaletteOpen}
        onClose={() => setIsCommandPaletteOpen(false)}
        onNavigate={handleTabChange}
      />

      {/* Background Mesh Layer */}
      <div className="gradient-mesh-bg" aria-hidden="true">
        <div className="ambient-blob ambient-blob-1" />
        <div className="ambient-blob ambient-blob-2" />
        <div className="ambient-blob ambient-blob-3" />
      </div>

      <div className="app-layout text-slate-900 dark:text-slate-100 antialiased">
        {/* Sidebar Navigation */}
        <aside className="app-sidebar flex-col justify-between p-4">
          <div>
            {/* Official Razorpay Branding */}
            <div className="flex items-center gap-3 px-2 py-4 mb-6">
              <div className="w-11 h-11 rounded-xl bg-[#0C2340] border border-[#3395FF]/40 flex items-center justify-center shadow-lg shadow-[#3395FF]/20 shrink-0">
                <RazorpayMark className="w-6 h-6" />
              </div>
              <div className="hidden lg:block">
                <div className="flex items-center gap-1.5">
                  <h1 className="text-lg font-extrabold tracking-tight text-white leading-none">
                    Recover<span className="text-[#3395FF]">Mandate</span>
                  </h1>
                </div>
                <div className="flex items-center gap-1 mt-1.5">
                  <span className="text-[9px] uppercase font-bold tracking-wider text-[#93c5fd]/90">
                    Powered by Razorpay Mandate Recovery Engine
                  </span>
                </div>
              </div>
            </div>

            {/* Quick Search Button */}
            <div className="hidden lg:block mb-4">
              <button
                onClick={() => setIsCommandPaletteOpen(true)}
                className="w-full flex items-center justify-between px-3 py-2 rounded-xl bg-slate-100 dark:bg-slate-800/50 border border-slate-200 dark:border-slate-700/60 text-xs text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white transition-colors group"
              >
                <div className="flex items-center gap-2">
                  <Search className="w-3.5 h-3.5 text-slate-400 group-hover:text-blue-500" />
                  <span>Search or Jump to...</span>
                </div>
                <kbd className="px-1.5 py-0.5 rounded bg-white dark:bg-slate-700 border border-slate-300 dark:border-slate-600 text-[10px] font-mono text-slate-500 dark:text-slate-300">
                  {isMac ? "⌘K" : "Ctrl+K"}
                </kbd>
              </button>
            </div>

            {/* Nav Menu */}
            <nav className="space-y-1 flex lg:flex-col lg:space-y-2 overflow-x-auto lg:overflow-visible">
              {navItems.map((item) => (
                <button
                  key={item.id}
                  onClick={() => handleTabChange(item.id)}
                  className={`flex items-center justify-between px-3 py-2.5 rounded-lg text-sm font-medium transition-all shrink-0 ${
                    activeTab === item.id
                      ? "bg-blue-600/10 text-blue-700 dark:text-blue-400 border-l-2 border-blue-600 dark:border-blue-500 shadow-sm"
                      : "text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800/50 hover:text-slate-900 dark:hover:text-slate-200 border-l-2 border-transparent"
                  }`}
                >
                  <div className="flex items-center gap-3">
                    <item.icon className="w-5 h-5 shrink-0" />
                    <span className="hidden lg:inline-block">{item.label}</span>
                  </div>

                  {/* Badges / Live indicators */}
                  {item.badge === "dot" && (
                    <span className="w-2 h-2 rounded-full bg-blue-500 shadow-lg shadow-blue-500 animate-pulse ml-2" />
                  )}
                  {item.badge && item.badge !== "dot" && (
                    <span className="hidden lg:inline-flex px-1.5 py-0.5 rounded-full bg-blue-500 text-white text-[10px] font-bold">
                      {item.badge}
                    </span>
                  )}
                </button>
              ))}
            </nav>
          </div>

          {/* Footer User Status */}
          <div className="pt-4 border-t border-slate-200 dark:border-slate-800 hidden lg:block">
            <div className="flex items-center gap-3 px-2 py-2">
              <div className="w-8 h-8 rounded-full bg-slate-200 dark:bg-slate-700 flex items-center justify-center">
                <User className="w-4 h-4 text-slate-500 dark:text-slate-400" />
              </div>
              <div className="flex-1 truncate">
                <p className="text-xs font-semibold text-slate-800 dark:text-slate-200 truncate">Ops Manager</p>
                <p className="text-[10px] text-slate-400 truncate">Razorpay Admin</p>
              </div>
              <SystemHealthStatusDot />
            </div>
          </div>
        </aside>

        {/* Main Content Area */}
        <main className="app-main">
          <div className="p-4 sm:p-6 lg:p-8 max-w-7xl mx-auto space-y-6 sm:space-y-8">
            {/* Top Bar (Theme Toggle & RecoverMandate Switch) */}
            <div className="flex items-center justify-between gap-4">
              <div className="flex items-center gap-3">
                <button
                  onClick={() => setTheme(theme === "dark" ? "light" : "dark")}
                  className="p-2 rounded-xl glass-card text-slate-600 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white transition-colors"
                  aria-label="Toggle theme"
                >
                  {theme === "dark" ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />}
                </button>
              </div>

              {/* RecoverMandate Toggle (Dashboard Tab) */}
              <AnimatePresence>
                {activeTab === "dashboard" && (
                  <motion.div
                    initial={{ opacity: 0, scale: 0.95 }}
                    animate={{ opacity: 1, scale: 1 }}
                    exit={{ opacity: 0, scale: 0.95 }}
                    className="flex items-center gap-3 glass-card rounded-full p-1.5 border-slate-200 dark:border-slate-700/50"
                  >
                    <span
                      className={`text-xs font-semibold pl-3 transition-colors ${
                        !isRecoverMandateEnabled ? "text-slate-900 dark:text-white" : "text-slate-400"
                      }`}
                    >
                      Standard
                    </span>
                    <button
                      onClick={() => setIsRecoverMandateEnabled(!isRecoverMandateEnabled)}
                      className="relative w-14 h-7 rounded-full bg-slate-200 dark:bg-slate-800 transition-colors shadow-inner outline-none focus:ring-2 focus:ring-blue-500"
                    >
                      <motion.div
                        className={`absolute top-1 left-1 w-5 h-5 rounded-full shadow-md flex items-center justify-center ${
                          isRecoverMandateEnabled
                            ? "bg-gradient-to-br from-blue-500 to-cyan-400"
                            : "bg-slate-400 dark:bg-slate-500"
                        }`}
                        animate={{ x: isRecoverMandateEnabled ? 28 : 0 }}
                        transition={{ type: "spring", stiffness: 500, damping: 30 }}
                      >
                        {isRecoverMandateEnabled && <Sparkles className="w-3 h-3 text-white" />}
                      </motion.div>
                    </button>
                    <span
                      className={`text-xs font-semibold pr-3 transition-colors flex items-center gap-1 ${
                        isRecoverMandateEnabled
                          ? "text-transparent bg-clip-text bg-gradient-to-r from-blue-600 to-cyan-500 dark:from-blue-400 dark:to-cyan-300"
                          : "text-slate-400"
                      }`}
                    >
                      RecoverMandate
                    </span>
                  </motion.div>
                )}
              </AnimatePresence>
            </div>

            {/* Dynamic View Content wrapped in ErrorBoundary */}
            <ErrorBoundary>
              <AnimatePresence mode="wait">
                <motion.div
                  key={activeTab}
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -10 }}
                  transition={{ duration: 0.2 }}
                >
                  {activeTab === "dashboard" && (
                    <DashboardPage
                      isEnabled={isRecoverMandateEnabled}
                      refreshTrigger={refreshTrigger}
                      onNavigate={handleTabChange}
                    />
                  )}
                  {activeTab === "mandates" && <FailedMandatesPage refreshTrigger={refreshTrigger} />}
                  {activeTab === "approvals" && <ApprovalQueuePage refreshTrigger={refreshTrigger} />}
                  {activeTab === "audit" && <AuditLogPage refreshTrigger={refreshTrigger} />}
                </motion.div>
              </AnimatePresence>
            </ErrorBoundary>
          </div>
        </main>
      </div>
      <Toaster />
    </>
  );
}
