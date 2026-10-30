package ro.ubm.distfs.client;

import com.github.f4b6a3.uuid.UuidCreator;
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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Talks to storage nodes over TCP.
 * For now the node is given by the caller; ALLOC and LOCATE through the coordinator come later.
 */
public final class Client {

    public UUID put(InetSocketAddress node, String name, Path file) throws IOException {
        if (!Protocol.isValidName(name)) {
            throw new IOException("Invalid name '" + name + "': no spaces or '/' allowed");
        }
        if (!Files.isRegularFile(file)) {
            throw new IOException("Not a file: " + file);
        }

        // TODO: the ID should come from the coordinator (ALLOC).
        UUID id = UuidCreator.getTimeOrderedEpoch();
        long size = Files.size(file);
        String checksum;
        try (InputStream fileIn = Files.newInputStream(file)) {
            checksum = Protocol.checksum(fileIn, size);
        }

        try (Socket socket = connect(node);
             OutputStream out = new BufferedOutputStream(socket.getOutputStream());
             InputStream in = new BufferedInputStream(socket.getInputStream());
             InputStream fileIn = Files.newInputStream(file)) {

            // TODO: add the node for the replica, from ALLOC
            Protocol.writeLine(out, new Put(id, name, size, checksum, null));
            Protocol.copy(fileIn, out, size, null);
            out.flush();

            Message reply = readMessage(in);
            if (reply instanceof Stored) {
                return id;
            }
            if (reply instanceof Error error) {
                throw new IOException("Node refused file: " + error.reason());
            }
            throw new IOException("Unexpected reply: " + reply.encode());
        }
    }

    /**
     * Saves the file to {@code destination} only if its checksum matches the one sent by the node.
     *
     * @return false if the node does not have the file
     */
    public boolean get(InetSocketAddress node, UUID id, Path destination) throws IOException {
        try (Socket socket = connect(node);
             OutputStream out = new BufferedOutputStream(socket.getOutputStream());
             InputStream in = new BufferedInputStream(socket.getInputStream())) {

            Protocol.writeLine(out, new Get(id));
            out.flush();

            Message reply = readMessage(in);
            if (reply instanceof NotFound) {
                return false;
            }
            if (!(reply instanceof File file)) {
                throw new IOException("Unexpected reply: " + reply.encode());
            }

            long size = file.size();
            String expected = file.checksum();

            Path parent = destination.toAbsolutePath().getParent();
            Path temp = Files.createTempFile(parent, ".distfs-", ".part");
            try {
                try (OutputStream fileOut = Files.newOutputStream(temp)) {
                    Protocol.copyVerified(in, fileOut, size, expected);
                }
                Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temp);
            }
            return true;
        }
    }

    private static Message readMessage(InputStream in) throws IOException {
        String line = Protocol.readLine(in);
        try {
            return Messages.decode(line);
        } catch (IllegalArgumentException e) {
            throw new IOException("Malformed reply: " + line, e);
        }
    }

    private static Socket connect(InetSocketAddress node) throws IOException {
        return new Socket(node.getHostString(), node.getPort());
    }
}
