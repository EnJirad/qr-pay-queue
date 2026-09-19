import type { Issue } from "@/convex/queueStatus";

export type { Issue } from "@/convex/queueStatus";

/**
 * PromptPay / EMVCo QR payload parsing.
 *
 * This module is pure string logic (no DOM, no browser APIs) so it can be
 * reasoned about in isolation. It never performs a payment: it only turns a
 * decoded QR payload into the facts a human needs to verify.
 *
 * Reference: EMVCo "QR Code Specification for Payment Systems", merchant
 * presented mode, plus the Thai PromptPay merchant account information layouts
 * (ITMX AIDs A000000677010111 for PromptPay and A000000677010112 for bill
 * payment).
 */

export const PROMPTPAY_AID = "A000000677010111";
export const BILL_PAYMENT_AID = "A000000677010112";
export const THB_CURRENCY_CODE = "764";

export type RecipientType =
  | "MOBILE"
  | "NATIONAL_ID"
  | "EWALLET"
  | "BILLER"
  | "UNKNOWN";

export type PaymentInfo = {
  /** `11` = static (payer enters the amount), `12` = dynamic (amount baked in). */
  pointOfInitiation: "STATIC" | "DYNAMIC" | "UNKNOWN";
  recipientType: RecipientType;
  /** Raw digits of the merchant identifier as encoded in the payload. */
  recipientId: string;
  /** Human readable recipient, formatted for review. */
  recipientDisplay: string;
  merchantName: string | null;
  merchantCity: string | null;
  /** Integer satang to avoid floating point money errors. null = not encoded. */
  amountSatang: number | null;
  currency: string;
  /** Reference / bill number fields, useful for duplicate + order matching. */
  reference1: string | null;
  reference2: string | null;
  referenceLabel: string | null;
  countryCode: string | null;
  merchantCategoryCode: string | null;
  aid: string | null;
  crcValid: boolean;
};

export type ParseOutcome = {
  /** True when no `error` severity issue was found. */
  ok: boolean;
  payment: PaymentInfo | null;
  issues: Issue[];
};

type TlvNode = { tag: string; value: string; children: TlvNode[] };

function isContainerTag(tag: string): boolean {
  const numeric = Number(tag);
  return (
    (numeric >= 26 && numeric <= 51) ||
    numeric === 62 ||
    numeric === 64 ||
    (numeric >= 80 && numeric <= 99)
  );
}

function find(root: TlvNode[], tag: string): TlvNode | undefined {
  return root.find((node) => node.tag === tag);
}

function value(root: TlvNode[], tag: string): string | null {
  const node = find(root, tag);
  return node && node.value.length > 0 ? node.value : null;
}

/** Parse a TLV (tag-length-value) string into a tree. */
export function parseTlv(
  input: string,
): { ok: true; nodes: TlvNode[] } | { ok: false; error: string } {
  const nodes: TlvNode[] = [];
  let index = 0;

  while (index < input.length) {
    if (index + 4 > input.length) {
      return { ok: false, error: `truncated TLV header at offset ${index}` };
    }
    const tag = input.slice(index, index + 2);
    const lengthRaw = input.slice(index + 2, index + 4);
    if (!/^\d{2}$/.test(tag) || !/^\d{2}$/.test(lengthRaw)) {
      return { ok: false, error: `invalid TLV header "${tag}${lengthRaw}"` };
    }
    const length = Number(lengthRaw);
    const start = index + 4;
    const end = start + length;
    if (end > input.length) {
      return { ok: false, error: `truncated value for tag ${tag}` };
    }

    const node: TlvNode = { tag, value: input.slice(start, end), children: [] };
    if (isContainerTag(tag) && node.value.length > 0) {
      const nested = parseTlv(node.value);
      if (nested.ok) node.children = nested.nodes;
    }
    nodes.push(node);
    index = end;
  }

  return { ok: true, nodes };
}

/** CRC-16/CCITT-FALSE, used by EMVCo tag 63. */
export function crc16ccitt(input: string): number {
  let crc = 0xffff;
  for (let i = 0; i < input.length; i += 1) {
    crc ^= input.charCodeAt(i) << 8;
    for (let bit = 0; bit < 8; bit += 1) {
      crc = crc & 0x8000 ? ((crc << 1) ^ 0x1021) & 0xffff : (crc << 1) & 0xffff;
    }
  }
  return crc;
}

