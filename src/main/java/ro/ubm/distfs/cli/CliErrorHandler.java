package ro.ubm.distfs.cli;

import picocli.CommandLine;

/**
 * Turns exceptions that escape a subcommand into a message and an exit code.
 */
public final class CliErrorHandler implements CommandLine.IExecutionExceptionHandler {

    private static final int FAILURE = 1;
    private static final int CONFIG_ERROR = 2;

    @Override
    public int handleExecutionException(Exception ex, CommandLine commandLine, CommandLine.ParseResult parseResult) {
        // picocli normally hands over the original exception; only unwrap if it wrapped one.
        Throwable cause = ex instanceof CommandLine.ExecutionException && ex.getCause() != null
                ? ex.getCause()
                : ex;

        if (cause instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }

        if (cause instanceof IllegalArgumentException) {
            commandLine.getErr().println("Configuration error: " + cause.getMessage());
            commandLine.usage(commandLine.getErr());
            return CONFIG_ERROR;
        }

        commandLine.getErr().println("Error: " + cause.getMessage());
        return FAILURE;
    }
}
