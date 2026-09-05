import { useState, useEffect } from "react";
import { motion, AnimatePresence } from "framer-motion";
import {
  ShieldCheck,
  CheckCircle2,
  AlertTriangle,
  Zap,
  Lock,
  Building2,
  QrCode,
  Smartphone,
  CreditCard,
  ChevronLeft,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { RazorpayMark } from "../components/RazorpayLogo";
import {
  fetchCheckoutDetails,
  simulateCheckoutPayment,
  type CheckoutDetails,
} from "../lib/api";
import { formatINR } from "../lib/formatters";

interface CustomerCheckoutPageProps {
  linkId: string;
  onBackToDashboard?: () => void;
}

export function CustomerCheckoutPage({
  linkId,
  onBackToDashboard,
}: CustomerCheckoutPageProps) {
  const [details, setDetails] = useState<CheckoutDetails | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [paying, setPaying] = useState(false);
  const [paymentSuccess, setPaymentSuccess] = useState<{
    paymentId: string;
    paidAt: string;
    amount: number;
  } | null>(null);

  const [selectedMethod, setSelectedMethod] = useState<"upi" | "card" | "netbanking">("upi");

  useEffect(() => {
    setLoading(true);
    fetchCheckoutDetails(linkId)
      .then((data) => {
        setDetails(data);
        if (data.status === "PAID") {
          setPaymentSuccess({
            paymentId: "pay_captured_prior",
            paidAt: new Date().toISOString(),
            amount: data.amount,
          });
        }
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, [linkId]);

  const handlePay = async () => {
    setPaying(true);
    setError("");
    try {
      const res = await simulateCheckoutPayment(linkId);
      setPaymentSuccess({
        paymentId: res.paymentId,
        paidAt: res.paidAt,
        amount: res.amount,
      });
    } catch (e: any) {
      setError(e.message || "Failed to process payment");
    } finally {
      setPaying(false);
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-[#02042B] flex flex-col items-center justify-center p-4">
        <div className="w-12 h-12 rounded-2xl bg-[#3395FF]/20 border border-[#3395FF]/40 flex items-center justify-center animate-pulse mb-4">
          <RazorpayMark className="w-6 h-6 text-[#3395FF]" />
        </div>
        <p className="text-sm font-semibold text-slate-400">Loading Secure Razorpay Checkout...</p>
      </div>
    );
  }

  if (error && !details) {
    return (
      <div className="min-h-screen bg-[#02042B] flex flex-col items-center justify-center p-4 text-center">
        <div className="max-w-md w-full glass-card p-8 rounded-2xl border-rose-500/30 text-white space-y-4">
          <AlertTriangle className="w-12 h-12 text-rose-400 mx-auto" />
          <h2 className="text-lg font-bold">Invalid or Expired Payment Link</h2>
          <p className="text-xs text-slate-400">{error}</p>
          {onBackToDashboard && (
            <Button onClick={onBackToDashboard} variant="outline" className="border-slate-700 text-xs">
              Return to Dashboard
            </Button>
          )}
        </div>
      </div>
    );
  }

  const safeDetails: CheckoutDetails = details || {
    linkId,
    amount: 49900,
    currency: "INR",
    customerName: "Valued Customer",
    customerEmail: "customer@example.com",
    merchantName: "RecoverMandate Merchant",
    planName: "Pro SaaS Subscription Plan",
    status: "CREATED",
    failureCategory: "insufficient_funds",
  };

  const isLiveLink = Boolean(
    details?.shortUrl &&
    details.shortUrl.includes("rzp.io") &&
    !linkId.startsWith("plink_sim_") &&
    !linkId.startsWith("plink_preview_")
  );
  const isDemoCheckout = !isLiveLink;

  return (
    <div className="min-h-screen bg-[#02042B] text-slate-100 flex flex-col items-center justify-center p-4 sm:p-6 font-sans">
      {/* Top Bar / Navigation */}
      <div className="w-full max-w-xl flex items-center justify-between mb-4">
        {onBackToDashboard && (
          <button
            onClick={onBackToDashboard}
            className="flex items-center gap-1.5 text-xs text-slate-400 hover:text-white transition-colors"
          >
            <ChevronLeft className="w-4 h-4" />
            <span>Back to Merchant Portal</span>
          </button>
        )}
        <div className="flex items-center gap-1.5 text-[11px] text-emerald-400 font-mono ml-auto">
          <Lock className="w-3.5 h-3.5 text-emerald-400" />
          <span>256-Bit SSL Encrypted</span>
        </div>
      </div>

      <motion.div
        initial={{ opacity: 0, y: 15 }}
        animate={{ opacity: 1, y: 0 }}
        className="w-full max-w-xl rounded-2xl bg-[#08182D] border border-[#3395FF]/30 shadow-2xl overflow-hidden"
      >
        {/* Demo Sandbox Top Banner */}
        {isDemoCheckout && (
          <div className="bg-gradient-to-r from-amber-500/20 via-indigo-500/15 to-blue-500/20 border-b border-amber-500/30 px-4 py-2.5 flex items-center justify-between gap-2 text-xs">
            <div className="flex items-center gap-2 font-mono text-[11px]">
              <span className="w-2 h-2 rounded-full bg-amber-400 animate-pulse shrink-0" />
              <span className="font-bold text-amber-300">🧪 Demo Sandbox Checkout</span>
              <span className="text-slate-400 hidden sm:inline">•</span>
              <span className="text-slate-300 hidden sm:inline">No Real Payment Will Be Processed</span>
            </div>
            <span className="text-[9px] font-mono uppercase tracking-wider px-2 py-0.5 rounded-full bg-amber-500/20 text-amber-300 border border-amber-500/30 font-bold shrink-0">
              Simulated Sandbox
            </span>
          </div>
        )}

        {/* Checkout Header */}
        <div className="p-6 bg-[#0C2340] border-b border-[#3395FF]/20 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div className="flex items-center gap-3.5">
            <div className="w-12 h-12 rounded-xl bg-[#02042B] border border-[#3395FF]/40 flex items-center justify-center p-2.5 shrink-0 shadow-inner">
              <RazorpayMark className="w-7 h-7 text-[#3395FF]" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-base font-extrabold text-white tracking-wide">
                  {safeDetails.merchantName}
                </h1>
                {isDemoCheckout ? (
                  <Badge className="bg-amber-500/20 text-amber-300 border-amber-500/30 text-[9px] font-bold">
                    🧪 Demo Sandbox
                  </Badge>
                ) : (
                  <Badge className="bg-emerald-500/20 text-emerald-300 border-emerald-500/30 text-[9px] font-bold">
                    ✓ Verified
                  </Badge>
                )}
              </div>
              <p className="text-xs text-slate-300 font-medium mt-0.5">
                {safeDetails.planName}
              </p>
            </div>
          </div>

          <div className="text-left sm:text-right">
            <span className="text-[10px] uppercase font-bold text-slate-400 tracking-wider">Amount Due</span>
            <div className="text-2xl font-extrabold text-white font-mono">
              {formatINR(safeDetails.amount)}
            </div>
          </div>
        </div>

        {/* Payment Confirmation State */}
        <AnimatePresence mode="wait">
          {paymentSuccess ? (
            <motion.div
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              className="p-8 text-center space-y-6 bg-gradient-to-b from-[#0C2340]/60 to-[#08182D]"
            >
              <div className="w-16 h-16 rounded-full bg-emerald-500/20 text-emerald-400 border border-emerald-500/40 flex items-center justify-center mx-auto shadow-lg shadow-emerald-500/20">
                <CheckCircle2 className="w-10 h-10" />
              </div>

              <div className="space-y-1.5">
                <h2 className="text-xl font-bold text-white">Payment Completed Successfully!</h2>
                <p className="text-xs text-slate-300 max-w-md mx-auto">
                  Your recurring mandate has been restored and your subscription remains active with zero interruption.
                </p>
              </div>

              <div className="p-4 rounded-xl bg-[#02042B] border border-slate-800 text-xs font-mono text-left space-y-2 max-w-md mx-auto">
                <div className="flex justify-between text-slate-400 pb-2 border-b border-slate-800">
                  <span>Payment ID:</span>
                  <span className="text-emerald-400 font-bold">{paymentSuccess.paymentId}</span>
                </div>
                <div className="flex justify-between text-slate-400 pb-2 border-b border-slate-800">
                  <span>Amount Settled:</span>
                  <span className="text-white font-bold">{formatINR(paymentSuccess.amount)}</span>
                </div>
                <div className="flex justify-between text-slate-400">
                  <span>Customer:</span>
                  <span className="text-slate-200 font-bold">
                    {safeDetails.customerName ? `${safeDetails.customerName} (${safeDetails.customerEmail})` : safeDetails.customerEmail}
                  </span>
                </div>
              </div>

              {onBackToDashboard && (
                <Button
                  onClick={onBackToDashboard}
                  className="bg-[#3395FF] hover:bg-[#2582eb] text-white font-bold text-xs px-6 shadow-lg shadow-[#3395FF]/20"
                >
                  Return to Dashboard
                </Button>
              )}
            </motion.div>
          ) : (
            <div className="p-6 space-y-6">
              {/* AI Root Cause Explanation Callout */}
              <div className="p-3.5 rounded-xl bg-[#0C2340]/90 border border-[#3395FF]/30 space-y-1.5 text-xs shadow-inner">
                <div className="flex items-center gap-1.5 text-amber-300 font-bold">
                  <Zap className="w-3.5 h-3.5 text-amber-400" />
                  <span>Why Did My Automatic Payment Fail?</span>
                </div>
                <p className="text-slate-300 leading-relaxed">
                  {safeDetails.aiExplanation ||
                    "Your bank encountered a temporary authorization window delay. Complete this 1-click settlement to keep your active subscription uninterrupted."}
                </p>
              </div>

              {/* Payment Method Selector */}
              <div className="space-y-2">
                <label className="text-xs font-bold uppercase tracking-wider text-slate-300">
                  Select Recovery Payment Method
                </label>
                <div className="grid grid-cols-3 gap-2.5">
                  <button
                    type="button"
                    onClick={() => setSelectedMethod("upi")}
                    className={`p-3 rounded-xl border flex flex-col items-center justify-center gap-1.5 transition-all text-xs font-bold ${
                      selectedMethod === "upi"
                        ? "bg-[#3395FF]/20 border-[#3395FF] text-white shadow-md shadow-[#3395FF]/10"
                        : "bg-[#0C2340]/40 border-slate-700/60 text-slate-400 hover:text-white"
                    }`}
                  >
                    <Smartphone className="w-4 h-4 text-[#3395FF]" />
                    <span>Instant UPI</span>
                  </button>

                  <button
                    type="button"
                    onClick={() => setSelectedMethod("card")}
                    className={`p-3 rounded-xl border flex flex-col items-center justify-center gap-1.5 transition-all text-xs font-bold ${
                      selectedMethod === "card"
                        ? "bg-[#3395FF]/20 border-[#3395FF] text-white shadow-md shadow-[#3395FF]/10"
                        : "bg-[#0C2340]/40 border-slate-700/60 text-slate-400 hover:text-white"
                    }`}
                  >
                    <CreditCard className="w-4 h-4 text-slate-300" />
                    <span>Card / Mandate</span>
                  </button>

                  <button
                    type="button"
                    onClick={() => setSelectedMethod("netbanking")}
                    className={`p-3 rounded-xl border flex flex-col items-center justify-center gap-1.5 transition-all text-xs font-bold ${
                      selectedMethod === "netbanking"
                        ? "bg-[#3395FF]/20 border-[#3395FF] text-white shadow-md shadow-[#3395FF]/10"
                        : "bg-[#0C2340]/40 border-slate-700/60 text-slate-400 hover:text-white"
                    }`}
                  >
                    <Building2 className="w-4 h-4 text-slate-300" />
                    <span>Netbanking</span>
                  </button>
                </div>
              </div>

              {/* UPI Quick-Pay Details Card */}
              {selectedMethod === "upi" && (
                <div className="p-4 rounded-xl bg-[#02042B] border border-slate-700/80 flex items-center justify-between gap-4">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 rounded-lg bg-white/10 flex items-center justify-center shrink-0">
                      <QrCode className="w-5 h-5 text-emerald-400" />
                    </div>
                    <div>
                      <div className="text-xs font-bold text-white">Supported UPI Apps</div>
                      <div className="text-[11px] text-slate-400 font-mono">
                        GPay · PhonePe · Paytm · CRED · BHIM
                      </div>
                    </div>
                  </div>
                  <span className="text-[10px] px-2 py-0.5 rounded bg-emerald-500/20 text-emerald-300 font-mono font-bold">
                    Zero Surcharge
                  </span>
                </div>
              )}

              {/* Action Button */}
              <div className="space-y-3 pt-2">
                <Button
                  onClick={handlePay}
                  disabled={paying}
                  className="w-full h-12 bg-gradient-to-r from-[#3395FF] to-blue-600 hover:from-[#2582eb] hover:to-blue-500 text-white font-extrabold text-sm gap-2 shadow-xl shadow-[#3395FF]/30 rounded-xl cursor-pointer"
                >
                  <Zap className={`w-4 h-4 text-amber-300 ${paying ? "animate-spin" : ""}`} />
                  {paying
                    ? "Authorizing Payment Gateway..."
                    : isDemoCheckout
                    ? `🧪 Simulate Demo Payment (${formatINR(safeDetails.amount)})`
                    : `Pay Overdue ${formatINR(safeDetails.amount)} via Razorpay Secure Checkout`}
                </Button>

                <div className="flex items-center justify-center gap-2 text-[11px] text-slate-400">
                  <ShieldCheck className="w-3.5 h-3.5 text-[#3395FF]" />
                  <span>
                    {isDemoCheckout
                      ? "RecoverMandate Sandbox Mode • Safe Simulated Settlement"
                      : "Powered by Razorpay Payment Gateway & Auto-Recovery Engine"}
                  </span>
                </div>
              </div>
            </div>
          )}
        </AnimatePresence>
      </motion.div>
    </div>
  );
}
