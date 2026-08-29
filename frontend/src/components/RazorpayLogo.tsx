export function RazorpayMark({ className = "w-5 h-5" }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 100 115"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={className}
    >
      <path
        d="M38.5 0L0 70.5H35L22 115L85 44.5H48L65 0H38.5Z"
        fill="url(#rzp-bolt-gradient)"
      />
      <defs>
        <linearGradient
          id="rzp-bolt-gradient"
          x1="10"
          y1="0"
          x2="75"
          y2="115"
          gradientUnits="userSpaceOnUse"
        >
          <stop stopColor="#52A8FF" />
          <stop offset="0.5" stopColor="#3395FF" />
          <stop offset="1" stopColor="#0B72E7" />
        </linearGradient>
      </defs>
    </svg>
  );
}

export function RazorpayBadge({
  text = "Razorpay Hosted",
  subtext = "Trusted Checkout",
  className = "",
}: {
  text?: string;
  subtext?: string;
  className?: string;
}) {
  return (
    <div
      className={`inline-flex items-center gap-2 px-2.5 py-1 rounded-lg bg-[#0C2340]/90 border border-[#3395FF]/30 text-white shadow-sm ${className}`}
    >
      <div className="w-5 h-5 rounded-md bg-[#02042B] flex items-center justify-center p-0.5 border border-[#3395FF]/40">
        <RazorpayMark className="w-3.5 h-3.5" />
      </div>
      <div className="flex flex-col text-left leading-tight">
        <span className="text-[11px] font-bold tracking-wide text-white flex items-center gap-1">
          {text}
          <span className="w-1.5 h-1.5 rounded-full bg-[#3395FF] animate-pulse" />
        </span>
        {subtext && (
          <span className="text-[9px] text-[#93c5fd] font-medium tracking-tight">
            {subtext}
          </span>
        )}
      </div>
    </div>
  );
}
