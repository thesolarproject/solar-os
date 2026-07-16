package com.solar.launcher.net;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 2026-07-16 — Loopback HTTP proxy for real-time JIT streaming on API 17 (Y1/Y2).
 * Layman: lets video/audio start immediately and scrub smoothly without downloading the whole file first.
 * Tech: bridges local plain HTTP sockets (IjkMediaPlayer/MediaPlayer) to SolarHttp (OkHttp + TlsHelper),
 * supporting HTTP Range seeking and bypassing system HttpURLConnection TLS limitations.
 * ponytail: loopback socket tee with thread pool — simple, zero disk usage, instant start.
 */
public final class SolarStreamProxy {
    private static final String TAG = "SolarStreamProxy";
    private static ServerSocket serverSocket;
    private static Thread acceptThread;
    private static final AtomicBoolean running = new AtomicBoolean(false);
    private static final ExecutorService executor = Executors.newCachedThreadPool();

    private SolarStreamProxy() {}

    private static void logInfo(String msg) {
        try { Log.i(TAG, msg); } catch (Throwable ignored) {}
    }

    private static void logWarn(String msg) {
        try { Log.w(TAG, msg); } catch (Throwable ignored) {}
    }

    public static synchronized void ensureStarted(Context ctx) throws IOException {
        if (serverSocket != null && !serverSocket.isClosed() && running.get()) return;
        if (ctx != null) TlsHelper.ensureSecurityProvider();
        serverSocket = new ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"));
        running.set(true);
        final int port = serverSocket.getLocalPort();
        logInfo("started loopback stream proxy on 127.0.0.1:" + port);
        acceptThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (running.get() && serverSocket != null && !serverSocket.isClosed()) {
                    try {
                        Socket client = serverSocket.accept();
                        executor.execute(new ProxyTask(client));
                    } catch (IOException e) {
                        if (running.get()) logWarn("accept error: " + e.getMessage());
                    }
                }
            }
        }, "SolarStreamProxyAccept");
        acceptThread.start();
    }

    public static synchronized void stop() {
        running.set(false);
        if (serverSocket != null) {
            try { serverSocket.close(); } catch (Exception ignored) {}
            serverSocket = null;
        }
        if (acceptThread != null) {
            try { acceptThread.interrupt(); } catch (Exception ignored) {}
            acceptThread = null;
        }
    }

    public static synchronized String proxyUrl(String targetUrl) {
        if (targetUrl == null || targetUrl.trim().isEmpty()) return targetUrl;
        if (targetUrl.startsWith("http://127.0.0.1:") || targetUrl.startsWith("file://") || targetUrl.startsWith("/")) {
            return targetUrl;
        }
        if (serverSocket == null || serverSocket.isClosed() || !running.get()) {
            return targetUrl;
        }
        try {
            return "http://127.0.0.1:" + serverSocket.getLocalPort() + "/stream?url="
                    + URLEncoder.encode(targetUrl, "UTF-8");
        } catch (Exception e) {
            return targetUrl;
        }
    }

    private static final class ProxyTask implements Runnable {
        private final Socket socket;

        ProxyTask(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            InputStream in = null;
            OutputStream out = null;
            Response response = null;
            try {
                in = socket.getInputStream();
                out = socket.getOutputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
                String requestLine = reader.readLine();
                if (requestLine == null || !requestLine.startsWith("GET ")) {
                    closeQuietly();
                    return;
                }
                String rangeHeader = null;
                String line;
                while ((line = reader.readLine()) != null && !line.isEmpty()) {
                    String lower = line.toLowerCase(java.util.Locale.US);
                    if (lower.startsWith("range:")) {
                        rangeHeader = line.substring(line.indexOf(':') + 1).trim();
                    }
                }

                // Parse target url from requestLine: GET /stream?url=... HTTP/1.1
                int qIdx = requestLine.indexOf("?url=");
                if (qIdx < 0) {
                    closeQuietly();
                    return;
                }
                int spaceIdx = requestLine.indexOf(' ', qIdx);
                String encodedUrl = spaceIdx > 0 ? requestLine.substring(qIdx + 5, spaceIdx) : requestLine.substring(qIdx + 5);
                String targetUrl = URLDecoder.decode(encodedUrl, "UTF-8");

                OkHttpClient client = SolarHttp.longReadClient();
                Request.Builder reqBuilder = new Request.Builder()
                        .url(targetUrl)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 4.2) AppleWebKit/537.36");
                if (rangeHeader != null && !rangeHeader.isEmpty()) {
                    reqBuilder.header("Range", rangeHeader);
                }

                response = client.newCall(reqBuilder.build()).execute();
                int code = response.code();
                String statusLine = "HTTP/1.1 " + code + " " + response.message() + "\r\n";
                out.write(statusLine.getBytes("UTF-8"));

                String contentType = response.header("Content-Type");
                if (contentType != null) {
                    out.write(("Content-Type: " + contentType + "\r\n").getBytes("UTF-8"));
                } else {
                    out.write("Content-Type: video/mp4\r\n".getBytes("UTF-8"));
                }
                String contentLen = response.header("Content-Length");
                if (contentLen != null) {
                    out.write(("Content-Length: " + contentLen + "\r\n").getBytes("UTF-8"));
                }
                String contentRange = response.header("Content-Range");
                if (contentRange != null) {
                    out.write(("Content-Range: " + contentRange + "\r\n").getBytes("UTF-8"));
                }
                out.write("Accept-Ranges: bytes\r\n".getBytes("UTF-8"));
                out.write("Connection: close\r\n\r\n".getBytes("UTF-8"));
                out.flush();

                if (response.body() != null) {
                    InputStream remoteIn = response.body().byteStream();
                    byte[] buf = new byte[32 * 1024];
                    int n;
                    while ((n = remoteIn.read(buf)) != -1) {
                        out.write(buf, 0, n);
                        out.flush();
                    }
                }
            } catch (IOException ignored) {
                // Client disconnect / seek broken pipe — normal JIT streaming behavior.
            } finally {
                if (response != null && response.body() != null) {
                    try { response.body().close(); } catch (Exception ignored) {}
                }
                closeQuietly();
            }
        }

        private void closeQuietly() {
            try { if (!socket.isClosed()) socket.close(); } catch (Exception ignored) {}
        }
    }
}
