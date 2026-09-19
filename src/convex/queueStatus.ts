import { v, type Infer } from "convex/values";

/**
 * Explicit payment lifecycle states.
 *
 * Status is only ever advanced deliberately. Opening, sharing, or handing a QR
 * off to a bank app never marks an item as paid — the user confirms that in the
 * bank app and records it here.
 */
export const QUEUE_STATUSES = [
  // Processing pipeline
  "DISCOVERED",
  "DECODED",
  "VALIDATED",
  "READY",
  // Handoff / bank confirmation
  "SUBMITTED",
  "WAITING_CONFIRMATION",
  "SUCCESS",
  "RECONCILED",
  "PAID",
  // Problem states
  "INVALID",
  "RECIPIENT_MISMATCH",
  "AMOUNT_MISMATCH",
  "ORDER_NOT_FOUND",
  "DUPLICATE",
  "EXPIRED",
  "PAYMENT_FAILED",
  "UNKNOWN",
] as const;

export const queueStatusValidator = v.union(
  ...QUEUE_STATUSES.map((status) => v.literal(status)),
);

export type QueueStatus = Infer<typeof queueStatusValidator>;

/** Statuses that mean "no money moved, the user should look at this item". */
export const PROBLEM_STATUSES: QueueStatus[] = [
  "INVALID",
  "RECIPIENT_MISMATCH",
  "AMOUNT_MISMATCH",
  "ORDER_NOT_FOUND",
  "DUPLICATE",
  "EXPIRED",
  "PAYMENT_FAILED",
  "UNKNOWN",
];

/** Statuses that require a human decision in the bank app. */
export const HANDOFF_STATUSES: QueueStatus[] = [
  "SUBMITTED",
  "WAITING_CONFIRMATION",
];

/** Statuses the user records manually after verifying in their banking app. */
export const SETTLED_STATUSES: QueueStatus[] = [
  "SUCCESS",
  "RECONCILED",
  "PAID",
];

/** Marks that only a human may set — the app never infers these. */
export const MANUAL_ONLY_STATUSES: QueueStatus[] = [
  ...SETTLED_STATUSES,
  "PAYMENT_FAILED",
  "RECIPIENT_MISMATCH",
  "AMOUNT_MISMATCH",
  "ORDER_NOT_FOUND",
  "EXPIRED",
  "UNKNOWN",
];

export const ISSUE_CODES = [
  "UNREADABLE_IMAGE",
  "INVALID_IMAGE",
  "NO_QR_FOUND",
  "MALFORMED_PAYLOAD",
  "CRC_FAILED",
  "UNSUPPORTED_PAYLOAD",
  "MISSING_AMOUNT",
  "INVALID_AMOUNT",
  "MISSING_RECIPIENT",
  "DUPLICATE_QR",
  "DUPLICATE_REFERENCE",
  "MISSING_REFERENCE",
] as const;

export const issueCodeValidator = v.union(
  ...ISSUE_CODES.map((code) => v.literal(code)),
);

export type IssueCode = Infer<typeof issueCodeValidator>;

export const issueValidator = v.object({
  code: issueCodeValidator,
  /** `error` blocks payment, `warning` needs a human glance. */
  severity: v.union(v.literal("error"), v.literal("warning")),
  message: v.string(),
});

export type Issue = Infer<typeof issueValidator>;
