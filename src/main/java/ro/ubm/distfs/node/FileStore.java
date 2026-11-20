package ro.ubm.distfs.node;

import ro.ubm.distfs.common.AtomicFile;
import ro.ubm.distfs.common.Protocol;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Stores file contents on local disk as {@code data/<nodeId>/<fileId>}.
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
        AtomicFile.write(pathOf(id), out -> Protocol.copyVerified(in, out, size, checksum));
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
