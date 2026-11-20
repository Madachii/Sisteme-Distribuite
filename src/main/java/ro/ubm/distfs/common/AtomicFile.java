package ro.ubm.distfs.common;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Publishes a file only once its content is complete: the bytes go to a temporary file next
 * to the target, and the temporary file is renamed onto the target afterwards. A transfer
 * that fails, or whose checksum does not match, therefore leaves the target untouched
 * instead of half written.
 */
public final class AtomicFile {

    /**
     * Writes the content of the file, throwing if the bytes are not acceptable.
     */
    @FunctionalInterface
    public interface Body {

        void writeTo(OutputStream out) throws IOException;
    }

    private AtomicFile() {
    }

    /**
     * Writes {@code body} to a temporary file in the target's directory, then moves it onto
     * {@code target} atomically.
     */
    public static void write(Path target, Body body) throws IOException {
        Path directory = target.toAbsolutePath().getParent();
        if (directory == null) {
            throw new IOException("No parent directory for " + target);
        }

        Path temp = Files.createTempFile(directory, ".distfs-", ".part");
        try {
            try (OutputStream out = Files.newOutputStream(temp)) {
                body.writeTo(out);
            }

            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
