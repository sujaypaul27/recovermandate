import { motion } from "framer-motion";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { ShieldCheck, Sparkles, Inbox, SearchX } from "lucide-react";
import React from "react";

interface EmptyStateProps {
  icon?: React.ReactNode;
  variant?: "clean" | "search" | "shield" | "sparkles";
  title: string;
  description: string;
  badgeText?: string;
  action?: {
    label: string;
    onClick: () => void;
    icon?: React.ReactNode;
  };
}

export function EmptyState({
  icon,
  variant = "clean",
  title,
  description,
  badgeText,
  action,
}: EmptyStateProps) {
  const getDefaultIcon = () => {
    switch (variant) {
      case "shield":
        return <ShieldCheck className="w-8 h-8 text-emerald-500" />;
      case "search":
        return <SearchX className="w-8 h-8 text-slate-400" />;
      case "sparkles":
        return <Sparkles className="w-8 h-8 text-purple-500" />;
      default:
        return <Inbox className="w-8 h-8 text-blue-500" />;
    }
  };

  const getGradient = () => {
    switch (variant) {
      case "shield":
        return "from-emerald-500/20 via-teal-500/10 to-transparent border-emerald-500/30";
      case "sparkles":
        return "from-purple-500/20 via-indigo-500/10 to-transparent border-purple-500/30";
      case "search":
        return "from-slate-500/20 via-slate-500/10 to-transparent border-slate-500/30";
      default:
        return "from-blue-500/20 via-cyan-500/10 to-transparent border-blue-500/30";
    }
  };

  return (
    <motion.div
      initial={{ opacity: 0, scale: 0.95 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ duration: 0.3 }}
      className="py-16 px-6 flex flex-col items-center justify-center text-center space-y-4 max-w-lg mx-auto"
    >
      <div className="relative">
        <div
          className={`w-20 h-20 rounded-3xl bg-gradient-to-br ${getGradient()} border flex items-center justify-center shadow-lg shadow-black/5 relative z-10`}
        >
          {icon || getDefaultIcon()}
        </div>
        <div className="absolute -inset-2 bg-gradient-to-r from-blue-500/10 to-purple-500/10 rounded-full blur-xl -z-0" />
      </div>

      {badgeText && (
        <Badge
          variant="outline"
          className="text-[10px] font-bold uppercase tracking-wider bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 border-slate-200 dark:border-slate-700"
        >
          {badgeText}
        </Badge>
      )}

      <div className="space-y-1.5">
        <h3 className="font-bold text-lg text-slate-900 dark:text-white tracking-tight">
          {title}
        </h3>
        <p className="text-xs text-slate-500 dark:text-slate-400 leading-relaxed">
          {description}
        </p>
      </div>

      {action && (
        <Button
          onClick={action.onClick}
          variant="outline"
          size="sm"
          className="mt-2 text-xs font-semibold gap-1.5 rounded-xl border-slate-300 dark:border-slate-700 hover:bg-slate-100 dark:hover:bg-slate-800"
        >
          {action.icon}
          {action.label}
        </Button>
      )}
    </motion.div>
  );
}
