#!/usr/bin/env python3
"""Guards the Help & Support email flow.

These are the failure modes that would make the feature quietly wrong rather than
loudly broken, so each one is a hard failure:

  1. The support address drifts from the one the user specified.
  2. The report is addressed with ACTION_SEND instead of ACTION_SENDTO+mailto,
     which lets any text-sharing app satisfy the intent — the draft would land in a
     chat app instead of mail.
  3. The attachment stops being a FileProvider content:// URI, or the read grant is
     dropped, so the mail app receives an unusable file:// path (and Android kills
     the app for it on API 24+).
  4. The no-mail-app fallback is deleted, leaving a button that does nothing.
  5. A network call is added anywhere in the support path other than the one
     approved endpoint.
  6. The crash writer starts claiming it can see every failure.
  7. The diagnostics stop saying "unavailable" and start inventing values.

Runs as part of CI and the release gate.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/java/com/gamershub"
DIAG = SRC / "core/SupportDiagnostics.kt"
MAIL = SRC / "core/SupportMail.kt"
SHEET = SRC / "ui/sheets/HelpSheets.kt"
WATCH = SRC / "core/StartupWatch.kt"

EXPECTED_ADDRESS = "official.hub.000@gmail.com"
APPROVED_ENDPOINT = "https://ipwho.is/"


def check():
    problems = []
    diag = DIAG.read_text()
    mail = MAIL.read_text()
    sheet = SHEET.read_text()
    watch = WATCH.read_text()

    # 1 ---------------------------------------------------------------- address
    if f'"{EXPECTED_ADDRESS}"' not in diag:
        problems.append(
            f"SupportDiagnostics.SUPPORT_EMAIL is not {EXPECTED_ADDRESS!r} — reports would "
            f"go to the wrong mailbox"
        )
    # The address must not be duplicated in several places where it could drift.
    literal_count = sum(
        1 for text in (diag, mail, sheet) if f'"{EXPECTED_ADDRESS}"' in text
    )
    if literal_count > 1:
        problems.append(
            f"the support address is hard-coded in {literal_count} files; it must live only "
            f"in SupportDiagnostics.SUPPORT_EMAIL so it cannot drift"
        )

    # 2 ---------------------------------------------------------------- intent
    if "ACTION_SENDTO" not in mail:
        problems.append(
            "SupportMail does not use Intent.ACTION_SENDTO — with ACTION_SEND any chat or "
            "notes app can satisfy the intent and the draft never reaches an inbox"
        )
    if 'mailto:' not in mail:
        problems.append("SupportMail has no mailto: URI — only mail clients can handle it")
    # Precise: the draft intent must be built on SENDTO. Substring-safe, because
    # "ACTION_SENDTO" contains "ACTION_SEND".
    send_intents = re.findall(r'Intent\((Intent\.ACTION_[A-Z_]+)', mail)
    if send_intents and any(a == "Intent.ACTION_SEND" for a in send_intents):
        problems.append(
            "the report intent uses Intent.ACTION_SEND, which any text-sharing app can "
            "satisfy — the draft would not necessarily reach a mail client"
        )
    if "Intent(Intent.ACTION_SENDTO" not in mail:
        problems.append("no Intent(Intent.ACTION_SENDTO, mailto:) draft is constructed")

    # 3 ---------------------------------------------------------------- attachment
    if "FileProvider.getUriForFile" not in mail:
        problems.append(
            "the attachment is not a FileProvider content:// URI — a file:// path in an "
            "intent is a FileUriExposedException on API 24+"
        )
    if "FLAG_GRANT_READ_URI_PERMISSION" not in mail:
        problems.append("the attachment intent carries no temporary read grant")
    if "FLAG_GRANT_PERSISTABLE_URI_PERMISSION" in mail:
        problems.append(
            "a persisted read grant would let the mail app read the report forever; it "
            "must be a one-hand-off grant"
        )

    # 4 ---------------------------------------------------------------- fallback
    for needle, why in (
        ("NoMailApp", "the no-email-app fallback result was removed"),
        ("COPY EMAIL ADDRESS", "the copy-address button was removed"),
        ("NOT sent", "the sheet no longer states that the report was not sent"),
    ):
        if needle not in (mail + sheet):
            problems.append(f"{why} ({needle!r} missing)")

    # 5 ---------------------------------------------------------------- network
    endpoints = set(re.findall(r'https?://[^\s"\'()]+', diag + mail))
    unapproved = {e for e in endpoints if not e.startswith(APPROVED_ENDPOINT.rstrip("/"))
                  and "schemas.android.com" not in e}
    if unapproved:
        problems.append(
            "unapproved network endpoint(s) in the support path: " + ", ".join(sorted(unapproved)) +
            f" — the only approved service is {APPROVED_ENDPOINT}"
        )
    if APPROVED_ENDPOINT.rstrip("/") not in diag:
        problems.append(f"the approved IP service {APPROVED_ENDPOINT} is no longer referenced")

    # 6 ---------------------------------------------------------------- honesty
    if "does not" not in watch or "every" not in sheet.lower():
        # Both the watch and the sheet must state the limit. Cheap shape check.
        problems.append(
            "the limitation wording ('not every failure can be detected') is missing from "
            "StartupWatch/HelpSheets — the feature must not imply total coverage"
        )
    # Bare placeholders written into the report itself. `!= "unknown"` in the guard is
    # fine — that is filtering a bad platform value out, not printing it.
    for invented in re.findall(r'appendLine\([^)]*"(unknown|n/a|N/A)"', diag):
        problems.append(f"diagnostics print {invented!r} instead of the literal 'unavailable'")
    if "unavailable" not in diag:
        problems.append("diagnostics never report 'unavailable' — missing data is being invented")

    # 7 ---------------------------------------------------------------- privacy
    for forbidden in ("getInstalledPackages", "getInstalledApplications", "ContactsContract",
                      "Browser.BOOKMARKS_URI", "READ_SMS", "getAccounts"):
        if forbidden in diag or forbidden in mail:
            problems.append(
                f"the support path touches {forbidden} — it must not read other apps, "
                f"messages, accounts or browsing data"
            )
    if "sanitize(" not in diag:
        problems.append("the log tail is no longer sanitized before it is attached")
    for pattern in ("pass(word)?", "token", "api[_-]?key"):
        if pattern not in diag:
            problems.append(f"sanitize() no longer redacts {pattern!r}")

    # 8 ---------------------------------------------------------------- retention
    if "pruneOld" not in diag:
        problems.append("report files are no longer pruned — they would accumulate forever")
    if "cacheDir" not in diag:
        problems.append(
            "reports are not written to the cache dir; they belong in private cache, not "
            "files, so the OS can reclaim them"
        )

    return problems


def main():
    problems = check()
    if problems:
        print("FAIL: Help & Support email flow is not safe to ship")
        for p in problems:
            print("  - " + p)
        return 1
    print("PASS: Help & Support builds a mailto draft with a FileProvider attachment, "
          "falls back honestly, and contacts only the approved endpoint")
    return 0


if __name__ == "__main__":
    sys.exit(main())
