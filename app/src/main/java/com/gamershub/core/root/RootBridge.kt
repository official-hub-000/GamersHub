package com.gamershub.core.root

import android.content.Context
import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * The uid-0 session that DFRoot's bootstrap publishes on a unix socket.
 *
 * ## Why a socket and not `su`
 *
 * After the exploit runs, `bootstrap` is a live uid-0 process in `u:r:init:s0`. That is
 * the only root this device will hand us, because:
 *
 *  - KernelSU has no `su` file: `kernel/feature/sucompat.c` intercepts execve for the
 *    literal path `/system/bin/su` and substitutes `/data/adb/ksud`, so `test -x` and
 *    `command -v su` can never succeed on disk;
 *  - that interception only fires for uids the kernel already allows
 *    (`ksu_is_allow_uid_for_current`), which is what turns `su -c id` into the shell's
 *    `su: inaccessible or not found`;
 *  - the one uid allowed without an allowlist entry is the manager, and the kernel
 *    matches it by `strncmp(package, KSU_PACKAGE_NAME)` with `KSU_PACKAGE_NAME` compiled
 *    into the `.ko` as `me.weishu.kernelsu` (`kernel/Kbuild`). It is not us, and no
 *    `ksud` invocation changes it.
 *
 * So [RootShell.suBinary] returning null was correct and the 15 s wait in
 * [DfRootRunner] could never succeed. This class is the path that does work.
 *
 * ## Protocol
 *
 * Byte-identical to the C side in `app/src/main/jni/bootstrap.c`:
 *
 * ```
 * write  "<command>\n"
 * read   output lines until "__GH_ROOT_DONE__\n"
 * ```
 *
 * `[exit N]` is appended by the server. Everything else is the command's own output.
 */
object RootBridge {

    /** The line the server writes after each command's output. Must match `BRIDGE_DONE`. */
    private const val DONE = "__GH_ROOT_DONE__"

    /** Where `bootstrap` binds, relative to the payload dir. */
    private const val NAME = "bridge.sock"

    fun socketFile(context: Context): File = File(DfPaths.payloadDir(context), NAME)

    /**
     * Whether a socket exists at all.
     *
     * A stale socket survives a crash, so this is a hint for the UI only — [exec] is the
     * thing that decides, because connecting to a dead socket fails immediately.
     */
    fun present(context: Context): Boolean = runCatching {
        val f = socketFile(context)
        f.exists() && f.length() >= 0
    }.getOrDefault(false)

    private fun connect(context: Context): LocalSocket? = runCatching {
        val s = LocalSocket()
        s.connect(
            LocalSocketAddress(
                socketFile(context).absolutePath,
                LocalSocketAddress.Namespace.FILESYSTEM,
            ),
        )
        s
    }.getOrNull()

    /**
     * Runs one command as uid 0 through the bridge.
     *
     * Returns the command's combined output, or null when the bridge is not there. Null
     * means "no uid-0 session", never "the command failed" — a failing command still
     * returns its output and a non-zero `[exit N]`, which callers can read.
     */
    fun exec(context: Context, command: String, timeoutMs: Long = 8_000): String? {
        val socket = connect(context) ?: return null
        return try {
            socket.soTimeout = timeoutMs.toInt()
            val out = OutputStreamWriter(socket.outputStream, Charsets.UTF_8)
            val input = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))

            out.write(command.replace("\n", " ") + "\n")
            out.write("quit\n")
            out.flush()

            val lines = StringBuilder()
            var line = input.readLine()
            var sawDone = false
            while (line != null) {
                if (line == DONE) {
                    sawDone = true
                    break
                }
                if (lines.isNotEmpty()) lines.append('\n')
                lines.append(line)
                line = input.readLine()
            }
            if (sawDone) lines.toString() else null
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { socket.close() }
        }
    }

    /**
     * Asks the bridge to prove it is really uid 0.
     *
     * `id` is checked rather than trusted: a socket that answers something other than
     * `uid=0(root)` means whatever is on the other end is not privileged, and reporting
     * that as root would be the same lie as claiming a hooked kernel is a root shell.
     */
    fun confirmRoot(context: Context): Boolean {
        val out = exec(context, "id", 5_000) ?: return false
        return out.contains("uid=0")
    }
}
