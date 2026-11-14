package org.openstreetmap.josm.plugins.dawarich;

import java.util.*;

/** Retrieves the newest bounded points by probing page one and bisecting saturated intervals. */
public final class MostRecentFetcher {
    private final TimeWindowFetcher.PageFetcher fetcher;
    private final DawarichClient client;

    public MostRecentFetcher(DawarichClient client) {
        this.client = Objects.requireNonNull(client);
        this.fetcher = null;
    }

    public MostRecentFetcher(TimeWindowFetcher.PageFetcher fetcher) {
        this.fetcher = Objects.requireNonNull(fetcher);
        this.client = null;
    }

    public ReferenceDataset fetch(String server, String sourceId, String key, QueryBounds bounds,
                                  long requestedPoints, BatchBudget budget) throws DawarichException {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(budget, "budget");
        if (server == null || server.isBlank() || sourceId == null || sourceId.isBlank()
                || requestedPoints < 1 || requestedPoints > LoadOptions.MAX_TOTAL_POINTS
                || (bounds.options().mode() == LoadOptions.Mode.MOST_RECENT
                    && requestedPoints != bounds.options().recentPointsPerSource())) {
            throw protocol("Invalid dataset parameters");
        }
        TimeWindowFetcher.PageFetcher actual = client == null ? fetcher
                : (start, end) -> client.fetchPageDescending(key, bounds, start, end, budget);
        record Window(long start, long end) {}
        ArrayDeque<Window> pending = new ArrayDeque<>();
        pending.push(new Window(bounds.startInclusive().getEpochSecond(), bounds.endInclusive().getEpochSecond()));
        List<ReferencePoint> result = new ArrayList<>();
        Set<Long> ids = new HashSet<>();

        while (!pending.isEmpty() && result.size() < requestedPoints) {
            budget.check();
            Window window = pending.pop();
            DawarichClient.Page page = actual.fetch(window.start(), window.end());
            budget.check();
            List<ReferencePoint> points = page.points();
            long previous = Long.MAX_VALUE;
            Set<Long> pageIds = new HashSet<>();
            for (ReferencePoint point : points) {
                budget.check();
                if (point.timestamp() > previous) throw protocol("Unexpected point order");
                previous = point.timestamp();
                if (!pageIds.add(point.id())) throw protocol("Duplicate point identifier");
                if (point.timestamp() < window.start() || point.timestamp() > window.end()
                        || point.timestamp() < bounds.startInclusive().getEpochSecond()
                        || point.timestamp() > bounds.endInclusive().getEpochSecond()
                        || point.latitude() < bounds.minLat() || point.latitude() > bounds.maxLat()
                        || point.longitude() < bounds.minLon() || point.longitude() > bounds.maxLon())
                    throw protocol("Point outside requested bounds");
            }
            if (page.totalPages() < 0 || points.size() > 10000 || (page.totalPages() == 0 && !points.isEmpty()))
                throw protocol("Contradictory pagination response");
            if (page.totalPages() > 1 && points.size() < requestedPoints - result.size()) {
                if (window.start() == window.end()) throw protocol("Too many points within one second; narrow the area");
                long middle = window.start() + (window.end() - window.start()) / 2;
                pending.push(new Window(window.start(), middle));
                pending.push(new Window(middle + 1, window.end())); // LIFO: newer interval first.
                continue;
            }
            int take = (int)Math.min(requestedPoints - result.size(), points.size());
            for (int i = 0; i < take; i++) {
                budget.check();
                ReferencePoint point = points.get(i);
                if (!ids.add(point.id())) throw protocol("Duplicate point identifier");
                result.add(point);
            }
        }
        return new ReferenceDataset(server, sourceId, bounds, result);
    }

    private static DawarichException protocol(String message) {
        return new DawarichException(DawarichException.Category.PROTOCOL, message);
    }
}
