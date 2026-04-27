package xyz.fallnight.server;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.slf4j.Logger;

public final class ConsoleCommandBridge implements AutoCloseable {
    private final BufferedReader reader;
    private final Consumer<String> commandConsumer;
    private final Logger logger;
    private final boolean closeReaderOnStop;
    private final AtomicBoolean running;
    private Thread thread;

    public ConsoleCommandBridge(Reader reader, Consumer<String> commandConsumer, Logger logger, boolean closeReaderOnStop) {
        this.reader = new BufferedReader(Objects.requireNonNull(reader, "reader"));
        this.commandConsumer = Objects.requireNonNull(commandConsumer, "commandConsumer");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.closeReaderOnStop = closeReaderOnStop;
        this.running = new AtomicBoolean(false);
    }

    public synchronized void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        thread = new Thread(this::runLoop, "fallnight-console-input");
        thread.setDaemon(true);
        thread.start();
    }

    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        if (closeReaderOnStop) {
            closeQuietly(reader);
        }
    }

    private void runLoop() {
        try {
            while (running.get()) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                try {
                    commandConsumer.accept(trimmed);
                } catch (RuntimeException exception) {
                    logger.error("Failed to execute console command: {}", trimmed, exception);
                }
            }
        } catch (IOException exception) {
            if (running.get()) {
                logger.error("Console command bridge stopped unexpectedly.", exception);
            }
        } finally {
            running.set(false);
        }
    }

    @Override
    public void close() {
        stop();
    }

    private static void closeQuietly(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ignored) {
        }
    }
}
