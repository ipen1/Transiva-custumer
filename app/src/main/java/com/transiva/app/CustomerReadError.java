package com.transiva.app;

import com.transiva.app.TransivaHttpRepository.ServerResponseException;

import java.io.IOException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;

/** Shared UI classification for read errors. */
public final class CustomerReadError {
    private CustomerReadError() {}

    public static boolean canRetryOnReconnect(Throwable error) {
        return error instanceof IOException || error instanceof TimeoutException
                || error instanceof RejectedExecutionException
                || (error instanceof ServerResponseException
                    && (((ServerResponseException) error).status == 429
                    || ((ServerResponseException) error).status >= 500));
    }

    public static long retryDelay(Throwable error) {
        if (!(error instanceof ServerResponseException)) return 0L;
        ServerResponseException response = (ServerResponseException) error;
        if (response.status == 429) return Math.max(1000L, response.retryAfterMs);
        return response.status == 503 ? response.retryAfterMs : 0L;
    }

    public static String message(Throwable error, long remainingRetryMs, String fallback) {
        if (remainingRetryMs > 0L) {
            long seconds = (remainingRetryMs + 999L) / 1000L;
            return "Server meminta jeda. Coba lagi dalam " + seconds + " detik.";
        }
        if (error instanceof ServerResponseException) {
            int status = ((ServerResponseException) error).status;
            if (status == 401 || status == 403) return "Akses akun perlu diperiksa.";
            if (status == 429) return "Server sedang sibuk. Coba lagi.";
            return fallback;
        }
        if (error instanceof java.net.UnknownHostException && !TransivaNetworkMonitor.isOnline()) {
            return "Tidak ada koneksi internet.";
        }
        if (error instanceof java.net.SocketTimeoutException || error instanceof TimeoutException) {
            return "Respons server terlalu lama.";
        }
        if (error instanceof RejectedExecutionException) return "Aplikasi sedang sibuk. Coba lagi.";
        if (error instanceof IOException) return "Koneksi ke server terganggu.";
        return fallback;
    }
}
