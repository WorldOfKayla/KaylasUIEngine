package org.takesome.kaylasEngine.game.process;

import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Executes an external process and retains only the tail of its merged stdout/stderr stream.
 *
 * <p>The bounded deque avoids the repeated front-deletion and array copying caused by a large
 * {@link StringBuilder}. The class is intentionally independent from any concrete launcher so the
 * engine owns process lifecycle, output capture, interruption handling, and diagnostics.</p>
 */
public final class ProcessExecution {
    private final Logger logger;

    public ProcessExecution(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public Result execute(Request request) throws IOException, InterruptedException {
        Objects.requireNonNull(request, "request");

        ProcessBuilder processBuilder = new ProcessBuilder(request.command());
        processBuilder.directory(request.workingDirectory().toFile());
        processBuilder.redirectErrorStream(true);
        processBuilder.redirectOutput(ProcessBuilder.Redirect.PIPE);
        processBuilder.environment().putAll(request.environment());

        long startedAt = System.nanoTime();
        Process process = processBuilder.start();
        TailTextBuffer output = new TailTextBuffer(request.maxCapturedChars());

        try {
            request.onStarted().run();
            try (BufferedReader reader = process.inputReader(request.outputCharset())) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.appendLine(line);
                }
            }

            int exitCode = process.waitFor();
            return new Result(exitCode, output.content(), Duration.ofNanos(System.nanoTime() - startedAt));
        } catch (InterruptedException interrupted) {
            terminate(process);
            Thread.currentThread().interrupt();
            throw interrupted;
        } catch (IOException | RuntimeException error) {
            terminate(process);
            throw error;
        }
    }

    private void terminate(Process process) {
        if (!process.isAlive()) {
            return;
        }
        process.destroy();
        if (process.isAlive()) {
            process.destroyForcibly();
        }
        logger.debug("External process was terminated during exceptional completion.");
    }

    public record Request(
            List<String> command,
            Path workingDirectory,
            Map<String, String> environment,
            Charset outputCharset,
            int maxCapturedChars,
            Runnable onStarted
    ) {
        public Request {
            Objects.requireNonNull(command, "command");
            Objects.requireNonNull(workingDirectory, "workingDirectory");
            if (command.isEmpty()) {
                throw new IllegalArgumentException("Process command must not be empty.");
            }
            command = List.copyOf(command);
            environment = environment == null
                    ? Map.of()
                    : Map.copyOf(new LinkedHashMap<>(environment));
            outputCharset = outputCharset == null ? StandardCharsets.UTF_8 : outputCharset;
            maxCapturedChars = Math.max(1_024, maxCapturedChars);
            onStarted = onStarted == null ? () -> { } : onStarted;
        }
    }

    public record Result(int exitCode, String output, Duration duration) {
        public Result {
            output = output == null ? "" : output;
            duration = duration == null ? Duration.ZERO : duration;
        }

        public boolean successful() {
            return exitCode == 0;
        }
    }

    private static final class TailTextBuffer {
        private final int maxChars;
        private final Deque<String> chunks = new ArrayDeque<>();
        private int charCount;

        private TailTextBuffer(int maxChars) {
            this.maxChars = Math.max(1, maxChars);
        }

        private void appendLine(String line) {
            String chunk = String.valueOf(line) + System.lineSeparator();
            chunks.addLast(chunk);
            charCount += chunk.length();
            trimToLimit();
        }

        private void trimToLimit() {
            while (charCount > maxChars && !chunks.isEmpty()) {
                String first = chunks.removeFirst();
                int overflow = charCount - maxChars;
                if (first.length() > overflow) {
                    String retained = first.substring(overflow);
                    chunks.addFirst(retained);
                    charCount -= overflow;
                    return;
                }
                charCount -= first.length();
            }
        }

        private String content() {
            StringBuilder result = new StringBuilder(charCount);
            chunks.forEach(result::append);
            return result.toString();
        }
    }
}
