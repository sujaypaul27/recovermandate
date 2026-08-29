import { useState, useEffect } from "react";
import { motion, AnimatePresence } from "framer-motion";
import {
  Settings,
  X,
  Zap,
  ShieldCheck,
  CheckCircle,
  Building,
  Save,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useToast } from "@/hooks/use-toast";
import {
  fetchMerchantSettings,
  updateMerchantSettings,
  type MerchantSettings,
} from "../lib/api";

interface SettingsModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export function SettingsModal({ isOpen, onClose }: SettingsModalProps) {
  const [settings, setSettings] = useState<MerchantSettings>({
    defaultTone: "balanced",
    autoPilotEnabled: false,
    autoPilotMaxAmount: 250000,
    autoPilotAllowedCategories: "insufficient_funds,technical_decline",
    businessDisplayName: "RecoverMandate Merchant",
  });
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const { toast } = useToast();

  useEffect(() => {
    if (isOpen) {
      setLoading(true);
      fetchMerchantSettings()
        .then((data) => {
          setSettings(data);
        })
        .catch((e) => {
          console.error("Failed to load settings:", e);
        })
        .finally(() => setLoading(false));
    }
  }, [isOpen]);

  if (!isOpen) return null;

  const categories = [
    { id: "insufficient_funds", label: "Insufficient Funds (Soft Decline)" },
    { id: "technical_decline", label: "Bank / Technical Decline (Auto-Retry)" },
    { id: "expired_mandate", label: "Expired Mandate" },
    { id: "unknown", label: "Unknown / Other" },
  ];

  const allowedCategoriesArray = settings.autoPilotAllowedCategories
    .split(",")
    .map((c) => c.trim().toLowerCase());

