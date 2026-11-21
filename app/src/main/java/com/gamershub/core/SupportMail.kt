package com.gamershub.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands a support report to the user's own email app.
 *
 * ## The rule
 *
 * GamersHub never sends mail. There is no SMTP client, no server, no credential, and
 * nothing in this file can transmit anything by itself. It builds an [Intent.ACTION_SENDTO]
 * draft and hands it to an app the user already trusts. The draft lands in that app's
 * composer, where the user reads it and taps send — or deletes it. That is the whole
 * contract, and it is enforced structurally: `ACTION_SENDTO` with a `mailto:` URI cannot
 * be delivered to anything but a mail client, so no other app can silently absorb it.
 *
 * ## Why ACTION_SENDTO and not ACTION_SEND
 *
 * `ACTION_SEND` with `text/plain` is satisfied by *every* app that shares text, so
 * Gmail-first ordering only reorders a chooser that still includes chat and notes apps.
 * `ACTION_SENDTO` + `mailto:` is answered only by mail apps, which makes "prefer Gmail,
 * otherwise let them choose" actually mean that.
 *
 * ## Fallback
 *
 * A device with no mail app at all is common on tablets and on freshly-reset phones.
 * Detecting it *before* showing a composer is the difference between an honest "we
 * could not open a mail app, here is the address" and a button that appears to do
 * nothing. See [NoMailApp].
 */
object SupportMail {

    /** Gmail's package, and the only client that is offered without a chooser. */
    private const val GMAIL_PACKAGE = "com.google.android.gm"

    /** Subject line prefix, so reports are filterable in the receiving mailbox. */
    const val SUBJECT_FEEDBACK = "GamersHub feedback"
    const val SUBJECT_PROBLEM = "GamersHub problem report"

    sealed interface Result {
        /** A mail app was launched with the draft. */
        data class Opened(val appLabel: String) : Result

        /** Nothing on this device can handle `mailto:`. Show the address instead. */
        data class NoMailApp(val address: String, val reason: String) : Result

        /** A mail app was launched but refused the attachment. Text-only draft was sent. */
        data class OpenedWithoutAttachment(val appLabel: String, val reason: String) : Result
    }

    /**
     * Opens a draft addressed to [SupportDiagnostics.SUPPORT_EMAIL].
     *
     * @param file the diagnostics file to attach, or null to send text only.
     * @param body the report body (a short summary; the detail travels in the file).
     * @param preferGmail launch Gmail directly when it is installed. When it is not, a
     *   chooser is shown so the user picks their own client rather than being handed
     *   whichever app the system ranked first.
     */
    fun openDraft(
        context: Context,
        subject: String,
        body: String,
        file: File?,
    ): Result {
        val address = SupportDiagnostics.SUPPORT_EMAIL
        val mailTo = Uri.parse("mailto:" + Uri.encode(address))

        val candidates = mailApps(context)
        if (candidates.isEmpty()) {
            return Result.NoMailApp(
                address,
                "no app on this device handles mailto: — the report was NOT sent"
            )
        }

        // Attachment first: a URI only this app can mint, with a read grant that lasts
        // for this one hand-off. Nothing else can read the file, and the grant is not
        // persisted, so the mail app loses access once it is done with it.
        val uri: Uri? = file?.let { f ->
            runCatching {
                FileProvider.getUriForFile(context, context.packageName + ".files", f)
            }.getOrNull()
        }
        val attachReason = when {
            file == null -> "no attachment was requested"
            uri == null -> "the diagnostic file could not be shared"
            else -> ""
        }

        val gmail = candidates.firstOrNull { it.activityInfo.packageName == GMAIL_PACKAGE }
        val withAttachment = intentFor(mailTo, subject, body, uri)
        val textOnly = intentFor(mailTo, subject, body, null)

        // 1. Gmail directly, when present. Still a draft in Gmail's composer.
        if (gmail != null && uri != null) {
            val r = tryLaunch(context, withAttachment, gmail)
            if (r != null) return Result.Opened(r)
            // Gmail is installed but would not take the attachment: fall back to a
            // text-only draft rather than failing the whole flow.
            val r2 = tryLaunch(context, textOnly, gmail)
            if (r2 != null) {
                return Result.OpenedWithoutAttachment(r2, "Gmail refused the attachment; the details are in the message body instead")
            }
        }

        // 2. A chooser over mail apps only. This is what a device without Gmail gets.
        val chooser = Intent.createChooser(withAttachment, "Send to $address").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (runCatching {
                context.startActivity(chooser)
                true
            }.getOrDefault(false)
        ) {
            return Result.Opened("your email app")
        }

        // 3. Text-only chooser, for clients that cannot take a stream.
        val textChooser = Intent.createChooser(textOnly, "Send to $address").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (runCatching {
                context.startActivity(textChooser)
                true
            }.getOrDefault(false)
        ) {
            return Result.OpenedWithoutAttachment(
                "your email app",
                attachReason.ifBlank { "it would not accept the attachment; the details are in the message body" }
            )
        }

        return Result.NoMailApp(
            address,
            "a mail app exists but refused to open a draft — the report was NOT sent"
        )
    }

    /**
     * Open the OS "choose a mail app" flow without a report, for the settings row that
     * lets the user pick their default later.
     */
    fun openMailAppChooser(context: Context): Boolean = runCatching {
        val probe = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        context.startActivity(
            Intent.createChooser(probe, "Choose an email app")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.getOrDefault(false)

    // ------------------------------------------------------------------ internals

    private fun intentFor(mailTo: Uri, subject: String, body: String, uri: Uri?): Intent =
        Intent(Intent.ACTION_SENDTO, mailTo).apply {
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            if (uri != null) {
                putExtra(Intent.EXTRA_STREAM, uri)
                // The type matters: Gmail uses it to decide whether to offer the file as
                // an attachment at all, and text/plain is the honest answer for a .txt.
                type = "text/plain"
                // Read grant for this hand-off only. Not persisted: the mail app cannot
                // come back for the file later.
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /**
     * Mail clients only.
     *
     * `queryIntentActivities` on an `ACTION_SENDTO` + `mailto:` intent returns exactly
     * the apps that registered a mail handler. It also lets [openDraft] know in advance
     * whether anything can answer, which is what makes the no-mail-app fallback honest
     * instead of a guess made after a failed launch.
     */
    private fun mailApps(context: Context): List<ResolveInfo> = runCatching {
        val probe = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        context.packageManager.queryIntentActivities(
            probe,
            PackageManager.MATCH_DEFAULT_ONLY
        )
    }.getOrDefault(emptyList())

    private fun tryLaunch(context: Context, intent: Intent, target: ResolveInfo): String? =
        runCatching {
            // Pin the component so "Gmail first" really means Gmail and not whatever the
            // system decided the default mail app is.
            val explicit = Intent(intent).apply {
                component = android.content.ComponentName(
                    target.activityInfo.packageName,
                    target.activityInfo.name
                )
            }
            context.startActivity(explicit)
            target.loadLabel(context.packageManager).toString()
        }.getOrNull()

    /** Human list of what can send mail, for the UI and for reports. */
    fun mailAppLabels(context: Context): List<String> = mailApps(context).mapNotNull {
        runCatching { it.loadLabel(context.packageManager).toString() }.getOrNull()
    }.distinct()
}
