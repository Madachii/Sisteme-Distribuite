package ro.ubm.distfs.common;

import java.util.Objects;
import java.util.UUID;

// datele personale ale unui nod
public record NodeInfo(UUID id, String host, int tcpPort) {

    public NodeInfo {
        Objects.requireNonNull(id, "id");

        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host must not be blank");
        }
        if (tcpPort < 1 || tcpPort > 65_535) {
            throw new IllegalArgumentException("tcpPort must be between 1 and 65535");
        }
    }

    /**
     * The node fields as they appear in a space-separated protocol message.
     */
    public String encode() {
        return id + " " + host + " " + tcpPort;
    }

    /**
     * Reads the three node fields starting at {@code offset}.
     *
     * @throws IllegalArgumentException if there are not enough fields or they are malformed
     */
    public static NodeInfo decode(String[] parts, int offset) {
        if (parts.length < offset + 3) {
            throw new IllegalArgumentException("Expected 3 node fields at offset " + offset);
        }
        return new NodeInfo(
                UUID.fromString(parts[offset]),
                parts[offset + 1],
                Integer.parseInt(parts[offset + 2])
        );
    }
}
