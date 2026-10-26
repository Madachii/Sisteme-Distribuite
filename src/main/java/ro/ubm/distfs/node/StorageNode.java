package ro.ubm.distfs.node;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;

/**
 * A storage node. It opens its two ports and discovers the other nodes over UDP multicast.
 * File storage will be added later.
 */
public final class StorageNode implements AutoCloseable {

    private final NodeConfig config;
    private final Membership membership = new Membership();

    private ServerSocket tcpSocket;
    private MulticastSocket udpSocket;
    private Discovery discovery;

    public StorageNode(NodeConfig config) {
        this.config = config;
    }

    public void start() throws IOException {
        try {
            bindSockets();
            discovery = new Discovery(config, udpSocket, membership);
            discovery.start();
        } catch (IOException e) {
            close();
            throw e;
        }

        System.out.println(config);
    }

    public Membership membership() {
        return membership;
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
        if (discovery != null) {
            discovery.close();
            discovery = null;
        }
        closeSockets();
    }
}
