package com.fongmi.android.tv.utils;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Probes live line latency on the shared Task pool instead of creating a
 * private thread pool per dialog/action.
 */
public final class LineProbe {

    private static final int MAX_CONCURRENT = 4;
    private static final int CONNECT_TIMEOUT_MS = 1500;
    private static final int READ_TIMEOUT_MS = 1500;

    private LineProbe() {
    }

    public static long[] probe(List<String> urls, long timeoutMs) {
        long[] result = new long[urls.size()];
        if (urls.isEmpty()) return result;

        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        Semaphore gate = new Semaphore(MAX_CONCURRENT);
        List<Future<Long>> futures = new ArrayList<>(urls.size());
        for (String url : urls) {
            futures.add(Task.largeExecutor().submit(() -> {
                gate.acquire();
                try {
                    return ping(url.split("\\$")[0]);
                } finally {
                    gate.release();
                }
            }));
        }

        for (int i = 0; i < futures.size(); i++) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                futures.get(i).cancel(true);
                result[i] = -1;
                continue;
            }
            try {
                result[i] = futures.get(i).get(remaining, TimeUnit.NANOSECONDS);
            } catch (Exception e) {
                futures.get(i).cancel(true);
                result[i] = -1;
            }
        }
        return result;
    }

    private static long ping(String url) {
        HttpURLConnection connection = null;
        try {
            long start = System.currentTimeMillis();
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Range", "bytes=0-0");
            int code = connection.getResponseCode();
            return code >= 400 ? -1 : System.currentTimeMillis() - start;
        } catch (Exception e) {
            return -1;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
