package ro.ubm.distfs.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import ro.ubm.distfs.client.Client;
import ro.ubm.distfs.client.ClientSession;
import ro.ubm.distfs.client.repl.CommandRegistry;
import ro.ubm.distfs.client.repl.Repl;
import ro.ubm.distfs.client.repl.commands.ClientCommands;

import java.io.IOException;
import java.net.InetSocketAddress;
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

    @Override
    public Integer call() {
        ClientSession session = new ClientSession(new Client(), NODE);
        CommandRegistry registry = new CommandRegistry(ClientCommands.create(session));

        try {
            new Repl(registry).run();
            return 0;
        } catch (IOException e) {
            System.err.println("REPL failed: " + e.getMessage());
            return 1;
        }
    }
}
