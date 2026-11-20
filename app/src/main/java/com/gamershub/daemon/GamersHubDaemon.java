package com.gamershub.daemon;

import android.net.LocalServerSocket;
import android.net.LocalSocket;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * GamersHub's own privileged daemon — the Shizuku architecture, without Shizuku.
 *
 * ## Why this class is not part of the normal app
 *
 * This class is never instantiated inside GamersHub's own process. It is started with
 * `app_process`, which boots it as a **separate process running as uid 2000 (shell)**,
 * and it is found there by the app over a local socket. That is exactly how Shizuku's
 * server works, and it is the difference between borrowing a shell and *having* one:
 *
 *  - **It outlives wireless debugging.** The 6.29.0 transport is a live ADB connection;
 *    toggle Wireless debugging off and it is gone. This daemon was already started as
 *    uid 2000 and simply keeps running until the phone reboots, so privileged features
 *    continue to work with ADB off, exactly like a running Shizuku server.
 *  - **One process, many commands.** No per-command ADB round trip, no mDNS lookup, no
 *    reconnect. A settings toggle costs a socket write instead of a protocol handshake.
 *  - **No port to lose.** The socket name is fixed for the life of the daemon, so nothing
 *    has to be re-discovered when the phone's wireless debugging ports change.
 *
 * ## Launch
 *
 *   `CLASSPATH=<base.apk> app_process /system/bin com.gamershub.daemon.GamersHubDaemon --token=<hex>`
 *
 * `app_process` accepts an APK on the classpath, so the daemon ships inside the app's own
 * dex. There is no second artifact, no dex blob in assets, and no way for them to drift
 * apart: the daemon the app starts is always the daemon in the APK the user installed.
 *
 * ## Security
 *
 * An abstract unix socket is reachable by any app that knows its name, and a privileged
 * daemon reachable by every app on the phone would be a privilege-escalation hole. The
 * socket name therefore contains a **random token the app generated and passed on the
 * command line** — the same shared-secret approach Shizuku uses. An app that does not
 * know the token cannot address the socket. The token is regenerated on every start and
 * never persisted, so it cannot be replayed against a later daemon.
 *
 * ## Protocol
 *
 * Line headers and length-prefixed bodies, so command text containing quotes, `$`, or
 * newlines can never be mangled by the framing:
 *
 *   client → `HELLO\n`                    server → `READY uid=<u> sdk=<s> pid=<p>\n`
 *   client → `RUN <n>\n<n bytes>`         server → `OUT <n>\n<n bytes>`
 *   client → `PING\n`                     server → `PONG\n`
 *   client → `BYE\n`                      server closes
 *
 * The daemon appends `__GH_EXIT__=<rc>` to every output itself, which is the same
 * sentinel the app's other transports use, so a call site cannot tell them apart.
 */
public final class GamersHubDaemon {

    private static final String TAG = "GamersHubDaemon";

    /** Idle timeout. A shell daemon nobody has spoken to in this long is a leak. */
    private static final long IDLE_LIMIT_MS = 12L * 60 * 60 * 1000;

    private static volatile long lastActivity = System.currentTimeMillis();

    private GamersHubDaemon() {
    }

