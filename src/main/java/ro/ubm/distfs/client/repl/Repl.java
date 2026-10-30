package ro.ubm.distfs.client.repl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Line-oriented REPL that dispatches the first token to a registered command.
 */
public final class Repl {

    private final CommandRegistry registry;
    private final BufferedReader in;
    private final PrintStream out;

    public Repl(CommandRegistry registry) {
        this(
                registry,
                new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)),
                System.out
        );
    }

    public Repl(CommandRegistry registry, BufferedReader in, PrintStream out) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.in = Objects.requireNonNull(in, "in");
        this.out = Objects.requireNonNull(out, "out");
    }

    public void run() throws IOException {
        out.println("Type 'help' for commands, 'quit' to exit.");

        while (true) {
            out.print("distfs> ");
            out.flush();

            String line = in.readLine();
            if (line == null) {
                out.println();
                return;
            }

            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            List<String> tokens = Arrays.asList(line.split("\\s+"));
            String name = tokens.getFirst();
            List<String> args = tokens.subList(1, tokens.size());

            if (name.equals("quit") || name.equals("exit")) {
                return;
            }
            if (name.equals("help")) {
                printHelp();
                continue;
            }

            Command<?> command = registry.find(name).orElse(null);
            if (command == null) {
                out.printf("Unknown command: %s. Type 'help' for commands.%n", name);
                continue;
            }

            execute(command, args, out);
        }
    }

    private static <T> void execute(Command<T> command, List<String> args, PrintStream out) {
        T parsed;
        try {
            parsed = command.spec().parse(args);
        } catch (IllegalArgumentException e) {
            out.println("Usage: " + command.spec().usage());
            return;
        }
        command.execute(parsed, out);
    }

    private void printHelp() {
        out.println("Commands:");
        for (Command<?> command : registry.all()) {
            CommandSpec<?> spec = command.spec();
            out.printf("  %-28s %s%n", spec.usage(), spec.description());
        }
        out.printf("  %-28s %s%n", "help", "Show this help");
        out.printf("  %-28s %s%n", "quit", "Exit");
    }
}
