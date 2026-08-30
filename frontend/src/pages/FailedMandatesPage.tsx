import { useState, useEffect, Fragment } from "react";
import { motion } from "framer-motion";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import {
  RefreshCw,
  ChevronLeft,
  ChevronRight,
  Search,
  ChevronDown,
  ChevronUp,
  ExternalLink,
  Calendar,
  X,
  Download,
  Cpu,
} from "lucide-react";
import {
  fetchPaymentEvents,
  exportRecoveryLedgerCsv,
  type PageResponse,
  type PaymentEventItem,
} from "../lib/api";
import { TransactionFlowDiagram } from "../components/TransactionFlowDiagram";
import { RazorpayMark } from "../components/RazorpayLogo";
import { SmartRetryTimeline } from "../components/SmartRetryTimeline";
import { RetryEligibilityLegend } from "../components/RetryEligibilityLegend";
import { EmptyState } from "../components/EmptyState";
import { formatINR } from "../lib/formatters";
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

interface FailedMandatesPageProps {
  refreshTrigger?: number;
  onOpenCheckout?: (linkId: string) => void;
  onNavigate?: (tab: string) => void;
}

export function FailedMandatesPage({
  refreshTrigger,
  onOpenCheckout,
  onNavigate,
}: FailedMandatesPageProps) {
  const [data, setData] = useState<PageResponse<PaymentEventItem> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [page, setPage] = useState(0);
  const [expandedId, setExpandedId] = useState<number | null>(null);

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

  const load = () => {
    setLoading(true);
    fetchPaymentEvents(page, 20)
      .then(setData)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, [page, refreshTrigger]);

  const toggleExpand = (id: number) => {
    setExpandedId((prev) => (prev === id ? null : id));
  };

  // Client-side filtering
  const rawItems: PaymentEventItem[] = data?.content || [];
  const filteredItems = rawItems.filter((item: PaymentEventItem) => {
    // Search query matching
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase().trim();
      const matchPaymentId = item.razorpayPaymentId?.toLowerCase().includes(q);
      const matchCategory = item.classificationCategory?.toLowerCase().includes(q);
      const matchSubId = item.razorpaySubscriptionId?.toLowerCase().includes(q) || item.subscriptionId?.toLowerCase().includes(q);
      const matchCustName = item.customerName?.toLowerCase().includes(q);
      const matchCustEmail = item.customerEmail?.toLowerCase().includes(q);
      const matchReason = item.errorReason?.toLowerCase().includes(q) || item.failureReasonCode?.toLowerCase().includes(q);
      const matchStatus = item.classificationStatus?.toLowerCase().includes(q);
      if (!matchPaymentId && !matchCategory && !matchSubId && !matchCustName && !matchCustEmail && !matchReason && !matchStatus) {
        return false;
      }
    }

    // Date range matching
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
        <Button onClick={load} variant="outline" size="sm">Retry</Button>
      </div>
    );
  }

  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.4 }} className="space-y-4">
      <div className="glass-card rounded-2xl overflow-hidden shadow-xl">
        {/* Header */}
        <div className="p-6 border-b border-slate-200 dark:border-slate-800 flex flex-col md:flex-row justify-between md:items-center gap-4 bg-white/40 dark:bg-transparent">
          <div>
            <h2 className="text-lg font-bold text-slate-900 dark:text-white">Failed Mandates Log</h2>
            <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
              Live feed of caught Razorpay webhook events. Click any row to inspect the lifecycle flow & retry schedule.
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
                  : "dark:border-slate-700 text-slate-300 hover:text-white"
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
              className="dark:border-slate-700 text-xs font-semibold gap-1.5"
              title="Download recovery ledger CSV"
            >
              <Download className={`w-3.5 h-3.5 text-blue-500 ${isExporting ? "animate-bounce" : ""}`} />
              {isExporting ? "Exporting..." : "Export Ledger (.CSV)"}
            </Button>
            <Button variant="outline" size="sm" onClick={load} className="dark:border-slate-700 text-xs">
              <RefreshCw className={`w-3.5 h-3.5 mr-1.5 ${loading ? "animate-spin" : ""}`} /> Refresh
            </Button>
          </div>
        </div>

        {/* Expandable Retry Eligibility Legend */}
        {showPolicyLegend && (
          <div className="p-4 border-b border-slate-200 dark:border-slate-800 bg-slate-900/40">
            <RetryEligibilityLegend defaultExpanded={true} />
          </div>
        )}

        {/* Filter Toolbar */}
        <div className="p-4 border-b border-slate-200 dark:border-slate-800 bg-slate-50/70 dark:bg-slate-900/40 flex flex-col sm:flex-row items-center justify-between gap-3">
          {/* Search Box */}
          <div className="relative w-full sm:w-72">
            <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
            <Input
              type="text"
              placeholder="Search by Payment ID, Customer, Sub ID..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="pl-9 pr-8 h-9 text-xs bg-white dark:bg-slate-800/80 border-slate-300 dark:border-slate-700"
            />
            {searchQuery && (
              <button
                onClick={() => setSearchQuery("")}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200"
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
                    ? "bg-blue-600 text-white shadow-sm"
                    : "bg-white dark:bg-slate-800 text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-700 border border-slate-200 dark:border-slate-700"
                }`}
              >
                {r === "all" ? "All Time" : r === "7d" ? "Last 7 Days" : "Last 30 Days"}
              </button>
            ))}

            {(searchQuery || dateRange !== "all") && (
              <Badge variant="secondary" className="ml-1 text-[10px] font-mono">
                {filteredItems.length} of {rawItems.length}
              </Badge>
            )}
          </div>
        </div>

        <div className="p-0">
          {loading ? (
            <div className="p-6 space-y-4">
              {Array.from({ length: 5 }).map((_, i) => (
                <div key={i} className="skeleton-shimmer h-16 w-full rounded-xl" />
              ))}
            </div>
          ) : filteredItems.length === 0 ? (
            <EmptyState
              variant={searchQuery || dateRange !== "all" ? "search" : "clean"}
              title={rawItems.length === 0 ? "No Failed Mandates Intercepted" : "No Matching Mandates"}
              description={
                rawItems.length === 0
                  ? "Webhook events from Razorpay subscription payments will automatically stream here with AI failure classifications."
                  : "No mandates matched your current filter criteria. Try clearing search keywords or expanding the time window."
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
          ) : (
            <>
              {/* Desktop Table */}
              <div className="hidden md:block overflow-x-auto">
                <Table>
                  <TableHeader>
                    <TableRow className="border-slate-200 dark:border-slate-800 hover:bg-transparent">
                      <TableHead className="text-xs uppercase tracking-wider text-slate-500 dark:text-slate-400 font-bold">Payment ID</TableHead>
                      <TableHead className="text-xs uppercase tracking-wider text-slate-500 dark:text-slate-400 font-bold">Customer</TableHead>
                      <TableHead className="text-xs uppercase tracking-wider text-slate-500 dark:text-slate-400 font-bold">Amount</TableHead>
                      <TableHead className="text-xs uppercase tracking-wider text-slate-500 dark:text-slate-400 font-bold">Category</TableHead>
                      <TableHead className="text-xs uppercase tracking-wider text-slate-500 dark:text-slate-400 font-bold">Recoverable</TableHead>
                      <TableHead className="text-xs uppercase tracking-wider text-slate-500 dark:text-slate-400 font-bold text-right">Status</TableHead>
                      <TableHead className="w-10"></TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {filteredItems.map((item: PaymentEventItem, i: number) => {
                      const isExpanded = expandedId === item.id;
                      const customerDisplay = item.customerName || (item.customerEmail ? item.customerEmail.split("@")[0] : "Customer");
                      const emailDisplay = item.customerEmail || item.subscriptionId || "subscriber@example.com";

                      return (
                        <Fragment key={item.id}>
                          <motion.tr
                            initial={{ opacity: 0, x: -10 }}
                            animate={{ opacity: 1, x: 0 }}
                            transition={{ delay: i * 0.03 }}
                            onClick={() => toggleExpand(item.id)}
                            className={`border-slate-200 dark:border-slate-800/50 hover:bg-slate-50 dark:hover:bg-slate-800/40 transition-all duration-200 group cursor-pointer ${
                              isExpanded ? "bg-slate-50/80 dark:bg-slate-800/60" : ""
                            }`}
                          >
                            <TableCell className="font-mono text-sm text-slate-700 dark:text-slate-300 group-hover:text-blue-600 dark:group-hover:text-blue-400 transition-colors">
                              {item.razorpayPaymentId}
                            </TableCell>
                            <TableCell>
                              <div className="flex flex-col">
                                <span className="font-semibold text-slate-900 dark:text-slate-100 text-xs">
                                  {customerDisplay}
                                </span>
                                <span className="text-[11px] text-slate-500 dark:text-slate-400 font-mono">
                                  {emailDisplay}
                                </span>
                              </div>
                            </TableCell>
                            <TableCell className="font-bold text-slate-900 dark:text-white">
                              {formatINR(item.amount)}
                            </TableCell>
                            <TableCell>
                              <Badge variant="outline" className={`text-xs font-semibold border ${getCategoryClass(item.classificationCategory)}`}>
                                {getCategoryLabel(item.classificationCategory)}
                              </Badge>
                            </TableCell>
                            <TableCell>
                              {item.autoRecoverable ? (
                                <span className="inline-flex items-center gap-1.5 text-xs font-bold text-emerald-600 dark:text-emerald-400 bg-emerald-100 dark:bg-emerald-500/10 px-2.5 py-1 rounded-md border border-emerald-500/20" title="Auto-retry candidate via Smart Retry Engine">
                                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
                                  Auto-Retry
                                </span>
                              ) : item.classificationCategory === "insufficient_funds" || item.classificationCategory === "expired_mandate" ? (
                                <span className="inline-flex items-center gap-1 text-xs font-bold text-blue-600 dark:text-blue-400 bg-blue-100 dark:bg-blue-500/10 px-2.5 py-1 rounded-md border border-blue-500/20" title="Recoverable via Razorpay Payment Link / Mandate Swap">
                                  Payment Link
                                </span>
                              ) : (
                                <span className="text-xs font-medium text-slate-500 bg-slate-100 dark:bg-slate-800 px-2 py-1 rounded-md">
                                  Manual Triage
                                </span>
                              )}
                            </TableCell>
                            <TableCell className="text-right">
                              {(() => {
                                const statusCfg = getStatusConfig(item.classificationStatus);
                                return (
                                  <div className="flex flex-col items-end" title={statusCfg.description}>
                                    <span className={`text-[11px] font-bold px-2 py-0.5 rounded-md border ${statusCfg.badgeClass}`}>
                                      {statusCfg.label}
                                    </span>
                                    <span className="text-[10px] text-slate-400 dark:text-slate-500 font-medium truncate max-w-[180px]">
                                      {statusCfg.description}
                                    </span>
                                  </div>
                                );
                              })()}
                            </TableCell>
                            <TableCell className="text-slate-400">
                              {isExpanded ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
                            </TableCell>
                          </motion.tr>

                          {/* Expanded Flow Diagram & Smart Retry Schedule Row */}
                          {isExpanded && (
                            <tr>
                              <td colSpan={7} className="p-4 bg-[#02042B]/90 border-b border-[#3395FF]/30 space-y-4">
                                <TransactionFlowDiagram
                                  failurePoint={item.classificationCategory}
                                  category={item.classificationCategory}
                                  failureReasonCode={item.failureReasonCode}
                                  autoRecoverable={item.autoRecoverable}
                                />

                                {/* Smart Retry Engine Schedule Timeline */}
                                <SmartRetryTimeline
                                  schedules={item.retrySchedules}
                                  paymentEventId={item.id}
                                  amount={item.amount}
                                  onUpdate={load}
                                />

                                {/* Razorpay Link Card */}
                                <div className="mt-3 p-3 rounded-xl bg-[#0C2340]/90 border border-[#3395FF]/30 flex flex-col sm:flex-row sm:items-center justify-between gap-3 shadow-inner">
                                  <div className="flex items-center gap-3">
                                    <div className="w-8 h-8 rounded-lg bg-[#02042B] border border-[#3395FF]/40 flex items-center justify-center p-1.5 shrink-0">
                                      <RazorpayMark className="w-4 h-4" />
                                    </div>
                                    <div>
                                      <div className="flex items-center gap-2">
                                        <span className="text-xs font-bold text-white tracking-wide">Razorpay Hosted Recovery Link</span>
                                        <span className="text-[10px] font-mono px-1.5 py-0.2 rounded bg-emerald-500/20 text-emerald-300 border border-emerald-500/30">
                                          Ready for Customer
                                        </span>
                                      </div>
                                      <p className="text-[11px] text-slate-300 font-mono mt-0.5">
                                        Customer: <strong className="text-white">{customerDisplay}</strong> ({emailDisplay})
                                      </p>
                                    </div>
                                  </div>

                                  <div className="flex items-center gap-2 self-end sm:self-auto">
                                    <Button
                                      size="sm"
                                      onClick={(e) => {
                                        e.stopPropagation();
                                        const targetLinkId = item.paymentLinkId || (item.paymentLinkUrl ? item.paymentLinkUrl.substring(item.paymentLinkUrl.lastIndexOf('/') + 1) : null);
                                        if (targetLinkId) {
                                          if (onOpenCheckout) {
                                            onOpenCheckout(targetLinkId);
                                          } else {
                                            window.open(`/pay/${targetLinkId}`, "_blank");
                                          }
                                        } else {
                                          if (onNavigate) {
                                            onNavigate("approvals");
                                          } else {
                                            window.location.hash = "approvals";
                                          }
                                        }
                                      }}
                                      className="bg-[#3395FF] hover:bg-[#2582eb] text-white text-xs font-bold gap-1.5 shadow-md shadow-[#3395FF]/20"
                                    >
                                      <ExternalLink className="w-3.5 h-3.5" />
                                      <span>{item.paymentLinkId ? "Launch Hosted Checkout" : "Review Draft in Queue"}</span>
                                    </Button>
                                  </div>
                                </div>
                              </td>
                            </tr>
                          )}
                        </Fragment>
                      );
                    })}
                  </TableBody>
                </Table>
              </div>

              {/* Mobile Card List */}
              <div className="md:hidden p-4 space-y-3">
                {filteredItems.map((item: PaymentEventItem) => {
                  const isExpanded = expandedId === item.id;
                  const customerDisplay = item.customerName || (item.customerEmail ? item.customerEmail.split("@")[0] : "Customer");
                  const emailDisplay = item.customerEmail || item.subscriptionId || "subscriber@example.com";
                  const statusCfg = getStatusConfig(item.classificationStatus);

                  return (
                    <div
                      key={item.id}
                      className="p-4 rounded-xl bg-white dark:bg-slate-800/30 border border-slate-200 dark:border-slate-700/50 shadow-sm space-y-3"
                    >
                      <div className="flex justify-between items-center" onClick={() => toggleExpand(item.id)}>
                        <div>
                          <span className="font-mono text-xs text-blue-600 dark:text-blue-400 font-medium block">{item.razorpayPaymentId}</span>
                          <span className="text-xs font-bold text-slate-900 dark:text-slate-100">{customerDisplay}</span>
                        </div>
                        <span className="font-bold text-slate-900 dark:text-white text-lg">{formatINR(item.amount)}</span>
                      </div>
                      <div className="flex justify-between items-center gap-2 flex-wrap" onClick={() => toggleExpand(item.id)}>
                        <Badge variant="outline" className={`text-[10px] font-bold border ${getCategoryClass(item.classificationCategory)}`}>
                          {getCategoryLabel(item.classificationCategory)}
                        </Badge>
                        <span className={`text-[10px] font-bold px-2 py-0.5 rounded border ${statusCfg.badgeClass}`} title={statusCfg.description}>
                          {statusCfg.label}
                        </span>
                      </div>
                      {isExpanded && (
                        <div className="pt-2 space-y-3">
                          <p className="text-xs text-slate-400 font-mono">Email: {emailDisplay}</p>
                          <TransactionFlowDiagram
                            failurePoint={item.classificationCategory}
                            category={item.classificationCategory}
                            failureReasonCode={item.failureReasonCode}
                            autoRecoverable={item.autoRecoverable}
                          />
                          <SmartRetryTimeline
                            schedules={item.retrySchedules}
                            paymentEventId={item.id}
                            amount={item.amount}
                            onUpdate={load}
                          />
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>

              {/* Pagination */}
              <div className="p-4 border-t border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-transparent flex items-center justify-between">
                <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
                  Page {page + 1} of {data?.totalPages || 1}
                </span>
                <div className="flex items-center gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setPage(Math.max(0, page - 1))}
                    disabled={page === 0}
                    className="rounded-lg h-9 w-9 p-0 shadow-sm border-slate-300 dark:border-slate-700"
                  >
                    <ChevronLeft className="w-4 h-4" />
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setPage(Math.min((data?.totalPages || 1) - 1, page + 1))}
                    disabled={page >= (data?.totalPages || 1) - 1}
                    className="rounded-lg h-9 w-9 p-0 shadow-sm border-slate-300 dark:border-slate-700"
                  >
                    <ChevronRight className="w-4 h-4" />
                  </Button>
                </div>
              </div>
            </>
          )}
        </div>
      </div>
    </motion.div>
  );
}
