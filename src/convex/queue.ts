import { getAuthUserId } from "@convex-dev/auth/server";
import { v } from "convex/values";
import { mutation, query, type MutationCtx, type QueryCtx } from "./_generated/server";
import type { Doc, Id } from "./_generated/dataModel";
import { paymentValidator } from "./paymentInfo";
import {
  HANDOFF_STATUSES,
  MANUAL_ONLY_STATUSES,
  SETTLED_STATUSES,
  issueValidator,
  queueStatusValidator,
  type QueueStatus,
} from "./queueStatus";

/**
 * Queue storage for the QR Payment Queue workspace.
 *
 * Every function is scoped to the signed-in user and refuses to touch rows that
 * belong to somebody else. Nothing here talks to a bank: the queue only records
 * what the user discovered and what the user confirmed.
 */

const MAX_LIST_LIMIT = 1000;
const SUMMARY_SCAN_LIMIT = 5000;

const itemInputValidator = v.object({
  fileName: v.string(),
  sourcePath: v.optional(v.string()),
  fileSize: v.optional(v.number()),
  mimeType: v.optional(v.string()),
  storageId: v.optional(v.id("_storage")),
  imageRecompressed: v.optional(v.boolean()),
  payload: v.optional(v.string()),
  payloadKey: v.optional(v.string()),
  referenceKey: v.optional(v.string()),
  status: queueStatusValidator,
  issues: v.array(issueValidator),
  payment: v.optional(paymentValidator),
});

async function requireUserId(ctx: QueryCtx | MutationCtx): Promise<Id<"users">> {
  const userId = await getAuthUserId(ctx);
  if (userId === null) {
    throw new Error("Not signed in.");
  }
  return userId;
}

async function requireOwnedBatch(
  ctx: MutationCtx,
  batchId: Id<"importBatches">,
  userId: Id<"users">,
) {
  const batch = await ctx.db.get(batchId);
  if (!batch || batch.userId !== userId) {
    throw new Error("Import batch not found.");
  }
  return batch;
}

/** Creates the import run that a group of images belongs to. */
export const createBatch = mutation({
  args: {
    name: v.string(),
    source: v.union(v.literal("files"), v.literal("folder")),
  },
  handler: async (ctx, args) => {
    const userId = await requireUserId(ctx);
    const name = args.name.trim().slice(0, 120) || "Import";
    return await ctx.db.insert("importBatches", {
      userId,
      name,
      source: args.source,
      createdAt: Date.now(),
    });
  },
});

/** Signed URL the browser can POST image bytes to. */
export const generateImageUploadUrl = mutation({
  args: {},
  handler: async (ctx) => {
    await requireUserId(ctx);
    return await ctx.storage.generateUploadUrl();
  },
});

/**
 * Stores one discovered image in the queue.
 *
 * Duplicate detection happens here (server side) so two imports running in
 * parallel cannot both claim a QR is new. An item is never marked paid: the
 * worst the server does is flag it DUPLICATE or INVALID.
 */
export const addItem = mutation({
  args: {
    batchId: v.id("importBatches"),
    item: itemInputValidator,
  },
  handler: async (ctx, args) => {
    const userId = await requireUserId(ctx);
    await requireOwnedBatch(ctx, args.batchId, userId);

    const { item } = args;
    const issues = [...item.issues];
    let status: QueueStatus = item.status;
    let duplicateOf: Id<"queueItems"> | undefined;

    if (item.payloadKey) {
      const exact = await ctx.db
        .query("queueItems")
        .withIndex("by_user_payloadKey", (q) =>
          q.eq("userId", userId).eq("payloadKey", item.payloadKey),
        )
        .first();
      if (exact && exact.payload === item.payload && exact.status !== "INVALID") {
        status = "DUPLICATE";
        duplicateOf = exact._id;
        issues.push({
          code: "DUPLICATE_QR",
          severity: "warning",
          message: `Identical QR already in the queue as "${exact.fileName}".`,
        });
      }
    }

    if (!duplicateOf && item.referenceKey && status !== "INVALID") {
      const sameReference = await ctx.db
        .query("queueItems")
        .withIndex("by_user_referenceKey", (q) =>
          q.eq("userId", userId).eq("referenceKey", item.referenceKey),
        )
        .first();
      if (sameReference && sameReference.status !== "INVALID") {
        status = "DUPLICATE";
        duplicateOf = sameReference._id;
        issues.push({
          code: "DUPLICATE_REFERENCE",
          severity: "warning",
          message: `Same recipient, amount and reference as "${sameReference.fileName}".`,
        });
      }
    }

    const now = Date.now();
    const itemId = await ctx.db.insert("queueItems", {
      userId,
      batchId: args.batchId,
      fileName: item.fileName,
      sourcePath: item.sourcePath,
      fileSize: item.fileSize,
      mimeType: item.mimeType,
      storageId: item.storageId,
      imageRecompressed: item.imageRecompressed,
      payload: item.payload,
      payloadKey: item.payloadKey,
      referenceKey: item.referenceKey,
      status,
      issues,
      payment: item.payment,
      duplicateOf,
      statusHistory: [{ status, at: now }],
      discoveredAt: now,
      updatedAt: now,
    });

    return { itemId, status, duplicateOf: duplicateOf ?? null };
  },
});

