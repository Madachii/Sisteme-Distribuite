package ro.ubm.distfs.client.repl;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A command available in the interactive client.
 */
public record ReplCommand(String name, String usage, String description, Consumer<List<String>> handler) {

    public ReplCommand {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(usage, "usage");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(handler, "handler");
    }

    public void execute(List<String> args) {
        handler.accept(args);
    }
}
