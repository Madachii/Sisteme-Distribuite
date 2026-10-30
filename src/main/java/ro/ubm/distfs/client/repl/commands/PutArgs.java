package ro.ubm.distfs.client.repl.commands;

import java.nio.file.Path;

public record PutArgs(String name, Path path) {
}
