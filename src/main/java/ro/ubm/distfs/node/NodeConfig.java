package ro.ubm.distfs.node;

import ro.ubm.distfs.common.NodeInfo;

import java.net.InetAddress;
import java.util.Objects;


public record NodeConfig(NodeInfo self, InetAddress multicastGroup, int multicastPort, boolean coordinator) {

    public NodeConfig {
        Objects.requireNonNull(self, "self");
        Objects.requireNonNull(multicastGroup, "multicastGroup");

        if (!multicastGroup.isMulticastAddress()) {
            throw new IllegalArgumentException("multicastGroup must be a multicast address, got " + multicastGroup.getHostAddress());
        }
        if (multicastPort < 1 || multicastPort > 65_535) {
            throw new IllegalArgumentException("multicastPort must be between 1 and 65535");
        }
    }
}
