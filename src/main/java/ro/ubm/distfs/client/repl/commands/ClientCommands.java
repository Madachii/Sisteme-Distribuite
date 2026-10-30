package ro.ubm.distfs.client.repl.commands;

import ro.ubm.distfs.client.ClientSession;
import ro.ubm.distfs.client.repl.Arg;
import ro.ubm.distfs.client.repl.Command;

import java.util.List;
import java.util.Objects;

/**
 * The user-facing operations from {@code docs/cerinte.md}, section 1.
 *
 * <p>Internal protocol messages such as ALLOC, REPLICATE and COMMIT are not
 * REPL commands; they belong to the client/coordinator/node protocol layer.</p>
 *
 * <p>This is the only place that needs to change when a command is added or
 * removed.</p>
 */
public final class ClientCommands {

    private ClientCommands() {
    }

    public static List<Command<?>> create(ClientSession session) {
        Objects.requireNonNull(session, "session");

        return List.of(
                new PutCommand(session),
                new GetCommand(session),
                NotImplementedCommand.of(
                        "locate",
                        "Find file locations",
                        Arg.required("name|id")
                ),
                NotImplementedCommand.of(
                        "list",
                        "List stored files",
                        Arg.optional("limit"),
                        Arg.optional("offset")
                ),
                NotImplementedCommand.of(
                        "del",
                        "Delete a file",
                        Arg.required("name|id")
                ),
                NotImplementedCommand.of(
                        "discover",
                        "List cluster nodes"
                )
        );
    }
}
