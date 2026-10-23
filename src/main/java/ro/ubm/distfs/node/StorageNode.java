package ro.ubm.distfs.node;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;

/**
 * A storage node. For now it only holds its identity and opens its two ports.
 * Membership, discovery and file storage will be added later.
 */
public final class StorageNode implements AutoCloseable {

    private final NodeConfig config;

    private ServerSocket tcpSocket;
    private MulticastSocket udpSocket;

    public StorageNode(NodeConfig config) {
        this.config = config;
    }

    public void start() throws IOException {
        try {
            bindSockets();
        } catch (IOException e) {
            closeSockets();
            throw e;
        }

        System.out.printf("[node %s] ready%n", config.self().id());
        System.out.printf("[tcp] listening on %s:%d%n",
                config.self().host(), config.self().tcpPort());
        System.out.printf("[udp] multicast port %d%n", config.multicastPort());
    }

    private void bindSockets() throws IOException {
        bindTcpSocket();
        bindMulticastSocket();
    }

    private void bindTcpSocket() throws IOException {
        try {
            tcpSocket = new ServerSocket();
            tcpSocket.setReuseAddress(true);
            tcpSocket.bind(new InetSocketAddress(config.self().tcpPort()), 50);
        } catch (IOException e) {
            throw new IOException("Could not bind TCP port " + config.self().tcpPort(), e);
        }
    }

    private void bindMulticastSocket() throws IOException {
        try {
            udpSocket = new MulticastSocket(null);
            udpSocket.setReuseAddress(true);
            udpSocket.bind(new InetSocketAddress(config.multicastPort()));
        } catch (IOException e) {
            throw new IOException("Could not bind UDP port " + config.multicastPort(), e);
        }
    }

    private void closeSockets() {
        if (udpSocket != null) {
            udpSocket.close();
            udpSocket = null;
        }

        if (tcpSocket != null) {
            try {
                tcpSocket.close();
            } catch (IOException e) {
                System.err.println("[tcp] close failed: " + e.getMessage());
            } finally {
                tcpSocket = null;
            }
        }
    }

    @Override
    public void close() {
        closeSockets();
    }
}
