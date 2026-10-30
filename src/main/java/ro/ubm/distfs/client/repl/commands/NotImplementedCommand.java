package ro.ubm.distfs.client.repl.commands;

import ro.ubm.distfs.client.repl.Arg;
import ro.ubm.distfs.client.repl.Command;
import ro.ubm.distfs.client.repl.CommandSpec;

import java.io.PrintStream;
import java.util.List;
import java.util.Objects;

public final class NotImplementedCommand implements Command<List<String>> {

    private final CommandSpec<List<String>> spec;

    public NotImplementedCommand(CommandSpec<List<String>> spec) {
        this.spec = Objects.requireNonNull(spec, "spec");
    }

    public static NotImplementedCommand of(String name, String description, Arg... arguments) {
        return new NotImplementedCommand(CommandSpec.of(
                name,
                description,
                List::copyOf,
                arguments
        ));
    }

    @Override
    public CommandSpec<List<String>> spec() {
        return spec;
    }

    @Override
    public void execute(List<String> args, PrintStream out) {
        out.println(spec.name() + ": not implemented yet.");
    }
}
