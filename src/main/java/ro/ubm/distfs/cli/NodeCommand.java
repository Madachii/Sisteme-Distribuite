package ro.ubm.distfs.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import ro.ubm.distfs.common.Role;
import ro.ubm.distfs.node.NodeConfig;
import ro.ubm.distfs.node.StorageNode;

import java.io.IOException;
import java.net.InetAddress;
import java.time.Duration;
import java.util.concurrent.Callable;

@Command(
        name = "node",
        description = "Start a storage node"
)
public final class NodeCommand implements Callable<Integer> {

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "Show this help message and exit.")
    private boolean helpRequested;

    @Option(names = "--host", required = true, description = "Host address")
    private String host;

    @Option(names = "--tcp-port", required = true, description = "TCP port")
    private int tcpPort;

    @Option(names = "--udp-port", required = true, description = "UDP port")
    private int udpPort;

    @Option(names = "--multicast-group", defaultValue = "239.255.42.99",
            description = "Multicast group for discovery (default: ${DEFAULT-VALUE})")
    private InetAddress multicastGroup;

    @Option(names = "--role", defaultValue = "NODE",
            description = "Role of this node, one of ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE})")
    private Role role;

    @Option(names = "--suspect", defaultValue = "4000",
            description = "Silence in ms after which a node is marked SUSPECT (default: ${DEFAULT-VALUE})")
    private long suspectMs;

    @Option(names = "--fail", defaultValue = "6000",
            description = "Silence in ms after which a node is marked CAZUT (default: ${DEFAULT-VALUE})")
    private long failMs;

    @Override
    public Integer call() throws IOException, InterruptedException {
        NodeConfig config = NodeConfig.create(host, tcpPort, multicastGroup, udpPort, role,
                Duration.ofMillis(suspectMs), Duration.ofMillis(failMs));
        new StorageNode(config).run();

        return 0;
    }
}
