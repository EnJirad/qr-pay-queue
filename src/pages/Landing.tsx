import { StatusBadge } from "@/components/queue/status-badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import type { QueueStatus } from "@/convex/queueStatus";
import { motion } from "framer-motion";
import {
  ArrowRight,
  BadgeCheck,
  Braces,
  CheckCircle2,
  Copy,
  FileImage,
  FolderOpen,
  Layers,
  ListChecks,
  Lock,
  QrCode,
  ScanLine,
  Send,
  ShieldCheck,
  Sparkles,
  XCircle,
  type LucideIcon,
} from "lucide-react";
import { Link } from "react-router";

const WORKSPACE_URL = "/auth?returnTo=%2Fdashboard";

const fadeUp = {
  initial: { opacity: 0, y: 16 },
  whileInView: { opacity: 1, y: 0 },
  viewport: { once: true, margin: "-80px" },
  transition: { duration: 0.5, ease: [0.22, 1, 0.36, 1] as const },
};

function Nav() {
  return (
    <header className="sticky top-0 z-30 border-b border-border/70 bg-background/80 backdrop-blur">
      <div className="mx-auto flex w-full max-w-6xl items-center justify-between gap-4 px-5 py-3.5 sm:px-8">
        <Link to="/" className="flex items-center gap-2.5">
          <span className="flex size-9 items-center justify-center rounded-xl bg-primary text-primary-foreground">
            <QrCode className="size-4" />
          </span>
          <span className="text-sm font-semibold tracking-tight">
            QR Payment Queue
          </span>
        </Link>
        <nav className="hidden items-center gap-7 text-sm text-muted-foreground md:flex">
          <a className="transition-colors hover:text-foreground" href="#how">
            How it works
          </a>
          <a className="transition-colors hover:text-foreground" href="#safety">
            Safety
          </a>
          <a className="transition-colors hover:text-foreground" href="#roadmap">
            Roadmap
          </a>
        </nav>
        <div className="flex items-center gap-2">
          <Button
            variant="ghost"
            size="sm"
            className="cursor-pointer"
            asChild
          >
            <Link to="/auth?returnTo=%2Fdashboard">Sign in</Link>
          </Button>
          <Button size="sm" className="cursor-pointer gap-1.5" asChild>
            <Link to={WORKSPACE_URL}>
              Open workspace
              <ArrowRight className="size-3.5" />
            </Link>
          </Button>
        </div>
      </div>
    </header>
  );
}

const MOCK_ROWS: {
  file: string;
  recipient: string;
  amount: string;
  status: QueueStatus;
}[] = [
  {
    file: "order-1042-qr.png",
    recipient: "082-441-9930",
    amount: "฿1,250.00",
    status: "READY",
  },
  {
    file: "invoice-2201.png",
    recipient: "1-2045-88731-04-2",
    amount: "฿8,400.00",
    status: "VALIDATED",
  },
  {
    file: "order-1042-copy.png",
    recipient: "082-441-9930",
    amount: "฿1,250.00",
    status: "DUPLICATE",
  },
  {
    file: "biller-9982.png",
    recipient: "Biller 0994000158421",
    amount: "฿640.00",
    status: "SUCCESS",
  },
];

function QueuePreview() {
  return (
    <Card className="border-border/70 bg-card shadow-[0_1px_2px_rgba(16,24,40,0.04),0_24px_48px_-32px_rgba(16,24,40,0.35)]">
      <CardContent className="space-y-4 p-5">
        <div className="flex items-center justify-between">
          <div>
            <p className="text-[11px] tracking-wide text-muted-foreground uppercase">
              Payment queue
            </p>
            <p className="text-sm font-semibold">128 items · ฿42,180.00</p>
          </div>
          <span className="inline-flex items-center gap-1.5 rounded-full border border-border/70 px-2.5 py-1 text-[11px] text-muted-foreground">
            <ShieldCheck className="size-3.5 text-emerald-600" />
            Manual confirm
          </span>
        </div>
        <div className="space-y-2">
          {MOCK_ROWS.map((row) => (
            <div
              key={row.file}
              className="flex items-center justify-between gap-3 rounded-xl border border-border/60 bg-background/60 px-3 py-2.5"
            >
              <div className="flex min-w-0 items-center gap-2.5">
                <FileImage className="size-4 shrink-0 text-muted-foreground" />
                <div className="min-w-0">
                  <p className="truncate text-xs font-medium">{row.recipient}</p>
                  <p className="truncate text-[11px] text-muted-foreground">
                    {row.file}
                  </p>
                </div>
              </div>
              <div className="flex items-center gap-3">
                <span className="text-xs font-medium tabular-nums">
                  {row.amount}
                </span>
                <StatusBadge status={row.status} withHint={false} />
              </div>
            </div>
          ))}
        </div>
        <p className="text-[11px] text-muted-foreground">
          Illustration of the queue view — not real payment data.
        </p>
      </CardContent>
    </Card>
  );
}

