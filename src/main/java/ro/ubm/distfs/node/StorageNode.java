package ro.ubm.distfs.node;

import ro.ubm.distfs.common.Protocol;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * A storage node. Opens its two ports and serves PUT/GET over TCP.
 * Membership and discovery will be added later.
 */
public final class StorageNode implements AutoCloseable {

    private final NodeConfig config;

    private ServerSocket tcpSocket;
    private MulticastSocket udpSocket;
    private FileStore store;

    public StorageNode(NodeConfig config) {
        this.config = config;
    }

    public void start() throws IOException {
        try {
            store = new FileStore(Path.of("data", "node-" + config.self().tcpPort()));
            bindSockets();
        } catch (IOException e) {
            closeSockets();
            throw e;
        }

        ServerSocket server = tcpSocket;
        Thread.ofPlatform().name("tcp-accept").start(() -> acceptLoop(server));
        System.out.println(config);
    }

    private void acceptLoop(ServerSocket server) {
        while (!server.isClosed()) {
            try {
                Socket socket = server.accept();
                Thread.ofVirtual().start(() -> handle(socket));
            } catch (IOException e) {
                if (!server.isClosed()) {
                    System.err.println("[tcp] accept failed: " + e.getMessage());
                }
            }
        }
    }

    private void handle(Socket socket) {
        try (socket;
             DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {

            String type = in.readUTF();
            switch (type) {
                case Protocol.PUT -> handlePut(in, out);
                case Protocol.GET -> handleGet(in, out);
                default -> {
                    out.writeUTF(Protocol.ERROR);
                    out.writeUTF("Unknown message: " + type);
                }
            }
            out.flush();
        } catch (EOFException e) {
            System.err.println("[tcp] " + socket.getRemoteSocketAddress() + " closed the connection early");
        } catch (IOException e) {
            System.err.println("[tcp] " + socket.getRemoteSocketAddress() + ": " + e.getMessage());
        }
    }

    private void handlePut(DataInputStream in, DataOutputStream out) throws IOException {
        String rawId = in.readUTF();
        String name = in.readUTF();
        long size = in.readLong();
        String checksum = in.readUTF();
        in.readUTF(); // TODO: forward to the next node as REPLICATE

        UUID id;
        try {
            id = UUID.fromString(rawId);
        } catch (IllegalArgumentException e) {
            out.writeUTF(Protocol.ERROR);
            out.writeUTF("Invalid id: " + rawId);
            return;
        }
        if (size < 0) {
            out.writeUTF(Protocol.ERROR);
            out.writeUTF("Invalid size: " + size);
            return;
        }

        try {
            store.write(id, in, size, checksum);
        } catch (IOException e) {
            out.writeUTF(Protocol.ERROR);
            out.writeUTF(e.getMessage());
            return;
        }

        System.out.printf("[put] stored %s (%s, %d bytes)%n", id, name, size);
        out.writeUTF(Protocol.STORED);
    }

    private void handleGet(DataInputStream in, DataOutputStream out) throws IOException {
        String rawId = in.readUTF();

        Path path;
        try {
            path = store.find(UUID.fromString(rawId));
        } catch (IllegalArgumentException e) {
            path = null;
        }
        if (path == null) {
            out.writeUTF(Protocol.NOT_FOUND);
            return;
        }

        long size = Files.size(path);
        out.writeUTF(Protocol.FILE);
        out.writeLong(size);
        try (InputStream fileIn = Files.newInputStream(path)) {
            Protocol.copy(fileIn, out, size, null);
        }
        System.out.printf("[get] sent %s (%d bytes)%n", rawId, size);
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
