package ro.ubm.distfs.common;

import ro.ubm.distfs.common.message.Error;
import ro.ubm.distfs.common.message.Message;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Accepts TCP connections and runs one {@link Handler} per connection: one request line,
 * one response, then the connection is closed (docs/cerinte.md). A line that is not a valid
 * message is answered with {@code ERROR} instead of being dropped silently.
 *
 * <p>It knows nothing about what the messages mean; the node and the coordinator differ only
 * in the handler they pass in.</p>
 */
public final class TcpServer implements AutoCloseable {

    /**
     * Serves the single request of a connection, writing the single response.
     */
    @FunctionalInterface
    public interface Handler {

        void handle(Message request, Connection connection) throws IOException;
    }

    private final int port;
    private final Handler handler;

    private ServerSocket serverSocket;

    public TcpServer(int port, Handler handler) {
        this.port = port;
        this.handler = handler;
    }

    public void start() throws IOException {
        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(port), 50);
        } catch (IOException e) {
            throw new IOException("Could not bind TCP port " + port, e);
        }

        Thread.ofPlatform().name("tcp-accept").start(this::acceptLoop);
    }

    private void acceptLoop() {
        ServerSocket server = serverSocket;
        while (!server.isClosed()) {
            try {
                Socket socket = server.accept();
                Thread.ofVirtual().start(() -> serve(socket));
            } catch (IOException e) {
                if (!server.isClosed()) {
                    System.err.println("[tcp] accept failed: " + e.getMessage());
                }
            }
        }
    }

    private void serve(Socket socket) {
        try (Connection connection = Connection.of(socket)) {
            Message request;
            try {
                request = connection.receive();
            } catch (Connection.MalformedMessageException e) {
                connection.send(new Error(e.getMessage()));
                return;
            }

            handler.handle(request, connection);
        } catch (IOException e) {
            System.err.println("[tcp] " + socket.getRemoteSocketAddress() + ": " + e.getMessage());
        }
    }

    @Override
    public void close() throws IOException {
        if (serverSocket != null) {
            serverSocket.close();
            serverSocket = null;
        }
    }
}
