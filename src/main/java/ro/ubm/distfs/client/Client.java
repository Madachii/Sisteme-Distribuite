package ro.ubm.distfs.client;

import com.github.f4b6a3.uuid.UuidCreator;
import ro.ubm.distfs.common.Protocol;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * Talks to storage nodes over TCP.
 * For now the node is addressed directly by port; ALLOC through the coordinator comes later.
 */
public final class Client {

    private final String host;

    public Client(String host) {
        this.host = host;
    }

    public UUID put(int port, Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("Not a file: " + file);
        }

        // TODO: the ID should come from the coordinator (ALLOC).
        UUID id = UuidCreator.getTimeOrderedEpoch();
        long size = Files.size(file);
        String checksum = checksum(file);

        try (Socket socket = new Socket(host, port);
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
             DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
             InputStream fileIn = Files.newInputStream(file)) {

            out.writeUTF(Protocol.PUT);
            out.writeUTF(id.toString());
            out.writeUTF(file.getFileName().toString());
            out.writeLong(size);
            out.writeUTF(checksum);
            out.writeUTF(""); // TODO: node for the replica, from ALLOC
            Protocol.copy(fileIn, out, size, null);
            out.flush();

            String reply = in.readUTF();
            if (reply.equals(Protocol.STORED)) {
                return id;
            }
            if (reply.equals(Protocol.ERROR)) {
                throw new IOException("Node refused file: " + in.readUTF());
            }
            throw new IOException("Unexpected reply: " + reply);
        }
    }

    /**
     * @return false if the node does not have the file
     */
    public boolean get(int port, UUID id, Path destination) throws IOException {
        try (Socket socket = new Socket(host, port);
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
             DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()))) {

            out.writeUTF(Protocol.GET);
            out.writeUTF(id.toString());
            out.flush();

            String reply = in.readUTF();
            if (reply.equals(Protocol.NOT_FOUND)) {
                return false;
            }
            if (!reply.equals(Protocol.FILE)) {
                throw new IOException("Unexpected reply: " + reply);
            }

            long size = in.readLong();
            Path parent = destination.toAbsolutePath().getParent();
            Path temp = Files.createTempFile(parent, ".distfs-", ".part");
            try {
                try (OutputStream fileOut = Files.newOutputStream(temp)) {
                    Protocol.copy(in, fileOut, size, null);
                }
                Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temp);
            }
            return true;
        }
    }

    private static String checksum(Path file) throws IOException {
        MessageDigest digest = Protocol.newDigest();
        try (InputStream in = Files.newInputStream(file)) {
            Protocol.copy(in, OutputStream.nullOutputStream(), Files.size(file), digest);
        }
        return Protocol.hex(digest);
    }
}
