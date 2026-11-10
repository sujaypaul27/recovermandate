import { useState, useEffect, useCallback, useRef } from "react";
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
  Keyboard,
  XCircle,
  Download,
  Menu,
  X,
} from "lucide-react";

import { API_BASE_URL, API_KEY } from "./lib/api";
import { useEventSource } from "./hooks/useEventSource";
import { SystemHealthBanner, SystemHealthStatusDot } from "./components/SystemHealthBanner";
import { CommandPalette } from "./components/CommandPalette";
import { SettingsModal } from "./components/SettingsModal";
import { KeyboardShortcutsModal } from "./components/KeyboardShortcutsModal";
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
  const [isShortcutsOpen, setIsShortcutsOpen] = useState(false);
  const [checkoutLinkId, setCheckoutLinkId] = useState<string | null>(null);
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);

  // SSE Live Notification States
  const [hasNewFailedMandate, setHasNewFailedMandate] = useState(false);
  const [pendingDraftCount, setPendingDraftCount] = useState(0);
  const [refreshTrigger, setRefreshTrigger] = useState(0);

  // Persistent Demo Target Email State (persists across tab navigation and browser sessions)
  const [targetCustomerEmail, setTargetCustomerEmail] = useState<string | null>(() => {
    if (typeof window !== "undefined") {
      return sessionStorage.getItem("recovermandate_target_email") || "sujaypaul2711@gmail.com";
    }
    return "sujaypaul2711@gmail.com";
  });

  const handleTargetCustomerEmailChange = (email: string | null) => {
    setTargetCustomerEmail(email);
    if (typeof window !== "undefined") {
      if (email) {
        sessionStorage.setItem("recovermandate_target_email", email);
      } else {
        sessionStorage.removeItem("recovermandate_target_email");
      }
    }
  };

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
      if (hash.startsWith("#/pay/")) {
        setCheckoutLinkId(hash.replace("#/pay/", ""));
      } else if (hash.startsWith("#pay/")) {
        setCheckoutLinkId(hash.replace("#pay/", ""));
      } else if (pathname.startsWith("/pay/")) {
        setCheckoutLinkId(pathname.replace("/pay/", ""));
      }
    };

    handleHash();
    window.addEventListener("hashchange", handleHash);
    return () => window.removeEventListener("hashchange", handleHash);
  }, []);

  const handleTabChange = useCallback((tabId: string) => {
    setActiveTab(tabId);
    if (tabId === "mandates") setHasNewFailedMandate(false);
    if (tabId === "approvals") setPendingDraftCount(0);
  }, []);

  // Global keyboard shortcuts (Ctrl+K, ?, R, 1-4, Esc)
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      const activeEl = document.activeElement;
      const isInputActive =
        activeEl instanceof HTMLInputElement ||
        activeEl instanceof HTMLTextAreaElement ||
        activeEl instanceof HTMLSelectElement;

      // Ctrl+K / Cmd+K -> Command Palette
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setIsCommandPaletteOpen((prev) => !prev);
        return;
      }

      // If user is currently typing in an input field, do not trigger single-key hotkeys
      if (isInputActive) {
        if (e.key === "Escape") {
          (activeEl as HTMLElement).blur();
        }
        return;
      }

      // '?' -> Toggle Keyboard Shortcuts Help
      if (e.key === "?" || (e.shiftKey && e.key === "/")) {
        e.preventDefault();
        setIsShortcutsOpen((prev) => !prev);
        return;
      }

      // 'r' or 'R' -> Refresh telemetry
      if (e.key.toLowerCase() === "r") {
        e.preventDefault();
        setRefreshTrigger((prev) => prev + 1);
        toast({
          title: "Telemetry Refreshed",
          description: "Fetched real-time payment telemetry & system health.",
        });
        return;
      }

      // '1' - '4' -> Switch views
      if (e.key === "1") handleTabChange("dashboard");
      else if (e.key === "2") handleTabChange("mandates");
      else if (e.key === "3") handleTabChange("approvals");
      else if (e.key === "4") handleTabChange("audit");

      // 'Escape' -> Close any modal
      if (e.key === "Escape") {
        setIsCommandPaletteOpen(false);
        setIsSettingsOpen(false);
        setIsShortcutsOpen(false);
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [handleTabChange, toast]);

  const refreshTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const triggerRefresh = useCallback(() => {
    if (refreshTimerRef.current) {
      clearTimeout(refreshTimerRef.current);
    }
    refreshTimerRef.current = setTimeout(() => {
      setRefreshTrigger((prev) => prev + 1);
    }, 250);
  }, []);

  // SSE Event Handler Callback
  const handleSseEvent = useCallback(
    (eventType: string, data: any) => {
      console.log("[SSE Event Received]", eventType, data);
      triggerRefresh();

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
        setPendingDraftCount((prev) => Math.max(0, prev - 1));
        toast({
          title: "Action Approved",
          description: `Recovery action #${data.actionId || ""} approved & signed.`,
        });
      } else if (eventType === "recovery.dispatched") {
        setPendingDraftCount((prev) => Math.max(0, prev - 1));
        toast({
          title: "Recovery Link Dispatched",
          description: `Razorpay payment link dispatched via ${data.channel || "EMAIL"}.`,
        });
      } else if (eventType === "payment.recovered" || eventType === "recovery.completed") {
        setPendingDraftCount((prev) => Math.max(0, prev - 1));
        toast({
          title: "🎉 Payment Recovered!",
          description: `Customer completed payment via Razorpay link. Mandate restored!`,
        });
      }
    },
    [toast, triggerRefresh]
  );

  // Connect to SSE Endpoint
  const sseUrl = `${API_BASE_URL}/stream/events?apiKey=${encodeURIComponent(API_KEY)}`;
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
      <div className="flex flex-col lg:flex-row h-screen overflow-hidden bg-slate-50 dark:bg-[#02042B] font-sans antialiased text-slate-900 dark:text-slate-100">
        <header className="lg:hidden flex items-center justify-between px-4 py-3 border-b border-slate-200 dark:border-slate-800 bg-white/80 dark:bg-[#08182D]/90 backdrop-blur-xl shrink-0 z-30">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-blue-600 to-cyan-500 flex items-center justify-center shadow-md shadow-blue-500/25">
              <RazorpayMark className="w-5 h-5 text-white" />
            </div>
            <div>
              <h1 className="text-sm font-bold text-slate-900 dark:text-white tracking-tight leading-tight">
                RecoverMandate
              </h1>
              <p className="text-[10px] text-slate-500 dark:text-slate-400 font-medium">
                Payment Recovery Engine
              </p>
            </div>
          </div>
          <div className="flex items-center gap-1.5">
            <button
              onClick={() => setTheme(theme === "dark" ? "light" : "dark")}
              className="p-2 rounded-xl glass-card text-slate-600 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white transition-colors"
              aria-label="Toggle theme"
            >
              {theme === "dark" ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />}
            </button>
            <button
              onClick={() => setIsSettingsOpen(true)}
              className="p-2 rounded-xl glass-card text-slate-600 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white transition-colors"
              aria-label="Settings"
            >
              <Settings className="w-4 h-4 text-[#3395FF]" />
            </button>
            <button
              onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
              className="p-2 rounded-xl glass-card text-slate-600 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white transition-colors relative"
              aria-label="Toggle Navigation Menu"
            >
              {isMobileMenuOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
              {(hasNewFailedMandate || pendingDraftCount > 0) && (
                <span className="absolute top-1.5 right-1.5 w-2 h-2 rounded-full bg-amber-500 animate-pulse" />
              )}
            </button>
          </div>
        </header>

        <AnimatePresence>
          {isMobileMenuOpen && (
            <>
              <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                onClick={() => setIsMobileMenuOpen(false)}
                className="lg:hidden fixed inset-0 bg-slate-950/70 backdrop-blur-sm z-40"
              />
              <motion.div
                initial={{ x: "-100%" }}
                animate={{ x: 0 }}
                exit={{ x: "-100%" }}
                transition={{ type: "spring", damping: 25, stiffness: 250 }}
                className="lg:hidden fixed inset-y-0 left-0 w-72 max-w-[85vw] bg-white dark:bg-[#08182D] z-50 p-4 flex flex-col border-r border-slate-200 dark:border-slate-800 shadow-2xl overflow-y-auto"
              >
                <div className="flex items-center justify-between pb-4 border-b border-slate-200 dark:border-slate-800">
                  <div className="flex items-center gap-2.5">
                    <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-blue-600 to-cyan-500 flex items-center justify-center">
                      <RazorpayMark className="w-5 h-5 text-white" />
                    </div>
                    <span className="text-sm font-bold text-slate-900 dark:text-white">RecoverMandate</span>
                  </div>
                  <button
                    onClick={() => setIsMobileMenuOpen(false)}
                    className="p-1.5 rounded-lg text-slate-400 hover:text-slate-900 dark:hover:text-white"
                  >
                    <X className="w-4 h-4" />
                  </button>
                </div>

                <div className="pt-3 pb-2">
                  <button
                    onClick={() => {
                      setIsMobileMenuOpen(false);
                      setIsCommandPaletteOpen(true);
                    }}
                    className="w-full flex items-center justify-between px-3 py-2 rounded-xl bg-slate-100 dark:bg-slate-800/80 text-xs text-slate-500 dark:text-slate-400 border border-slate-200 dark:border-slate-700/60"
                  >
                    <div className="flex items-center gap-2">
                      <Search className="w-3.5 h-3.5 text-blue-500" />
                      <span>Search or jump to...</span>
                    </div>
                    <kbd className="px-1.5 py-0.5 rounded bg-white dark:bg-slate-900 text-[10px] font-mono">
                      {isMac ? "⌘K" : "Ctrl+K"}
                    </kbd>
                  </button>
                </div>

                <nav className="flex-1 py-3 space-y-1">
                  {navItems.map((item) => {
                    const Icon = item.icon;
                    const isActive = activeTab === item.id;
                    return (
                      <button
                        key={item.id}
                        onClick={() => {
                          handleTabChange(item.id);
                          setIsMobileMenuOpen(false);
                        }}
                        className={`w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs font-bold transition-all ${
                          isActive
                            ? "bg-blue-600 text-white shadow-lg shadow-blue-600/30"
                            : "text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-800/50"
                        }`}
                      >
                        <div className="flex items-center gap-3">
                          <Icon className={`w-4 h-4 ${isActive ? "text-white" : "text-slate-400"}`} />
                          <span>{item.label}</span>
                        </div>
                        {item.badge != null && (
                          <span
                            className={`px-2 py-0.5 text-[10px] font-bold rounded-full ${
                              isActive ? "bg-white/20 text-white" : "bg-amber-100 text-amber-700 dark:bg-amber-500/20 dark:text-amber-300"
                            }`}
                          >
                            {item.badge}
                          </span>
                        )}
                      </button>
                    );
                  })}
                </nav>

                <div className="pt-3 border-t border-slate-200 dark:border-slate-800 space-y-2">
                  <div className="flex items-center justify-between p-2 rounded-xl bg-slate-100 dark:bg-slate-800/50">
                    <span className="text-xs font-medium text-slate-700 dark:text-slate-300">RecoverMandate AI</span>
                    <button
                      onClick={() => setIsRecoverMandateEnabled(!isRecoverMandateEnabled)}
                      className={`relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out ${
                        isRecoverMandateEnabled ? "bg-blue-600" : "bg-slate-400 dark:bg-slate-600"
                      }`}
                    >
                      <span
                        className={`pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white shadow-lg ring-0 transition duration-200 ease-in-out ${
                          isRecoverMandateEnabled ? "translate-x-4" : "translate-x-0"
                        }`}
                      />
                    </button>
                  </div>

                  <a
                    href="/RecoverMandate_Pitch_Deck.pptx"
                    download="RecoverMandate_Pitch_Deck.pptx"
                    className="w-full flex items-center justify-center gap-2 py-2 px-3 rounded-xl bg-blue-600/10 text-xs font-semibold text-blue-600 dark:text-blue-400 border border-blue-500/30"
                  >
                    <Download className="w-3.5 h-3.5" />
                    <span>Pitch Deck (.pptx)</span>
                  </a>

                  <SystemHealthBanner />
                </div>
              </motion.div>
            </>
          )}
        </AnimatePresence>

        <CommandPalette
          isOpen={isCommandPaletteOpen}
          onClose={() => setIsCommandPaletteOpen(false)}
          onNavigate={(tab) => {
            handleTabChange(tab);
            setIsCommandPaletteOpen(false);
          }}
          onOpenShortcuts={() => setIsShortcutsOpen(true)}
        />

        {/* Keyboard Shortcuts Guide Modal */}
        <KeyboardShortcutsModal
          isOpen={isShortcutsOpen}
          onClose={() => setIsShortcutsOpen(false)}
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
        <aside className="app-sidebar hidden lg:flex flex-col border-r border-slate-200 dark:border-slate-800 bg-white/70 dark:bg-[#08182D]/80 backdrop-blur-xl shrink-0 z-20">
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
          <div className="p-3 sm:p-6 lg:p-8 max-w-7xl mx-auto space-y-6 sm:space-y-8 pb-24 lg:pb-8">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
              <div className="flex items-center gap-2 flex-wrap">
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

                <button
                  onClick={() => setIsShortcutsOpen(true)}
                  className="flex items-center gap-1.5 px-3 py-2 rounded-xl glass-card text-xs font-semibold text-slate-700 dark:text-slate-200 hover:text-slate-900 dark:hover:text-white transition-colors border border-slate-200 dark:border-slate-700/60"
                  aria-label="Keyboard Shortcuts Guide"
                  title="Keyboard Shortcuts Guide (?)"
                >
                  <Keyboard className="w-4 h-4 text-[#3395FF]" />
                  <span className="hidden sm:inline">Shortcuts</span>
                  <kbd className="hidden lg:inline text-[9px] font-mono px-1 rounded bg-slate-200 dark:bg-slate-800 text-slate-500 font-bold">?</kbd>
                </button>

                <a
                  href="/RecoverMandate_Pitch_Deck.pptx"
                  download="RecoverMandate_Pitch_Deck.pptx"
                  className="flex items-center gap-1.5 px-3 py-2 rounded-xl bg-blue-600/10 hover:bg-blue-600/20 text-xs font-semibold text-blue-600 dark:text-blue-400 transition-colors border border-blue-500/30"
                  aria-label="Download Presentation Deck (PPTX)"
                  title="Download Pitch Deck Presentation (.pptx)"
                >
                  <Download className="w-4 h-4 text-blue-500" />
                  <span className="hidden md:inline font-bold">Pitch Deck (.pptx)</span>
                </a>
              </div>

              {/* RecoverMandate Toggle (Dashboard Tab) */}
              <AnimatePresence>
                {activeTab === "dashboard" && (
                  <motion.div
                    initial={{ opacity: 0, scale: 0.95 }}
                    animate={{ opacity: 1, scale: 1 }}
                    exit={{ opacity: 0, scale: 0.95 }}
                    className="flex items-center gap-2.5 glass-card rounded-full p-1.5 border-slate-200 dark:border-slate-700/50 shadow-sm"
                    title="Toggle between Legacy Mode (Without RecoverMandate) and RecoverMandate AI Active"
                  >
                    <span
                      className={`text-xs font-semibold pl-3 transition-colors ${
                        !isRecoverMandateEnabled ? "text-rose-500 font-bold" : "text-slate-400"
                      }`}
                    >
                      Legacy Mode
                    </span>
                    <button
                      onClick={() => setIsRecoverMandateEnabled(!isRecoverMandateEnabled)}
                      className={`relative w-14 h-7 rounded-full transition-colors shadow-inner outline-none focus:ring-2 focus:ring-blue-500 ${
                        isRecoverMandateEnabled ? "bg-gradient-to-r from-blue-600 to-cyan-500" : "bg-slate-300 dark:bg-slate-700"
                      }`}
                      aria-label="Toggle RecoverMandate AI comparison mode"
                    >
                      <motion.div
                        className="absolute top-1 left-1 w-5 h-5 rounded-full shadow-md flex items-center justify-center bg-white"
                        animate={{ x: isRecoverMandateEnabled ? 28 : 0 }}
                        transition={{ type: "spring", stiffness: 500, damping: 30 }}
                      >
                        {isRecoverMandateEnabled ? (
                          <Sparkles className="w-3 h-3 text-blue-600" />
                        ) : (
                          <XCircle className="w-3 h-3 text-rose-500" />
                        )}
                      </motion.div>
                    </button>
                    <span
                      className={`text-xs font-semibold pr-3 transition-colors flex items-center gap-1 ${
                        isRecoverMandateEnabled
                          ? "text-transparent bg-clip-text bg-gradient-to-r from-blue-600 to-cyan-500 dark:from-blue-400 dark:to-cyan-300 font-bold"
                          : "text-slate-400"
                      }`}
                    >
                      RecoverMandate AI
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
                      onToggleMode={() => setIsRecoverMandateEnabled(true)}
                      refreshTrigger={refreshTrigger}
                      onNavigate={handleTabChange}
                      confirmedEmail={targetCustomerEmail}
                      onConfirmedEmailChange={handleTargetCustomerEmailChange}
                    />
                  )}
                  {activeTab === "mandates" && (
                    <FailedMandatesPage
                      refreshTrigger={refreshTrigger}
                      onOpenCheckout={(linkId) => {
                        setCheckoutLinkId(linkId);
                        window.location.hash = `pay/${linkId}`;
                      }}
                      onNavigate={handleTabChange}
                    />
                  )}
                  {activeTab === "approvals" && <ApprovalQueuePage refreshTrigger={refreshTrigger} />}
                  {activeTab === "audit" && <AuditLogPage refreshTrigger={refreshTrigger} />}
                </motion.div>
              </AnimatePresence>
            </ErrorBoundary>
          </div>
        </main>

        <nav className="lg:hidden fixed bottom-0 left-0 right-0 z-30 flex items-center justify-around px-2 py-1.5 border-t border-slate-200 dark:border-slate-800 bg-white/95 dark:bg-[#08182D]/95 backdrop-blur-xl shadow-lg">
          {navItems.map((item) => {
            const Icon = item.icon;
            const isActive = activeTab === item.id;
            return (
              <button
                key={item.id}
                onClick={() => {
                  handleTabChange(item.id);
                  const mainEl = document.querySelector(".app-main");
                  if (mainEl) mainEl.scrollTop = 0;
                }}
                className={`flex flex-col items-center justify-center py-1 px-3 rounded-xl transition-all relative ${
                  isActive
                    ? "text-blue-600 dark:text-blue-400 font-bold"
                    : "text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white"
                }`}
              >
                <div className="relative">
                  <Icon className={`w-5 h-5 ${isActive ? "text-blue-600 dark:text-blue-400" : "text-slate-400"}`} />
                  {item.badge != null && (
                    <span className="absolute -top-1 -right-2 px-1 py-0.2 min-w-3.5 h-3.5 text-[9px] font-bold rounded-full bg-amber-500 text-white flex items-center justify-center">
                      {item.badge === "dot" ? "•" : item.badge}
                    </span>
                  )}
                </div>
                <span className="text-[10px] mt-0.5 tracking-tight">{item.label.split(" ")[0]}</span>
              </button>
            );
          })}
        </nav>
      </div>
      <Toaster />
    </>
  );
}
