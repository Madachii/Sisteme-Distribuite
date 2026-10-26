package ro.ubm.distfs.node;

import ro.ubm.distfs.common.Heartbeat;

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
 * Multicasts this node's heartbeat and feeds the heartbeats of all nodes into {@link Membership}.
 */
public final class Discovery implements AutoCloseable {

    public static final String GROUP = "239.255.42.99";
    public static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(2);

    private static final int MAX_DATAGRAM = 512;

    private final NodeConfig config;
    private final MulticastSocket socket;
    private final Membership membership;
    private final InetSocketAddress group;

    private ScheduledExecutorService scheduler;
    private Thread receiver;

    public Discovery(NodeConfig config, MulticastSocket socket, Membership membership) throws IOException {
        this.config = config;
        this.socket = socket;
        this.membership = membership;
        this.group = new InetSocketAddress(InetAddress.getByName(GROUP), config.multicastPort());
    }

    public void start() throws IOException {
        joinGroup();

        receiver = Thread.ofPlatform().name("discovery-receiver").daemon().start(this::receiveLoop);

        scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name("discovery-scheduler").daemon().factory()
        );
        scheduler.scheduleAtFixedRate(this::sendHeartbeat, 0, HEARTBEAT_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(membership::sweep, 1, 1, TimeUnit.SECONDS);
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
            throw new IOException("Could not join multicast group " + GROUP + " on " + iface.getName(), e);
        }
    }

    private void sendHeartbeat() {
        long freeBytes = new File(".").getUsableSpace();
        byte[] data = new Heartbeat(config.self(), config.coordinator(), freeBytes)
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
                membership.onHeartbeat(Heartbeat.decode(message));
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
        if (receiver != null) {
            receiver.interrupt();
            receiver = null;
        }
    }
}
