package ro.ubm.distfs.client.repl.commands;

import ro.ubm.distfs.client.ClientSession;
import ro.ubm.distfs.client.repl.Arg;
import ro.ubm.distfs.client.repl.Command;
import ro.ubm.distfs.client.repl.CommandSpec;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

public final class PutCommand implements Command<PutArgs> {

    private static final CommandSpec<PutArgs> SPEC = CommandSpec.of(
            "put",
            "Upload a file",
            tokens -> new PutArgs(tokens.get(0), Path.of(tokens.get(1))),
            Arg.required("name"),
            Arg.required("path")
    );

    private final ClientSession session;

    public PutCommand(ClientSession session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    @Override
    public CommandSpec<PutArgs> spec() {
        return SPEC;
    }

    @Override
    public void execute(PutArgs args, PrintStream out) {
        try {
            UUID id = session.put(args.name(), args.path());
            out.println("STORED " + args.name() + " " + id);
        } catch (IOException e) {
            out.println("put failed: " + e.getMessage());
        }
    }
}
