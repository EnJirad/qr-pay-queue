import { StatusBadge, StatusDot } from "@/components/queue/status-badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import type { Id } from "@/convex/_generated/dataModel";
import { formatRelative, formatSatang, truncateMiddle } from "@/lib/format";
import type { QueueListItem } from "@/lib/queue-types";
import { STATUS_GROUPS } from "@/lib/status-meta";
import type { SummaryView } from "@/lib/summary";
import { cn } from "@/lib/utils";
import { Loader2, Search, Send, SlidersHorizontal } from "lucide-react";
import type { ChangeEvent } from "react";

export type QueueFilter = "all" | "duplicates" | StatusGroupKey;
type StatusGroupKey = (typeof STATUS_GROUPS)[number]["key"];

const FILTERS: { key: QueueFilter; label: string; count: (s: SummaryView) => number }[] = [
  { key: "all", label: "All", count: (s) => s.total },
  ...STATUS_GROUPS.map((group) => ({
    key: group.key as QueueFilter,
    label: group.label,
    count: (s: SummaryView) => s.counts[group.key] ?? 0,
  })),
  {
    key: "duplicates" as QueueFilter,
    label: "Duplicates",
    count: (s) => s.duplicateCount,
  },
];

export function QueueList({
  items,
  isLoading,
  summary,
  filter,
  onFilterChange,
  search,
  onSearchChange,
  batchId,
  onBatchChange,
  onSelect,
  onHandoff,
  limit,
}: {
  items: QueueListItem[];
  isLoading: boolean;
  summary: SummaryView;
  filter: QueueFilter;
  onFilterChange: (filter: QueueFilter) => void;
  search: string;
  onSearchChange: (value: string) => void;
  batchId: Id<"importBatches"> | null;
  onBatchChange: (batchId: Id<"importBatches"> | null) => void;
  onSelect: (itemId: Id<"queueItems">) => void;
  onHandoff: (item: QueueListItem) => void;
  limit: number;
}) {
  const handleSearch = (event: ChangeEvent<HTMLInputElement>) =>
    onSearchChange(event.target.value);

  return (
    <Card className="overflow-hidden border-border/70 shadow-[0_1px_2px_rgba(16,24,40,0.04),0_8px_24px_-16px_rgba(16,24,40,0.18)]">
      <CardHeader className="gap-4 border-b border-border/60 pb-4">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          <div className="flex flex-wrap items-center gap-2">
            {FILTERS.map((entry) => {
              const active = filter === entry.key;
              const count = entry.count(summary);
              return (
                <button
                  key={entry.key}
                  type="button"
                  onClick={() => onFilterChange(entry.key)}
                  className={cn(
                    "inline-flex cursor-pointer items-center gap-2 rounded-full border px-3 py-1.5 text-xs font-medium transition-colors",
                    active
                      ? "border-primary/20 bg-primary text-primary-foreground"
                      : "border-border/70 bg-background text-muted-foreground hover:bg-muted hover:text-foreground",
                  )}
                >
                  {entry.label}
                  <span
                    className={cn(
                      "rounded-full px-1.5 py-0.5 text-[10px] tabular-nums",
                      active ? "bg-primary-foreground/15" : "bg-muted",
                    )}
                  >
                    {count}
                  </span>
                </button>
              );
            })}
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <div className="relative">
              <Search className="absolute top-2.5 left-2.5 size-3.5 text-muted-foreground" />
              <Input
                value={search}
                onChange={handleSearch}
                placeholder="Search file, recipient, reference"
                className="h-9 w-full pl-8 sm:w-64"
              />
            </div>
            <Select
              value={batchId ?? "all"}
              onValueChange={(value) =>
                onBatchChange(value === "all" ? null : (value as Id<"importBatches">))
              }
            >
              <SelectTrigger className="h-9 w-full cursor-pointer sm:w-52">
                <SlidersHorizontal className="size-3.5 text-muted-foreground" />
                <SelectValue placeholder="All imports" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All imports</SelectItem>
                {summary.batches.map((batch) => (
                  <SelectItem key={batch._id} value={batch._id}>
                    {batch.name} · {batch.itemCount}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>
      </CardHeader>

      <CardContent className="p-0">
        {isLoading ? (
          <div className="flex items-center justify-center gap-2 py-16 text-sm text-muted-foreground">
            <Loader2 className="size-4 animate-spin" />
            Loading queue…
          </div>
        ) : items.length === 0 ? (
          <div className="flex flex-col items-center gap-2 py-16 text-center">
            <p className="text-sm font-medium">No items match this view</p>
            <p className="text-xs text-muted-foreground">
              Try another filter, clear the search, or import more images.
            </p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow className="hover:bg-transparent">
                  <TableHead className="w-[150px]">Status</TableHead>
                  <TableHead>Recipient</TableHead>
                  <TableHead className="w-[120px] text-right">Amount</TableHead>
                  <TableHead className="hidden w-[170px] lg:table-cell">
                    Reference
                  </TableHead>
                  <TableHead className="hidden xl:table-cell">Image</TableHead>
                  <TableHead className="w-[90px] text-right">Found</TableHead>
                  <TableHead className="w-[70px]" />
                </TableRow>
              </TableHeader>
              <TableBody>
                {items.map((item) => (
                  <TableRow
                    key={item._id}
                    className="cursor-pointer"
                    onClick={() => onSelect(item._id)}
                  >
                    <TableCell>
                      <StatusBadge status={item.status} />
                    </TableCell>
                    <TableCell className="max-w-[260px]">
                      <div className="flex items-center gap-2">
                        <StatusDot status={item.status} />
                        <div className="min-w-0">
                          <p className="truncate text-sm font-medium">
                            {item.payment?.recipientDisplay ?? "Unreadable QR"}
                          </p>
                          <p className="truncate text-xs text-muted-foreground">
                            {item.payment?.merchantName ??
                              item.issues[0]?.message ??
                              "No payment data"}
                          </p>
                        </div>
                      </div>
                    </TableCell>
                    <TableCell className="text-right text-sm font-medium tabular-nums">
                      {formatSatang(item.payment?.amountSatang ?? null)}
                    </TableCell>
                    <TableCell className="hidden lg:table-cell">
                      <span className="font-mono text-xs text-muted-foreground">
                        {truncateMiddle(
                          item.payment?.reference1 ??
                            item.payment?.referenceLabel ??
                            "—",
                          22,
                        )}
                      </span>
                    </TableCell>
                    <TableCell className="hidden max-w-[240px] xl:table-cell">
                      <p className="truncate text-xs text-foreground">
                        {item.fileName}
                      </p>
                      <p className="truncate text-[11px] text-muted-foreground">
                        {item.sourcePath ?? ""}
                      </p>
                    </TableCell>
                    <TableCell className="text-right text-xs text-muted-foreground">
                      {formatRelative(item.discoveredAt)}
                    </TableCell>
                    <TableCell className="text-right">
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon-sm"
                        className="cursor-pointer"
                        title="Hand off this QR to your bank app"
                        onClick={(event) => {
                          event.stopPropagation();
                          onHandoff(item);
                        }}
                      >
                        <Send className="size-3.5" />
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}

        <div className="flex flex-wrap items-center justify-between gap-2 border-t border-border/60 px-4 py-3 text-[11px] text-muted-foreground">
          <span>
            Showing {items.length} item{items.length === 1 ? "" : "s"}
            {summary.total > items.length ? ` of ${summary.total} in the queue` : ""}
            {items.length >= limit ? ` (list capped at ${limit} per view)` : ""}
          </span>
          {summary.truncated ? (
            <span className="text-amber-700">
              Summary covers the newest 5000 items only.
            </span>
          ) : null}
        </div>
      </CardContent>
    </Card>
  );
}
