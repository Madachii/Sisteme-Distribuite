package ro.ubm.distfs.client.repl;

import java.io.PrintStream;

/**
 * One command available in the interactive client.
 *
 * <p>The command receives a typed argument object. Argument count is validated
 * and the tokens are parsed by {@link CommandSpec} before {@link #execute} is
 * called.</p>
 */
public interface Command<T> {

    CommandSpec<T> spec();

    void execute(T args, PrintStream out);
}
