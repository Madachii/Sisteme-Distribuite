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

public final class GetCommand implements Command<GetArgs> {

    private static final CommandSpec<GetArgs> SPEC = CommandSpec.of(
            "get",
            "Download a file",
            tokens -> new GetArgs(tokens.get(0), Path.of(tokens.get(1))),
            Arg.required("name|id"),
            Arg.required("path")
    );

    private final ClientSession session;

    public GetCommand(ClientSession session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    @Override
    public CommandSpec<GetArgs> spec() {
        return SPEC;
    }

    @Override
    public void execute(GetArgs args, PrintStream out) {
        UUID id = session.resolve(args.nameOrId());
        if (id == null) {
            out.println("NOT_FOUND " + args.nameOrId());
            return;
        }

        try {
            if (session.get(id, args.path())) {
                out.println("Saved to " + args.path());
            } else {
                out.println("NOT_FOUND " + args.nameOrId());
            }
        } catch (IOException e) {
            out.println("get failed: " + e.getMessage());
        }
    }
}
