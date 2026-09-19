import { StatusBadge } from "@/components/queue/status-badge";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Input } from "@/components/ui/input";
import { Separator } from "@/components/ui/separator";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { Textarea } from "@/components/ui/textarea";
import { api } from "@/convex/_generated/api";
import type { Id } from "@/convex/_generated/dataModel";
import type { QueueStatus } from "@/convex/queueStatus";
import { inputValueToSatang, formatDateTime, formatSatang, satangToInputValue } from "@/lib/format";
import { openImageInNewTab, handOffQrImage } from "@/lib/handoff";
import type { QueueItemDetail } from "@/lib/queue-types";
import { STATUS_META } from "@/lib/status-meta";
import { cn } from "@/lib/utils";
import { useMutation, useQuery } from "convex/react";
import {
  CheckCircle2,
  Copy,
  ExternalLink,
  HandCoins,
  Loader2,
  Send,
  ShieldAlert,
  Trash2,
} from "lucide-react";
import { useEffect, useState } from "react";
import { toast } from "sonner";

/** Outcome states the user records after checking the payment themselves. */
const OUTCOME_STATUSES: QueueStatus[] = [
  "SUCCESS",
  "RECONCILED",
  "PAID",
  "WAITING_CONFIRMATION",
  "PAYMENT_FAILED",
  "RECIPIENT_MISMATCH",
  "AMOUNT_MISMATCH",
  "ORDER_NOT_FOUND",
  "EXPIRED",
  "DUPLICATE",
  "UNKNOWN",
];

function Fact({ label, value }: { label: string; value: string | null }) {
  return (
    <div className="space-y-0.5">
      <dt className="text-[11px] tracking-wide text-muted-foreground uppercase">
        {label}
      </dt>
      <dd className="text-sm break-words">{value || "—"}</dd>
    </div>
  );
}