function Hero() {
  return (
    <section className="relative overflow-hidden">
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 bg-[radial-gradient(60%_50%_at_15%_-10%,rgba(56,116,255,0.10),transparent),radial-gradient(45%_40%_at_95%_5%,rgba(16,185,129,0.10),transparent)]"
      />
      <div className="relative mx-auto grid w-full max-w-6xl gap-12 px-5 py-16 sm:px-8 sm:py-24 lg:grid-cols-[1.05fr_1fr] lg:items-center">
        <motion.div {...fadeUp} className="space-y-6">
          <span className="inline-flex items-center gap-2 rounded-full border border-border/70 bg-card px-3 py-1.5 text-[11px] font-medium tracking-wide text-muted-foreground uppercase">
            <Sparkles className="size-3.5 text-primary" />
            Built for Thai PromptPay collections
          </span>
          <h1 className="text-4xl leading-[1.08] font-semibold tracking-tight text-balance sm:text-5xl">
            Clear a folder of PromptPay QR codes without losing track of a single
            payment.
          </h1>
          <p className="max-w-xl text-base leading-relaxed text-muted-foreground">
            Drop in your payment screenshots. QR Payment Queue decodes each one
            locally, verifies the amount, recipient and reference, flags duplicates
            and unreadable codes, then walks you through the queue one payment at a
            time — you confirm every transaction yourself in your bank app.
          </p>
          <div className="flex flex-wrap items-center gap-3">
            <Button size="lg" className="cursor-pointer gap-2" asChild>
              <Link to={WORKSPACE_URL}>
                Open the workspace
                <ArrowRight className="size-4" />
              </Link>
            </Button>
            <Button
              size="lg"
              variant="outline"
              className="cursor-pointer gap-2"
              asChild
            >
              <a href="#how">
                <ListChecks className="size-4" />
                See how it works
              </a>
            </Button>
          </div>
          <div className="flex flex-wrap gap-x-6 gap-y-2 pt-2 text-xs text-muted-foreground">
            <span className="inline-flex items-center gap-1.5">
              <CheckCircle2 className="size-3.5 text-emerald-600" />
              EMVCo + CRC verification
            </span>
            <span className="inline-flex items-center gap-1.5">
              <CheckCircle2 className="size-3.5 text-emerald-600" />
              Duplicate detection
            </span>
            <span className="inline-flex items-center gap-1.5">
              <CheckCircle2 className="size-3.5 text-emerald-600" />
              No bank credentials, ever
            </span>
          </div>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 24 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6, delay: 0.1, ease: [0.22, 1, 0.36, 1] }}
        >
          <QueuePreview />
        </motion.div>
      </div>
    </section>
  );
}

const PIPELINE: { icon: LucideIcon; title: string; body: string }[] = [
  {
    icon: FolderOpen,
    title: "Import",
    body: "Pick a folder or a set of images from your device. Nothing leaves your browser until the QR is decoded.",
  },
  {
    icon: Copy,
    title: "Decode",
    body: "Every image is scanned for a QR code and the EMVCo payload is parsed into amount, recipient and reference.",
  },
  {
    icon: BadgeCheck,
    title: "Validate",
    body: "CRC checksum, currency, amount format, missing fields, duplicate QR payloads and duplicate references.",
  },
  {
    icon: Layers,
    title: "Queue",
    body: "Validated items land in a review queue you can filter by status, recipient, reference or import batch.",
  },
  {
    icon: Send,
    title: "Hand off",
    body: "Share or download the QR image, scan it in your bank app and confirm the payment yourself.",
  },
  {
    icon: ListChecks,
    title: "Record",
    body: "Log the outcome you saw in the bank app and let the summary show what is settled and what is still open.",
  },
];

