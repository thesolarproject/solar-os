package com.solar.launcher;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.TimeUnit;

/**
 * 2026-07-16 — Minimal SNTP client (UDP/123) for Solar auto-time on rooted Y1/Y2/A5.
 * Returns UTC epoch millis; no timezone change here.
 */
public final class SolarNtpClient {
    private static final int NTP_PORT = 123;
    private static final int PACKET_SIZE = 48;
    /** NTP epoch (1900) → Unix epoch (1970) seconds. */
    private static final long NTP_EPOCH_OFFSET_SEC = 2208988800L;
    private static final int DEFAULT_TIMEOUT_MS = 4000;

    private SolarNtpClient() {}

    /**
     * Query {@code host} once; returns Unix epoch ms or -1 on failure.
     * Blocking — call off the UI thread.
     */
    public static long queryUtcEpochMs(String host) {
        return queryUtcEpochMs(host, DEFAULT_TIMEOUT_MS);
    }

    public static long queryUtcEpochMs(String host, int timeoutMs) {
        if (host == null || host.trim().isEmpty()) return -1L;
        DatagramSocket socket = null;
        try {
            InetAddress addr = InetAddress.getByName(host.trim());
            byte[] buf = new byte[PACKET_SIZE];
            // LI=0, VN=3, Mode=3 (client)
            buf[0] = 0x1B;
            DatagramPacket request = new DatagramPacket(buf, buf.length, addr, NTP_PORT);
            socket = new DatagramSocket();
            socket.setSoTimeout(Math.max(500, timeoutMs));
            long t1 = System.currentTimeMillis();
            socket.send(request);
            DatagramPacket response = new DatagramPacket(buf, buf.length);
            socket.receive(response);
            long t4 = System.currentTimeMillis();
            if (response.getLength() < PACKET_SIZE) return -1L;
            // Transmit timestamp at bytes 40–47 (seconds + fraction)
            long seconds = readUint32(buf, 40);
            long fraction = readUint32(buf, 44);
            if (seconds == 0L) return -1L;
            long ntpMs = (seconds - NTP_EPOCH_OFFSET_SEC) * 1000L
                    + (fraction * 1000L) / 0x100000000L;
            // Correct for one-way RTT/2 so set clock is closer to true UTC
            long rtt = Math.max(0L, t4 - t1);
            return ntpMs + (rtt / 2L);
        } catch (Exception e) {
            return -1L;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (Exception ignored) {}
            }
        }
    }

    /** Try hosts in order; first success wins. */
    public static long queryFirstUtcEpochMs(String[] hosts) {
        if (hosts == null) return -1L;
        for (int i = 0; i < hosts.length; i++) {
            long ms = queryUtcEpochMs(hosts[i]);
            if (ms > 0L) return ms;
        }
        return -1L;
    }

    static long readUint32(byte[] b, int offset) {
        return ((b[offset] & 0xFFL) << 24)
                | ((b[offset + 1] & 0xFFL) << 16)
                | ((b[offset + 2] & 0xFFL) << 8)
                | (b[offset + 3] & 0xFFL);
    }

    /** Self-check: known NTP seconds encode. */
    static void selfCheck() {
        // 2020-01-01 00:00:00 UTC = 1577836800 unix → + offset = NTP seconds
        long unixSec = 1577836800L;
        long ntpSec = unixSec + NTP_EPOCH_OFFSET_SEC;
        byte[] b = new byte[8];
        b[0] = (byte) ((ntpSec >> 24) & 0xFF);
        b[1] = (byte) ((ntpSec >> 16) & 0xFF);
        b[2] = (byte) ((ntpSec >> 8) & 0xFF);
        b[3] = (byte) (ntpSec & 0xFF);
        long got = readUint32(b, 0) - NTP_EPOCH_OFFSET_SEC;
        if (got != unixSec) throw new AssertionError("ntp decode " + got);
        if (TimeUnit.SECONDS.toMillis(1) != 1000L) throw new AssertionError("timeunit");
    }
}
