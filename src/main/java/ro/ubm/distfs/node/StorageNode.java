package ro.ubm.distfs.node;

import ro.ubm.distfs.common.Protocol;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * A storage node. It opens its two ports, discovers the other nodes over UDP multicast
 * and serves PUT/GET over TCP.
 */
public final class StorageNode implements AutoCloseable {

    private final NodeConfig config;
    private final Membership membership = new Membership();

    private ServerSocket tcpSocket;
    private MulticastSocket udpSocket;
    private Discovery discovery;
    private FileStore store;

    public StorageNode(NodeConfig config) {
        this.config = config;
    }

    public void start() throws IOException {
        try {
            store = new FileStore(Path.of("data", config.self().id().toString()));
            bindSockets();
            discovery = new Discovery(config, udpSocket, membership);
            discovery.start();
        } catch (IOException e) {
            close();
            throw e;
        }

        ServerSocket server = tcpSocket;
        Thread.ofPlatform().name("tcp-accept").start(() -> acceptLoop(server));
        System.out.println(config);
    }

    public Membership membership() {
        return membership;
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
             InputStream in = new BufferedInputStream(socket.getInputStream());
             OutputStream out = new BufferedOutputStream(socket.getOutputStream())) {

            String[] request = Protocol.readLine(in).split(" ");
            switch (request[0]) {
                case Protocol.PUT -> handlePut(request, in, out);
                case Protocol.GET -> handleGet(request, out);
                default -> error(out, "Unknown message: " + request[0]);
            }
            out.flush();
        } catch (EOFException e) {
            System.err.println("[tcp] " + socket.getRemoteSocketAddress() + " closed the connection early");
        } catch (IOException e) {
            System.err.println("[tcp] " + socket.getRemoteSocketAddress() + ": " + e.getMessage());
        }
    }

    // PUT <fileId> <name> <size> <sha256> [<nextHost:port>]
    private void handlePut(String[] request, InputStream in, OutputStream out) throws IOException {
        if (request.length != 5 && request.length != 6) {
            error(out, "Usage: PUT <fileId> <name> <size> <sha256> [<nextNode>]");
            return;
        }
        // TODO: forward to request[5] as REPLICATE

        UUID id;
        long size;
        try {
            id = UUID.fromString(request[1]);
            size = Long.parseLong(request[3]);
        } catch (IllegalArgumentException e) {
            error(out, "Invalid id or size");
            return;
        }
        String name = request[2];
        if (size < 0 || !Protocol.isValidName(name)) {
            error(out, "Invalid name or size");
            return;
        }

        try {
            store.write(id, in, size, request[4]);
        } catch (IOException e) {
            error(out, e.getMessage());
            return;
        }

        System.out.printf("[put] stored %s (%s, %d bytes)%n", id, name, size);
        Protocol.writeLine(out, Protocol.STORED);
    }

    // GET <fileId>
    private void handleGet(String[] request, OutputStream out) throws IOException {
        Path path = null;
        if (request.length == 2) {
            try {
                path = store.find(UUID.fromString(request[1]));
            } catch (IllegalArgumentException e) {
                // not a UUID, so not stored here
            }
        }
        if (path == null) {
            Protocol.writeLine(out, Protocol.NOT_FOUND);
            return;
        }

        long size = Files.size(path);
        String checksum;
        try (InputStream fileIn = Files.newInputStream(path)) {
            checksum = Protocol.checksum(fileIn, size);
        }

        Protocol.writeLine(out, Protocol.FILE, Long.toString(size), checksum);
        try (InputStream fileIn = Files.newInputStream(path)) {
            Protocol.copy(fileIn, out, size, null);
        }
        System.out.printf("[get] sent %s (%d bytes)%n", request[1], size);
    }

    private static void error(OutputStream out, String reason) throws IOException {
        Protocol.writeLine(out, Protocol.ERROR, reason.replace('\n', ' '));
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