function HowItWorks() {
  return (
    <section id="how" className="border-y border-border/70 bg-muted/25">
      <div className="mx-auto w-full max-w-6xl px-5 py-16 sm:px-8 sm:py-20">
        <motion.div {...fadeUp} className="max-w-2xl space-y-3">
          <p className="text-[11px] font-medium tracking-wide text-primary uppercase">
            The pipeline
          </p>
          <h2 className="text-3xl font-semibold tracking-tight">
            Six deliberate steps, and a human at the end of every one.
          </h2>
          <p className="text-sm leading-relaxed text-muted-foreground">
            The queue never guesses. Anything uncertain — a missing amount, an
            unreadable code, a repeat reference — is surfaced for you instead of
            being silently accepted.
          </p>
        </motion.div>

        <div className="mt-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {PIPELINE.map((step, index) => (
            <motion.div
              key={step.title}
              {...fadeUp}
              transition={{ duration: 0.45, delay: index * 0.05 }}
            >
              <Card className="h-full border-border/70 bg-card shadow-[0_1px_2px_rgba(16,24,40,0.04)]">
                <CardContent className="space-y-3 p-5">
                  <div className="flex items-center justify-between">
                    <span className="flex size-9 items-center justify-center rounded-xl bg-primary/10 text-primary">
                      <step.icon className="size-4" />
                    </span>
                    <span className="text-[11px] font-medium tabular-nums text-muted-foreground">
                      {String(index + 1).padStart(2, "0")}
                    </span>
                  </div>
                  <h3 className="text-sm font-semibold tracking-tight">
                    {step.title}
                  </h3>
                  <p className="text-sm leading-relaxed text-muted-foreground">
                    {step.body}
                  </p>
                </CardContent>
              </Card>
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  );
}

const FEATURES: { icon: LucideIcon; title: string; body: string }[] = [
  {
    icon: ScanLine,
    title: "Local QR decoding",
    body: "Images are scanned in the browser with a full QR decoder, including inverted and low-contrast codes.",
  },
  {
    icon: Braces,
    title: "PromptPay parsing",
    body: "Tag-length-value parsing for PromptPay mobile, national ID, e-wallet and bill payment payloads.",
  },
  {
    icon: ShieldCheck,
    title: "Integrity checks",
    body: "CRC-16 verification on every payload so a corrupted or edited QR is never queued as trustworthy.",
  },
  {
    icon: Copy,
    title: "Duplicate detection",
    body: "Identical payloads and repeated recipient-amount-reference combinations are flagged server-side.",
  },
  {
    icon: Layers,
    title: "Review workspace",
    body: "Filter by status group, search by recipient or reference, and correct a parsed amount when the QR is wrong.",
  },
  {
    icon: ListChecks,
    title: "Reconciliation summary",
    body: "Money-ready, money-on-hold and money-settled totals, plus a status breakdown across the whole workspace.",
  },
];

function Features() {
  return (
    <section className="mx-auto w-full max-w-6xl px-5 py-16 sm:px-8 sm:py-20">
      <motion.div {...fadeUp} className="max-w-2xl space-y-3">
        <p className="text-[11px] font-medium tracking-wide text-primary uppercase">
          Built for the boring part
        </p>
        <h2 className="text-3xl font-semibold tracking-tight">
          Everything that makes bulk PromptPay queues painful, handled.
        </h2>
      </motion.div>

      <div className="mt-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {FEATURES.map((feature, index) => (
          <motion.div
            key={feature.title}
            {...fadeUp}
            transition={{ duration: 0.45, delay: index * 0.04 }}
          >
            <Card className="h-full border-border/70 shadow-[0_1px_2px_rgba(16,24,40,0.04),0_12px_32px_-24px_rgba(16,24,40,0.25)]">
              <CardContent className="space-y-3 p-5">
                <span className="flex size-9 items-center justify-center rounded-xl border border-border/70 bg-muted/40 text-foreground">
                  <feature.icon className="size-4" />
                </span>
                <h3 className="text-sm font-semibold tracking-tight">
                  {feature.title}
                </h3>
                <p className="text-sm leading-relaxed text-muted-foreground">
                  {feature.body}
                </p>
              </CardContent>
            </Card>
          </motion.div>
        ))}
      </div>
    </section>
  );
}

