package ro.ubm.distfs.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.util.concurrent.Callable;

@Command(
        name = "DistFS",
        description = "Distributed file system",
        subcommands = { NodeCommand.class, ClientCommand.class }
)
public final class DistFsCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "Show this help message and exit.")
    private boolean helpRequested;

    @Override
    public Integer call() {
        spec.commandLine().usage(System.err);
        return 1;
    }
}
