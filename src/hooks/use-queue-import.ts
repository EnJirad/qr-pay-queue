import { api } from "@/convex/_generated/api";
import type { Id } from "@/convex/_generated/dataModel";
import type { Issue, QueueStatus } from "@/convex/queueStatus";
import { buildItemKeys, parsePromptPayPayload } from "@/lib/promptpay";
import {
  collectImageFiles,
  prepareImageForStorage,
  scanQrImage,
  uploadImageBlob,
  type ImportedFile,
} from "@/lib/qr-scan";
import { useMutation } from "convex/react";
import { useCallback, useRef, useState } from "react";

export type ImportPhase =
  | "idle"
  | "scanning"
  | "finishing"
  | "finished"
  | "cancelled";

export type ImportState = {
  phase: ImportPhase;
  total: number;
  processed: number;
  saved: number;
  decoded: number;
  invalid: number;
  duplicates: number;
  warnings: number;
  currentFile: string | null;
  batchId: Id<"importBatches"> | null;
  errors: string[];
};

const INITIAL_STATE: ImportState = {
  phase: "idle",
  total: 0,
  processed: 0,
  saved: 0,
  decoded: 0,
  invalid: 0,
  duplicates: 0,
  warnings: 0,
  currentFile: null,
  batchId: null,
  errors: [],
};

function decideStatus(issues: Issue[]): QueueStatus {
  if (issues.some((issue) => issue.severity === "error")) return "INVALID";
  if (issues.some((issue) => issue.severity === "warning")) return "VALIDATED";
  return "READY";
}

function batchName(files: ImportedFile[], source: "files" | "folder"): string {
  if (source === "folder") {
    const first = files[0]?.relativePath ?? "";
    const folder = first.split("/")[0];
    if (folder) return folder;
  }
  return `Import ${new Date().toLocaleString(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  })}`;
}

const yieldToUi = () => new Promise<void>((resolve) => setTimeout(resolve, 0));

/**
 * Runs the local import: decode each image, parse the PromptPay payload, keep a
 * hand-off copy in storage, then record the queue row.
 *
 * The QR payload is parsed on the client; only the payload text, the parsed
 * facts and the image copy are sent to the server.
 */
export function useQueueImport() {
  const createBatch = useMutation(api.queue.createBatch);
  const addItem = useMutation(api.queue.addItem);
  const generateImageUploadUrl = useMutation(api.queue.generateImageUploadUrl);

  const [state, setState] = useState<ImportState>(INITIAL_STATE);
  const cancelled = useRef(false);

  const reset = useCallback(() => {
    cancelled.current = false;
    setState(INITIAL_STATE);
  }, []);

  const cancel = useCallback(() => {
    cancelled.current = true;
  }, []);

  const run = useCallback(
    async (input: FileList | File[], source: "files" | "folder") => {
      const files = collectImageFiles(input);
      if (files.length === 0) {
        setState({
          ...INITIAL_STATE,
          phase: "finished",
          errors: ["No image files were found in that selection."],
        });
        return;
      }

      cancelled.current = false;
      const errors: string[] = [];
      const progress: ImportState = {
        ...INITIAL_STATE,
        phase: "scanning",
        total: files.length,
        errors,
      };
      setState({ ...progress });

      let batchId: Id<"importBatches"> | null = null;
      try {
        batchId = await createBatch({ name: batchName(files, source), source });
      } catch (error) {
        setState({
          ...progress,
          phase: "finished",
          errors: [
            error instanceof Error
              ? `Could not create the import: ${error.message}`
              : "Could not create the import.",
          ],
        });
        return;
      }
      progress.batchId = batchId;
      setState({ ...progress });

      for (const { file, relativePath } of files) {
        if (cancelled.current) {
          setState({ ...progress, phase: "cancelled", currentFile: null });
          return;
        }

        progress.currentFile = relativePath;
        setState({ ...progress });

        try {
          const scan = await scanQrImage(file);

          const issues: Issue[] =
            scan.kind === "decoded"
              ? []
              : [
                  {
                    code: scan.code,
                    severity: "error",
                    message: scan.message,
                  },
                ];

          const parse =
            scan.kind === "decoded"
              ? parsePromptPayPayload(scan.payload)
              : { ok: false, payment: null, issues: [] as Issue[] };

          const allIssues = [...issues, ...parse.issues];
          const payment = parse.payment;
          const payload = scan.kind === "decoded" ? scan.payload : null;
          const keys = buildItemKeys(payload ?? "", payment);
          const status = decideStatus(allIssues);

          const { blob, recompressed } = await prepareImageForStorage(file);
          const uploadUrl = await generateImageUploadUrl({});
          const storageId = await uploadImageBlob(uploadUrl, blob);

          const result = await addItem({
            batchId,
            item: {
              fileName: file.name,
              sourcePath: relativePath,
              fileSize: file.size,
              mimeType: blob.type || file.type || undefined,
              storageId: storageId as Id<"_storage">,
              imageRecompressed: recompressed,
              payload: payload ?? undefined,
              payloadKey: payload ? keys.payloadKey : undefined,
              referenceKey: keys.referenceKey ?? undefined,
              status,
              issues: allIssues,
              payment: payment ?? undefined,
            },
          });

          progress.saved += 1;
          if (scan.kind === "decoded" && parse.ok) progress.decoded += 1;
          if (result.status === "DUPLICATE") progress.duplicates += 1;
          else if (result.status === "INVALID") progress.invalid += 1;
          else if (result.status === "VALIDATED") progress.warnings += 1;
        } catch (error) {
          if (errors.length < 5) {
            errors.push(
              `${file.name}: ${
                error instanceof Error ? error.message : "processing failed"
              }`,
            );
          }
        }

        progress.processed += 1;
        setState({ ...progress });
        await yieldToUi();
      }

      progress.currentFile = null;
      setState({ ...progress, phase: "finishing" });
      await new Promise((resolve) => setTimeout(resolve, 250));
      setState({ ...progress, phase: "finished" });
    },
    [addItem, createBatch, generateImageUploadUrl],
  );

  return { state, run, reset, cancel };
}
