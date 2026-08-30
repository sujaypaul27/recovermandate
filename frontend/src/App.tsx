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
  Settings,
} from "lucide-react";

import { API_BASE_URL, API_KEY } from "./lib/api";
import { useEventSource } from "./hooks/useEventSource";
import { SystemHealthBanner, SystemHealthStatusDot } from "./components/SystemHealthBanner";
import { CommandPalette } from "./components/CommandPalette";
import { SettingsModal } from "./components/SettingsModal";
import { RazorpayMark } from "./components/RazorpayLogo";
import { ErrorBoundary } from "./components/ErrorBoundary";

import { DashboardPage } from "./pages/DashboardPage";
import { FailedMandatesPage } from "./pages/FailedMandatesPage";
import { ApprovalQueuePage } from "./pages/ApprovalQueuePage";
import { AuditLogPage } from "./pages/AuditLogPage";
import { CustomerCheckoutPage } from "./pages/CustomerCheckoutPage";

export default function App() {
  const [isRecoverMandateEnabled, setIsRecoverMandateEnabled] = useState(true);
  const [theme, setTheme] = useState<"dark" | "light">("dark");
  const [activeTab, setActiveTab] = useState("dashboard");
  const [isCommandPaletteOpen, setIsCommandPaletteOpen] = useState(false);
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const [checkoutLinkId, setCheckoutLinkId] = useState<string | null>(null);

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

  // Check URL hash / query on load and on hashchange for /pay/:linkId
  useEffect(() => {
    const handleHash = () => {
      const hash = window.location.hash;
      const pathname = window.location.pathname;
      if (hash.startsWith("#pay/")) {
        setCheckoutLinkId(hash.replace("#pay/", ""));
      } else if (pathname.startsWith("/pay/")) {
        setCheckoutLinkId(pathname.replace("/pay/", ""));
      }
    };

    handleHash();
    window.addEventListener("hashchange", handleHash);
    return () => window.removeEventListener("hashchange", handleHash);
  }, []);

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
          title: "Action Approved",
          description: `Recovery action #${data.actionId || ""} approved & signed.`,
        });
      } else if (eventType === "recovery.dispatched") {
        toast({
          title: "Recovery Link Dispatched",
          description: `Razorpay payment link dispatched via ${data.channel || "EMAIL"}.`,
        });
      } else if (eventType === "payment.recovered") {
        toast({
          title: "🎉 Payment Recovered!",
          description: `Customer completed payment via Razorpay link. Mandate restored!`,
        });
      }
    },
    [toast]
  );

  // Connect to SSE Endpoint
  const sseUrl = `${API_BASE_URL}/events?apiKey=${encodeURIComponent(API_KEY)}`;
  useEventSource(sseUrl, handleSseEvent);

  const navItems = [
    { id: "dashboard", label: "Overview & ROI", icon: LayoutDashboard },
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

  if (checkoutLinkId) {
    return (
      <>
        <CustomerCheckoutPage
          linkId={checkoutLinkId}
          onBackToDashboard={() => {
            setCheckoutLinkId(null);
            window.location.hash = "";
            setRefreshTrigger((prev) => prev + 1);
          }}
        />
        <Toaster />
      </>
    );
  }

  return (
    <>
      <div className="flex h-screen overflow-hidden bg-slate-50 dark:bg-[#02042B] font-sans antialiased text-slate-900 dark:text-slate-100">
        {/* Global Command Palette */}
        <CommandPalette
          isOpen={isCommandPaletteOpen}
          onClose={() => setIsCommandPaletteOpen(false)}
          onNavigate={(tab) => {
            setActiveTab(tab);
            setIsCommandPaletteOpen(false);
          }}
        />

        {/* Merchant Settings Modal */}
        <SettingsModal
          isOpen={isSettingsOpen}
          onClose={() => {
            setIsSettingsOpen(false);
            setRefreshTrigger((prev) => prev + 1);
          }}
        />

        {/* Sidebar Navigation */}
        <aside className="app-sidebar hidden md:flex flex-col border-r border-slate-200 dark:border-slate-800 bg-white/70 dark:bg-[#08182D]/80 backdrop-blur-xl shrink-0 z-20">
          <div className="p-6 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-blue-600 to-cyan-500 flex items-center justify-center shadow-lg shadow-blue-500/25">
                <RazorpayMark className="w-6 h-6 text-white" />
              </div>
              <div>
                <h1 className="text-base font-bold text-slate-900 dark:text-white tracking-tight flex items-center gap-1.5">
                  RecoverMandate
                </h1>
                <p className="text-[11px] text-slate-500 dark:text-slate-400 font-medium">
                  Payment Recovery Engine
                </p>
              </div>
            </div>
          </div>

          <div className="px-4 pt-4 pb-2">
            <button
              onClick={() => setIsCommandPaletteOpen(true)}
              className="w-full flex items-center justify-between px-3 py-2 rounded-xl bg-slate-100 dark:bg-slate-800/80 text-xs text-slate-500 dark:text-slate-400 border border-slate-200 dark:border-slate-700/60 hover:bg-slate-200/80 dark:hover:bg-slate-700/80 transition-colors shadow-inner"
            >
              <div className="flex items-center gap-2">
                <Search className="w-3.5 h-3.5 text-blue-500" />
                <span>Search or jump to...</span>
              </div>
              <kbd className="px-1.5 py-0.5 rounded bg-white dark:bg-slate-900 border border-slate-300 dark:border-slate-700 text-[10px] font-mono font-bold">
                {isMac ? "⌘K" : "Ctrl+K"}
              </kbd>
            </button>
          </div>

          <nav className="flex-1 px-3 py-3 space-y-1">
            {navItems.map((item) => {
              const Icon = item.icon;
              const isActive = activeTab === item.id;
              return (
                <button
                  key={item.id}
                  onClick={() => handleTabChange(item.id)}
                  className={`w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs font-bold transition-all duration-200 group ${
                    isActive
                      ? "bg-blue-600 text-white shadow-lg shadow-blue-600/30"
                      : "text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-800/50"
                  }`}
                >
                  <div className="flex items-center gap-3">
                    <Icon
                      className={`w-4 h-4 transition-transform duration-200 group-hover:scale-110 ${
                        isActive ? "text-white" : "text-slate-400 group-hover:text-slate-600 dark:group-hover:text-slate-300"
                      }`}
                    />
                    <span>{item.label}</span>
                  </div>
                  {item.badge != null && (
                    <span
                      className={`px-2 py-0.5 text-[10px] font-bold rounded-full transition-colors ${
                        isActive
                          ? "bg-white/20 text-white"
                          : "bg-amber-100 text-amber-700 dark:bg-amber-500/20 dark:text-amber-300 animate-pulse"
                      }`}
                    >
                      {item.badge}
                    </span>
                  )}
                </button>
              );
            })}
          </nav>

          <div className="p-4 border-t border-slate-200 dark:border-slate-800 space-y-2">
            <SystemHealthBanner />
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
            {/* Top Bar (Theme Toggle, Settings & RecoverMandate Switch) */}
            <div className="flex items-center justify-between gap-4">
              <div className="flex items-center gap-2">
                <button
                  onClick={() => setTheme(theme === "dark" ? "light" : "dark")}
                  className="p-2 rounded-xl glass-card text-slate-600 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white transition-colors"
                  aria-label="Toggle theme"
                  title="Toggle Light/Dark Theme"
                >
                  {theme === "dark" ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />}
                </button>

                <button
                  onClick={() => setIsSettingsOpen(true)}
                  className="flex items-center gap-1.5 px-3 py-2 rounded-xl glass-card text-xs font-semibold text-slate-700 dark:text-slate-200 hover:text-slate-900 dark:hover:text-white transition-colors border border-slate-200 dark:border-slate-700/60"
                  aria-label="Merchant Auto-Pilot Settings"
                  title="Configure Merchant Dunning & Auto-Pilot Settings"
                >
                  <Settings className="w-4 h-4 text-[#3395FF]" />
                  <span className="hidden sm:inline">Settings</span>
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
                  {activeTab === "mandates" && (
                    <FailedMandatesPage
                      refreshTrigger={refreshTrigger}
                      onOpenCheckout={(id) => {
                        setCheckoutLinkId(`pay_rec_${id}`);
                        window.location.hash = `pay/pay_rec_${id}`;
                      }}
                    />
                  )}
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
