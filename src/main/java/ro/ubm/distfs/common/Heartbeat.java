package ro.ubm.distfs.common;

import java.util.Objects;
import java.util.UUID;

/**
 * Announcement a node multicasts periodically:
 * {@code HEARTBEAT <nodeId> <host> <tcpPort> <role> <freeBytes>}.
 */
public record Heartbeat(NodeInfo node, boolean coordinator, long freeBytes) {

    private static final String TYPE = "HEARTBEAT";
    private static final String ROLE_NODE = "NODE";
    private static final String ROLE_COORDINATOR = "COORDINATOR";

    public Heartbeat {
        Objects.requireNonNull(node, "node");

        if (freeBytes < 0) {
            throw new IllegalArgumentException("freeBytes must not be negative");
        }
    }

    public String encode() {
        return String.join(" ",
                TYPE,
                node.id().toString(),
                node.host(),
                Integer.toString(node.tcpPort()),
                coordinator ? ROLE_COORDINATOR : ROLE_NODE,
                Long.toString(freeBytes)
        );
    }

    /**
     * @throws IllegalArgumentException if the message is not a well-formed heartbeat
     */
    public static Heartbeat decode(String message) {
        String[] parts = message.trim().split(" ");
        if (parts.length != 6 || !parts[0].equals(TYPE)) {
            throw new IllegalArgumentException("Not a heartbeat: " + message);
        }

        boolean coordinator = switch (parts[4]) {
            case ROLE_COORDINATOR -> true;
            case ROLE_NODE -> false;
            default -> throw new IllegalArgumentException("Unknown role: " + parts[4]);
        };

        NodeInfo node = new NodeInfo(UUID.fromString(parts[1]), parts[2], Integer.parseInt(parts[3]));
        return new Heartbeat(node, coordinator, Long.parseLong(parts[5]));
    }
}
