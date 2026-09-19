import { v, type Infer } from "convex/values";

/** Mirrors `PaymentInfo` in `src/lib/promptpay.ts`. */
export const paymentValidator = v.object({
  pointOfInitiation: v.union(
    v.literal("STATIC"),
    v.literal("DYNAMIC"),
    v.literal("UNKNOWN"),
  ),
  recipientType: v.union(
    v.literal("MOBILE"),
    v.literal("NATIONAL_ID"),
    v.literal("EWALLET"),
    v.literal("BILLER"),
    v.literal("UNKNOWN"),
  ),
  recipientId: v.string(),
  recipientDisplay: v.string(),
  merchantName: v.union(v.string(), v.null()),
  merchantCity: v.union(v.string(), v.null()),
  /** Integer satang. null = the QR does not carry an amount. */
  amountSatang: v.union(v.number(), v.null()),
  currency: v.string(),
  reference1: v.union(v.string(), v.null()),
  reference2: v.union(v.string(), v.null()),
  referenceLabel: v.union(v.string(), v.null()),
  countryCode: v.union(v.string(), v.null()),
  merchantCategoryCode: v.union(v.string(), v.null()),
  aid: v.union(v.string(), v.null()),
  crcValid: v.boolean(),
});

export type PaymentRecord = Infer<typeof paymentValidator>;
