package ro.ubm.distfs.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Shared state for one interactive client session.
 *
 * <p>The uploaded map is a temporary stand-in for LOCATE: it only remembers
 * names uploaded by this client process.</p>
 */
public final class ClientSession {

    private final Client client;
    private final InetSocketAddress node;
    private final Map<String, UUID> uploaded = new HashMap<>();

    public ClientSession(Client client, InetSocketAddress node) {
        this.client = Objects.requireNonNull(client, "client");
        this.node = Objects.requireNonNull(node, "node");
    }

    public UUID put(String name, Path file) throws IOException {
        UUID id = client.put(node, name, file);
        uploaded.put(name, id);
        return id;
    }

    public boolean get(UUID id, Path destination) throws IOException {
        return client.get(node, id, destination);
    }

    public UUID resolve(String nameOrId) {
        try {
            return UUID.fromString(nameOrId);
        } catch (IllegalArgumentException e) {
            return uploaded.get(nameOrId);
        }
    }
}
