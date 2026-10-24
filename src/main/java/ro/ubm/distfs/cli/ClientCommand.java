package ro.ubm.distfs.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import ro.ubm.distfs.client.repl.Repl;
import ro.ubm.distfs.client.repl.ReplCommand;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "client",
        description = "Start an interactive client"
)
public final class ClientCommand implements Callable<Integer> {

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "Show this help message and exit.")
    private boolean helpRequested;

    @Override
    public Integer call() {
        List<ReplCommand> commands = List.of(
                new ReplCommand("alloc", "alloc <name> <size>", "Allocate storage for a file",
                        args -> System.out.println("alloc: not implemented yet.")),
                new ReplCommand("put", "put <name> <path>", "Upload a file",
                        args -> System.out.println("put: not implemented yet.")),
                new ReplCommand("get", "get <id> <path>", "Download a file",
                        args -> System.out.println("get: not implemented yet.")),
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
}
