package ro.ubm.distfs.client;

import com.github.f4b6a3.uuid.UuidCreator;
import ro.ubm.distfs.common.AtomicFile;
import ro.ubm.distfs.common.Connection;
import ro.ubm.distfs.common.Protocol;
import ro.ubm.distfs.common.message.Error;
import ro.ubm.distfs.common.message.File;
import ro.ubm.distfs.common.message.Get;
import ro.ubm.distfs.common.message.Message;
import ro.ubm.distfs.common.message.NotFound;
import ro.ubm.distfs.common.message.Put;
import ro.ubm.distfs.common.message.Stored;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
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

        try (Connection connection = Connection.open(node);
             InputStream fileIn = Files.newInputStream(file)) {

            // TODO: add the node for the replica, from ALLOC
            connection.send(new Put(id, name, size, checksum, null), fileIn, size);

            Message reply = connection.receive();
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
        try (Connection connection = Connection.open(node)) {

            connection.send(new Get(id));

            Message reply = connection.receive();
            if (reply instanceof NotFound) {
                return false;
            }
            if (!(reply instanceof File file)) {
                throw new IOException("Unexpected reply: " + reply.encode());
            }

            long size = file.size();
            String expected = file.checksum();

            AtomicFile.write(destination, out -> connection.receiveBody(out, size, expected));
            return true;
        }
    }
}
