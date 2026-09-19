import type { QueueStatus } from "@/convex/queueStatus";
import { STATUS_META, type StatusGroup } from "@/lib/status-meta";

export type SummarySource = {
  total: number;
  truncated: boolean;
  byStatus: Partial<Record<QueueStatus, number>>;
  amountReadySatang: number;
  amountSettledSatang: number;
  amountNeedsReviewSatang: number;
  batches: {
    _id: string;
    name: string;
    source: "files" | "folder";
    createdAt: number;
    itemCount: number;
  }[];
};

export type SummaryView = {
  total: number;
  truncated: boolean;
  counts: Partial<Record<StatusGroup, number>>;
  byStatus: Partial<Record<QueueStatus, number>>;
  duplicateCount: number;
  amountReadySatang: number;
  amountSettledSatang: number;
  amountNeedsReviewSatang: number;
  batches: SummarySource["batches"];
};

const EMPTY_COUNTS: Partial<Record<StatusGroup, number>> = {
  review: 0,
  ready: 0,
  handoff: 0,
  settled: 0,
  problem: 0,
};

export function buildSummaryView(source: SummarySource | undefined): SummaryView {
  const base: SummaryView = {
    total: 0,
    truncated: false,
    counts: { ...EMPTY_COUNTS },
    byStatus: {},
    duplicateCount: 0,
    amountReadySatang: 0,
    amountSettledSatang: 0,
    amountNeedsReviewSatang: 0,
    batches: [],
  };
  if (!source) return base;

  const counts = { ...EMPTY_COUNTS };
  let duplicateCount = 0;

  for (const [status, count] of Object.entries(source.byStatus) as [
    QueueStatus,
    number,
  ][]) {
    const group = STATUS_META[status]?.group;
    if (group) counts[group] = (counts[group] ?? 0) + count;
    if (status === "DUPLICATE") duplicateCount += count;
  }

  return {
    total: source.total,
    truncated: source.truncated,
    counts,
    byStatus: source.byStatus,
    duplicateCount,
    amountReadySatang: source.amountReadySatang,
    amountSettledSatang: source.amountSettledSatang,
    amountNeedsReviewSatang: source.amountNeedsReviewSatang,
    batches: source.batches,
  };
}
