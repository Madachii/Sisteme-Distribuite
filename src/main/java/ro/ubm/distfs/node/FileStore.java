package ro.ubm.distfs.node;

import ro.ubm.distfs.common.Protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * Stores file contents on local disk, one file per ID.
 */
public final class FileStore {

    private final Path root;

    public FileStore(Path root) throws IOException {
        this.root = Files.createDirectories(root);
    }

    /**
     * The file only becomes visible once the checksum matches.
     */
    public void write(UUID id, InputStream in, long size, String checksum) throws IOException {
        Path temp = Files.createTempFile(root, id + "-", ".part");
        try {
            MessageDigest digest = Protocol.newDigest();
            try (OutputStream out = Files.newOutputStream(temp)) {
                Protocol.copy(in, out, size, digest);
            }

            String actual = Protocol.hex(digest);
            if (!actual.equalsIgnoreCase(checksum)) {
                throw new IOException("Checksum mismatch: expected " + checksum + ", got " + actual);
            }
            Files.move(temp, pathOf(id), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /**
     * @return the stored file, or null if there is none
     */
    public Path find(UUID id) {
        Path path = pathOf(id);
        return Files.isRegularFile(path) ? path : null;
    }

    private Path pathOf(UUID id) {
        return root.resolve(id.toString());
    }
}