/** Queue rows for the table, newest first. Filters are applied server side. */
export const listItems = query({
  args: {
    batchId: v.optional(v.id("importBatches")),
    statuses: v.optional(v.array(queueStatusValidator)),
    search: v.optional(v.string()),
    limit: v.optional(v.number()),
  },
  handler: async (ctx, args) => {
    const userId = await requireUserId(ctx);
    const limit = Math.min(Math.max(args.limit ?? 300, 1), MAX_LIST_LIMIT);
    const search = args.search?.trim().toLowerCase() ?? "";
    const statusFilter = args.statuses?.length ? new Set(args.statuses) : null;

    const source = args.batchId
      ? ctx.db
          .query("queueItems")
          .withIndex("by_batch", (q) => q.eq("batchId", args.batchId!))
      : ctx.db
          .query("queueItems")
          .withIndex("by_user", (q) => q.eq("userId", userId));

    const rows: Doc<"queueItems">[] = [];
    for await (const row of source.order("desc")) {
      if (row.userId !== userId) continue;
      if (statusFilter && !statusFilter.has(row.status)) continue;
      if (search) {
        const haystack = [
          row.fileName,
          row.sourcePath ?? "",
          row.payment?.recipientDisplay ?? "",
          row.payment?.recipientId ?? "",
          row.payment?.merchantName ?? "",
          row.payment?.reference1 ?? "",
          row.payment?.referenceLabel ?? "",
        ]
          .join(" ")
          .toLowerCase();
        if (!haystack.includes(search)) continue;
      }
      rows.push(row);
      if (rows.length >= limit) break;
    }

    return rows.map((row) => ({
      _id: row._id,
      _creationTime: row._creationTime,
      batchId: row.batchId,
      fileName: row.fileName,
      sourcePath: row.sourcePath ?? null,
      status: row.status,
      issues: row.issues,
      payment: row.payment ?? null,
      hasImage: row.storageId !== undefined,
      discoveredAt: row.discoveredAt,
      updatedAt: row.updatedAt,
      submittedAt: row.submittedAt ?? null,
      settledAt: row.settledAt ?? null,
      note: row.note ?? null,
    }));
  },
});

/** One item plus a downloadable URL for its image (for bank hand-off). */
export const getItem = query({
  args: { itemId: v.id("queueItems") },
  handler: async (ctx, args) => {
    const userId = await requireUserId(ctx);
    const item = await ctx.db.get(args.itemId);
    if (!item || item.userId !== userId) return null;

    const imageUrl = item.storageId
      ? await ctx.storage.getUrl(item.storageId)
      : null;

    const duplicateOf = item.duplicateOf
      ? await ctx.db.get(item.duplicateOf)
      : null;

    return {
      item,
      imageUrl,
      duplicateOf:
        duplicateOf && duplicateOf.userId === userId
          ? {
              _id: duplicateOf._id,
              fileName: duplicateOf.fileName,
              status: duplicateOf.status,
            }
          : null,
    };
  },
});

