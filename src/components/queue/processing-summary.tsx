import type { QueueStatus } from "@/convex/queueStatus";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { formatSatang } from "@/lib/format";
import { STATUS_META, type StatusGroup } from "@/lib/status-meta";
import type { SummaryView } from "@/lib/summary";
import { cn } from "@/lib/utils";
import { ClipboardList } from "lucide-react";

const GROUP_BAR: Record<StatusGroup, string> = {
  review: "bg-amber-400",
  ready: "bg-indigo-500",
  handoff: "bg-sky-500",
  settled: "bg-emerald-500",
  problem: "bg-rose-500",
};

const GROUP_ORDER: StatusGroup[] = [
  "review",
  "ready",
  "handoff",
  "settled",
  "problem",
];

export function ProcessingSummary({ summary }: { summary: SummaryView }) {
  const total = Math.max(summary.total, 1);
  const statusBreakdown = (Object.entries(summary.byStatus) as [QueueStatus, number][])
    .filter(([, count]) => count > 0)
    .sort((a, b) => b[1] - a[1]);
  const money = [
    {
      label: "Ready to pay",
      value: formatSatang(summary.amountReadySatang),
      hint: "Amount encoded in QR codes waiting for payment.",
    },
    {
      label: "Needs review",
      value: formatSatang(summary.amountNeedsReviewSatang),
      hint: "Amounts on items with warnings — check before paying.",
    },
    {
      label: "Recorded as settled",
      value: formatSatang(summary.amountSettledSatang),
      hint: "Only counts the items you confirmed yourself.",
    },
  ];

  return (
    <Card className="border-border/70 shadow-[0_1px_2px_rgba(16,24,40,0.04),0_8px_24px_-16px_rgba(16,24,40,0.18)]">
      <CardHeader className="gap-1.5">
        <CardTitle className="flex items-center gap-2 text-base">
          <ClipboardList className="size-4 text-primary" />
          Processing summary
        </CardTitle>
        <p className="text-sm text-muted-foreground">
          Where every discovered image currently stands.
        </p>
      </CardHeader>
      <CardContent className="space-y-5">
        <div className="space-y-2">
          <div className="flex h-2.5 w-full overflow-hidden rounded-full bg-muted">
            {GROUP_ORDER.map((group) => {
              const count = summary.counts[group] ?? 0;
              if (count === 0) return null;
              return (
                <span
                  key={group}
                  className={cn("h-full", GROUP_BAR[group])}
                  style={{ width: `${(count / total) * 100}%` }}
                  title={`${count} ${group}`}
                />
              );
            })}
          </div>
          <div className="flex flex-wrap gap-x-4 gap-y-1 text-[11px] text-muted-foreground">
            {GROUP_ORDER.map((group) => (
              <span key={group} className="inline-flex items-center gap-1.5">
                <span className={cn("size-2 rounded-full", GROUP_BAR[group])} />
                {group === "review"
                  ? "Needs review"
                  : group === "ready"
                    ? "Ready"
                    : group === "handoff"
                      ? "Handed off"
                      : group === "settled"
                        ? "Settled"
                        : "Problems"}{" "}
                {summary.counts[group] ?? 0}
              </span>
            ))}
          </div>
        </div>

        <div className="grid gap-3 sm:grid-cols-3">
          {money.map((entry) => (
            <div
              key={entry.label}
              className="rounded-xl border border-border/70 bg-muted/20 p-3.5"
            >
              <p className="text-[11px] tracking-wide text-muted-foreground uppercase">
                {entry.label}
              </p>
              <p className="mt-1 text-lg font-semibold tabular-nums">
                {entry.value}
              </p>
              <p className="mt-1 text-[11px] leading-4 text-muted-foreground">
                {entry.hint}
              </p>
            </div>
          ))}
        </div>

        <div className="space-y-2">
          <p className="text-xs font-medium text-muted-foreground">
            Status breakdown ({summary.total} items)
          </p>
          {statusBreakdown.length === 0 ? (
            <p className="text-xs text-muted-foreground">
              Nothing imported yet — the breakdown appears after your first import.
            </p>
          ) : (
            <ul className="grid gap-1.5 sm:grid-cols-2">
              {statusBreakdown.map(([status, count]) => (
                <li
                  key={status}
                  className="flex items-center justify-between gap-3 rounded-lg border border-border/60 px-3 py-1.5 text-xs"
                >
                  <span className="inline-flex items-center gap-2">
                    <span
                      className={cn(
                        "size-2 rounded-full",
                        GROUP_BAR[STATUS_META[status].group],
                      )}
                    />
                    {STATUS_META[status].label}
                  </span>
                  <span className="tabular-nums text-muted-foreground">
                    {count}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>

        <p className="rounded-xl border border-border/70 bg-background p-3.5 text-[11px] leading-4 text-muted-foreground">
          A QR is never counted as paid because it was decoded, shared or opened.
          Items only appear as settled after you record the result you saw in your
          bank app.
        </p>
      </CardContent>
    </Card>
  );
}
