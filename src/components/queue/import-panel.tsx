import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import type { ImportState } from "@/hooks/use-queue-import";
import { cn } from "@/lib/utils";
import {
  FolderOpen,
  Images,
  ScanLine,
  ShieldCheck,
  Square,
  X,
} from "lucide-react";
import { useRef, type InputHTMLAttributes } from "react";

const directoryAttributes = {
  webkitdirectory: "",
  directory: "",
} as unknown as InputHTMLAttributes<HTMLInputElement>;

function Tally({ label, value, tone }: { label: string; value: number; tone?: string }) {
  return (
    <div className="flex flex-col">
      <span className={cn("text-lg font-semibold tabular-nums", tone)}>{value}</span>
      <span className="text-[11px] tracking-wide text-muted-foreground uppercase">
        {label}
      </span>
    </div>
  );
}

export function ImportPanel({
  state,
  onRun,
  onReset,
  onCancel,
  busy,
}: {
  state: ImportState;
  onRun: (files: FileList | File[], source: "files" | "folder") => void;
  onReset: () => void;
  onCancel: () => void;
  busy: boolean;
}) {
  const filesInput = useRef<HTMLInputElement>(null);
  const folderInput = useRef<HTMLInputElement>(null);

  const running = state.phase === "scanning" || state.phase === "finishing";
  const percent =
    state.total === 0 ? 0 : Math.round((state.processed / state.total) * 100);

  return (
    <Card className="border-border/70 shadow-[0_1px_2px_rgba(16,24,40,0.04),0_8px_24px_-16px_rgba(16,24,40,0.18)]">
      <CardHeader className="gap-3">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="space-y-1.5">
            <CardTitle className="flex items-center gap-2 text-base">
              <ScanLine className="size-4 text-primary" />
              Import PromptPay QR images
            </CardTitle>
            <p className="max-w-2xl text-sm text-muted-foreground">
              Pick a folder of payment screenshots or individual images. QR codes
              are decoded and validated on this device, then added to your queue
              for you to pay manually.
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <input
              ref={filesInput}
              type="file"
              accept="image/*"
              multiple
              className="hidden"
              onChange={(event) => {
                if (event.target.files?.length) {
                  onRun(event.target.files, "files");
                }
                event.target.value = "";
              }}
            />
            <input
              ref={folderInput}
              type="file"
              accept="image/*"
              multiple
              className="hidden"
              {...directoryAttributes}
              onChange={(event) => {
                if (event.target.files?.length) {
                  onRun(event.target.files, "folder");
                }
                event.target.value = "";
              }}
            />
            <Button
              type="button"
              variant="outline"
              className="cursor-pointer gap-2"
              disabled={busy || running}
              onClick={() => folderInput.current?.click()}
            >
              <FolderOpen className="size-4" />
              Choose folder
            </Button>
            <Button
              type="button"
              className="cursor-pointer gap-2"
              disabled={busy || running}
              onClick={() => filesInput.current?.click()}
            >
              <Images className="size-4" />
              Choose images
            </Button>
          </div>
        </div>
      </CardHeader>

      <CardContent className="space-y-5">
        {running ? (
          <div className="space-y-3 rounded-xl border border-border/70 bg-muted/40 p-4">
            <div className="flex items-center justify-between gap-4">
              <div className="min-w-0">
                <p className="text-sm font-medium">
                  {state.phase === "finishing"
                    ? "Wrapping up the import…"
                    : "Decoding and validating images"}
                </p>
                <p className="truncate text-xs text-muted-foreground">
                  {state.currentFile ?? `${state.processed} of ${state.total} processed`}
                </p>
              </div>
              <div className="flex items-center gap-2">
                <span className="text-sm tabular-nums text-muted-foreground">
                  {state.processed}/{state.total}
                </span>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  className="cursor-pointer gap-1.5"
                  onClick={onCancel}
                >
                  <Square className="size-3" />
                  Stop
                </Button>
              </div>
            </div>
            <Progress value={percent} />
            <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
              <Tally label="Decoded" value={state.decoded} />
              <Tally label="Warnings" value={state.warnings} tone="text-amber-600" />
              <Tally label="Invalid" value={state.invalid} tone="text-rose-600" />
              <Tally label="Duplicates" value={state.duplicates} tone="text-amber-700" />
            </div>
          </div>
        ) : null}

        {state.phase === "finished" || state.phase === "cancelled" ? (
          <div className="flex flex-wrap items-center justify-between gap-4 rounded-xl border border-border/70 bg-muted/30 p-4">
            <div className="text-sm">
              <p className="font-medium">
                {state.phase === "cancelled" ? "Import stopped" : "Import complete"} —{" "}
                {state.saved} of {state.total} images queued
              </p>
              <p className="text-xs text-muted-foreground">
                {state.decoded} decoded · {state.warnings} need review ·{" "}
                {state.invalid} invalid · {state.duplicates} duplicates. Nothing was
                paid — pay each QR yourself in your bank app.
              </p>
            </div>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              className="cursor-pointer gap-1.5"
              onClick={onReset}
            >
              <X className="size-3.5" />
              Dismiss
            </Button>
          </div>
        ) : null}

        {state.errors.length > 0 ? (
          <ul className="space-y-1 rounded-xl border border-rose-200 bg-rose-50/60 p-4 text-xs text-rose-700">
            {state.errors.map((error) => (
              <li key={error}>{error}</li>
            ))}
          </ul>
        ) : null}

        <div className="flex items-start gap-2.5 rounded-xl border border-border/70 bg-background p-3.5 text-xs text-muted-foreground">
          <ShieldCheck className="mt-0.5 size-4 shrink-0 text-emerald-600" />
          <p>
            This tool never enters a PIN, password or OTP, never taps through bank
            security screens and never confirms a payment. It only prepares the QR
            for your bank app; you stay in control of the transaction.
          </p>
        </div>
      </CardContent>
    </Card>
  );
}

export function EmptyQueueState({ message }: { message?: string }) {
  return (
    <Card className="border-dashed border-border/80 bg-muted/20">
      <CardContent className="flex flex-col items-center gap-4 px-6 py-14 text-center">
        <span className="flex size-12 items-center justify-center rounded-2xl bg-primary/10 text-primary">
          <ScanLine className="size-6" />
        </span>
        <div className="space-y-1.5">
          <h3 className="text-base font-semibold tracking-tight">
            Your payment queue is empty
          </h3>
          <p className="mx-auto max-w-md text-sm text-muted-foreground">
            {message ??
              "Import a folder of PromptPay QR screenshots to build your queue. Each image is decoded, checked for duplicates and prepared for hand-off to your bank app."}
          </p>
        </div>
      </CardContent>
    </Card>
  );
}
