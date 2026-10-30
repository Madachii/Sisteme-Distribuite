package ro.ubm.distfs.node;

import com.github.f4b6a3.uuid.UuidCreator;
import ro.ubm.distfs.common.NodeInfo;
import ro.ubm.distfs.common.Role;

import java.net.InetAddress;
import java.util.Objects;


public record NodeConfig(NodeInfo self, InetAddress multicastGroup, int multicastPort, Role role) {

    public NodeConfig {
        Objects.requireNonNull(self, "self");
        Objects.requireNonNull(multicastGroup, "multicastGroup");
        Objects.requireNonNull(role, "role");

        if (!multicastGroup.isMulticastAddress()) {
            throw new IllegalArgumentException("multicastGroup must be a multicast address, got " + multicastGroup.getHostAddress());
        }
        if (multicastPort < 1 || multicastPort > 65_535) {
            throw new IllegalArgumentException("multicastPort must be between 1 and 65535");
        }
    }

    /**
     * Builds the configuration of a new node, with a freshly generated time-ordered id.
     */
    public static NodeConfig create(String host, int tcpPort, InetAddress multicastGroup, int multicastPort, Role role) {
        return new NodeConfig(
                new NodeInfo(UuidCreator.getTimeOrderedEpoch(), host, tcpPort),
                multicastGroup,
                multicastPort,
                role
        );
    }
}
