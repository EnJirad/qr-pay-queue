import type { QueueStatus } from "@/convex/queueStatus";

export type StatusTone =
  | "neutral"
  | "info"
  | "progress"
  | "success"
  | "warning"
  | "danger";

export type StatusGroup = "review" | "ready" | "handoff" | "settled" | "problem";

export type StatusMeta = {
  label: string;
  group: StatusGroup;
  tone: StatusTone;
  /** What this state means in plain language. */
  hint: string;
};

/**
 * Single source of truth for how a queue status is described in the UI.
 * `manualOnly` states are never reached automatically — the user records them.
 */
export const STATUS_META: Record<QueueStatus, StatusMeta> = {
  DISCOVERED: {
    label: "Discovered",
    group: "review",
    tone: "neutral",
    hint: "The image was found but not processed yet.",
  },
  DECODED: {
    label: "Decoded",
    group: "review",
    tone: "info",
    hint: "A QR payload was read from the image.",
  },
  VALIDATED: {
    label: "Needs review",
    group: "review",
    tone: "warning",
    hint: "Decoded with warnings — read the notes before paying.",
  },
  READY: {
    label: "Ready",
    group: "ready",
    tone: "progress",
    hint: "Checked and waiting for you to pay it in your bank app.",
  },
  SUBMITTED: {
    label: "Handed off",
    group: "handoff",
    tone: "info",
    hint: "The QR image was opened or shared. No payment happened yet.",
  },
  WAITING_CONFIRMATION: {
    label: "Awaiting confirmation",
    group: "handoff",
    tone: "info",
    hint: "Sent to the bank app — confirm the result there, then record it here.",
  },
  SUCCESS: {
    label: "Success",
    group: "settled",
    tone: "success",
    hint: "You recorded that the bank app confirmed this payment.",
  },
  RECONCILED: {
    label: "Reconciled",
    group: "settled",
    tone: "success",
    hint: "Matched against a bank statement or receipt.",
  },
  PAID: {
    label: "Paid",
    group: "settled",
    tone: "success",
    hint: "Manually recorded as paid.",
  },
  INVALID: {
    label: "Invalid",
    group: "problem",
    tone: "danger",
    hint: "The QR could not be read as a PromptPay payload.",
  },
  RECIPIENT_MISMATCH: {
    label: "Recipient mismatch",
    group: "problem",
    tone: "danger",
    hint: "The recipient did not match what you expected.",
  },
  AMOUNT_MISMATCH: {
    label: "Amount mismatch",
    group: "problem",
    tone: "danger",
    hint: "The amount differs from the expected order value.",
  },
  ORDER_NOT_FOUND: {
    label: "Order not found",
    group: "problem",
    tone: "danger",
    hint: "No matching order or invoice for this payment.",
  },
  DUPLICATE: {
    label: "Duplicate",
    group: "problem",
    tone: "warning",
    hint: "The same QR or reference is already in the queue.",
  },
  EXPIRED: {
    label: "Expired",
    group: "problem",
    tone: "warning",
    hint: "The QR or its reference has expired.",
  },
  PAYMENT_FAILED: {
    label: "Payment failed",
    group: "problem",
    tone: "danger",
    hint: "The bank app reported a failure.",
  },
  UNKNOWN: {
    label: "Unknown",
    group: "problem",
    tone: "neutral",
    hint: "Outcome not verified — check it in your bank app before retrying.",
  },
};

export const STATUS_GROUPS: { key: StatusGroup; label: string; hint: string }[] = [
  {
    key: "review",
    label: "Needs review",
    hint: "Decoded, but with warnings you should read first.",
  },
  {
    key: "ready",
    label: "Ready",
    hint: "Validated and queued for payment.",
  },
  {
    key: "handoff",
    label: "Handed off",
    hint: "Opened in a bank app — the outcome is still unconfirmed.",
  },
  {
    key: "settled",
    label: "Settled",
    hint: "You recorded the confirmed result of the payment.",
  },
  {
    key: "problem",
    label: "Problems",
    hint: "Nothing moved. These need a human decision.",
  },
];

export function statusesInGroup(group: StatusGroup): QueueStatus[] {
  return (Object.keys(STATUS_META) as QueueStatus[]).filter(
    (status) => STATUS_META[status].group === group,
  );
}

export const TONE_CLASSES: Record<StatusTone, string> = {
  neutral: "bg-muted text-muted-foreground ring-1 ring-inset ring-border",
  info: "bg-sky-50 text-sky-700 ring-1 ring-inset ring-sky-200",
  progress: "bg-indigo-50 text-indigo-700 ring-1 ring-inset ring-indigo-200",
  success: "bg-emerald-50 text-emerald-700 ring-1 ring-inset ring-emerald-200",
  warning: "bg-amber-50 text-amber-800 ring-1 ring-inset ring-amber-200",
  danger: "bg-rose-50 text-rose-700 ring-1 ring-inset ring-rose-200",
};
