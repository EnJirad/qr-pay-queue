import { EmptyQueueState, ImportPanel } from "@/components/queue/import-panel";
import { ItemDetailSheet } from "@/components/queue/item-detail-sheet";
import { ProcessingSummary } from "@/components/queue/processing-summary";
import { QueueList, type QueueFilter } from "@/components/queue/queue-list";
import { SummaryCards } from "@/components/queue/summary-cards";
import { Button } from "@/components/ui/button";
import { api } from "@/convex/_generated/api";
import type { Id } from "@/convex/_generated/dataModel";
import type { QueueStatus } from "@/convex/queueStatus";
import { useAuth } from "@/hooks/use-auth";
import { useQueueImport } from "@/hooks/use-queue-import";
import { handOffQrImage } from "@/lib/handoff";
import type { QueueListItem } from "@/lib/queue-types";
import { statusesInGroup } from "@/lib/status-meta";
import { buildSummaryView } from "@/lib/summary";
import { useConvex, useMutation, useQuery } from "convex/react";
import { LogOut, QrCode, ShieldCheck } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router";
import { toast } from "sonner";

const LIST_LIMIT = 300;

function statusesForFilter(filter: QueueFilter): QueueStatus[] | undefined {
  if (filter === "all") return undefined;
  if (filter === "duplicates") return ["DUPLICATE"];
  return statusesInGroup(filter);
}

export default function Dashboard() {
  const { user, signOut } = useAuth();
  const navigate = useNavigate();
  const convex = useConvex();
  const updateStatus = useMutation(api.queue.updateStatus);

  const summarySource = useQuery(api.queue.workspaceSummary);
  const summary = useMemo(() => buildSummaryView(summarySource), [summarySource]);

  const [filter, setFilter] = useState<QueueFilter>("all");
  const [search, setSearch] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [batchId, setBatchId] = useState<Id<"importBatches"> | null>(null);
  const [selectedItem, setSelectedItem] = useState<Id<"queueItems"> | null>(null);

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(search.trim()), 250);
    return () => clearTimeout(timer);
  }, [search]);

  const statuses = useMemo(() => statusesForFilter(filter), [filter]);

  const items = useQuery(api.queue.listItems, {
    batchId: batchId ?? undefined,
    statuses,
    search: debouncedSearch || undefined,
    limit: LIST_LIMIT,
  });

  const importRunner = useQueueImport();
  const importing =
    importRunner.state.phase === "scanning" || importRunner.state.phase === "finishing";

  const handleHandoff = async (item: QueueListItem) => {
    try {
      const detail = await convex.query(api.queue.getItem, { itemId: item._id });
      if (!detail?.imageUrl) {
        toast.error("This item has no stored image to hand off.");
        return;
      }
      const outcome = await handOffQrImage(detail.imageUrl, detail.item.fileName);
      if (outcome.message.startsWith("Share cancelled")) {
        toast.info(outcome.message);
        return;
      }
      await updateStatus({
        itemId: item._id,
        status: "SUBMITTED",
        note: "QR image handed off to the bank app. This app did not pay anything.",
      });
      toast.success(outcome.message);
    } catch (error) {
      toast.error(
        error instanceof Error ? error.message : "Could not hand off the QR image.",
      );
    }
  };

  const handleSignOut = async () => {
    await signOut();
    navigate("/");
  };

  const summaryLoaded = summarySource !== undefined;

  return (
    <main className="min-h-screen bg-background">
      <header className="sticky top-0 z-20 border-b border-border/70 bg-background/85 backdrop-blur">
        <div className="mx-auto flex w-full max-w-7xl flex-wrap items-center justify-between gap-3 px-5 py-3.5 sm:px-8">
          <div className="flex items-center gap-3">
            <span className="flex size-9 items-center justify-center rounded-xl bg-primary text-primary-foreground">
              <QrCode className="size-4.5" />
            </span>
            <div className="leading-tight">
              <p className="text-sm font-semibold tracking-tight">
                QR Payment Queue
              </p>
              <p className="text-[11px] text-muted-foreground">
                {user?.email ?? "PromptPay review workspace"}
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <span className="hidden items-center gap-1.5 rounded-full border border-border/70 px-3 py-1.5 text-[11px] text-muted-foreground sm:inline-flex">
              <ShieldCheck className="size-3.5 text-emerald-600" />
              Manual confirmation only
            </span>
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="cursor-pointer gap-2"
              onClick={handleSignOut}
            >
              <LogOut className="size-3.5" />
              Sign out
            </Button>
          </div>
        </div>
      </header>

      <div className="mx-auto flex w-full max-w-7xl flex-col gap-6 px-5 py-7 sm:px-8 sm:py-10">
        <div className="flex flex-col gap-2">
          <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">
            PromptPay payment queue
          </h1>
          <p className="max-w-3xl text-sm text-muted-foreground">
            Import a folder of QR payment images, decode and validate them locally,
            then work through the queue one payment at a time — confirming each one
            yourself in your bank app.
          </p>
        </div>

        <ImportPanel
          state={importRunner.state}
          onRun={(files, source) => {
            void importRunner.run(files, source);
          }}
          onReset={importRunner.reset}
          onCancel={importRunner.cancel}
          busy={importing}
        />

        <SummaryCards summary={summary} />

        {summaryLoaded && summary.total === 0 && !importing ? (
          <EmptyQueueState />
        ) : (
          <>
            <QueueList
              items={items ?? []}
              isLoading={items === undefined}
              summary={summary}
              filter={filter}
              onFilterChange={setFilter}
              search={search}
              onSearchChange={setSearch}
              batchId={batchId}
              onBatchChange={setBatchId}
              onSelect={setSelectedItem}
              onHandoff={(item) => {
                void handleHandoff(item);
              }}
              limit={LIST_LIMIT}
            />
            <ProcessingSummary summary={summary} />
          </>
        )}

        <p className="pb-4 text-[11px] leading-4 text-muted-foreground">
          This workspace never stores bank credentials, PINs, OTPs or card data, and
          it never automates payment confirmation. Unknown outcomes are never retried
          automatically — check them in your bank app first.
        </p>
      </div>

      <ItemDetailSheet itemId={selectedItem} onOpenChange={(open) => {
        if (!open) setSelectedItem(null);
      }} />
    </main>
  );
}
