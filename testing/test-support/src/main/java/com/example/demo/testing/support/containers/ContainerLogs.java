package com.example.demo.testing.support.containers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.function.Consumer;

import org.testcontainers.containers.output.OutputFrame;

/**
 * Writes a container's stdout/stderr to {@code target/container-logs/<name>.log} (appending, one
 * {@code STDOUT: <line>} / {@code STDERR: <line>} per frame, as Testcontainers' Slf4jLogConsumer formats it),
 * where {@code LogScan} reads it. The root pom clears the directory once per run before the ITs.
 */
public final class ContainerLogs {

    private static final Path DIRECTORY = Path.of("target", "container-logs");
    private static final String SUFFIX = ".log";
    private static final String NEWLINE = "\n";
    private static final String PREFIX_SEPARATOR = ": ";

    private ContainerLogs() {
    }

    /** A log consumer appending every frame of the container to {@code <name>.log}. */
    public static Consumer<OutputFrame> toFile(String name) {
        Path file = DIRECTORY.resolve(name + SUFFIX);
        return frame -> {
            if (frame.getType() != OutputFrame.OutputType.END) {
                append(file, frame.getType() + PREFIX_SEPARATOR + frame.getUtf8StringWithoutLineEnding() + NEWLINE);
            }
        };
    }

    private static synchronized void append(Path file, String line) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
