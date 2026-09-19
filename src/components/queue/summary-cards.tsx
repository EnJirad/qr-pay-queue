import { Card, CardContent } from "@/components/ui/card";
import { formatSatang } from "@/lib/format";
import type { SummaryView } from "@/lib/summary";
import { cn } from "@/lib/utils";
import {
  BadgeCheck,
  CircleAlert,
  Layers,
  Send,
  type LucideIcon,
} from "lucide-react";

function StatCard({
  icon: Icon,
  label,
  value,
  detail,
  accent,
}: {
  icon: LucideIcon;
  label: string;
  value: string;
  detail: string;
  accent: string;
}) {
  return (
    <Card className="border-border/70 bg-card shadow-[0_1px_2px_rgba(16,24,40,0.04),0_8px_24px_-16px_rgba(16,24,40,0.18)]">
      <CardContent className="flex flex-col gap-3 p-5">
        <div className="flex items-center gap-2">
          <span
            className={cn(
              "flex size-8 items-center justify-center rounded-lg",
              accent,
            )}
          >
            <Icon className="size-4" />
          </span>
          <span className="text-xs font-medium tracking-wide text-muted-foreground uppercase">
            {label}
          </span>
        </div>
        <div className="flex items-baseline gap-2">
          <span className="text-2xl font-semibold tracking-tight tabular-nums">
            {value}
          </span>
          <span className="text-xs text-muted-foreground">{detail}</span>
        </div>
      </CardContent>
    </Card>
  );
}

export function SummaryCards({ summary }: { summary: SummaryView }) {
  const ready = summary.counts.ready ?? 0;
  const review = summary.counts.review ?? 0;
  const handoff = summary.counts.handoff ?? 0;
  const settled = summary.counts.settled ?? 0;

  return (
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <StatCard
        icon={Layers}
        label="In queue"
        value={String(summary.total)}
        detail={
          summary.batches.length > 0
            ? `across ${summary.batches.length} import${summary.batches.length === 1 ? "" : "s"}`
            : "no imports yet"
        }
        accent="bg-primary/10 text-primary"
      />
      <StatCard
        icon={BadgeCheck}
        label="Ready to pay"
        value={String(ready)}
        detail={formatSatang(summary.amountReadySatang)}
        accent="bg-indigo-50 text-indigo-600"
      />
      <StatCard
        icon={CircleAlert}
        label="Needs review"
        value={String(review)}
        detail={formatSatang(summary.amountNeedsReviewSatang)}
        accent="bg-amber-50 text-amber-600"
      />
      <StatCard
        icon={Send}
        label="Handed off / settled"
        value={`${handoff} / ${settled}`}
        detail={formatSatang(summary.amountSettledSatang)}
        accent="bg-emerald-50 text-emerald-600"
      />
    </div>
  );
}