function verifyCrc(payload: string, roots: TlvNode[]): boolean {
  const declared = value(roots, "63");
  if (!declared || declared.length !== 4) return false;
  const expected = crc16ccitt(payload.slice(0, payload.length - 4))
    .toString(16)
    .toUpperCase()
    .padStart(4, "0");
  return expected === declared.toUpperCase();
}

/** `12.50` -> 1250 satang. Returns null when the amount is not a valid number. */
export function amountToSatang(raw: string): number | null {
  const trimmed = raw.trim();
  if (!/^\d{1,12}(\.\d{0,2})?$/.test(trimmed)) return null;
  const [whole, fraction = ""] = trimmed.split(".");
  const satang = Number(whole) * 100 + Number(fraction.padEnd(2, "0"));
  return Number.isFinite(satang) ? satang : null;
}

/** PromptPay mobiles are encoded as 0066XXXXXXXXX. Renders as 0XX-XXX-XXXX. */
export function formatMobile(raw: string): string {
  const digits = raw.replace(/\D/g, "");
  let local = digits;
  if (local.startsWith("0066")) local = `0${local.slice(4)}`;
  else if (local.startsWith("66") && local.length === 11) {
    local = `0${local.slice(2)}`;
  }
  if (local.length === 10) {
    return `${local.slice(0, 3)}-${local.slice(3, 6)}-${local.slice(6)}`;
  }
  return local || raw;
}

/** Thai national / tax IDs render as X-XXXX-XXXXX-XX-X. */
export function formatNationalId(raw: string): string {
  const digits = raw.replace(/\D/g, "");
  if (digits.length !== 13) return digits || raw;
  return `${digits[0]}-${digits.slice(1, 5)}-${digits.slice(5, 10)}-${digits.slice(10, 12)}-${digits[12]}`;
}

function invalid(...issues: Issue[]): ParseOutcome {
  return { ok: false, payment: null, issues };
}

/**
 * Parse one decoded QR payload into structured payment information.
 * Never throws; unreadable payloads come back as issues.
 */
