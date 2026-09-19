import jsQR from "jsqr";

/**
 * Browser-side image scanning.
 *
 * Everything in this module is local to the browser tab: images are read from
 * the file input, decoded in memory, and only the QR payload plus a small
 * hand-off copy ever leave the device.
 */

/** Decoding happens on a downscaled copy so a 400-image folder stays responsive. */
const MAX_DECODE_DIMENSION = 1600;
/** Retry resolution when the first pass finds nothing. */
const RETRY_DECODE_DIMENSION = 2600;
/** Larger files are stored re-encoded so the queue stays light. */
const MAX_STORED_BYTES = 3 * 1024 * 1024;

export type ScanFailureCode =
  | "INVALID_IMAGE"
  | "UNREADABLE_IMAGE"
  | "NO_QR_FOUND";

export type ScanResult =
  | { kind: "decoded"; payload: string; width: number; height: number }
  | {
      kind: "failed";
      code: ScanFailureCode;
      message: string;
      width: number;
      height: number;
    };

export type ImportedFile = {
  file: File;
  /** Folder-relative path when the user picked a directory. */
  relativePath: string;
};

const IMAGE_EXTENSIONS = [
  ".png",
  ".jpg",
  ".jpeg",
  ".webp",
  ".gif",
  ".bmp",
  ".heic",
  ".heif",
  ".avif",
];

export function looksLikeImage(file: File): boolean {
  if (file.type.startsWith("image/")) return true;
  const name = file.name.toLowerCase();
  return IMAGE_EXTENSIONS.some((extension) => name.endsWith(extension));
}

/** Filters a picker result down to images, preserving folder paths. */
export function collectImageFiles(files: FileList | File[]): ImportedFile[] {
  const list = Array.from(files as ArrayLike<File>);
  return list
    .filter(looksLikeImage)
    .map((file) => {
      const withPath = file as File & { webkitRelativePath?: string };
      return {
        file,
        relativePath: withPath.webkitRelativePath || file.name,
      };
    });
}

type LoadedImage = {
  source: CanvasImageSource;
  width: number;
  height: number;
  release: () => void;
};

async function loadImage(file: File): Promise<LoadedImage> {
  if (typeof createImageBitmap === "function") {
    const bitmap = await createImageBitmap(file);
    return {
      source: bitmap,
      width: bitmap.width,
      height: bitmap.height,
      release: () => bitmap.close(),
    };
  }

  const url = URL.createObjectURL(file);
  const image = new Image();
  await new Promise<void>((resolve, reject) => {
    image.onload = () => resolve();
    image.onerror = () => reject(new Error("The browser could not decode this image."));
    image.src = url;
  });
  return {
    source: image,
    width: image.naturalWidth,
    height: image.naturalHeight,
    release: () => URL.revokeObjectURL(url),
  };
}

function readPixels(
  image: LoadedImage,
  maxDimension: number,
): { data: Uint8ClampedArray; width: number; height: number } {
  const scale = Math.min(1, maxDimension / Math.max(image.width, image.height));
  const width = Math.max(1, Math.round(image.width * scale));
  const height = Math.max(1, Math.round(image.height * scale));

  const canvas =
    typeof OffscreenCanvas === "function"
      ? new OffscreenCanvas(width, height)
      : Object.assign(document.createElement("canvas"), { width, height });

  const context = canvas.getContext("2d") as
    | CanvasRenderingContext2D
    | OffscreenCanvasRenderingContext2D
    | null;
  if (!context) {
    throw new Error("Canvas 2D is unavailable in this browser.");
  }
  context.drawImage(image.source, 0, 0, width, height);
  const imageData = context.getImageData(0, 0, width, height);
  return { data: imageData.data, width, height };
}

/** Decodes one QR image. Never throws for unreadable images. */
export async function scanQrImage(file: File): Promise<ScanResult> {
  if (!looksLikeImage(file)) {
    return {
      kind: "failed",
      code: "INVALID_IMAGE",
      message: "Not an image file.",
      width: 0,
      height: 0,
    };
  }

  let image: LoadedImage;
  try {
    image = await loadImage(file);
  } catch (error) {
    return {
      kind: "failed",
      code: "UNREADABLE_IMAGE",
      message:
        error instanceof Error ? error.message : "The image could not be opened.",
      width: 0,
      height: 0,
    };
  }

  try {
    const attempts = [MAX_DECODE_DIMENSION, RETRY_DECODE_DIMENSION];
    for (const maxDimension of attempts) {
      const pixels = readPixels(image, maxDimension);
      const code = jsQR(pixels.data, pixels.width, pixels.height, {
        inversionAttempts: "attemptBoth",
      });
      if (code?.data) {
        return {
          kind: "decoded",
          payload: code.data,
          width: image.width,
          height: image.height,
        };
      }
    }
    return {
      kind: "failed",
      code: "NO_QR_FOUND",
      message: "No QR code was found in this image.",
      width: image.width,
      height: image.height,
    };
  } catch (error) {
    return {
      kind: "failed",
      code: "UNREADABLE_IMAGE",
      message:
        error instanceof Error ? error.message : "The image could not be read.",
      width: image.width,
      height: image.height,
    };
  } finally {
    image.release();
  }
}

/**
 * The copy of the image that gets stored with the queue item. Originals under
 * the size limit are kept byte-for-byte so nothing about the QR changes.
 */
export async function prepareImageForStorage(
  file: File,
): Promise<{ blob: Blob; recompressed: boolean }> {
  if (file.size <= MAX_STORED_BYTES) {
    return { blob: file, recompressed: false };
  }

  const image = await loadImage(file);
  try {
    const width = Math.min(image.width, MAX_DECODE_DIMENSION);
    const height = Math.max(1, Math.round((image.height / image.width) * width));
    const canvas =
      typeof OffscreenCanvas === "function"
        ? new OffscreenCanvas(width, height)
        : Object.assign(document.createElement("canvas"), { width, height });
    const context = canvas.getContext("2d") as
      | CanvasRenderingContext2D
      | OffscreenCanvasRenderingContext2D
      | null;
    if (!context) return { blob: file, recompressed: false };
    context.drawImage(image.source, 0, 0, width, height);
    const blob = await canvasToBlob(canvas);
    if (!blob) return { blob: file, recompressed: false };
    return { blob, recompressed: true };
  } catch {
    return { blob: file, recompressed: false };
  } finally {
    image.release();
  }
}

async function canvasToBlob(
  canvas: HTMLCanvasElement | OffscreenCanvas,
): Promise<Blob | null> {
  if (canvas instanceof HTMLCanvasElement) {
    return await new Promise((resolve) =>
      canvas.toBlob((blob) => resolve(blob), "image/jpeg", 0.85),
    );
  }
  return await canvas.convertToBlob({ type: "image/jpeg", quality: 0.85 });
}

/** Uploads bytes straight to Convex file storage with a signed URL. */
export async function uploadImageBlob(
  uploadUrl: string,
  blob: Blob,
): Promise<string> {
  const response = await fetch(uploadUrl, {
    method: "POST",
    headers: { "Content-Type": blob.type || "application/octet-stream" },
    body: blob,
  });
  if (!response.ok) {
    throw new Error(`Image upload failed (${response.status}).`);
  }
  const json = (await response.json()) as { storageId?: string };
  if (!json.storageId) {
    throw new Error("Image upload did not return a storage id.");
  }
  return json.storageId;
}