  const toggleCategory = (catId: string) => {
    let updated: string[];
    if (allowedCategoriesArray.includes(catId)) {
      updated = allowedCategoriesArray.filter((c) => c !== catId);
    } else {
      updated = [...allowedCategoriesArray, catId];
    }
    setSettings({ ...settings, autoPilotAllowedCategories: updated.join(",") });
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      const saved = await updateMerchantSettings(settings);
      setSettings(saved);
      toast({
        title: "Settings Saved",
        description: "Merchant Auto-Pilot & Dunning policies updated successfully.",
      });
      onClose();
    } catch (e: any) {
      toast({
        title: "Save Failed",
        description: e.message || "Could not update settings",
        variant: "destructive",
      });
    } finally {
      setSaving(false);
    }
  };

  return (
    <AnimatePresence>
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-6">
        {/* Backdrop */}
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          onClick={onClose}
          className="fixed inset-0 bg-slate-950/80 backdrop-blur-md"
        />

        {/* Modal Card */}
        <motion.div
          initial={{ opacity: 0, scale: 0.95, y: 15 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          exit={{ opacity: 0, scale: 0.95, y: 15 }}
          transition={{ type: "spring", stiffness: 350, damping: 30 }}
          className="relative w-full max-w-xl rounded-2xl bg-[#0C2340]/95 border border-[#3395FF]/40 shadow-2xl overflow-hidden backdrop-blur-xl z-10 text-white"
        >
          {/* Header */}
          <div className="flex items-center justify-between px-6 py-4 border-b border-[#3395FF]/20 bg-[#02042B]/90">
            <div className="flex items-center gap-2.5">
              <div className="w-8 h-8 rounded-lg bg-[#02042B] border border-[#3395FF]/40 flex items-center justify-center">
                <Settings className="w-4 h-4 text-[#3395FF]" />
              </div>
              <div>
                <h3 className="text-sm font-bold text-white flex items-center gap-2">
                  Merchant Dunning & Auto-Pilot Policy
                </h3>
                <p className="text-[11px] text-[#93c5fd]">
                  Configure automated recovery rules, tone defaults, and spend limits
                </p>
              </div>
            </div>
            <button
              onClick={onClose}
              className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800/60 transition-colors"
            >
              <X className="w-4 h-4" />
            </button>
          </div>

          {/* Form Body */}
          <div className="p-6 space-y-5 max-h-[75vh] overflow-y-auto">
            {/* Auto-Pilot Toggle Card */}
            <div className="p-4 rounded-xl bg-[#02042B]/90 border border-[#3395FF]/30 flex items-center justify-between gap-4">
              <div className="space-y-0.5">
                <div className="flex items-center gap-2">
                  <Zap className="w-4 h-4 text-amber-400" />
                  <span className="text-xs font-bold text-white">
                    Auto-Pilot Automated Dunning
                  </span>
                  <span className="text-[9px] font-mono px-1.5 py-0.2 rounded bg-amber-500/20 text-amber-300 border border-amber-500/30 font-bold">
                    AUTO-DISPATCH
                  </span>
                </div>
                <p className="text-[11px] text-slate-300">
                  Automatically approve and dispatch validated AI drafts below your threshold limit.
                </p>
              </div>

              <button
                type="button"
                onClick={() => setSettings({ ...settings, autoPilotEnabled: !settings.autoPilotEnabled })}
                className={`relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none ${
                  settings.autoPilotEnabled ? "bg-[#3395FF]" : "bg-slate-700"
                }`}
              >
                <span
                  className={`pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow-lg ring-0 transition duration-200 ease-in-out ${
                    settings.autoPilotEnabled ? "translate-x-5" : "translate-x-0"
                  }`}
                />
              </button>
            </div>

            {/* Threshold & Tone Settings */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              {/* Max Auto-Pilot Amount */}
              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-300 flex items-center justify-between">
                  <span>Auto-Pilot Max Limit (₹)</span>
                  <span className="text-[10px] font-mono text-slate-400">per mandate</span>
                </label>
                <div className="relative">
                  <span className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 text-xs font-bold">₹</span>
                  <Input
                    type="number"
                    value={Math.round(settings.autoPilotMaxAmount / 100)}
                    onChange={(e) =>
                      setSettings({
                        ...settings,
                        autoPilotMaxAmount: Math.max(0, parseInt(e.target.value || "0") * 100),
                      })
                    }
                    className="pl-7 text-xs bg-[#02042B] border-[#3395FF]/30 text-white font-mono"
                    placeholder="2500"
                  />
                </div>
                <p className="text-[10px] text-slate-400">
                  Failures exceeding this amount require manual review.
                </p>
              </div>

              {/* Default AI Tone */}
              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-300">Default Recovery Tone</label>
                <div className="grid grid-cols-3 gap-1 bg-[#02042B] p-1 rounded-lg border border-[#3395FF]/30 text-xs font-bold">
                  {(["gentle", "balanced", "urgent"] as const).map((t) => (
                    <button
                      key={t}
                      type="button"
                      onClick={() => setSettings({ ...settings, defaultTone: t })}
                      className={`py-1.5 rounded-md capitalize transition-all ${
                        settings.defaultTone === t
                          ? "bg-[#3395FF] text-white shadow-sm font-extrabold"
                          : "text-slate-400 hover:text-white"
                      }`}
                    >
                      {t}
                    </button>
                  ))}
                </div>
                <p className="text-[10px] text-slate-400">
                  Default tone applied to auto-dispatched recovery links.
                </p>
              </div>
            </div>

            {/* Allowed Failure Categories */}
            <div className="space-y-2">
              <label className="text-xs font-semibold text-slate-300 block">
                Auto-Pilot Eligible Categories
              </label>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                {categories.map((cat) => {
                  const isChecked = allowedCategoriesArray.includes(cat.id);
                  return (
                    <button
                      key={cat.id}
                      type="button"
                      onClick={() => toggleCategory(cat.id)}
                      className={`p-2.5 rounded-xl border text-left text-xs transition-all flex items-center justify-between ${
                        isChecked
                          ? "bg-[#0C2340] border-[#3395FF] text-white shadow-sm"
                          : "bg-[#02042B]/70 border-slate-800 text-slate-400 hover:border-slate-700"
                      }`}
                    >
                      <span className="font-medium text-[11px] truncate">{cat.label}</span>
                      <span
                        className={`w-4 h-4 rounded flex items-center justify-center border ${
                          isChecked ? "bg-[#3395FF] border-[#3395FF] text-white" : "border-slate-700"
                        }`}
                      >
                        {isChecked && <CheckCircle className="w-3 h-3" />}
                      </span>
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Business Display Name */}
            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-slate-300 flex items-center gap-1.5">
                <Building className="w-3.5 h-3.5 text-[#3395FF]" /> Business Display Name
              </label>
              <Input
                type="text"
                value={settings.businessDisplayName}
                onChange={(e) => setSettings({ ...settings, businessDisplayName: e.target.value })}
                className="text-xs bg-[#02042B] border-[#3395FF]/30 text-white"
                placeholder="e.g. Acme Cloud Subscriptions"
              />
              <p className="text-[10px] text-slate-400">
                Shown to customers in Razorpay hosted recovery links and email notifications.
              </p>
            </div>
          </div>

          {/* Footer Actions */}
          <div className="px-6 py-4 bg-[#02042B]/90 border-t border-[#3395FF]/20 flex items-center justify-between">
            <div className="flex items-center gap-2 text-[11px] text-slate-400">
              <ShieldCheck className="w-3.5 h-3.5 text-emerald-400" />
              <span>Guardrails active (PII redacted)</span>
            </div>
            <div className="flex items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={onClose}
                className="border-slate-700 hover:bg-slate-800 text-slate-300 text-xs"
              >
                Cancel
              </Button>
              <Button
                size="sm"
                onClick={handleSave}
                disabled={saving || loading}
                className="bg-[#3395FF] hover:bg-blue-600 text-white text-xs font-bold gap-1.5 shadow-md shadow-blue-500/20"
              >
                <Save className="w-3.5 h-3.5" />
                {saving ? "Saving..." : "Save Policy"}
              </Button>
            </div>
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  );
}