export function parsePromptPayPayload(payload: string): ParseOutcome {
  const trimmed = payload.trim();
  if (!trimmed) {
    return invalid({
      code: "MALFORMED_PAYLOAD",
      severity: "error",
      message: "The QR code contained no data.",
    });
  }

  const parsed = parseTlv(trimmed);
  if (!parsed.ok) {
    return invalid({
      code: "MALFORMED_PAYLOAD",
      severity: "error",
      message: `The payload is not valid EMVCo TLV data (${parsed.error}).`,
    });
  }

  const roots = parsed.nodes;
  const issues: Issue[] = [];

  const crcValid = verifyCrc(trimmed, roots);
  if (!crcValid) {
    issues.push({
      code: "CRC_FAILED",
      severity: "error",
      message:
        "CRC checksum does not match the payload — the QR may be corrupted or tampered with.",
    });
  }

  const formatIndicator = value(roots, "00");
  if (formatIndicator !== "01") {
    issues.push({
      code: "UNSUPPORTED_PAYLOAD",
      severity: "error",
      message: `Unsupported payload format indicator "${formatIndicator ?? "missing"}" (expected 01).`,
    });
  }

  const poiRaw = value(roots, "01");
  const pointOfInitiation =
    poiRaw === "11" ? "STATIC" : poiRaw === "12" ? "DYNAMIC" : "UNKNOWN";

  // Merchant account information: PromptPay tag 29, bill payment tag 30.
  const merchantRoot =
    find(roots, "29") ?? find(roots, "30") ?? find(roots, "26");
  const merchant = merchantRoot?.children ?? [];
  const aid = value(merchant, "00");
  const isBillPayment = merchantRoot?.tag === "30";

  let recipientType: RecipientType = "UNKNOWN";
  let recipientId = "";
  let recipientDisplay = "";

  if (isBillPayment) {
    recipientType = "BILLER";
    recipientId = value(merchant, "01") ?? "";
    recipientDisplay = recipientId ? `Biller ${recipientId}` : "";
  } else {
    const mobile = value(merchant, "01");
    const nationalId = value(merchant, "02");
    const ewallet = value(merchant, "03");
    if (mobile) {
      recipientType = "MOBILE";
      recipientId = mobile.replace(/\D/g, "");
      recipientDisplay = formatMobile(mobile);
    } else if (nationalId) {
      recipientType = "NATIONAL_ID";
      recipientId = nationalId.replace(/\D/g, "");
      recipientDisplay = formatNationalId(nationalId);
    } else if (ewallet) {
      recipientType = "EWALLET";
      recipientId = ewallet.replace(/\D/g, "");
      recipientDisplay = `e-Wallet ${recipientId}`;
    }
  }

  if (!recipientId) {
    issues.push({
      code: "MISSING_RECIPIENT",
      severity: "error",
      message:
        "No PromptPay recipient (mobile, national ID, e-wallet or biller) was found in the QR.",
    });
  }

  const amountRaw = value(roots, "54");
  let amountSatang: number | null = null;
  if (amountRaw !== null) {
    amountSatang = amountToSatang(amountRaw);
    if (amountSatang === null) {
      issues.push({
        code: "INVALID_AMOUNT",
        severity: "error",
        message: `The encoded amount "${amountRaw}" is not a valid THB amount.`,
      });
    }
  }

  const currency = value(roots, "53") ?? "";
  if (currency !== THB_CURRENCY_CODE) {
    issues.push({
      code: "UNSUPPORTED_PAYLOAD",
      severity: "error",
      message: `Transaction currency is "${currency || "missing"}" but this queue only supports THB (764).`,
    });
  }

  if (amountSatang === null) {
    issues.push({
      code: "MISSING_AMOUNT",
      severity: "warning",
      message:
        pointOfInitiation === "STATIC"
          ? "Static PromptPay QR — the payer enters the amount in the bank app."
          : "No amount encoded in the QR — confirm the amount in the bank app.",
    });
  }

  const additional = find(roots, "62")?.children ?? [];
  const referenceLabel = value(additional, "05");
  const billNumber = value(additional, "01");
  const reference1 = isBillPayment
    ? (value(merchant, "02") ?? billNumber)
    : (referenceLabel ?? billNumber);
  const reference2 = isBillPayment ? value(merchant, "03") : null;

  if (!reference1 && !reference2) {
    issues.push({
      code: "MISSING_REFERENCE",
      severity: "warning",
      message:
        "No reference label or bill number — duplicates can only be matched on recipient and amount.",
    });
  }

  const payment: PaymentInfo = {
    pointOfInitiation,
    recipientType,
    recipientId,
    recipientDisplay: recipientDisplay || recipientId || "Unknown recipient",
    merchantName: value(roots, "59"),
    merchantCity: value(roots, "60"),
    amountSatang,
    currency,
    reference1,
    reference2,
    referenceLabel,
    countryCode: value(roots, "58"),
    merchantCategoryCode: value(roots, "52"),
    aid,
    crcValid,
  };

  return {
    ok: !issues.some((issue) => issue.severity === "error"),
    payment,
    issues,
  };
}

/** FNV-1a 32-bit hash, used only to build a compact indexable duplicate key. */
export function hash32(input: string): string {
  let hash = 0x811c9dc5;
  for (let i = 0; i < input.length; i += 1) {
    hash ^= input.charCodeAt(i);
    hash = Math.imul(hash, 0x01000193) >>> 0;
  }
  return `00000000${hash.toString(16)}`.slice(-8);
}

export type ItemKeys = {
  /** Exact-payload duplicate key (identical QR content). */
  payloadKey: string;
  /** Softer duplicate key: recipient + amount + reference. Null when too weak. */
  referenceKey: string | null;
};

export function buildItemKeys(
  payload: string,
  payment: PaymentInfo | null,
): ItemKeys {
  const normalized = payload.trim();
  const payloadKey = `v1-${hash32(normalized)}-${normalized.length}`;

  if (!payment) return { payloadKey, referenceKey: null };

  const reference = payment.reference1 ?? payment.referenceLabel ?? "";
  if (!reference && payment.amountSatang === null) {
    return { payloadKey, referenceKey: null };
  }

  return {
    payloadKey,
    referenceKey: [
      payment.recipientType,
      payment.recipientId,
      payment.amountSatang ?? "any",
      reference,
    ].join("|"),
  };
}
