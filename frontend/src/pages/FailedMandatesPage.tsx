import { useState, useEffect } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import {
  RefreshCw,
  ChevronLeft,
  ChevronRight,
  Search,
  ExternalLink,
  Calendar,
  X,
  Download,
  Cpu,
  Zap,
  CheckCircle,
  Check,
  Bot,
  Activity,
  ArrowRight,
  Clock,
  ChevronRight as ChevronRightIcon,
} from "lucide-react";
import {
  fetchPaymentEvents,
  exportRecoveryLedgerCsv,
  type PageResponse,
  type PaymentEventItem,
} from "../lib/api";
import { TransactionFlowDiagram } from "../components/TransactionFlowDiagram";
import { RazorpayMark, RazorpayBadge } from "../components/RazorpayLogo";
import { SmartRetryTimeline } from "../components/SmartRetryTimeline";
import { RetryEligibilityLegend } from "../components/RetryEligibilityLegend";
import { EmptyState } from "../components/EmptyState";
import { formatINR, formatDateIST } from "../lib/formatters";
import { getStatusConfig } from "../lib/statusFormatters";

function getCategoryClass(cat: string | null | undefined) {
  if (!cat) return "";
  const lower = cat.toLowerCase();
  if (lower.includes("insufficient")) return "category-insufficient_funds";
  if (lower.includes("technical")) return "category-technical_decline";
  if (lower.includes("expired")) return "category-expired_mandate";
  return "category-unknown";
}

function getCategoryLabel(cat: string | null | undefined) {
  if (!cat) return "PENDING";
  return cat.replace(/_/g, " ").replace(/\b\w/g, (l) => l.toUpperCase());
}

export function isDemoRecord(item: PaymentEventItem | null | undefined): boolean {
  if (!item) return false;
  return Boolean(
    item.isDemoData ||
    item.razorpayPaymentId?.startsWith("pay_demo_") ||
    item.customerEmail?.includes("demo.customer") ||
    item.customerEmail?.includes("sujaypaul2711@gmail.com") ||
    item.subscriptionId?.startsWith("sub_demo_") ||
    item.razorpaySubscriptionId?.startsWith("sub_demo_")
  );
}

interface FailedMandatesPageProps {
  refreshTrigger?: number;
  onOpenCheckout?: (linkId: string) => void;
  onNavigate?: (tab: string) => void;
}

type SubTableTab = "live" | "recovered" | "demo";

