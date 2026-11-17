package ro.ubm.distfs.node;

import com.github.f4b6a3.uuid.UuidCreator;
import ro.ubm.distfs.common.NodeInfo;
import ro.ubm.distfs.common.Role;

import java.net.InetAddress;
import java.time.Duration;
import java.util.Objects;


/**
 * @param suspectAfter silence after which another node is marked SUSPECT
 * @param failAfter    silence after which another node is marked CAZUT
 */
public record NodeConfig(
        NodeInfo self,
        InetAddress multicastGroup,
        int multicastPort,
        Role role,
        Duration suspectAfter,
        Duration failAfter
) {

    public NodeConfig {
        Objects.requireNonNull(self, "self");
        Objects.requireNonNull(multicastGroup, "multicastGroup");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(suspectAfter, "suspectAfter");
        Objects.requireNonNull(failAfter, "failAfter");

        if (!multicastGroup.isMulticastAddress()) {
            throw new IllegalArgumentException("multicastGroup must be a multicast address, got " + multicastGroup.getHostAddress());
        }
        if (multicastPort < 1 || multicastPort > 65_535) {
            throw new IllegalArgumentException("multicastPort must be between 1 and 65535");
        }
        if (suspectAfter.isNegative() || suspectAfter.isZero()) {
            throw new IllegalArgumentException("suspect must be positive, got " + suspectAfter.toMillis() + " ms");
        }
        if (failAfter.compareTo(suspectAfter) <= 0) {
            throw new IllegalArgumentException("fail (" + failAfter.toMillis() + " ms) must be greater than suspect ("
                    + suspectAfter.toMillis() + " ms)");
        }
    }

    /**
     * Builds the configuration of a new node, with a freshly generated time-ordered id.
     */
    public static NodeConfig create(String host, int tcpPort, InetAddress multicastGroup, int multicastPort, Role role,
                                    Duration suspectAfter, Duration failAfter) {
        return new NodeConfig(
                new NodeInfo(UuidCreator.getTimeOrderedEpoch(), host, tcpPort),
                multicastGroup,
                multicastPort,
                role,
                suspectAfter,
                failAfter
        );
    }
}
