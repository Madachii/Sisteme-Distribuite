package ro.ubm.distfs.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import ro.ubm.distfs.client.Client;
import ro.ubm.distfs.client.repl.Repl;
import ro.ubm.distfs.client.repl.ReplCommand;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

@Command(
        name = "client",
        description = "Start an interactive client"
)
public final class ClientCommand implements Callable<Integer> {

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "Show this help message and exit.")
    private boolean helpRequested;

    // TODO: hardcoded until nodes are found through the coordinator.
    private final Client client = new Client("localhost");

    @Override
    public Integer call() {
        List<ReplCommand> commands = List.of(
                new ReplCommand("alloc", "alloc <name> <size>", "Allocate storage for a file",
                        args -> System.out.println("alloc: not implemented yet.")),
                new ReplCommand("put", "put <port> <path>", "Upload a file to a node",
                        this::put),
                new ReplCommand("get", "get <port> <id> <path>", "Download a file from a node",
                        this::get),
                new ReplCommand("locate", "locate <name|id>", "Find file locations",
                        args -> System.out.println("locate: not implemented yet.")),
                new ReplCommand("list", "list [limit] [offset]", "List stored files",
                        args -> System.out.println("list: not implemented yet.")),
                new ReplCommand("del", "del <name|id>", "Delete a file",
                        args -> System.out.println("del: not implemented yet.")),
                new ReplCommand("discover", "discover", "List cluster nodes",
                        args -> System.out.println("discover: not implemented yet."))
        );

        try {
            new Repl(commands).run();
            return 0;
        } catch (IOException e) {
            System.err.println("REPL failed: " + e.getMessage());
            return 1;
        }
    }

    private void put(List<String> args) {
        if (args.size() != 2) {
            System.out.println("Usage: put <port> <path>");
            return;
        }
        try {
            int port = Integer.parseInt(args.get(0));
            UUID id = client.put(port, Path.of(args.get(1)));
            System.out.println("STORED " + id);
        } catch (NumberFormatException e) {
            System.out.println("Invalid port: " + args.get(0));
        } catch (IOException e) {
            System.out.println("put failed: " + e.getMessage());
        }
    }

    private void get(List<String> args) {
        if (args.size() != 3) {
            System.out.println("Usage: get <port> <id> <path>");
            return;
        }
        try {
            int port = Integer.parseInt(args.get(0));
            UUID id = UUID.fromString(args.get(1));
            if (client.get(port, id, Path.of(args.get(2)))) {
                System.out.println("Saved to " + args.get(2));
            } else {
                System.out.println("NOT_FOUND " + id);
            }
        } catch (IllegalArgumentException e) {
            System.out.println("Invalid port or id: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("get failed: " + e.getMessage());
        }
    }
}
