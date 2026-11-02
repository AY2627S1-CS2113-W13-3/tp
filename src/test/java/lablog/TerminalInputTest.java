package lablog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.jline.keymap.KeyMap;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp.Capability;
import org.junit.jupiter.api.Test;

/** Exercises pager navigation, live resizing and cleanup using JLine's stream terminal. */
class TerminalInputTest {
    @Test
    void pager_pageAndLineKeysClampAtBoundaries() throws Exception {
        try (PipedInputStream input = new PipedInputStream();
                PipedOutputStream keys = new PipedOutputStream(input)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (Terminal terminal = TerminalBuilder.builder().system(false).type("xterm")
                    .streams(input, output).encoding(StandardCharsets.UTF_8).build()) {
                terminal.setSize(new Size(100, 24));
                TerminalInput console = console(terminal);
                String attributes = terminal.getAttributes().toString();
                String text = IntStream.rangeClosed(1, 50).mapToObj(i -> "Result line " + i)
                        .collect(Collectors.joining("\n"));
                CompletableFuture<Void> pager = CompletableFuture.runAsync(
                        () -> console.presentOutput(text, System.out));
                await(() -> output.toString(StandardCharsets.UTF_8).contains("Lines 1-18/50"));
                assertPage(terminal, keys, output, " ", "Lines 19-36/50");
                assertPage(terminal, keys, output, "\r", "Lines 20-37/50");
                assertPage(terminal, keys, output, KeyMap.key(terminal, Capability.key_npage), "Lines 33-50/50");
                assertPage(terminal, keys, output, " ", "Lines 33-50/50");
                assertPage(terminal, keys, output, "b", "Lines 15-32/50");
                assertPage(terminal, keys, output, KeyMap.key(terminal, Capability.key_ppage), "Lines 1-18/50");
                assertPage(terminal, keys, output, "b", "Lines 1-18/50");
                assertFalse(pager.isDone());
                write(keys, "q");
                pager.get(5, TimeUnit.SECONDS);
                assertEquals(attributes, terminal.getAttributes().toString());
            }
        }
    }

    /** Forces a complete redraw to expose offsets, including navigation that stays at a boundary. */
    private void assertPage(Terminal terminal, PipedOutputStream keys, ByteArrayOutputStream output,
                            String navigation, String expected) throws Exception {
        // An ignored key must not close the pager or become the next command.
        write(keys, navigation + "x");
        // Resize polling is bounded at 200 ms; allow queued key handling before requesting a full redraw.
        Thread.sleep(300);
        int start = output.toString(StandardCharsets.UTF_8).length();
        terminal.setSize(new Size(terminal.getSize().getColumns() + 1, 24));
        await(() -> output.toString(StandardCharsets.UTF_8).substring(start).contains(expected));
    }

    @Test
    void pager_arrowsResizeAndQuit_restoreTerminal() throws Exception {
        try (PipedInputStream input = new PipedInputStream();
                PipedOutputStream keys = new PipedOutputStream(input)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (Terminal terminal = TerminalBuilder.builder().system(false).type("xterm")
                    .streams(input, output).encoding(StandardCharsets.UTF_8).build()) {
                terminal.setSize(new Size(80, 24));
                TerminalInput console = console(terminal);
                String attributes = terminal.getAttributes().toString();
                CompletableFuture<Void> pager = CompletableFuture.runAsync(
                        () -> console.presentOutput(new HelpCommands().execute("help"), System.out));
                await(() -> output.toString(StandardCharsets.UTF_8).contains("Lines 1-18/"));
                int beforeDown = output.size();
                write(keys, KeyMap.key(terminal, Capability.key_down));
                await(() -> output.size() > beforeDown);
                // Display emits only changed cells; a full redraw on resize exposes the current offset.
                terminal.setSize(new Size(60, 20));
                await(() -> output.toString(StandardCharsets.UTF_8).contains("Lines 2-15/"));
                int beforeUp = output.size();
                write(keys, KeyMap.key(terminal, Capability.key_up));
                await(() -> output.size() > beforeUp);
                terminal.setSize(new Size(100, 30));
                await(() -> output.toString(StandardCharsets.UTF_8).contains("Lines 1-24/"));
                write(keys, "q");
                pager.get(5, TimeUnit.SECONDS);
                assertEquals(attributes, terminal.getAttributes().toString());
                assertTrue(output.toString(StandardCharsets.UTF_8).contains("HELP"));
                assertTrue(output.toString(StandardCharsets.UTF_8).contains("Space: page"));
            }
        }
    }

    @Test
    void pager_eofRestoresTerminal() throws Exception {
        try (PipedInputStream input = new PipedInputStream();
                PipedOutputStream keys = new PipedOutputStream(input)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (Terminal terminal = TerminalBuilder.builder().system(false).type("xterm")
                    .streams(input, output).build()) {
                terminal.setSize(new Size(80, 24));
                TerminalInput console = console(terminal);
                String attributes = terminal.getAttributes().toString();
                CompletableFuture<Void> pager = CompletableFuture.runAsync(
                        () -> console.presentOutput(new HelpCommands().execute("help"), System.out));
                await(() -> output.toString(StandardCharsets.UTF_8).contains("Lines 1-18/"));
                keys.close();
                pager.get(5, TimeUnit.SECONDS);
                assertEquals(attributes, terminal.getAttributes().toString());
            }
        }
    }

    @Test
    void pager_controlKeysRestoreTerminal() throws Exception {
        for (String close : new String[]{KeyMap.ctrl('C'), KeyMap.ctrl('D')}) {
            try (PipedInputStream input = new PipedInputStream();
                    PipedOutputStream keys = new PipedOutputStream(input)) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                try (Terminal terminal = TerminalBuilder.builder().system(false).type("xterm")
                        .streams(input, output).build()) {
                    terminal.setSize(new Size(80, 24));
                    TerminalInput console = console(terminal);
                    String attributes = terminal.getAttributes().toString();
                    CompletableFuture<Void> pager = CompletableFuture.runAsync(
                            () -> console.presentOutput(new HelpCommands().execute("help"), System.out));
                    await(() -> output.toString(StandardCharsets.UTF_8).contains("Lines 1-18/"));
                    write(keys, close);
                    pager.get(5, TimeUnit.SECONDS);
                    assertEquals(attributes, terminal.getAttributes().toString());
                }
            }
        }
    }

    /** Constructs the input boundary around a disposable terminal, never the real system console. */
    private TerminalInput console(Terminal terminal) throws Exception {
        Constructor<TerminalInput> constructor = TerminalInput.class.getDeclaredConstructor(Terminal.class);
        constructor.setAccessible(true);
        return constructor.newInstance(terminal);
    }

    private void write(PipedOutputStream keys, String text) throws Exception {
        keys.write(text.getBytes(StandardCharsets.UTF_8));
        keys.flush();
    }

    /** Bounds asynchronous redraw waits so failures cannot leave a CI worker hanging. */
    private void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertTrue(condition.getAsBoolean(), "Pager did not redraw before the timeout");
    }
}
