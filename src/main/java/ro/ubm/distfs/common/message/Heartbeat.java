package ro.ubm.distfs.common.message;

import ro.ubm.distfs.common.NodeInfo;
import ro.ubm.distfs.common.Role;

import java.util.Objects;
import java.util.UUID;

/**
 * Announcement a node multicasts periodically:
 * {@code HEARTBEAT <nodeId> <host> <tcpPort> <role> <freeBytes>}.
 */
public record   Heartbeat(NodeInfo node, Role role, long freeBytes) implements Message {

    public Heartbeat {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(role, "role");

        if (freeBytes < 0) {
            throw new IllegalArgumentException("freeBytes must not be negative");
        }
    }

    @Override
    public MessageType type() {
        return MessageType.HEARTBEAT;
    }

    @Override
    public String encode() {
        return Message.line(
                type(),
                node.encode(),
                role.name(),
                Long.toString(freeBytes)
        );
    }

    /**
     * @throws IllegalArgumentException if the body is not a well-formed heartbeat
     */
    static Heartbeat decode(String body) {
        FieldReader reader = new FieldReader(MessageType.HEARTBEAT, body, 5, 5);

        NodeInfo node = new NodeInfo(reader.nextUUID(), reader.nextString(), reader.nextInt());
        return new Heartbeat(node, Role.from(reader.nextString()), reader.nextLong());
    }
}
