package ro.ubm.distfs.node;

import ro.ubm.distfs.common.message.Heartbeat;
import ro.ubm.distfs.common.message.Messages;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.StandardSocketOptions;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Binds the multicast socket, announces this node's heartbeat on it and feeds the heartbeats
 * of all nodes into {@link Membership}. It owns its socket, so closing it releases the port
 * and unblocks the receiver.
 */
public final class Discovery implements AutoCloseable {

    public static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(2);

    // How often the membership table is checked for silent nodes; adds at most this much to the detection time.
    public static final Duration SWEEP_INTERVAL = Duration.ofMillis(100);

    private static final int MAX_DATAGRAM = 512;

    private final NodeConfig config;
    private final Membership membership;
    private final InetSocketAddress group;

    private MulticastSocket socket;
    private ScheduledExecutorService scheduler;
    private Thread receiver;

    public Discovery(NodeConfig config, Membership membership) {
        this.config = config;
        this.membership = membership;
        this.group = new InetSocketAddress(config.multicastGroup(), config.multicastPort());
    }

    public void start() throws IOException {
        socket = bind();

        try {
            joinGroup();
        } catch (IOException e) {
            close();
            throw e;
        }

        receiver = Thread.ofPlatform().name("discovery-receiver").daemon().start(this::receiveLoop);

        scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name("discovery-scheduler").daemon().factory()
        );
        scheduler.scheduleAtFixedRate(this::sendHeartbeat, 0, HEARTBEAT_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(membership::sweep,
                SWEEP_INTERVAL.toMillis(), SWEEP_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
    }

    private MulticastSocket bind() throws IOException {
        MulticastSocket bound = null;
        try {
            bound = new MulticastSocket(null);
            bound.setReuseAddress(true);
            bound.bind(new InetSocketAddress(config.multicastPort()));
            return bound;
        } catch (IOException e) {
            if (bound != null) {
                bound.close();
            }
            throw new IOException("Could not bind UDP port " + config.multicastPort(), e);
        }
    }

    // Uses the interface that owns --host, so nodes started on localhost talk over loopback.
    private void joinGroup() throws IOException {
        NetworkInterface iface = NetworkInterface.getByInetAddress(InetAddress.getByName(config.self().host()));
        if (iface == null) {
            throw new IOException("No network interface has address " + config.self().host());
        }

        try {
            socket.setOption(StandardSocketOptions.IP_MULTICAST_IF, iface);
            socket.setOption(StandardSocketOptions.IP_MULTICAST_LOOP, true);
            socket.setTimeToLive(1);
            socket.joinGroup(group, iface);
        } catch (IOException e) {
            throw new IOException("Could not join multicast group " + config.multicastGroup().getHostAddress() + " on " + iface.getName(), e);
        }
    }

    private void sendHeartbeat() {
        long freeBytes = new File(".").getUsableSpace();
        byte[] data = new Heartbeat(config.self(), config.role(), freeBytes)
                .encode()
                .getBytes(StandardCharsets.UTF_8);

        try {
            socket.send(new DatagramPacket(data, data.length, group));
        } catch (IOException e) {
            System.err.println("[discovery] heartbeat send failed: " + e.getMessage());
        }
    }

    private void receiveLoop() {
        byte[] buffer = new byte[MAX_DATAGRAM];

        while (!socket.isClosed()) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
            } catch (SocketException e) {
                return; // socket closed on shutdown
            } catch (IOException e) {
                System.err.println("[discovery] receive failed: " + e.getMessage());
                continue;
            }

            String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
            try {
                if (Messages.decode(message) instanceof Heartbeat heartbeat) {
                    membership.onHeartbeat(heartbeat);
                }
            } catch (IllegalArgumentException e) {
                System.err.println("[discovery] ignored datagram from " + packet.getSocketAddress() + ": " + e.getMessage());
            }
        }
    }

    @Override
    public void close() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }

        if (socket != null && !socket.isClosed()) {
            socket.close(); // unblocks the receiver; the field stays non-null for it
        }

        if (receiver != null) {
            receiver.interrupt();
            receiver = null;
        }
    }
}