    public static void main(String[] args) {
        String token = null;
        for (String arg : args) {
            if (arg != null && arg.startsWith("--token=")) {
                token = arg.substring("--token=".length());
            }
        }

        // A predictable name would make the socket addressable by any app, so refuse to
        // start without a token rather than quietly running an open door.
        if (token == null || token.length() < 16) {
            System.err.println(TAG + ": refusing to start without a strong --token");
            System.exit(2);
            return;
        }

        String name = "com.gamershub.daemon." + token;
        LocalServerSocket server;
        try {
            // Abstract namespace: no file to leave behind, and no filesystem permissions
            // to get wrong.
            server = new LocalServerSocket(name);
        } catch (IOException e) {
            System.err.println(TAG + ": cannot bind " + name + ": " + e);
            System.exit(3);
            return;
        }

        System.out.println(TAG + ": listening as uid " + android.os.Process.myUid()
                + " pid " + android.os.Process.myPid());
        System.out.flush();

        Thread reaper = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(60_000L);
                } catch (InterruptedException e) {
                    return;
                }
                if (System.currentTimeMillis() - lastActivity > IDLE_LIMIT_MS) {
                    System.out.println(TAG + ": idle, exiting");
                    System.exit(0);
                }
            }
        }, "gh-idle");
        reaper.setDaemon(true);
        reaper.start();

        while (true) {
            LocalSocket client;
            try {
                client = server.accept();
            } catch (IOException e) {
                System.err.println(TAG + ": accept failed: " + e);
                continue;
            }
            // Sequential on purpose: the app is the only client, and serialising makes the
            // output framing impossible to interleave. A second connection queues here and
            // is answered as soon as the first closes.
            serve(client);
        }
    }

    private static void serve(LocalSocket client) {
        try (LocalSocket socket = client) {
            socket.setSoTimeout(0);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            String hello = readLine(in);
            if (!"HELLO".equals(hello)) {
                return;
            }
            writeLine(out, "READY uid=" + android.os.Process.myUid()
                    + " sdk=" + android.os.Build.VERSION.SDK_INT
                    + " pid=" + android.os.Process.myPid());

            while (true) {
                lastActivity = System.currentTimeMillis();
                String header = readLine(in);
                if (header == null) {
                    return;
                }
                if (header.startsWith("PING")) {
                    writeLine(out, "PONG");
                    continue;
                }
                if (header.startsWith("BYE")) {
                    return;
                }
                if (!header.startsWith("RUN ")) {
                    writeLine(out, "ERR unknown request");
                    continue;
                }

                int length;
                try {
                    length = Integer.parseInt(header.substring(4).trim());
                } catch (NumberFormatException e) {
                    writeLine(out, "ERR bad length");
                    continue;
                }
                byte[] body = new byte[length];
                in.readFully(body);
                String command = new String(body, StandardCharsets.UTF_8);

                String result = run(command);
                byte[] payload = result.getBytes(StandardCharsets.UTF_8);
                writeLine(out, "OUT " + payload.length);
                out.write(payload);
                out.flush();
            }
        } catch (Throwable t) {
            // A client that vanishes mid-frame is normal (the app was killed). Nothing to
            // do but go back and wait for the next one.
            System.err.println(TAG + ": client ended: " + t);
        }
    }

    /**
     * Runs one command and appends the exit sentinel.
     *
     * stdout and stderr are merged on purpose: every existing call site parses a single
     * stream, and ADB's own shell service merges them too, so this stays indistinguishable
     * from the transport it replaces.
     */
    private static String run(String command) {
        StringBuilder combined = new StringBuilder();
        try {
            Process process = new ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start();
            try (InputStream is = process.getInputStream()) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int n;
                while ((n = is.read(chunk)) > 0) {
                    buffer.write(chunk, 0, n);
                }
                combined.append(buffer.toString("UTF-8"));
            }
            int rc = process.waitFor();
            combined.append("__GH_EXIT__=").append(rc).append('\n');
        } catch (Throwable t) {
            combined.append("__GH_EXIT__=127\n");
        }
        return combined.toString();
    }

    private static String readLine(DataInputStream in) throws IOException {
        StringBuilder line = new StringBuilder();
        while (true) {
            int c = in.read();
            if (c < 0) {
                return line.length() == 0 ? null : line.toString();
            }
            if (c == '\n') {
                return line.toString();
            }
            if (c != '\r') {
                line.append((char) c);
            }
            if (line.length() > 4096) {
                throw new IOException("header too long");
            }
        }
    }

    private static void writeLine(DataOutputStream out, String text) throws IOException {
        out.write((text + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
}
