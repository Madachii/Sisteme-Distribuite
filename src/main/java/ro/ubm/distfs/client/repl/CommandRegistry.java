package ro.ubm.distfs.client.repl;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable registry of available REPL commands.
 *
 * <p>The {@link LinkedHashMap} keeps declaration order for help output and
 * provides lookup by name.</p>
 */
public final class CommandRegistry {

    private final Map<String, Command<?>> byName;

    public CommandRegistry(List<? extends Command<?>> commandList) {
        Objects.requireNonNull(commandList, "commandList");

        Map<String, Command<?>> index = new LinkedHashMap<>();
        for (Command<?> command : commandList) {
            Objects.requireNonNull(command, "command");
            CommandSpec<?> spec = Objects.requireNonNull(command.spec(), "spec");
            if (index.putIfAbsent(spec.name(), command) != null) {
                throw new IllegalArgumentException("Duplicate command: " + spec.name());
            }
        }
        byName = Collections.unmodifiableMap(index);
    }

    public Optional<Command<?>> find(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    public Collection<Command<?>> all() {
        return byName.values();
    }
}
