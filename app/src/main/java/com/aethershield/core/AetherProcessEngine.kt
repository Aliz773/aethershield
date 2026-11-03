package com.aethershield.core

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

/**
 * Runs the OFFICIAL prebuilt Aether v2.4.0 CLI binary as a child process
 * (shipped as jniLibs/arm64-v8a/libaether_cli.so so Android lets us exec it),
 * then waits until its local SOCKS5 proxy really answers.
 *
 * Verified against the binary's own embedded `--help`: the mode flags, `--h2/--h3`,
 * `--turbo`, `-4`, `--quick-reconnect`, `--bind` and `--log-level` all exist, and the
 * SOCKS5 default is 127.0.0.1:1819. We never rely on that default: a port is picked for
 * us and passed with `--bind`, so a leftover process from a previous run can no longer
 * make the next start fail with a confusing "port already in use".
 */
class AetherProcessEngine(private val context: Context) : TunnelEngine {

    private val processLock = Any()
    private val logLock = Any()
    private val tail = ArrayDeque<String>()

    @Volatile
    private var process: Process? = null

    @Volatile
    private var endpoint: EngineEndpoint? = null

    @Volatile
    private var pidMethod: java.lang.reflect.Method? = null

    override val isAlive: Boolean
        get() = process?.isAlive == true

    override fun status(): EngineStatus {
        val p = process
        val log = synchronized(logLock) { tail.toList() }
        if (p == null || !p.isAlive) {
            val code = p?.let { runCatching { it.exitValue() }.getOrNull() }
            return EngineStatus(running = false, exitCode = code, lastLog = log)
        }
        return EngineStatus(
            running = true,
            pid = pidOf(p),
            endpoint = endpoint,
            lastLog = log,
        )
    }

    /**
     * `Process.pid()` is reflected on rather than called directly: it is present in the
     * platform but not resolvable at compile time here, and a missing pid must never be
     * able to break the build.
     */
    private fun pidOf(p: Process): Long? = runCatching {
        val m = pidMethod ?: Process::class.java.getMethod("pid").also { pidMethod = it }
        m.invoke(p) as? Long
    }.getOrNull()

    override suspend fun start(settings: VpnSettings): EngineEndpoint = withContext(Dispatchers.IO) {
        stopInternal(graceful = false)

        val exe = File(context.applicationInfo.nativeLibraryDir, BINARY_NAME)
        if (!exe.exists()) {
            throw EngineUnavailableException(
                "libaether_cli.so not found in ${exe.parent} (wrong ABI, or packaging not extracted)"
            )
        }
        synchronized(logLock) { tail.clear() }

        val port = freeLoopbackPort()
        val target = EngineEndpoint(AetherArgs.LOOPBACK, port)

        val builder = ProcessBuilder(listOf(exe.absolutePath) + AetherArgs.build(settings, port))
            .directory(context.filesDir)
            .redirectErrorStream(true)
        builder.environment()["HOME"] = context.filesDir.absolutePath

        val p = try {
            builder.start()
        } catch (e: IOException) {
            throw EngineUnavailableException("cannot start Aether: ${e.message}")
        }
        process = p
        endpoint = target
        pumpLogs(p)

        // Bootstrapping tor is measured in minutes on a filtered network, not seconds.
        val timeout = if (settings.mode == TunnelMode.TOR_ONLY) TOR_TIMEOUT_MS else START_TIMEOUT_MS
        awaitReady(p, target, timeout)
        target
    }

    override suspend fun stop() = withContext(Dispatchers.IO) { stopInternal(graceful = true) }

    override fun stopNow() = stopInternal(graceful = false)

    private fun stopInternal(graceful: Boolean) {
        val p = synchronized(processLock) {
            val current = process
            process = null
            endpoint = null
            current
        } ?: return
        if (!p.isAlive) {
            runCatching { p.waitFor(1, TimeUnit.SECONDS) }
            return
        }
        p.destroy()
        try {
            // SIGTERM first so the core can flush aether.toml; force only if it hangs.
            val graceMs = if (graceful) 2_000L else 300L
            if (!p.waitFor(graceMs, TimeUnit.MILLISECONDS)) p.destroyForcibly()
        } catch (_: InterruptedException) {
            p.destroyForcibly()
            Thread.currentThread().interrupt()
        }
    }

    private suspend fun awaitReady(p: Process, target: EngineEndpoint, timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            currentCoroutineContext().ensureActive()
            if (!p.isAlive) {
                val code = runCatching { p.exitValue() }.getOrNull()
                throw EngineUnavailableException("Aether exited (code $code): ${lastLines()}")
            }
            if (SocksProbe.greets(target.host, target.port, timeoutMs = 400)) return
            delay(400)
        }
        stopInternal(graceful = false)
        throw EngineUnavailableException(
            "Aether did not open SOCKS5 on ${target.port} within ${timeoutMs / 1000}s: ${lastLines()}"
        )
    }

    /** Keeps the last lines of the core's stdout so failures can explain themselves. */
    private fun pumpLogs(p: Process) {
        Thread({
            try {
                p.inputStream.bufferedReader().forEachLine { line ->
                    Log.d(TAG, line)
                    synchronized(logLock) {
                        tail.addLast(line)
                        while (tail.size > TAIL_LINES) tail.removeFirst()
                    }
                }
            } catch (_: IOException) {
                // process ended
            }
        }, "aether-log").apply { isDaemon = true }.start()
    }

    private fun freeLoopbackPort(): Int =
        ServerSocket(0, 1, InetAddress.getByName(AetherArgs.LOOPBACK)).use { it.localPort }

    private fun lastLines(): String =
        synchronized(logLock) { tail.takeLast(5).joinToString(" | ") }.ifBlank { "(no output)" }

    companion object {
        /** Must match the binary in jniLibs/arm64-v8a/. */
        const val BINARY_NAME = "libaether_cli.so"
        const val CORE_VERSION = "2.4.0"

        private const val TAG = "AetherCore"
        private const val START_TIMEOUT_MS = 90_000L
        private const val TOR_TIMEOUT_MS = 240_000L
        private const val TAIL_LINES = 40
    }
}