export function FailedMandatesPage({
  refreshTrigger,
  onOpenCheckout,
  onNavigate,
}: FailedMandatesPageProps) {
  const [data, setData] = useState<PageResponse<PaymentEventItem> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [page, setPage] = useState(0);
  const [selectedDrawerItem, setSelectedDrawerItem] = useState<PaymentEventItem | null>(null);

  // Sub-Navigation Tabs
  const [activeTab, setActiveTab] = useState<SubTableTab>("live");

  // Filters
  const [searchQuery, setSearchQuery] = useState("");
  const [dateRange, setDateRange] = useState<"all" | "7d" | "30d">("all");
  const [isExporting, setIsExporting] = useState(false);
  const [showPolicyLegend, setShowPolicyLegend] = useState(false);

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

  const load = (showSkeleton = false) => {
    if (showSkeleton) {
      setLoading(true);
    }
    fetchPaymentEvents(page, 50)
      .then(setData)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load(!data);
  }, [page, refreshTrigger]);

  // Keep selectedDrawerItem updated if data reloads
  useEffect(() => {
    if (selectedDrawerItem && data?.content) {
      const refreshed = data.content.find((item) => item.id === selectedDrawerItem.id);
      if (refreshed) {
        setSelectedDrawerItem(refreshed);
      }
    }
  }, [data]);

  // Client-side filtering
  const rawItems: PaymentEventItem[] = data?.content || [];

  // Tab counts
  const liveCount = rawItems.filter((item) => !isDemoRecord(item)).length;
  const recoveredCount = rawItems.filter(
    (item) =>
      item.recoveryStatus === "RECOVERED" ||
      item.classificationStatus === "RECOVERED" ||
      item.recoveryStatus === "COMPLETED" ||
      item.classificationStatus === "COMPLETED" ||
      item.classificationStatus === "SUPERSEDED"
  ).length;
  const demoCount = rawItems.filter((item) => isDemoRecord(item)).length;

  const filteredItems = rawItems
    .filter((item: PaymentEventItem) => {
      const isDemo = isDemoRecord(item);

      // 1. Sub-table Tab Filter
      if (activeTab === "live" && isDemo) {
        return false;
      }
      if (activeTab === "recovered") {
        const isRecOrComp =
          item.recoveryStatus === "RECOVERED" ||
          item.classificationStatus === "RECOVERED" ||
          item.recoveryStatus === "COMPLETED" ||
          item.classificationStatus === "COMPLETED" ||
          item.classificationStatus === "SUPERSEDED";
        if (!isRecOrComp) {
          return false;
        }
      }
      if (activeTab === "demo" && !isDemo) {
        return false;
      }

      // 2. Search Query Matching
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase().trim();
        const matchPaymentId = item.razorpayPaymentId?.toLowerCase().includes(q);
        const matchCategory = item.classificationCategory?.toLowerCase().includes(q);
        const matchSubId =
          item.razorpaySubscriptionId?.toLowerCase().includes(q) ||
          item.subscriptionId?.toLowerCase().includes(q);
        const matchCustName = item.customerName?.toLowerCase().includes(q);
        const matchCustEmail = item.customerEmail?.toLowerCase().includes(q);
        const matchReason =
          item.errorReason?.toLowerCase().includes(q) ||
          item.failureReasonCode?.toLowerCase().includes(q);
        const matchStatus = item.classificationStatus?.toLowerCase().includes(q);
        if (
          !matchPaymentId &&
          !matchCategory &&
          !matchSubId &&
          !matchCustName &&
          !matchCustEmail &&
          !matchReason &&
          !matchStatus
        ) {
          return false;
        }
      }

      // 3. Date Range Matching
      if (dateRange !== "all" && item.createdAt) {
        const itemTime = new Date(item.createdAt).getTime();
        const now = Date.now();
        const days = dateRange === "7d" ? 7 : 30;
        const cutoff = now - days * 24 * 60 * 60 * 1000;
        if (itemTime < cutoff) {
          return false;
        }
      }

      return true;
    })
    .sort((a: PaymentEventItem, b: PaymentEventItem) => {
      const timeA = new Date(a.receivedAt || a.createdAt || 0).getTime();
      const timeB = new Date(b.receivedAt || b.createdAt || 0).getTime();
      if (timeB !== timeA) return timeB - timeA;
      return (b.id || 0) - (a.id || 0);
    });

  if (error) {
    return (
      <div className="glass-card rounded-2xl p-8 text-center text-rose-500 space-y-3">
        <p className="font-bold">Failed to load payment events: {error}</p>
        <Button onClick={() => load(true)} variant="outline" size="sm">
          Retry
        </Button>
      </div>
    );
  }

  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.4 }} className="space-y-4">
      <div className="glass-card rounded-2xl overflow-hidden shadow-xl border border-slate-700/60 bg-[#0C2340]/90">
        {/* Header */}
        <div className="p-6 border-b border-slate-700/70 flex flex-col md:flex-row justify-between md:items-center gap-4 bg-[#02042B]/50">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <h2 className="text-lg font-bold text-slate-900 dark:text-white">Failed Mandates Ledger</h2>
              <RazorpayBadge text="Razorpay Webhooks" subtext="Automated Interception" />
            </div>
            <p className="text-xs text-slate-400">
              Live feed of intercepted recurring mandate payment failures, smart auto-retries, and payment link dunning. Click any row to open the lifecycle inspector drawer.
            </p>
          </div>
          <div className="flex items-center gap-2 flex-wrap">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setShowPolicyLegend(!showPolicyLegend)}
              className={`text-xs font-semibold gap-1.5 transition-all ${
                showPolicyLegend
                  ? "bg-[#3395FF]/20 text-[#93c5fd] border-[#3395FF]/50"
                  : "border-slate-700 text-slate-300 hover:text-white hover:bg-slate-800"
              }`}
              title="View Auto-Retry eligibility rules across categories"
            >
              <Cpu className="w-3.5 h-3.5 text-[#3395FF]" />
              <span>{showPolicyLegend ? "Hide Policy Legend" : "Retry Policy Legend"}</span>
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={handleExportCsv}
              disabled={isExporting}
              className="border-slate-700 text-slate-300 hover:text-white hover:bg-slate-800 text-xs font-semibold gap-1.5"
              title="Download recovery ledger CSV"
            >
              <Download className={`w-3.5 h-3.5 text-[#3395FF] ${isExporting ? "animate-bounce" : ""}`} />
              {isExporting ? "Exporting..." : "Export Ledger (.CSV)"}
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={() => load(true)}
              className="border-slate-700 text-slate-300 hover:text-white hover:bg-slate-800 text-xs font-semibold"
            >
              <RefreshCw className={`w-3.5 h-3.5 mr-1.5 ${loading ? "animate-spin" : ""}`} /> Refresh
            </Button>
          </div>
        </div>

        {/* Sub-Navigation Tab Bar (3 Dedicated Sub-Tables) */}
        <div className="px-6 pt-4 pb-2 border-b border-slate-700/60 bg-[#02042B]/30 flex flex-col sm:flex-row justify-between sm:items-center gap-3">
          <div className="flex items-center gap-2 bg-[#02042B] p-1 rounded-xl border border-slate-700/80">
            <button
              onClick={() => {
                setActiveTab("live");
                setPage(0);
              }}
              className={`flex items-center gap-2 px-3.5 py-1.5 rounded-lg text-xs font-bold transition-all ${
                activeTab === "live"
                  ? "bg-[#3395FF] text-white shadow-lg shadow-[#3395FF]/20"
                  : "text-slate-400 hover:text-slate-200"
              }`}
            >
              <Zap className="w-3.5 h-3.5 text-amber-300" />
              <span>Live Production Log</span>
              <span className={`px-1.5 py-0.2 rounded text-[10px] font-mono ${
                activeTab === "live" ? "bg-white/20 text-white" : "bg-slate-800 text-slate-400"
              }`}>
                {liveCount}
              </span>
            </button>

            <button
              onClick={() => {
                setActiveTab("recovered");
                setPage(0);
              }}
              className={`flex items-center gap-2 px-3.5 py-1.5 rounded-lg text-xs font-bold transition-all ${
                activeTab === "recovered"
                  ? "bg-emerald-600 text-white shadow-lg shadow-emerald-600/20"
                  : "text-slate-400 hover:text-slate-200"
              }`}
            >
              <CheckCircle className="w-3.5 h-3.5 text-emerald-200" />
              <span>Recovered Payments Ledger</span>
              <span className={`px-1.5 py-0.2 rounded text-[10px] font-mono ${
                activeTab === "recovered" ? "bg-white/20 text-white" : "bg-slate-800 text-slate-400"
              }`}>
                {recoveredCount}
              </span>
            </button>

            <button
              onClick={() => {
                setActiveTab("demo");
                setPage(0);
              }}
              className={`flex items-center gap-2 px-3.5 py-1.5 rounded-lg text-xs font-bold transition-all ${
                activeTab === "demo"
                  ? "bg-slate-700 text-white shadow-lg"
                  : "text-slate-400 hover:text-slate-200"
              }`}
            >
              <Bot className="w-3.5 h-3.5 text-purple-300" />
              <span>Demo &amp; Sandbox Queue</span>
              <span className={`px-1.5 py-0.2 rounded text-[10px] font-mono ${
                activeTab === "demo" ? "bg-white/20 text-white" : "bg-slate-800 text-slate-400"
              }`}>
                {demoCount}
              </span>
            </button>
          </div>

          <div className="text-xs text-slate-400 font-medium">
            Showing <strong className="text-white">{filteredItems.length}</strong> {activeTab} records
          </div>
        </div>

        {/* Expandable Retry Eligibility Legend */}
        {showPolicyLegend && (
          <div className="p-4 border-b border-slate-700/60 bg-[#02042B]/70">
            <RetryEligibilityLegend defaultExpanded={true} />
          </div>
        )}

        {/* Filter Toolbar */}
        <div className="p-4 border-b border-slate-700/60 bg-[#02042B]/20 flex flex-col sm:flex-row items-center justify-between gap-3">
          {/* Search Box */}
          <div className="relative w-full sm:w-80">
            <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
            <Input
              type="text"
              placeholder="Search by Payment ID, Customer, Email..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="pl-9 pr-8 h-9 text-xs bg-[#02042B] border-slate-700 text-white placeholder:text-slate-500 focus:border-[#3395FF]"
            />
            {searchQuery && (
              <button
                onClick={() => setSearchQuery("")}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-200"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            )}
          </div>

          {/* Date Range Selector */}
          <div className="flex items-center gap-1.5 w-full sm:w-auto justify-end">
            <span className="text-xs font-semibold text-slate-400 mr-1 flex items-center gap-1">
              <Calendar className="w-3.5 h-3.5" /> Period:
            </span>
            {(["all", "7d", "30d"] as const).map((r) => (
              <button
                key={r}
                onClick={() => setDateRange(r)}
                className={`px-3 py-1 rounded-lg text-xs font-bold transition-all ${
                  dateRange === r
                    ? "bg-[#3395FF] text-white shadow-sm"
                    : "bg-[#02042B] text-slate-400 hover:text-slate-200 border border-slate-700"
                }`}
              >
                {r === "all" ? "All Time" : r === "7d" ? "Last 7 Days" : "Last 30 Days"}
              </button>
            ))}

            {(searchQuery || dateRange !== "all") && (
              <Badge variant="secondary" className="ml-1 text-[10px] font-mono bg-slate-800 text-slate-300">
                {filteredItems.length} of {rawItems.length}
              </Badge>
            )}
          </div>
        </div>

        <div className="p-0">
          {loading ? (
            <div className="p-6 space-y-4">
              {Array.from({ length: 5 }).map((_, i) => (
                <div key={i} className="h-16 w-full rounded-xl bg-slate-800/40 animate-pulse" />
              ))}
            </div>
          ) : filteredItems.length === 0 ? (
            <div className="p-8">
              <EmptyState
                icon={<CheckCircle className="w-8 h-8 text-emerald-400" />}
                variant={searchQuery || dateRange !== "all" ? "search" : "clean"}
                title={
                  activeTab === "live"
                    ? "No Live Failed Mandates"
                    : activeTab === "recovered"
                    ? "No Recovered Payments Yet"
                    : "No Demo Records Ingested"
                }
                description={
                  activeTab === "live"
                    ? "Real webhook events from your Razorpay test/live subscriptions will stream here in real time."
                    : activeTab === "recovered"
                    ? "Mandates recovered via payment links or automated smart retries will appear in this ledger."
                    : "Simulate a failure from the Overview & ROI dashboard to generate sandbox test records."
                }
                action={
                  searchQuery || dateRange !== "all"
                    ? {
                        label: "Clear Filters",
                        onClick: () => {
                          setSearchQuery("");
                          setDateRange("all");
                        },
                      }
                    : undefined
                }
              />
            </div>
          ) : (
            <>
              {/* Desktop Table with Fixed Layout and Clean Spacing */}
              <div className="hidden md:block overflow-x-auto">
                <Table className="table-fixed w-full">
                  <TableHeader>
                    <TableRow className="border-b border-slate-700/80 bg-[#02042B]/40 hover:bg-transparent">
                      <TableHead className="w-[18%] px-6 py-4 text-xs uppercase tracking-wider text-slate-400 font-bold">
                        Payment ID
                      </TableHead>
                      <TableHead className="w-[22%] px-6 py-4 text-xs uppercase tracking-wider text-slate-400 font-bold">
                        Customer
                      </TableHead>
                      <TableHead className="w-[12%] px-6 py-4 text-xs uppercase tracking-wider text-slate-400 font-bold">
                        Amount
                      </TableHead>
                      <TableHead className="w-[14%] px-6 py-4 text-xs uppercase tracking-wider text-slate-400 font-bold">
                        Category
                      </TableHead>
                      <TableHead className="w-[11%] px-6 py-4 text-xs uppercase tracking-wider text-slate-400 font-bold">
                        Data Source
                      </TableHead>
                      <TableHead className="w-[11%] px-6 py-4 text-xs uppercase tracking-wider text-slate-400 font-bold">
                        Recovery
                      </TableHead>
                      <TableHead className="w-[12%] px-6 py-4 text-xs uppercase tracking-wider text-slate-400 font-bold text-right">
                        Status
                      </TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {filteredItems.map((item: PaymentEventItem, i: number) => {
                      const isSelected = selectedDrawerItem?.id === item.id;
                      const isVoidEmail = !item.customerEmail || item.customerEmail.toLowerCase().includes("void@") || item.customerEmail.toLowerCase().endsWith("@razorpay.com") || item.customerEmail.toLowerCase() === "null";
                      const emailDisplay: string = (isVoidEmail || !item.customerEmail) ? "sujaypaul2711@gmail.com" : item.customerEmail;
                      const isVoidName = !item.customerName || item.customerName.toLowerCase() === "void" || item.customerName.toLowerCase() === "null";
                      const customerDisplay: string = (isVoidName || !item.customerName) ? (isVoidEmail ? "Sujay Paul" : (emailDisplay.split("@")[0] || "Customer")) : item.customerName;
                      const isRecovered =
                        item.recoveryStatus === "RECOVERED" ||
                        item.classificationStatus === "RECOVERED";
                      const statusCfg = getStatusConfig(item.classificationStatus);

                      return (
                        <motion.tr
                          key={item.id}
                          initial={{ opacity: 0, y: 4 }}
                          animate={{ opacity: 1, y: 0 }}
                          transition={{ delay: i * 0.02 }}
                          onClick={() => setSelectedDrawerItem(item)}
                          className={`border-b border-slate-800/60 hover:bg-[#133055]/50 transition-all duration-150 cursor-pointer group ${
                            isSelected ? "bg-[#133055]/80 border-l-4 border-l-[#3395FF]" : ""
                          }`}
                        >
                          {/* Payment ID */}
                          <TableCell className="px-6 py-4">
                            <div className="flex items-center gap-1.5">
                              <span className="font-mono text-xs font-semibold text-[#93c5fd] group-hover:text-blue-300 transition-colors truncate block">
                                {item.razorpayPaymentId}
                              </span>
                              <ChevronRightIcon className="w-3.5 h-3.5 text-slate-500 opacity-0 group-hover:opacity-100 transition-opacity shrink-0" />
                            </div>
                          </TableCell>

                          {/* Customer */}
                          <TableCell className="px-6 py-4">
                            <div className="flex flex-col min-w-0">
                              <span className="font-semibold text-slate-100 text-xs truncate">
                                {customerDisplay}
                              </span>
                              <span className="text-[11px] text-slate-400 font-mono truncate">
                                {emailDisplay}
                              </span>
                            </div>
                          </TableCell>

                          {/* Amount */}
                          <TableCell className="px-6 py-4 font-bold text-white text-sm">
                            {formatINR(item.amount)}
                          </TableCell>

                          {/* Category */}
                          <TableCell className="px-6 py-4">
                            <Badge
                              variant="outline"
                              className={`text-xs font-semibold border ${getCategoryClass(
                                item.classificationCategory
                              )}`}
                            >
                              {getCategoryLabel(item.classificationCategory)}
                            </Badge>
                          </TableCell>

                          {/* Data Source Pill */}
                          <TableCell className="px-6 py-4">
                            {!isDemoRecord(item) ? (
                              <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-[#3395FF]/10 text-[#93c5fd] border border-[#3395FF]/30 shadow-sm shadow-[#3395FF]/10 whitespace-nowrap">
                                <span className="w-1.5 h-1.5 rounded-full bg-[#3395FF]" />
                                Live Data
                              </span>
                            ) : (
                              <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-slate-700/40 text-slate-300 border border-slate-600/40 whitespace-nowrap">
                                <span className="w-1.5 h-1.5 rounded-full bg-slate-400" />
                                Demo Data
                              </span>
                            )}
                          </TableCell>

                          {/* Recovery Status Pill */}
                          <TableCell className="px-6 py-4">
                            {isRecovered ? (
                              <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 shadow-sm shadow-emerald-500/10 whitespace-nowrap">
                                <Check className="w-3 h-3 text-emerald-400" />
                                Recovered
                              </span>
                            ) : (item.recoveryStatus === "COMPLETED" || item.classificationStatus === "COMPLETED" || item.classificationStatus === "SUPERSEDED") ? (
                              <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-teal-500/10 text-teal-300 border border-teal-500/20 shadow-sm shadow-teal-500/10 whitespace-nowrap">
                                <Check className="w-3 h-3 text-teal-300" />
                                Completed
                              </span>
                            ) : (
                              <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-amber-500/10 text-amber-400 border border-amber-500/20 whitespace-nowrap">
                                <span className="w-1.5 h-1.5 rounded-full bg-amber-400 animate-pulse" />
                                In Progress
                              </span>
                            )}
                          </TableCell>

                          {/* Classification Status (Clean Badge without smushed descriptions) */}
                          <TableCell className="px-6 py-4 text-right">
                            <span
                              className={`inline-block text-[11px] font-bold px-2.5 py-0.5 rounded-md border ${statusCfg.badgeClass}`}
                              title={statusCfg.description}
                            >
                              {statusCfg.label}
                            </span>
                          </TableCell>
                        </motion.tr>
                      );
                    })}
                  </TableBody>
                </Table>
              </div>

              {/* Mobile Card List */}
              <div className="md:hidden p-4 space-y-3">
                {filteredItems.map((item: PaymentEventItem) => {
                  const customerDisplay =
                    item.customerName ||
                    (item.customerEmail ? item.customerEmail.split("@")[0] : "Customer");
                  const isRecovered =
                    item.recoveryStatus === "RECOVERED" ||
                    item.classificationStatus === "RECOVERED";
                  const statusCfg = getStatusConfig(item.classificationStatus);

                  return (
                    <div
                      key={item.id}
                      onClick={() => setSelectedDrawerItem(item)}
                      className="p-4 rounded-xl bg-[#02042B] border border-slate-700/70 shadow-sm space-y-3 cursor-pointer active:bg-slate-800"
                    >
                      <div className="flex justify-between items-center">
                        <div>
                          <span className="font-mono text-xs text-[#93c5fd] font-medium block">
                            {item.razorpayPaymentId}
                          </span>
                          <span className="text-xs font-bold text-white">{customerDisplay}</span>
                        </div>
                        <span className="font-bold text-white text-base">
                          {formatINR(item.amount)}
                        </span>
                      </div>

                      <div className="flex items-center gap-2 flex-wrap">
                        <Badge
                          variant="outline"
                          className={`text-[10px] font-bold border ${getCategoryClass(
                            item.classificationCategory
                          )}`}
                        >
                          {getCategoryLabel(item.classificationCategory)}
                        </Badge>

                        {!isDemoRecord(item) ? (
                          <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-[#3395FF]/10 text-[#93c5fd] border border-[#3395FF]/30">
                            Live Data
                          </span>
                        ) : (
                          <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-slate-700/40 text-slate-300 border border-slate-600/40">
                            Demo Data
                          </span>
                        )}

                        {isRecovered ? (
                          <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                            ✓ Recovered
                          </span>
                        ) : (item.recoveryStatus === "COMPLETED" || item.classificationStatus === "COMPLETED" || item.classificationStatus === "SUPERSEDED") ? (
                          <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-teal-500/10 text-teal-300 border border-teal-500/20">
                            ✓ Completed
                          </span>
                        ) : (
                          <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-amber-500/10 text-amber-400 border border-amber-500/20">
                            ⏳ In Progress
                          </span>
                        )}

                        <span className={`text-[10px] font-bold px-2 py-0.5 rounded border ml-auto ${statusCfg.badgeClass}`}>
                          {statusCfg.label}
                        </span>
                      </div>
                    </div>
                  );
                })}
              </div>

              {/* Pagination */}
              <div className="p-4 border-t border-slate-700/60 bg-[#02042B]/40 flex items-center justify-between">
                <span className="text-xs font-bold uppercase tracking-wider text-slate-400">
                  Page {page + 1} of {data?.totalPages || 1}
                </span>
                <div className="flex items-center gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setPage(Math.max(0, page - 1))}
                    disabled={page === 0}
                    className="rounded-lg h-8 w-8 p-0 border-slate-700 text-slate-300 hover:bg-slate-800"
                  >
                    <ChevronLeft className="w-4 h-4" />
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setPage(Math.min((data?.totalPages || 1) - 1, page + 1))}
                    disabled={page >= (data?.totalPages || 1) - 1}
                    className="rounded-lg h-8 w-8 p-0 border-slate-700 text-slate-300 hover:bg-slate-800"
                  >
                    <ChevronRight className="w-4 h-4" />
                  </Button>
                </div>
              </div>
            </>
          )}
        </div>
      </div>

      {/* Right Slide-Over Inspector Drawer */}
      <AnimatePresence>
        {selectedDrawerItem && (
          <div className="fixed inset-0 z-50 flex justify-end">
            {/* Backdrop */}
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={() => setSelectedDrawerItem(null)}
              className="fixed inset-0 bg-slate-950/75 backdrop-blur-sm"
            />

            {/* Slide-Over Panel */}
            <motion.div
              initial={{ x: "100%" }}
              animate={{ x: 0 }}
              exit={{ x: "100%" }}
              transition={{ type: "spring", stiffness: 350, damping: 35 }}
              className="relative w-full max-w-2xl bg-[#0C2340] border-l border-[#3395FF]/40 shadow-2xl h-full flex flex-col z-10 text-white overflow-hidden"
            >
              {/* Drawer Header */}
              <div className="p-6 border-b border-slate-700/80 bg-[#02042B]/80 flex items-center justify-between shrink-0">
                <div>
                  <div className="flex items-center gap-2 mb-1">
                    <h3 className="text-base font-bold text-white">Mandate Lifecycle Inspector</h3>
                    <RazorpayBadge text="Automated Recovery" />
                  </div>
                  <p className="text-xs text-slate-400 font-mono">
                    Payment Event ID: <span className="text-[#93c5fd] font-bold">{selectedDrawerItem.razorpayPaymentId}</span>
                  </p>
                </div>

                <button
                  onClick={() => setSelectedDrawerItem(null)}
                  className="w-8 h-8 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-400 hover:text-white flex items-center justify-center transition-colors border border-slate-700"
                  title="Close Inspector"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>

              {/* Drawer Body (Scrollable) */}
              <div className="flex-1 overflow-y-auto p-6 space-y-6">
                {/* Top Quick Status Pill Card */}
                <div className="p-4 rounded-xl bg-[#02042B] border border-slate-700/80 space-y-3">
                  <div className="flex flex-wrap items-center justify-between gap-2 pb-3 border-b border-slate-800">
                    <div className="flex items-center gap-2">
                      {!isDemoRecord(selectedDrawerItem) ? (
                        <span className="px-2.5 py-1 rounded-full text-xs font-bold bg-[#3395FF]/10 text-[#93c5fd] border border-[#3395FF]/30">
                          ● Live Data
                        </span>
                      ) : (
                        <span className="px-2.5 py-1 rounded-full text-xs font-bold bg-slate-700/40 text-slate-300 border border-slate-600/40">
                          ● Demo Data
                        </span>
                      )}

                      {selectedDrawerItem.recoveryStatus === "RECOVERED" ||
                      selectedDrawerItem.classificationStatus === "RECOVERED" ? (
                        <span className="px-2.5 py-1 rounded-full text-xs font-bold bg-emerald-500/15 text-emerald-300 border border-emerald-500/30">
                          ✓ Payment Recovered
                        </span>
                      ) : selectedDrawerItem.recoveryStatus === "COMPLETED" ||
                      selectedDrawerItem.classificationStatus === "COMPLETED" ||
                      selectedDrawerItem.classificationStatus === "SUPERSEDED" ? (
                        <span className="px-2.5 py-1 rounded-full text-xs font-bold bg-teal-500/15 text-teal-300 border border-teal-500/30">
                          ✓ Mandate Resolved / Completed
                        </span>
                      ) : (
                        <span className="px-2.5 py-1 rounded-full text-xs font-bold bg-amber-500/15 text-amber-300 border border-amber-500/30">
                          ⏳ Recovery In Progress
                        </span>
                      )}
                    </div>

                    <span className="text-xl font-extrabold text-white">
                      {formatINR(selectedDrawerItem.amount)}
                    </span>
                  </div>

                  <div className="grid grid-cols-2 gap-3 text-xs font-mono text-slate-300">
                    <div>
                      <span className="text-slate-500 block text-[10px] uppercase">Customer</span>
                      <strong className="text-white">
                        {(!selectedDrawerItem.customerName || selectedDrawerItem.customerName.toLowerCase() === "void")
                          ? "Rsiv ece2024"
                          : selectedDrawerItem.customerName}
                      </strong>
                    </div>
                    <div>
                      <span className="text-slate-500 block text-[10px] uppercase">Email</span>
                      <span className="text-slate-300 truncate block">
                        {(!selectedDrawerItem.customerEmail || selectedDrawerItem.customerEmail.toLowerCase().includes("void@") || selectedDrawerItem.customerEmail.toLowerCase().endsWith("@razorpay.com"))
                          ? "sujaypaul2711@gmail.com"
                          : selectedDrawerItem.customerEmail}
                      </span>
                    </div>
                    <div>
                      <span className="text-slate-500 block text-[10px] uppercase">Subscription ID</span>
                      <span className="text-slate-300 truncate block">
                        {selectedDrawerItem.razorpaySubscriptionId || selectedDrawerItem.subscriptionId || "N/A"}
                      </span>
                    </div>
                    <div>
                      <span className="text-slate-500 block text-[10px] uppercase">Received Time</span>
                      <span className="text-slate-300 truncate block">
                        {selectedDrawerItem.receivedAt ? formatDateIST(selectedDrawerItem.receivedAt) : "N/A"}
                      </span>
                    </div>
                  </div>
                </div>

                {/* Section 1: Transaction Path Diagram */}
                <div>
                  <div className="flex items-center gap-2 mb-2.5">
                    <Activity className="w-4 h-4 text-[#3395FF]" />
                    <h4 className="text-xs font-bold uppercase tracking-wider text-slate-300">
                      Transaction Path &amp; Failure Point
                    </h4>
                  </div>
                  <TransactionFlowDiagram
                    failurePoint={selectedDrawerItem.classificationCategory}
                    category={selectedDrawerItem.classificationCategory}
                    failureReasonCode={selectedDrawerItem.failureReasonCode}
                    autoRecoverable={selectedDrawerItem.autoRecoverable}
                  />
                </div>

                {/* Section 2: Smart Retry Timeline */}
                <div>
                  <div className="flex items-center gap-2 mb-2.5">
                    <Clock className="w-4 h-4 text-emerald-400" />
                    <h4 className="text-xs font-bold uppercase tracking-wider text-slate-300">
                      Smart Retry Schedule (Indian Banking Rails)
                    </h4>
                  </div>
                  <SmartRetryTimeline
                    schedules={selectedDrawerItem.retrySchedules}
                    paymentEventId={selectedDrawerItem.id}
                    amount={selectedDrawerItem.amount}
                    onUpdate={load}
                  />
                </div>

                {/* Section 3: Razorpay Payment Link Recovery Action */}
                <div>
                  <div className="flex items-center gap-2 mb-2.5">
                    <RazorpayMark className="w-4 h-4" />
                    <h4 className="text-xs font-bold uppercase tracking-wider text-slate-300">
                      Payment Link Settlement Channel
                    </h4>
                  </div>

                  <div className="p-4 rounded-xl bg-[#02042B] border border-[#3395FF]/30 space-y-3">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <span className="text-xs font-bold text-white">Razorpay Hosted Payment Link</span>
                        <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-emerald-500/20 text-emerald-300 border border-emerald-500/30">
                          {selectedDrawerItem.paymentLinkId ? "Active Link" : "Pending Approval"}
                        </span>
                      </div>
                      <span className="text-xs font-mono text-slate-400">
                        {selectedDrawerItem.paymentLinkId || "plink_preview"}
                      </span>
                    </div>

                    {selectedDrawerItem.paymentLinkUrl && (
                      <div className="p-2 rounded bg-slate-900/80 border border-slate-800 font-mono text-xs text-[#93c5fd] break-all">
                        {selectedDrawerItem.paymentLinkUrl}
                      </div>
                    )}

                    <div className="flex items-center justify-end gap-3 pt-2">
                      {selectedDrawerItem.paymentLinkId ? (
                        <Button
                          size="sm"
                          onClick={() => {
                            if (onOpenCheckout) {
                              onOpenCheckout(selectedDrawerItem.paymentLinkId!);
                            } else {
                              window.open(`/pay/${selectedDrawerItem.paymentLinkId}`, "_blank");
                            }
                          }}
                          className="bg-[#3395FF] hover:bg-[#2582eb] text-white text-xs font-bold gap-1.5 shadow-lg shadow-[#3395FF]/20"
                        >
                          <ExternalLink className="w-3.5 h-3.5" />
                          <span>Open Hosted Checkout</span>
                        </Button>
                      ) : (
                        <Button
                          size="sm"
                          onClick={() => {
                            setSelectedDrawerItem(null);
                            if (onNavigate) {
                              onNavigate("approvals");
                            } else {
                              window.location.hash = "approvals";
                            }
                          }}
                          className="bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-500 hover:to-indigo-500 text-white text-xs font-bold gap-1.5"
                        >
                          <ArrowRight className="w-3.5 h-3.5" />
                          <span>Review &amp; Dispatch in Approval Queue</span>
                        </Button>
                      )}
                    </div>
                  </div>
                </div>
              </div>

              {/* Drawer Footer */}
              <div className="p-4 border-t border-slate-700/80 bg-[#02042B]/80 flex justify-between items-center shrink-0">
                <span className="text-xs text-slate-400 font-mono">
                  Trace ID: {selectedDrawerItem.traceId || "trace_" + selectedDrawerItem.id}
                </span>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setSelectedDrawerItem(null)}
                  className="border-slate-700 text-slate-300 hover:text-white text-xs"
                >
                  Close
                </Button>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>
    </motion.div>
  );
}
