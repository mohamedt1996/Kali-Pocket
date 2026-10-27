package dev.kalipocket.guest;

import java.io.*;
import java.nio.file.*;
import java.security.*;

/** Downloads a pinned guest image into a private staging directory. */
public final class VerifiedDownload {
    public interface Progress { void update(long bytes); }
    private VerifiedDownload() {}

    public static void copy(InputStream source, File target, String sha256, long maximumBytes, Progress progress) throws IOException {
        if (!sha256.matches("(?i)[0-9a-f]{64}")) throw new IllegalArgumentException("A pinned SHA-256 digest is required");
        if (maximumBytes <= 0) throw new IllegalArgumentException("A maximum image size is required");
        File parent = target.getAbsoluteFile().getParentFile();
        if (!parent.isDirectory()) throw new IOException("Missing private staging directory");
        File temporary = File.createTempFile("guest-download-", ".part", parent);
        try {
            MessageDigest digest;
            try { digest = MessageDigest.getInstance("SHA-256"); }
            catch (NoSuchAlgorithmException e) { throw new IOException(e); }
            long total = 0;
            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(temporary))) {
                byte[] buffer = new byte[65536];
                for (int n; (n = source.read(buffer)) != -1;) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Download cancelled");
                    if (n == 0) continue;
                    if (n > maximumBytes - total) throw new IOException("Guest image exceeds the configured maximum");
                    total += n;
                    digest.update(buffer, 0, n);
                    out.write(buffer, 0, n);
                    if (progress != null) progress.update(total);
                }
            }
            byte[] expected = new byte[32];
            for (int i = 0; i < expected.length; i++) expected[i] = (byte) Integer.parseInt(sha256.substring(i * 2, i * 2 + 2), 16);
            if (!MessageDigest.isEqual(digest.digest(), expected)) throw new IOException("Guest image checksum mismatch");
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            if (temporary.exists() && !temporary.delete()) temporary.deleteOnExit();
        }
    }
}
