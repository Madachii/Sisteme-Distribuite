package ro.ubm.distfs.node;

import ro.ubm.distfs.common.Connection;
import ro.ubm.distfs.common.Protocol;
import ro.ubm.distfs.common.TcpServer;
import ro.ubm.distfs.common.message.Error;
import ro.ubm.distfs.common.message.File;
import ro.ubm.distfs.common.message.Get;
import ro.ubm.distfs.common.message.Message;
import ro.ubm.distfs.common.message.NotFound;
import ro.ubm.distfs.common.message.Put;
import ro.ubm.distfs.common.message.Stored;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A storage node: the file store, the membership view, discovery and the messages it serves.
 * The sockets themselves belong to {@link TcpServer} and {@link Discovery}; this class only
 * decides what a request means.
 */
public final class StorageNode implements AutoCloseable {

    private final NodeConfig config;
    private final Membership membership;

    private TcpServer tcp;
    private Discovery discovery;
    private FileStore store;

    public StorageNode(NodeConfig config) {
        this.config = config;
        this.membership = new Membership(config.suspectAfter(), config.failAfter());
    }

    public void start() throws IOException {
        try {
            store = new FileStore(Path.of("data", config.self().id().toString()));

            tcp = new TcpServer(config.self().tcpPort(), this::serve);
            tcp.start();

            discovery = new Discovery(config, membership);
            discovery.start();
        } catch (IOException e) {
            close();
            throw e;
        }

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
            // Parks this thread: Main passes the exit code of call() straight to System.exit,
            // so returning here would end the process. Ctrl+C ends it through the hook above.
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            close();
            throw e;
        }
    }

    public Membership membership() {
        return membership;
    }

    private void serve(Message request, Connection connection) throws IOException {
        switch (request) {
            case Put put -> handlePut(put, connection);
            case Get get -> handleGet(get, connection);
            default -> connection.send(new Error("Unknown message: " + request.type()));
        }
    }

    // PUT <fileId> <name> <size> <sha256> [<nextHost:port>]
    private void handlePut(Put put, Connection connection) throws IOException {
        // TODO: forward to put.nextNode() as REPLICATE

        try {
            store.write(put.fileId(), connection.body(), put.size(), put.checksum());
        } catch (IOException e) {
            connection.send(new Error(e.getMessage()));
            return;
        }

        System.out.printf("[put] stored %s (%s, %d bytes)%n", put.fileId(), put.name(), put.size());
        connection.send(new Stored());
    }

    // GET <fileId>
    private void handleGet(Get get, Connection connection) throws IOException {
        Path path = store.find(get.fileId());
        if (path == null) {
            connection.send(new NotFound());
            return;
        }

        long size = Files.size(path);
        String checksum;
        try (InputStream fileIn = Files.newInputStream(path)) {
            checksum = Protocol.checksum(fileIn, size);
        }

        try (InputStream fileIn = Files.newInputStream(path)) {
            connection.send(new File(size, checksum), fileIn, size);
        }
        System.out.printf("[get] sent %s (%d bytes)%n", get.fileId(), size);
    }

    @Override
    public void close() {
        if (discovery != null) {
            discovery.close();
            discovery = null;
        }

        if (tcp != null) {
            try {
                tcp.close();
            } catch (IOException e) {
                System.err.println("[tcp] close failed: " + e.getMessage());
            } finally {
                tcp = null;
            }
        }
    }
}
