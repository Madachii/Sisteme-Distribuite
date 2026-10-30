package ro.ubm.distfs.client.repl;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Metadata and parsing for one REPL command.
 *
 * <p>Usage and argument bounds are derived from {@link Arg} metadata, so a
 * command only declares its arguments once.</p>
 */
public record CommandSpec<T>(
        String name,
        String description,
        List<Arg> arguments,
        Function<List<String>, T> parser
) {

    public CommandSpec {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(parser, "parser");

        if (name.isBlank() || name.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Command name must be a single non-blank token");
        }
        if (description.isBlank()) {
            throw new IllegalArgumentException("Command description must not be blank");
        }

        arguments = List.copyOf(arguments);
    }

    public static <T> CommandSpec<T> of(
            String name,
            String description,
            Function<List<String>, T> parser,
            Arg... arguments
    ) {
        return new CommandSpec<>(name, description, List.of(arguments), parser);
    }

    public String usage() {
        StringBuilder usage = new StringBuilder(name);
        for (Arg argument : arguments) {
            usage.append(' ').append(argument.render());
        }
        return usage.toString();
    }

    public int minArgs() {
        return (int) arguments.stream().filter(argument -> !argument.optional()).count();
    }

    public int maxArgs() {
        return arguments.size();
    }

    public boolean accepts(List<String> args) {
        Objects.requireNonNull(args, "args");
        return args.size() >= minArgs() && args.size() <= maxArgs();
    }

    public T parse(List<String> args) {
        if (!accepts(args)) {
            throw new IllegalArgumentException("Invalid argument count");
        }
        return parser.apply(args);
    }
}