/** Counts and money totals for the summary header + batch switcher. */
export const workspaceSummary = query({
  args: {},
  handler: async (ctx) => {
    const userId = await requireUserId(ctx);

    const byStatus = new Map<QueueStatus, number>();
    let amountReadySatang = 0;
    let amountSettledSatang = 0;
    let amountNeedsReviewSatang = 0;
    let scanned = 0;
    let truncated = false;

    const batchCounts = new Map<string, number>();

    for await (const row of ctx.db
      .query("queueItems")
      .withIndex("by_user", (q) => q.eq("userId", userId))) {
      if (scanned >= SUMMARY_SCAN_LIMIT) {
        truncated = true;
        break;
      }
      scanned += 1;
      byStatus.set(row.status, (byStatus.get(row.status) ?? 0) + 1);
      batchCounts.set(row.batchId, (batchCounts.get(row.batchId) ?? 0) + 1);

      const amount = row.payment?.amountSatang ?? 0;
      if (SETTLED_STATUSES.includes(row.status)) {
        amountSettledSatang += amount;
      } else if (row.status === "READY") {
        amountReadySatang += amount;
      } else if (
        row.status === "DISCOVERED" ||
        row.status === "DECODED" ||
        row.status === "VALIDATED"
      ) {
        amountNeedsReviewSatang += amount;
      }
    }

    const batches = await ctx.db
      .query("importBatches")
      .withIndex("by_user_created", (q) => q.eq("userId", userId))
      .order("desc")
      .take(50);

    return {
      total: scanned,
      truncated,
      byStatus: Object.fromEntries(byStatus) as Partial<Record<QueueStatus, number>>,
      amountReadySatang,
      amountSettledSatang,
      amountNeedsReviewSatang,
      batches: batches.map((batch) => ({
        _id: batch._id,
        name: batch.name,
        source: batch.source,
        createdAt: batch.createdAt,
        itemCount: batchCounts.get(batch._id) ?? 0,
      })),
    };
  },
});

/**
 * Records an explicit status change. Pipeline states come from processing;
 * settlement states are only ever chosen by the user after they checked the
 * payment in their banking app.
 */
export const updateStatus = mutation({
  args: {
    itemId: v.id("queueItems"),
    status: queueStatusValidator,
    note: v.optional(v.string()),
  },
  handler: async (ctx, args) => {
    const userId = await requireUserId(ctx);
    const item = await ctx.db.get(args.itemId);
    if (!item || item.userId !== userId) {
      throw new Error("Queue item not found.");
    }

    const now = Date.now();
    const note = args.note?.trim().slice(0, 500) || undefined;
    const patch: Partial<Doc<"queueItems">> = {
      status: args.status,
      updatedAt: now,
      statusHistory: [...item.statusHistory, { status: args.status, at: now, note }],
    };
    if (HANDOFF_STATUSES.includes(args.status)) {
      patch.submittedAt = item.submittedAt ?? now;
    }
    if (SETTLED_STATUSES.includes(args.status)) {
      patch.settledAt = now;
    }
    if (args.status === "READY") {
      patch.submittedAt = undefined;
      patch.settledAt = undefined;
    }

    await ctx.db.patch(args.itemId, patch);
    return { status: args.status, manualOnly: MANUAL_ONLY_STATUSES.includes(args.status) };
  },
});

/** Manual correction of parsed data (e.g. a wrong amount) plus a note. */
export const updateItem = mutation({
  args: {
    itemId: v.id("queueItems"),
    amountSatang: v.optional(v.union(v.number(), v.null())),
    note: v.optional(v.string()),
  },
  handler: async (ctx, args) => {
    const userId = await requireUserId(ctx);
    const item = await ctx.db.get(args.itemId);
    if (!item || item.userId !== userId) {
      throw new Error("Queue item not found.");
    }

    const patch: Partial<Doc<"queueItems">> = { updatedAt: Date.now() };
    if (args.amountSatang !== undefined) {
      if (!item.payment) {
        throw new Error("This item has no parsed payment information to correct.");
      }
      patch.payment = { ...item.payment, amountSatang: args.amountSatang };
    }
    if (args.note !== undefined) {
      patch.note = args.note.trim().slice(0, 500) || undefined;
    }

    await ctx.db.patch(args.itemId, patch);
  },
});

/** Deletes an item and its stored image copy. */
export const deleteItem = mutation({
  args: { itemId: v.id("queueItems") },
  handler: async (ctx, args) => {
    const userId = await requireUserId(ctx);
    const item = await ctx.db.get(args.itemId);
    if (!item || item.userId !== userId) {
      throw new Error("Queue item not found.");
    }
    if (item.storageId) {
      await ctx.storage.delete(item.storageId);
    }
    await ctx.db.delete(args.itemId);
  },
});

/** Deletes an import batch, every item in it, and their stored images. */
export const deleteBatch = mutation({
  args: { batchId: v.id("importBatches") },
  handler: async (ctx, args) => {
    const userId = await requireUserId(ctx);
    await requireOwnedBatch(ctx, args.batchId, userId);

    const items = await ctx.db
      .query("queueItems")
      .withIndex("by_batch", (q) => q.eq("batchId", args.batchId))
      .collect();

    for (const item of items) {
      if (item.userId !== userId) continue;
      if (item.storageId) {
        await ctx.storage.delete(item.storageId);
      }
      await ctx.db.delete(item._id);
    }
    await ctx.db.delete(args.batchId);
  },
});