export function ItemDetailSheet({
  itemId,
  onOpenChange,
}: {
  itemId: Id<"queueItems"> | null;
  onOpenChange: (open: boolean) => void;
}) {
  const detail = useQuery(
    api.queue.getItem,
    itemId ? { itemId } : "skip",
  ) as QueueItemDetail | null | undefined;

  const updateStatus = useMutation(api.queue.updateStatus);
  const updateItem = useMutation(api.queue.updateItem);
  const deleteItem = useMutation(api.queue.deleteItem);

  const [amountDraft, setAmountDraft] = useState("");
  const [noteDraft, setNoteDraft] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    setAmountDraft(satangToInputValue(detail?.item.payment?.amountSatang ?? null));
    setNoteDraft(detail?.item.note ?? "");
  }, [detail?.item._id, detail?.item.payment?.amountSatang, detail?.item.note]);

  const run = async (action: () => Promise<unknown>, message: string) => {
    setBusy(true);
    try {
      await action();
      toast.success(message);
    } catch (error) {
      toast.error(
        error instanceof Error ? error.message : "Something went wrong.",
      );
    } finally {
      setBusy(false);
    }
  };

  const handoff = async () => {
    if (!detail?.imageUrl) {
      toast.error("This item has no stored image to hand off.");
      return;
    }
    setBusy(true);
    try {
      const outcome = await handOffQrImage(detail.imageUrl, detail.item.fileName);
      if (outcome.method === "share" && outcome.message.startsWith("Share cancelled")) {
        toast.info(outcome.message);
        return;
      }
      await updateStatus({
        itemId: detail.item._id,
        status: "SUBMITTED",
        note: "QR image handed off to the bank app. This app did not pay anything.",
      });
      toast.success(outcome.message);
    } catch (error) {
      toast.error(
        error instanceof Error ? error.message : "Could not hand off the QR image.",
      );
    } finally {
      setBusy(false);
    }
  };

  const open = itemId !== null;

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="w-full gap-0 overflow-y-auto sm:max-w-xl">
        {detail === undefined ? (
          <div className="flex h-full items-center justify-center">
            <Loader2 className="size-5 animate-spin text-muted-foreground" />
          </div>
        ) : detail === null ? (
          <div className="p-6 text-sm text-muted-foreground">
            This item is no longer in your queue.
          </div>
        ) : (
          <>
            <SheetHeader className="gap-3 border-b border-border/60 px-6 py-5">
              <div className="flex flex-wrap items-center gap-2">
                <StatusBadge status={detail.item.status} />
                {detail.item.payment?.crcValid ? (
                  <span className="inline-flex items-center gap-1 text-[11px] font-medium text-emerald-700">
                    <CheckCircle2 className="size-3.5" /> CRC verified
                  </span>
                ) : (
                  <span className="inline-flex items-center gap-1 text-[11px] font-medium text-rose-700">
                    <ShieldAlert className="size-3.5" /> CRC not verified
                  </span>
                )}
              </div>
              <SheetTitle className="text-lg leading-tight break-all">
                {detail.item.fileName}
              </SheetTitle>
              <SheetDescription>
                {STATUS_META[detail.item.status].hint} The app never confirms the
                payment — you do that in your bank app.
              </SheetDescription>
            </SheetHeader>

            <div className="space-y-6 px-6 py-5">
              {/* Hand-off */}
              <div className="space-y-3 rounded-xl border border-border/70 bg-muted/20 p-4">
                <p className="text-sm font-medium">Hand off to your bank app</p>
                <p className="text-xs text-muted-foreground">
                  Share or save the QR image, then scan it and confirm the payment
                  yourself. Recording a result below is your own confirmation.
                </p>
                <div className="flex flex-wrap gap-2">
                  <Button
                    type="button"
                    className="cursor-pointer gap-2"
                    disabled={busy || !detail.imageUrl}
                    onClick={handoff}
                  >
                    {busy ? (
                      <Loader2 className="size-4 animate-spin" />
                    ) : (
                      <Send className="size-4" />
                    )}
                    Share / save QR image
                  </Button>
                  {detail.imageUrl ? (
                    <>
                      <Button
                        type="button"
                        variant="outline"
                        className="cursor-pointer gap-2"
                        onClick={() => openImageInNewTab(detail.imageUrl!)}
                      >
                        <ExternalLink className="size-4" />
                        Open image
                      </Button>
                      <Button
                        type="button"
                        variant="outline"
                        className="cursor-pointer gap-2"
                        asChild
                      >
                        <a href={detail.imageUrl} download={detail.item.fileName}>
                          Download
                        </a>
                      </Button>
                    </>
                  ) : (
                    <span className="self-center text-xs text-muted-foreground">
                      No image stored for this item.
                    </span>
                  )}
                </div>
                {detail.item.imageRecompressed ? (
                  <p className="text-[11px] text-muted-foreground">
                    Stored copy was re-encoded to keep the queue light. The QR
                    content is unchanged.
                  </p>
                ) : null}
              </div>

              {/* Image preview */}
              {detail.imageUrl ? (
                <div className="overflow-hidden rounded-xl border border-border/70 bg-white">
                  <img
                    src={detail.imageUrl}
                    alt={`QR image for ${detail.item.fileName}`}
                    className="max-h-72 w-full object-contain"
                  />
                </div>
              ) : null}

              {/* Payment facts */}
              {detail.item.payment ? (
                <section className="space-y-3">
                  <h3 className="text-sm font-semibold tracking-tight">
                    PromptPay information
                  </h3>
                  <dl className="grid grid-cols-2 gap-4">
                    <Fact
                      label="Amount"
                      value={formatSatang(detail.item.payment.amountSatang)}
                    />
                    <Fact
                      label="QR type"
                      value={
                        detail.item.payment.pointOfInitiation === "DYNAMIC"
                          ? "Dynamic (amount set)"
                          : detail.item.payment.pointOfInitiation === "STATIC"
                            ? "Static (amount entered at payment)"
                            : "Unknown"
                      }
                    />
                    <Fact
                      label="Recipient"
                      value={detail.item.payment.recipientDisplay}
                    />
                    <Fact
                      label="Recipient type"
                      value={detail.item.payment.recipientType.replace("_", " ")}
                    />
                    <Fact
                      label="Merchant"
                      value={detail.item.payment.merchantName}
                    />
                    <Fact
                      label="City"
                      value={detail.item.payment.merchantCity}
                    />
                    <Fact
                      label="Reference 1"
                      value={detail.item.payment.reference1}
                    />
                    <Fact
                      label="Reference 2"
                      value={detail.item.payment.reference2}
                    />
                    <Fact
                      label="Reference label"
                      value={detail.item.payment.referenceLabel}
                    />
                    <Fact
                      label="Currency"
                      value={detail.item.payment.currency || "—"}
                    />
                    <Fact
                      label="Country"
                      value={detail.item.payment.countryCode}
                    />
                    <Fact
                      label="AID"
                      value={detail.item.payment.aid}
                    />
                  </dl>
                </section>
              ) : null}

              {/* Issues */}
              {detail.item.issues.length > 0 ? (
                <section className="space-y-2">
                  <h3 className="text-sm font-semibold tracking-tight">
                    Validation notes
                  </h3>
                  <ul className="space-y-2">
                    {detail.item.issues.map((issue, index) => (
                      <li
                        key={`${issue.code}-${index}`}
                        className={cn(
                          "rounded-lg border px-3 py-2 text-xs",
                          issue.severity === "error"
                            ? "border-rose-200 bg-rose-50/70 text-rose-700"
                            : "border-amber-200 bg-amber-50/70 text-amber-800",
                        )}
                      >
                        <span className="font-semibold uppercase tracking-wide">
                          {issue.severity === "error" ? "Error" : "Warning"}
                        </span>{" "}
                        {issue.message}
                      </li>
                    ))}
                  </ul>
                </section>
              ) : null}

              {detail.duplicateOf ? (
                <p className="rounded-lg border border-amber-200 bg-amber-50/70 px-3 py-2 text-xs text-amber-800">
                  Duplicate of “{detail.duplicateOf.fileName}”.
                </p>
              ) : null}

              {/* Raw payload */}
              {detail.item.payload ? (
                <section className="space-y-2">
                  <div className="flex items-center justify-between">
                    <h3 className="text-sm font-semibold tracking-tight">
                      Raw EMVCo payload
                    </h3>
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      className="cursor-pointer gap-1.5"
                      onClick={() => {
                        void navigator.clipboard
                          ?.writeText(detail.item.payload ?? "")
                          .then(() => toast.success("Payload copied."))
                          .catch(() => toast.error("Clipboard not available."));
                      }}
                    >
                      <Copy className="size-3.5" />
                      Copy
                    </Button>
                  </div>
                  <pre className="max-h-32 overflow-auto rounded-lg border border-border/70 bg-muted/40 p-3 text-[11px] leading-4 break-all whitespace-pre-wrap">
                    {detail.item.payload}
                  </pre>
                </section>
              ) : null}

              <Separator />

              {/* Manual corrections */}
              <section className="space-y-3">
                <h3 className="text-sm font-semibold tracking-tight">
                  Correct the parsed data
                </h3>
                <p className="text-xs text-muted-foreground">
                  Use this when the QR encodes the wrong amount (a common mistake in
                  manually generated QRs). The original payload is never modified.
                </p>
                <div className="flex flex-wrap items-end gap-2">
                  <div className="space-y-1">
                    <label className="text-[11px] tracking-wide text-muted-foreground uppercase">
                      Amount (THB)
                    </label>
                    <Input
                      value={amountDraft}
                      onChange={(event) => setAmountDraft(event.target.value)}
                      placeholder="0.00"
                      inputMode="decimal"
                      className="w-32"
                      disabled={!detail.item.payment}
                    />
                  </div>
                  <Button
                    type="button"
                    variant="outline"
                    className="cursor-pointer gap-2"
                    disabled={busy || !detail.item.payment}
                    onClick={() =>
                      run(async () => {
                        const satang = inputValueToSatang(amountDraft);
                        if (amountDraft.trim() && satang === null) {
                          throw new Error("Enter the amount as a number, e.g. 250.00");
                        }
                        await updateItem({
                          itemId: detail.item._id,
                          amountSatang: satang,
                        });
                      }, "Amount updated.")
                    }
                  >
                    <HandCoins className="size-4" />
                    Save amount
                  </Button>
                </div>
                <div className="space-y-2">
                  <label className="text-[11px] tracking-wide text-muted-foreground uppercase">
                    Note for this payment
                  </label>
                  <Textarea
                    value={noteDraft}
                    onChange={(event) => setNoteDraft(event.target.value)}
                    placeholder="Order number, customer, or anything you need when checking the bank app."
                    rows={3}
                  />
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    className="cursor-pointer"
                    disabled={busy}
                    onClick={() =>
                      run(
                        () =>
                          updateItem({
                            itemId: detail.item._id,
                            note: noteDraft,
                          }),
                        "Note saved.",
                      )
                    }
                  >
                    Save note
                  </Button>
                </div>
              </section>

              <Separator />

              {/* Recording the outcome */}
              <section className="space-y-3">
                <h3 className="text-sm font-semibold tracking-tight">
                  Record what your bank app showed
                </h3>
                <p className="text-xs text-muted-foreground">
                  Only record a result you have actually seen in your banking app.
                  Choosing “Success” here does not move money — it records your own
                  verification.
                </p>
                <div className="flex flex-wrap gap-2">
                  <Button
                    type="button"
                    variant="outline"
                    className="cursor-pointer gap-2"
                    disabled={busy}
                    onClick={() =>
                      run(
                        () =>
                          updateStatus({
                            itemId: detail.item._id,
                            status: "READY",
                            note: "Marked ready for payment.",
                          }),
                        "Marked ready for payment.",
                      )
                    }
                  >
                    <CheckCircle2 className="size-4" />
                    Mark ready
                  </Button>
                  <Button
                    type="button"
                    className="cursor-pointer gap-2"
                    disabled={busy}
                    onClick={() =>
                      run(
                        () =>
                          updateStatus({
                            itemId: detail.item._id,
                            status: "SUCCESS",
                            note: "Payment confirmed by the user in their bank app.",
                          }),
                        "Recorded as confirmed in your bank app.",
                      )
                    }
                  >
                    <CheckCircle2 className="size-4" />
                    Confirmed in bank app
                  </Button>
                  <DropdownMenu>
                    <DropdownMenuTrigger asChild>
                      <Button
                        type="button"
                        variant="outline"
                        className="cursor-pointer"
                        disabled={busy}
                      >
                        Other outcome
                      </Button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="start" className="w-64">
                      <DropdownMenuLabel>Record status</DropdownMenuLabel>
                      <DropdownMenuSeparator />
                      {OUTCOME_STATUSES.map((status) => (
                        <DropdownMenuItem
                          key={status}
                          className="cursor-pointer"
                          onSelect={() => {
                            void run(
                              () =>
                                updateStatus({
                                  itemId: detail.item._id,
                                  status,
                                  note: "Recorded by the user.",
                                }),
                              `Recorded as “${STATUS_META[status].label}”.`,
                            );
                          }}
                        >
                          <span className="flex flex-col">
                            <span className="text-sm">
                              {STATUS_META[status].label}
                            </span>
                            <span className="text-[11px] text-muted-foreground">
                              {STATUS_META[status].hint}
                            </span>
                          </span>
                        </DropdownMenuItem>
                      ))}
                    </DropdownMenuContent>
                  </DropdownMenu>
                </div>
              </section>

              <Separator />

              {/* History */}
              <section className="space-y-3">
                <h3 className="text-sm font-semibold tracking-tight">History</h3>
                <ol className="space-y-3">
                  {[...detail.item.statusHistory].reverse().map((entry, index) => (
                    <li key={`${entry.at}-${index}`} className="flex gap-3">
                      <span className="mt-1.5 size-2 shrink-0 rounded-full bg-primary/60" />
                      <div className="text-xs">
                        <p className="font-medium">
                          {STATUS_META[entry.status].label}
                          <span className="ml-2 font-normal text-muted-foreground">
                            {formatDateTime(entry.at)}
                          </span>
                        </p>
                        {entry.note ? (
                          <p className="text-muted-foreground">{entry.note}</p>
                        ) : null}
                      </div>
                    </li>
                  ))}
                </ol>
              </section>

              <Separator />

              <AlertDialog>
                <AlertDialogTrigger asChild>
                  <Button
                    type="button"
                    variant="ghost"
                    className="cursor-pointer gap-2 text-destructive hover:text-destructive"
                    disabled={busy}
                  >
                    <Trash2 className="size-4" />
                    Remove from queue
                  </Button>
                </AlertDialogTrigger>
                <AlertDialogContent>
                  <AlertDialogHeader>
                    <AlertDialogTitle>Remove this queue item?</AlertDialogTitle>
                    <AlertDialogDescription>
                      The stored image copy and its history are deleted. Payments you
                      already made in your bank app are unaffected.
                    </AlertDialogDescription>
                  </AlertDialogHeader>
                  <AlertDialogFooter>
                    <AlertDialogCancel className="cursor-pointer">
                      Keep item
                    </AlertDialogCancel>
                    <AlertDialogAction
                      className="cursor-pointer"
                      onClick={() => {
                        void run(async () => {
                          await deleteItem({ itemId: detail.item._id });
                          onOpenChange(false);
                        }, "Item removed from the queue.");
                      }}
                    >
                      Remove
                    </AlertDialogAction>
                  </AlertDialogFooter>
                </AlertDialogContent>
              </AlertDialog>

              <p className="pb-2 text-[11px] leading-4 text-muted-foreground">
                Discovered {formatDateTime(detail.item.discoveredAt)} · File{" "}
                {detail.item.fileName}
                {detail.item.sourcePath ? ` · Path ${detail.item.sourcePath}` : ""}
              </p>
            </div>
          </>
        )}
      </SheetContent>
    </Sheet>
  );
}
