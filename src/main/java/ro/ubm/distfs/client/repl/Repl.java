package ro.ubm.distfs.client.repl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Line-oriented REPL that dispatches the first token to a registered command.
 */
public final class  Repl {

    private final Map<String, ReplCommand> commands;
    private final BufferedReader in;
    private final PrintStream out;

    public Repl(List<ReplCommand> commands) {
        this(
                commands,
                new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)),
                System.out
        );
    }

    public Repl(List<ReplCommand> commands, BufferedReader in, PrintStream out) {
        Objects.requireNonNull(commands, "commands");
        this.in = Objects.requireNonNull(in, "in");
        this.out = Objects.requireNonNull(out, "out");

        Map<String, ReplCommand> indexed = new LinkedHashMap<>();
        for (ReplCommand command : commands) {
            if (indexed.putIfAbsent(command.name(), command) != null) {
                throw new IllegalArgumentException("Duplicate command: " + command.name());
            }
        }
        this.commands = Collections.unmodifiableMap(indexed);
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

            ReplCommand command = commands.get(name);
            if (command == null) {
                out.printf("Unknown command: %s. Type 'help' for commands.%n", name);
                continue;
            }

            command.execute(args);
        }
    }

    private void printHelp() {
        out.println("Commands:");
        for (ReplCommand command : commands.values()) {
            out.printf("  %-28s %s%n", command.usage(), command.description());
        }
        out.printf("  %-28s %s%n", "help", "Show this help");
        out.printf("  %-28s %s%n", "quit", "Exit");
    }
}
