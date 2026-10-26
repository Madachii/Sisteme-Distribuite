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
}
