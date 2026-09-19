import { authTables } from "@convex-dev/auth/server";
import { defineSchema, defineTable } from "convex/server";
import { Infer, v } from "convex/values";
import { paymentValidator } from "./paymentInfo";
import { issueValidator, queueStatusValidator } from "./queueStatus";

// default user roles. can add / remove based on the project as needed
export const ROLES = {
  ADMIN: "admin",
  USER: "user",
  MEMBER: "member",
} as const;

export const roleValidator = v.union(
  v.literal(ROLES.ADMIN),
  v.literal(ROLES.USER),
  v.literal(ROLES.MEMBER),
);
export type Role = Infer<typeof roleValidator>;

const schema = defineSchema(
  {
    // default auth tables using convex auth.
    ...authTables, // do not remove or modify

    // the users table is the default users table that is brought in by the authTables
    users: defineTable({
      name: v.optional(v.string()), // name of the user. do not remove
      image: v.optional(v.string()), // image of the user. do not remove
      email: v.optional(v.string()), // email of the user. do not remove
      emailVerificationTime: v.optional(v.number()), // email verification time. do not remove
      isAnonymous: v.optional(v.boolean()), // is the user anonymous. do not remove

      role: v.optional(roleValidator), // role of the user. do not remove
    }).index("email", ["email"]), // index for the email. do not remove or modify

    // One import run over a set of files or a folder.
    importBatches: defineTable({
      userId: v.id("users"),
      name: v.string(),
      source: v.union(v.literal("files"), v.literal("folder")),
      createdAt: v.number(),
    }).index("by_user_created", ["userId", "createdAt"]),

    // One QR payment candidate discovered from an image.
    queueItems: defineTable({
      userId: v.id("users"),
      batchId: v.id("importBatches"),
      fileName: v.string(),
      /** Folder-relative path when the user picked a whole folder. */
      sourcePath: v.optional(v.string()),
      fileSize: v.optional(v.number()),
      mimeType: v.optional(v.string()),
      /** Convex storage copy of the image used for bank hand-off. */
      storageId: v.optional(v.id("_storage")),
      /** True when the stored copy was re-encoded to keep storage sane. */
      imageRecompressed: v.optional(v.boolean()),

      payload: v.optional(v.string()),
      /** Hash key for exact-payload duplicate detection. */
      payloadKey: v.optional(v.string()),
      /** recipient + amount + reference key for softer duplicate detection. */
      referenceKey: v.optional(v.string()),

      status: queueStatusValidator,
      issues: v.array(issueValidator),
      payment: v.optional(paymentValidator),
      duplicateOf: v.optional(v.id("queueItems")),
      note: v.optional(v.string()),

      /** Every status change, in order. Only humans create settlement entries. */
      statusHistory: v.array(
        v.object({
          status: queueStatusValidator,
          at: v.number(),
          note: v.optional(v.string()),
        }),
      ),

      discoveredAt: v.number(),
      updatedAt: v.number(),
      submittedAt: v.optional(v.number()),
      settledAt: v.optional(v.number()),
    })
      .index("by_user", ["userId"])
      .index("by_batch", ["batchId"])
      .index("by_user_payloadKey", ["userId", "payloadKey"])
      .index("by_user_referenceKey", ["userId", "referenceKey"]),
  },
  {
    schemaValidation: false,
  },
);

export default schema;
