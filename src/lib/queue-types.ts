import type { api } from "@/convex/_generated/api";
import type { FunctionReturnType } from "convex/server";

export type QueueListItem = FunctionReturnType<
  typeof api.queue.listItems
>[number];

export type QueueItemDetail = NonNullable<
  FunctionReturnType<typeof api.queue.getItem>
>;

export type WorkspaceSummaryResult = FunctionReturnType<
  typeof api.queue.workspaceSummary
>;

export type BatchSummary = WorkspaceSummaryResult["batches"][number];
