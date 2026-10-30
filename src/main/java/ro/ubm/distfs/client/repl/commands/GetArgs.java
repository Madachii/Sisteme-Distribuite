package ro.ubm.distfs.client.repl.commands;

import java.nio.file.Path;

public record GetArgs(String nameOrId, Path path) {
}
