package org.openstreetmap.josm.plugins.dawarich;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CompletableFuture;

/** Shared hard limits and cooperative cancellation for one fetch batch. */
public final class BatchBudget {
    public static final int DEFAULT_REQUESTS = 128;
    public static final long DEFAULT_BYTES = 64L * 1024 * 1024;
    public static final int DEFAULT_RESPONSE_BYTES = 8 * 1024 * 1024;
    private final int maxRequests, maxResponseBytes;
    private final long maxBytes, deadlineNanos;
    private final AtomicLong requests = new AtomicLong(), bytes = new AtomicLong();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private CompletableFuture<?> active;

    public BatchBudget() { this(DEFAULT_REQUESTS, Duration.ofSeconds(60), DEFAULT_BYTES, DEFAULT_RESPONSE_BYTES); }
    BatchBudget(int maxRequests, Duration duration, long maxBytes, int maxResponseBytes) {
        if (maxRequests < 1 || duration.isNegative() || duration.isZero() || maxBytes < 1 || maxResponseBytes < 1)
            throw new IllegalArgumentException("Invalid batch limits");
        this.maxRequests=maxRequests; this.maxBytes=maxBytes; this.maxResponseBytes=maxResponseBytes;
        this.deadlineNanos=System.nanoTime()+duration.toNanos();
    }
    public void beginRequest() throws DawarichException {
        check(); if (requests.incrementAndGet()>maxRequests) throw new DawarichException(DawarichException.Category.LIMIT,"Request limit exceeded; narrow the area or dates"); check();
    }
    public void addBytes(int n) throws DawarichException {
        check(); if (n<0 || bytes.addAndGet(n)>maxBytes) throw new DawarichException(DawarichException.Category.LIMIT,"Download byte limit exceeded; narrow the area or dates"); check();
    }
    public void check() throws DawarichException {
        if (cancelled.get()) throw DawarichException.cancelled();
        if (remainingNanos()<=0) throw DawarichException.deadline();
    }
    long remainingNanos() { return Math.max(0, deadlineNanos-System.nanoTime()); }
    int maxResponseBytes(){return maxResponseBytes;}
    public synchronized void register(CompletableFuture<?> future) throws DawarichException {
        check();
        if (active != null) throw DawarichException.limit();
        active=future;
        if (cancelled.get()) { active=null; future.cancel(true); throw DawarichException.cancelled(); }
    }
    public synchronized void unregister(CompletableFuture<?> future) { if(active==future) active=null; }
    public synchronized void cancel(){cancelled.set(true); if(active!=null) active.cancel(true);}
    public boolean isCancelled(){return cancelled.get();}
}
