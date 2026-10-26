package ro.ubm.distfs.client;

import com.github.f4b6a3.uuid.UuidCreator;
import ro.ubm.distfs.common.Protocol;

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
import java.security.MessageDigest;
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
            Protocol.writeLine(out, Protocol.PUT, id.toString(), name, Long.toString(size), checksum);
            Protocol.copy(fileIn, out, size, null);
            out.flush();

            String reply = Protocol.readLine(in);
            if (reply.equals(Protocol.STORED)) {
                return id;
            }
            if (reply.startsWith(Protocol.ERROR)) {
                throw new IOException("Node refused file: " + reply.substring(Protocol.ERROR.length()).strip());
            }
            throw new IOException("Unexpected reply: " + reply);
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

            Protocol.writeLine(out, Protocol.GET, id.toString());
            out.flush();

            String[] reply = Protocol.readLine(in).split(" ");
            if (reply[0].equals(Protocol.NOT_FOUND)) {
                return false;
            }
            if (!reply[0].equals(Protocol.FILE) || reply.length != 3) {
                throw new IOException("Unexpected reply: " + String.join(" ", reply));
            }
            long size;
            try {
                size = Long.parseLong(reply[1]);
            } catch (NumberFormatException e) {
                throw new IOException("Invalid size in reply: " + reply[1]);
            }
            String expected = reply[2];

            Path parent = destination.toAbsolutePath().getParent();
            Path temp = Files.createTempFile(parent, ".distfs-", ".part");
            try {
                MessageDigest digest = Protocol.newDigest();
                try (OutputStream fileOut = Files.newOutputStream(temp)) {
                    Protocol.copy(in, fileOut, size, digest);
                }
                String actual = Protocol.hex(digest);
                if (!actual.equalsIgnoreCase(expected)) {
                    throw new IOException("Checksum mismatch: expected " + expected + ", got " + actual);
                }
                Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temp);
            }
            return true;
        }
    }

    private static Socket connect(InetSocketAddress node) throws IOException {
        return new Socket(node.getHostString(), node.getPort());
    }
}
