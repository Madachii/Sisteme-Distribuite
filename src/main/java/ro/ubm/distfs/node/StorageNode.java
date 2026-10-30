package ro.ubm.distfs.node;

import ro.ubm.distfs.common.Protocol;
import ro.ubm.distfs.common.message.Error;
import ro.ubm.distfs.common.message.File;
import ro.ubm.distfs.common.message.Get;
import ro.ubm.distfs.common.message.Message;
import ro.ubm.distfs.common.message.Messages;
import ro.ubm.distfs.common.message.NotFound;
import ro.ubm.distfs.common.message.Put;
import ro.ubm.distfs.common.message.Stored;

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
import java.util.concurrent.CountDownLatch;

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

    /**
     * Starts the node and blocks until the process is interrupted or the JVM shuts down.
     * The node is closed on the way out.
     */
    public void run() throws IOException, InterruptedException {
        start();
        Runtime.getRuntime().addShutdownHook(new Thread(this::close, "node-shutdown"));
        System.out.println("Press Ctrl+C to stop.");
        try {
            new CountDownLatch(1).await();
        } catch (InterruptedException e) {
            close();
            throw e;
        }
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

            Message request;
            try {
                request = Messages.decode(Protocol.readLine(in));
            } catch (IllegalArgumentException e) {
                error(out, e.getMessage() != null ? e.getMessage() : "Invalid message");
                return;
            }

            switch (request) {
                case Put put -> handlePut(put, in, out);
                case Get get -> handleGet(get, out);
                default -> error(out, "Unknown message: " + request.type());
            }
            out.flush();
        } catch (EOFException e) {
            System.err.println("[tcp] " + socket.getRemoteSocketAddress() + " closed the connection early");
        } catch (IOException e) {
            System.err.println("[tcp] " + socket.getRemoteSocketAddress() + ": " + e.getMessage());
        }
    }

    // PUT <fileId> <name> <size> <sha256> [<nextHost:port>]
    private void handlePut(Put put, InputStream in, OutputStream out) throws IOException {
        // TODO: forward to put.nextNode() as REPLICATE

        try {
            store.write(put.fileId(), in, put.size(), put.checksum());
        } catch (IOException e) {
            error(out, e.getMessage());
            return;
        }

        System.out.printf("[put] stored %s (%s, %d bytes)%n", put.fileId(), put.name(), put.size());
        Protocol.writeLine(out, new Stored());
    }

    // GET <fileId>
    private void handleGet(Get get, OutputStream out) throws IOException {
        Path path = store.find(get.fileId());
        if (path == null) {
            Protocol.writeLine(out, new NotFound());
            return;
        }

        long size = Files.size(path);
        String checksum;
        try (InputStream fileIn = Files.newInputStream(path)) {
            checksum = Protocol.checksum(fileIn, size);
        }

        Protocol.writeLine(out, new File(size, checksum));
        try (InputStream fileIn = Files.newInputStream(path)) {
            Protocol.copy(fileIn, out, size, null);
        }
        System.out.printf("[get] sent %s (%d bytes)%n", get.fileId(), size);
    }

    private static void error(OutputStream out, String reason) throws IOException {
        Protocol.writeLine(out, new Error(reason));
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
