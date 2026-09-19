import type { QueueStatus } from "@/convex/queueStatus";
import { STATUS_META, TONE_CLASSES } from "@/lib/status-meta";
import { cn } from "@/lib/utils";

export function StatusBadge({
  status,
  className,
  withHint = true,
}: {
  status: QueueStatus;
  className?: string;
  withHint?: boolean;
}) {
  const meta = STATUS_META[status];
  return (
    <span
      title={withHint ? meta.hint : undefined}
      className={cn(
        "inline-flex items-center rounded-full px-2.5 py-1 text-[11px] font-medium tracking-wide whitespace-nowrap",
        TONE_CLASSES[meta.tone],
        className,
      )}
    >
      {meta.label}
    </span>
  );
}

export function StatusDot({ status }: { status: QueueStatus }) {
  const tone = STATUS_META[status].tone;
  const color =
    tone === "success"
      ? "bg-emerald-500"
      : tone === "danger"
        ? "bg-rose-500"
        : tone === "warning"
          ? "bg-amber-500"
          : tone === "progress"
            ? "bg-indigo-500"
            : tone === "info"
              ? "bg-sky-500"
              : "bg-muted-foreground/40";
  return <span className={cn("size-2 rounded-full", color)} aria-hidden />;
}
