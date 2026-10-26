package ro.ubm.distfs.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import ro.ubm.distfs.client.Client;
import ro.ubm.distfs.client.repl.Repl;
import ro.ubm.distfs.client.repl.ReplCommand;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

@Command(
        name = "client",
        description = "Start an interactive client"
)
public final class ClientCommand implements Callable<Integer> {

    // TODO: hardcoded until ALLOC/LOCATE return the nodes that hold a file.
    private static final InetSocketAddress NODE = InetSocketAddress.createUnresolved("localhost", 7001);

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "Show this help message and exit.")
    private boolean helpRequested;

    private final Client client = new Client();

    // TODO: remove once LOCATE resolves names through the coordinator.
    private final Map<String, UUID> uploaded = new HashMap<>();

    @Override
    public Integer call() {
        List<ReplCommand> commands = List.of(
                new ReplCommand("alloc", "alloc <name> <size>", "Allocate storage for a file",
                        args -> System.out.println("alloc: not implemented yet.")),
                new ReplCommand("put", "put <name> <path>", "Upload a file",
                        this::put),
                new ReplCommand("get", "get <name|id> <path>", "Download a file",
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
            System.out.println("Usage: put <name> <path>");
            return;
        }
        String name = args.get(0);
        try {
            UUID id = client.put(NODE, name, Path.of(args.get(1)));
            uploaded.put(name, id);
            System.out.println("STORED " + name + " " + id);
        } catch (IOException e) {
            System.out.println("put failed: " + e.getMessage());
        }
    }

    private void get(List<String> args) {
        if (args.size() != 2) {
            System.out.println("Usage: get <name|id> <path>");
            return;
        }
        UUID id = resolve(args.get(0));
        if (id == null) {
            System.out.println("NOT_FOUND " + args.get(0));
            return;
        }
        try {
            if (client.get(NODE, id, Path.of(args.get(1)))) {
                System.out.println("Saved to " + args.get(1));
            } else {
                System.out.println("NOT_FOUND " + args.get(0));
            }
        } catch (IOException e) {
            System.out.println("get failed: " + e.getMessage());
        }
    }

    private UUID resolve(String nameOrId) {
        try {
            return UUID.fromString(nameOrId);
        } catch (IllegalArgumentException e) {
            return uploaded.get(nameOrId);
        }
    }
}
