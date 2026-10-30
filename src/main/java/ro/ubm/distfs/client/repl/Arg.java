package ro.ubm.distfs.client.repl;

import java.util.Objects;

/**
 * One argument in a command usage line.
 *
 * <p>The label is used verbatim between {@code <>} or {@code []}; it can be a
 * choice such as {@code name|id}.</p>
 */
public record Arg(String label, boolean optional) {

    public Arg {
        Objects.requireNonNull(label, "label");
        if (label.isBlank() || label.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Argument label must be a single non-blank token");
        }
    }

    public static Arg required(String label) {
        return new Arg(label, false);
    }

    public static Arg optional(String label) {
        return new Arg(label, true);
    }

    public String render() {
        return optional ? "[" + label + "]" : "<" + label + ">";
    }
}
