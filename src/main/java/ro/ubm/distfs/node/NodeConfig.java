package ro.ubm.distfs.node;

import ro.ubm.distfs.common.NodeInfo;

import java.util.Objects;


public record NodeConfig(NodeInfo self, int multicastPort) {

    public NodeConfig {
        Objects.requireNonNull(self, "self");

        if (multicastPort < 1 || multicastPort > 65_535) {
            throw new IllegalArgumentException("multicastPort must be between 1 and 65535");
        }
    }
}
