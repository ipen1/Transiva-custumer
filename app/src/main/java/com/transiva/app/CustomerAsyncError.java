package com.transiva.app;

import java.io.InterruptedIOException;
import java.util.Locale;
import java.util.concurrent.CancellationException;

/** User-facing async error policy. Lifecycle cancellation is normal and must not become a Toast. */
public final class CustomerAsyncError {
    private CustomerAsyncError() {}

    public static boolean isCancellation(Throwable error) {
        Throwable current = error;
        int depth = 0;
        while (current != null && depth++ < 8) {
            if (current instanceof InterruptedException
                    || current instanceof InterruptedIOException
                    || current instanceof CancellationException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String m = message.trim().toLowerCase(Locale.US);
                if (m.equals("thread interrupted")
                        || m.contains("thread interrupted")
                        || m.equals("interrupted")
                        || m.contains("operation canceled")
                        || m.contains("operation cancelled")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return Thread.currentThread().isInterrupted();
    }

    public static String userMessage(Throwable error, String fallback) {
        if (isCancellation(error)) return fallback == null ? "" : fallback;
        String message = error == null ? null : error.getMessage();
        if (message == null || message.trim().isEmpty() || "null".equalsIgnoreCase(message.trim())) {
            return fallback == null ? "" : fallback;
        }
        return message.trim();
    }
}