function Safety() {
  return (
    <section id="safety" className="border-y border-border/70 bg-muted/25">
      <div className="mx-auto grid w-full max-w-6xl gap-8 px-5 py-16 sm:px-8 sm:py-20 lg:grid-cols-2">
        <motion.div {...fadeUp} className="space-y-4">
          <span className="inline-flex items-center gap-2 rounded-full border border-border/70 bg-card px-3 py-1.5 text-[11px] font-medium tracking-wide text-muted-foreground uppercase">
            <Lock className="size-3.5 text-emerald-600" />
            Payment safety
          </span>
          <h2 className="text-3xl font-semibold tracking-tight">
            It prepares the payment. It never makes one.
          </h2>
          <p className="text-sm leading-relaxed text-muted-foreground">
            Bulk payment tools go wrong when they start clicking through your bank
            app. This workspace deliberately stops at the edge of your banking
            security.
          </p>
          <Card className="border-rose-200/70 bg-rose-50/40">
            <CardContent className="space-y-2.5 p-5">
              <p className="text-sm font-semibold text-rose-800">
                Never implemented, by design
              </p>
              <ul className="space-y-2 text-sm text-rose-800/90">
                {[
                  "PIN, password, OTP or biometric entry",
                  "Accessibility-based blind clicking in banking apps",
                  "Hidden bank APIs or background transactions",
                  "Marking a payment as paid because a QR was opened",
                  "Automatic retry of an unknown transaction",
                ].map((item) => (
                  <li key={item} className="flex gap-2">
                    <XCircle className="mt-0.5 size-4 shrink-0" />
                    {item}
                  </li>
                ))}
              </ul>
            </CardContent>
          </Card>
        </motion.div>

        <motion.div {...fadeUp} className="space-y-4">
          <p className="text-[11px] font-medium tracking-wide text-muted-foreground uppercase">
            What it does instead
          </p>
          <ul className="space-y-3">
            {[
              {
                title: "Explicit status, never inferred",
                body: "An item moves through Discovered → Decoded → Validated → Ready, then the hand-off states. Only you can record Success, Reconciled or Paid.",
              },
              {
                title: "You stay in the bank app",
                body: "The workspace shares or saves the QR image so you can scan it and approve the transfer with your own credentials.",
              },
              {
                title: "No bank data stored",
                body: "No credentials, PINs, OTPs or card data are ever collected, and nothing is sent to a bank on your behalf.",
              },
              {
                title: "Honest about uncertainty",
                body: "Unverified outcomes stay in a problem state. They are never silently retried or counted as settled.",
              },
            ].map((item) => (
              <li
                key={item.title}
                className="rounded-xl border border-border/70 bg-card p-4 shadow-[0_1px_2px_rgba(16,24,40,0.04)]"
              >
                <p className="text-sm font-semibold tracking-tight">
                  {item.title}
                </p>
                <p className="mt-1 text-sm leading-relaxed text-muted-foreground">
                  {item.body}
                </p>
              </li>
            ))}
          </ul>
        </motion.div>
      </div>
    </section>
  );
}

const ROADMAP: { version: string; title: string; body: string; done: boolean }[] = [
  {
    version: "V0.1",
    title: "Workspace foundation",
    body: "Authentication, protected workspace, app shell and empty states.",
    done: true,
  },
  {
    version: "V0.2 – V0.4",
    title: "Import, discovery and decoding",
    body: "Folder and file import, image discovery and QR decoding in the browser.",
    done: true,
  },
  {
    version: "V0.5 – V0.7",
    title: "PromptPay parsing and validation",
    body: "EMVCo TLV parsing, CRC verification, duplicate QR and reference detection.",
    done: true,
  },
  {
    version: "V0.8",
    title: "Payment queue",
    body: "Filterable queue with per-item review, notes and amount corrections.",
    done: true,
  },
  {
    version: "V0.9",
    title: "Bank hand-off",
    body: "Share or download the QR image so the payment is confirmed by a human.",
    done: true,
  },
  {
    version: "V1.0",
    title: "Result tracking and summary",
    body: "Status history, settlement recording and workspace-level reporting.",
    done: true,
  },
  {
    version: "Next",
    title: "Bulk export and reconciliation",
    body: "CSV export of the queue and per-batch reconciliation against bank statements.",
    done: false,
  },
];

