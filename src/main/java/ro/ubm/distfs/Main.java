package ro.ubm.distfs;

import picocli.CommandLine;
import ro.ubm.distfs.cli.CliErrorHandler;
import ro.ubm.distfs.cli.DistFsCommand;

public final class Main {

    public static void main(String[] args) {
        System.exit(new CommandLine(new DistFsCommand())
                .setExecutionExceptionHandler(new CliErrorHandler())
                .execute(args));
    }
}
