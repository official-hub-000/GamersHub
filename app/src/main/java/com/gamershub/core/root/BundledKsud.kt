package com.gamershub.core.root

import android.content.Context
import java.io.File

/**
 * The KernelSU userspace backend, carried inside this APK.
 *
 * ## What this is, and what it is not
 *
 * DFRoot's kernel module does not create root by itself. It patches the running kernel,
 * goes permissive, and then execs `ksud late-load`. `ksud` is KernelSU's userspace
 * backend: it starts the `su` daemon, holds the policy and mounts modules. The KernelSU
 * *app* is a UI on top of that same binary.
 *
 * Shipping `ksud` here therefore means the exploit has everything it needs and no
 * separate manager app has to be installed — the dependency is moved, not removed.
 *
 * ## Licence
 *
 * `ksud` is GPL-3.0. Carrying it makes this APK a GPL-3.0 work, which is why
 * `licenses/KernelSU-GPL-3.0.txt`, the written source offer in `licenses/KernelSU-SOURCE.txt`
 * and the in-app notice in Help ▸ Licences all exist and are checked by
 * `tools/check_licenses.py`. Removing the notice while keeping the binary would violate
 * the licence; the gate exists so that cannot happen quietly.
 *
 * ## Where the file lives
 *
 * Android only packages `lib*.so` out of `jniLibs`, so the binary ships as
 * `libksud.so` in `arm64-v8a` and is copied out to the payload directory at run time,
 * which is exactly where [DfPaths.ksudStage] points.
 */
object BundledKsud {

    /** The name AGP gives it inside the APK. */
    private const val ASSET_NAME = "libksud.so"

    /** True when this build actually carries the backend. */
    fun present(context: Context): Boolean = runCatching {
        File(context.applicationInfo.nativeLibraryDir, ASSET_NAME).exists()
    }.getOrDefault(false)

    /**
     * Copies `libksud.so` out of `nativeLibraryDir` to the staging path and makes it
     * executable.
     *
     * This is the same operation [df.root.ExploitRunner.stageAssets] performs for an
     * installed manager, minus the other app: the source is this APK, not somebody
     * else's `nativeLibraryDir`.
     */
    fun stage(context: Context): File? = runCatching {
        val src = File(context.applicationInfo.nativeLibraryDir, ASSET_NAME)
        if (!src.exists() || src.length() == 0L) return@runCatching null
        val dst = DfPaths.ksudStage(context)
        dst.parentFile?.mkdirs()
        // Copy only when the sizes differ: the binary is ~10 MB and this runs on a path
        // that also sits behind the exploit's start-up, so it should not rewrite it every
        // attempt.
        if (!dst.exists() || dst.length() != src.length()) {
            src.inputStream().use { input ->
                dst.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
            }
        }
        dst.setExecutable(true, false)
        if (dst.length() != src.length()) null else dst
    }.getOrNull()
}