function Roadmap() {
  return (
    <section id="roadmap" className="mx-auto w-full max-w-6xl px-5 py-16 sm:px-8 sm:py-20">
      <motion.div {...fadeUp} className="max-w-2xl space-y-3">
        <p className="text-[11px] font-medium tracking-wide text-primary uppercase">
          Roadmap
        </p>
        <h2 className="text-3xl font-semibold tracking-tight">
          Shipped in this workspace, and what comes next.
        </h2>
        <p className="text-sm leading-relaxed text-muted-foreground">
          The original plan was a native Android app. This build delivers the full
          processing pipeline as a browser workspace first, so the QR logic is proven
          before it is wrapped in a mobile shell.
        </p>
      </motion.div>

      <div className="mt-10 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {ROADMAP.map((entry, index) => (
          <motion.div key={entry.version} {...fadeUp} transition={{ duration: 0.4, delay: index * 0.04 }}>
            <div className="flex h-full flex-col gap-2 rounded-xl border border-border/70 bg-card p-5 shadow-[0_1px_2px_rgba(16,24,40,0.04)]">
              <div className="flex items-center justify-between">
                <span className="text-[11px] font-semibold tracking-wide text-muted-foreground uppercase">
                  {entry.version}
                </span>
                {entry.done ? (
                  <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2 py-0.5 text-[11px] font-medium text-emerald-700 ring-1 ring-emerald-200 ring-inset">
                    <CheckCircle2 className="size-3" />
                    Shipped
                  </span>
                ) : (
                  <span className="rounded-full bg-muted px-2 py-0.5 text-[11px] font-medium text-muted-foreground">
                    Planned
                  </span>
                )}
              </div>
              <p className="text-sm font-semibold tracking-tight">
                {entry.title}
              </p>
              <p className="text-sm leading-relaxed text-muted-foreground">
                {entry.body}
              </p>
            </div>
          </motion.div>
        ))}
      </div>
    </section>
  );
}

function FinalCta() {
  return (
    <section className="border-t border-border/70 bg-primary text-primary-foreground">
      <div className="mx-auto flex w-full max-w-6xl flex-col items-start gap-6 px-5 py-16 sm:px-8 sm:py-20 lg:flex-row lg:items-center lg:justify-between">
        <div className="space-y-3">
          <h2 className="text-3xl font-semibold tracking-tight text-balance">
            Turn a messy QR folder into a queue you can actually finish.
          </h2>
          <p className="max-w-xl text-sm leading-relaxed text-primary-foreground/75">
            Sign in with your email, import your images and start clearing the queue.
            Nothing about your banking is automated.
          </p>
        </div>
        <Button
          size="lg"
          variant="secondary"
          className="cursor-pointer gap-2"
          asChild
        >
          <Link to={WORKSPACE_URL}>
            Open the workspace
            <ArrowRight className="size-4" />
          </Link>
        </Button>
      </div>
    </section>
  );
}

export default function Landing() {
  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{ duration: 0.4 }}
      className="flex min-h-screen flex-col bg-background"
    >
      <Nav />
      <Hero />
      <HowItWorks />
      <Features />
      <Safety />
      <Roadmap />
      <FinalCta />
      <footer className="border-t border-border/70 bg-background">
        <div className="mx-auto flex w-full max-w-6xl flex-col gap-2 px-5 py-8 text-xs text-muted-foreground sm:flex-row sm:items-center sm:justify-between sm:px-8">
          <p>
            QR Payment Queue — a PromptPay QR review workspace. Not affiliated with
            any bank.
          </p>
          <p>
            Never stores credentials, PINs or OTPs. Always confirm payments yourself.
          </p>
        </div>
      </footer>
    </motion.div>
  );
}
