package ro.ubm.distfs.common;

import ro.ubm.distfs.common.message.Message;
import ro.ubm.distfs.common.message.Messages;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * One TCP connection, carrying the single request and single response of {@link Protocol}.
 * It owns the buffered streams, so callers only deal with messages and bodies.
 * Both ends use it: a client dials with {@link #open}, a server wraps an accepted socket
 * with {@link #of}.
 */
public final class Connection implements AutoCloseable {

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    private Connection(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new BufferedInputStream(socket.getInputStream());
        this.out = new BufferedOutputStream(socket.getOutputStream());
    }

    /**
     * Dials a node.
     */
    public static Connection open(InetSocketAddress node) throws IOException {
        Socket socket = new Socket(node.getHostString(), node.getPort());
        try {
            return new Connection(socket);
        } catch (IOException e) {
            try {
                socket.close();
            } catch (IOException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
    }

    /**
     * Wraps a socket a server has already accepted.
     */
    public static Connection of(Socket socket) throws IOException {
        return new Connection(socket);
    }

    /**
     * Sends a message that carries no body.
     */
    public void send(Message message) throws IOException {
        Protocol.writeLine(out, message);
        out.flush();
    }

    /**
     * Sends a message followed by exactly {@code size} bytes of the given body.
     */
    public void send(Message message, InputStream body, long size) throws IOException {
        Protocol.writeLine(out, message);
        Protocol.copy(body, out, size, null);
        out.flush();
    }

    /**
     * Reads the message line.
     *
     * @throws MalformedMessageException if the line is not a well-formed message
     */
    public Message receive() throws IOException {
        String line = Protocol.readLine(in);
        try {
            return Messages.decode(line);
        } catch (IllegalArgumentException e) {
            throw new MalformedMessageException(line, e);
        }
    }

    /**
     * The bytes that follow the message line, positioned at their first byte. Only a serving
     * side reads them, because only a request carries a body; the store hashes them while
     * writing, so this hands out the stream instead of an already copied body.
     */
    public InputStream body() {
        return in;
    }

    /**
     * Copies the {@code size} bytes of a response body into {@code body}, verifying its checksum.
     */
    public void receiveBody(OutputStream body, long size, String checksum) throws IOException {
        Protocol.copyVerified(in, body, size, checksum);
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

    /**
     * The peer sent a line that is not a valid message. A client cannot do anything about it;
     * a server answers it with {@code ERROR}, which is why it is a distinct type.
     */
    public static final class MalformedMessageException extends IOException {

        MalformedMessageException(String line, IllegalArgumentException cause) {
            super(cause.getMessage() != null ? cause.getMessage() : "Malformed message: " + line, cause);
        }
    }
}
