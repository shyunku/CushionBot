package service.watcher;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One open SSE connection. Messages are queued without blocking; a writer thread drains the queue.
 * A client that falls too far behind (queue full) is disconnected instead of slowing everyone down.
 */
public class SseClient {
    private static final int QUEUE_SIZE = 256;
    /** Closing a chunked stream writes to the socket, so it is done off the caller's thread. */
    private static final ExecutorService closer = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "sse-close");
        t.setDaemon(true);
        return t;
    });

    private final HttpExchange exchange;
    private final OutputStream out;
    private final ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<>(QUEUE_SIZE);
    private final AtomicBoolean draining = new AtomicBoolean(false);
    private volatile boolean closed = false;

    public SseClient(HttpExchange exchange) {
        this.exchange = exchange;
        this.out = exchange.getResponseBody();
    }

    public void offer(String message) {
        if (closed) return;
        if (!queue.offer(message)) {
            close();
            return;
        }
        if (draining.compareAndSet(false, true)) {
            Watcher.writers.execute(this::drain);
        }
    }

    private void drain() {
        try {
            String message;
            while (!closed && (message = queue.poll()) != null) {
                out.write(message.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        } catch (IOException e) {
            close();
        } finally {
            draining.set(false);
            // a message may have arrived after the loop ended
            if (!closed && !queue.isEmpty() && draining.compareAndSet(false, true)) {
                Watcher.writers.execute(this::drain);
            }
        }
    }

    public void close() {
        if (closed) return;
        closed = true;
        Watcher.unregister(this);
        queue.clear();
        closer.execute(() -> {
            try {
                out.close();
            } catch (IOException ignored) {
            }
            exchange.close();
        });
    }
}
