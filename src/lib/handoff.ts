/**
 * Hand-off of a QR image to the user's banking app.
 *
 * The app never types a PIN, never touches an OTP, and never confirms a payment.
 * It puts the right QR image in front of the user and stops there.
 */

export type HandoffOutcome = {
  method: "share" | "download";
  message: string;
};

export async function handOffQrImage(
  imageUrl: string,
  fileName: string,
): Promise<HandoffOutcome> {
  let file: File | null = null;
  try {
    const response = await fetch(imageUrl);
    if (response.ok) {
      const blob = await response.blob();
      file = new File([blob], fileName, {
        type: blob.type || "image/png",
      });
    }
  } catch {
    file = null;
  }

  const shareData: ShareData = file
    ? {
        files: [file],
        title: "PromptPay QR",
        text: "Open this QR in your banking app — confirm the payment yourself.",
      }
    : { title: "PromptPay QR", url: imageUrl };

  if (typeof navigator !== "undefined" && navigator.canShare?.(shareData)) {
    try {
      await navigator.share(shareData);
      return {
        method: "share",
        message: "Shared the QR image. Complete and confirm the payment yourself in your bank app.",
      };
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") {
        return { method: "share", message: "Share cancelled. Nothing was sent." };
      }
    }
  }

  if (file) {
    const objectUrl = URL.createObjectURL(file);
    const link = document.createElement("a");
    link.href = objectUrl;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(objectUrl), 10_000);
    return {
      method: "download",
      message:
        "Saved the QR image to your device. Open it from your bank app and confirm the payment yourself.",
    };
  }

  return {
    method: "download",
    message: "Open the image link from your bank app and confirm the payment yourself.",
  };
}

export function openImageInNewTab(imageUrl: string) {
  window.open(imageUrl, "_blank", "noopener,noreferrer");
}
