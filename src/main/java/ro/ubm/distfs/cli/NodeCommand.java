package ro.ubm.distfs.cli;

import com.github.f4b6a3.uuid.UuidCreator;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import ro.ubm.distfs.common.NodeInfo;
import ro.ubm.distfs.node.NodeConfig;
import ro.ubm.distfs.node.StorageNode;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

@Command(
        name = "node",
        description = "Start a storage node"
)
public final class NodeCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "Show this help message and exit.")
    private boolean helpRequested;

    @Option(names = "--host", required = true, description = "Host address")
    private String host;

    @Option(names = "--tcp-port", required = true, description = "TCP port")
    private int tcpPort;

    @Option(names = "--udp-port", required = true, description = "UDP port")
    private int udpPort;

    @Override
    public Integer call() {
        try {
            NodeInfo self = new NodeInfo(
                    UuidCreator.getTimeOrderedEpoch(),
                    host,
                    tcpPort
            );
            NodeConfig config = new NodeConfig(self, udpPort);
            startNode(config);
            return 0;
        } catch (IllegalArgumentException e) {
            System.err.println("Configuration error: " + e.getMessage());
            spec.commandLine().usage(System.err);
            return 2;
        } catch (IOException e) {
            System.err.println("Startup failed: " + e.getMessage());
            return 1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 1;
        }
    }

    private static void startNode(NodeConfig config) throws IOException, InterruptedException {
        StorageNode node = new StorageNode(config);
        node.start();
        System.out.printf(
                "Node %s is running on %s:%d. Press Ctrl+C to stop.%n",
                config.self().id(),
                config.self().host(),
                config.self().tcpPort()
        );

        // Keep the process alive until Ctrl+C.
        new CountDownLatch(1).await();
    }
}
