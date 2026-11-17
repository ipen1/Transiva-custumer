package com.transiva.app;

import java.io.*;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.net.ssl.HttpsURLConnection;

/** Downloads the exact, versioned game package expected by this app release. */
final class PasangAntarPack {
    static final String FILE_NAME = "pasang-antar-v2.zip";
    static final long SIZE = 1265980L;
    private static final String SHA256 = "cf7e00c16a2b073cb7674d035e78ff8bbae38404fc98baba57a8193a69065d06";
    private static final Set<String> ENTRIES = new HashSet<>(Arrays.asList(
            "index.html", "style.css", "game.bundle.js", "art/travi.webp", "art/siva.webp",
            "art/kampung.webp", "art/kebun.webp", "art/sungai.webp", "art/bukit.webp"));

    interface Progress { void update(int percent); }

    private PasangAntarPack() {}

    static File fetch(File directory, Progress progress) throws IOException {
        File installed = new File(directory, FILE_NAME);
        if (isVerified(installed)) return installed;
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot prepare game directory");
        File staging = File.createTempFile("pasang-antar-", ".part", directory);
        try {
            download(staging, progress);
            if (!isVerified(staging)) throw new IOException("Game integrity check failed");
            if (!staging.renameTo(installed)) throw new IOException("Cannot commit game package");
            return installed;
        } finally {
            if (staging.exists()) staging.delete();
        }
    }

    private static void download(File staging, Progress progress) throws IOException {
        URL url = new URL(ApiConfig.SERVER + "game_packs/" + FILE_NAME);
        if (!"https".equals(url.getProtocol())) throw new IOException("HTTPS required");
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(12000);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/zip");
        connection.setRequestProperty("Accept-Encoding", "identity");
        long start = System.nanoTime();
        try {
            if (connection.getResponseCode() != 200) throw new IOException("Game package unavailable");
            String length = connection.getHeaderField("Content-Length");
            if (length != null && !Long.toString(SIZE).equals(length.trim())) {
                throw new IOException("Unexpected game package size");
            }
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream output = new FileOutputStream(staging)) {
                byte[] buffer = new byte[16384];
                long received = 0;
                int previous = -1;
                for (int read; (read = input.read(buffer)) != -1;) {
                    received += read;
                    if (received > SIZE || System.nanoTime() - start > 90_000_000_000L) {
                        throw new IOException("Invalid or stalled game download");
                    }
                    output.write(buffer, 0, read);
                    int now = (int) Math.min(99, received * 100 / SIZE);
                    if (now != previous) progress.update(now);
                    previous = now;
                }
                if (received != SIZE) throw new EOFException("Incomplete game download");
                output.getFD().sync();
            }
        } finally {
            connection.disconnect();
        }
    }

    static boolean isVerified(File file) {
        if (!file.isFile() || file.length() != SIZE) return false;
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
                byte[] buffer = new byte[16384];
                for (int n; (n = input.read(buffer)) != -1;) sha.update(buffer, 0, n);
            }
            StringBuilder hash = new StringBuilder(64);
            for (byte value : sha.digest()) hash.append(String.format(Locale.ROOT, "%02x", value & 255));
            if (!SHA256.contentEquals(hash)) return false;
            try (ZipFile zip = new ZipFile(file)) {
                Set<String> found = new HashSet<>();
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory() || entry.getSize() <= 0 || entry.getSize() > 2_000_000
                            || !ENTRIES.contains(entry.getName()) || !found.add(entry.getName())) return false;
                }
                return found.equals(ENTRIES);
            }
        } catch (IOException | NoSuchAlgorithmException e) {
            return false;
        }
    }

    static InputStream open(File file, String path) throws IOException {
        if (!ENTRIES.contains(path)) throw new FileNotFoundException(path);
        final ZipFile zip = new ZipFile(file);
        try {
            ZipEntry entry = zip.getEntry(path);
            if (entry == null) throw new FileNotFoundException(path);
            return new FilterInputStream(zip.getInputStream(entry)) {
                @Override public void close() throws IOException {
                    try { super.close(); } finally { zip.close(); }
                }
            };
        } catch (IOException | RuntimeException e) {
            zip.close();
            throw e;
        }
    }
}
