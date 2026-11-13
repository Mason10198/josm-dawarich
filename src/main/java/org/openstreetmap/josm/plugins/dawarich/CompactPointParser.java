package org.openstreetmap.josm.plugins.dawarich;

import jakarta.json.Json;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Strict streaming parser for the compact reference-point response. */
public final class CompactPointParser {
    private static final String INVALID = "Invalid reference-point response";
    private static final Set<String> REQUIRED = Set.of("id", "latitude", "longitude", "timestamp");
    private CompactPointParser() { }

    /** Parses at most {@code maxRows}; malformed or oversized input is rejected, never truncated. */
    public static List<ReferencePoint> parse(InputStream input, int maxRows) {
        if (input == null || maxRows < 0) throw invalid();
        try (JsonParser parser = Json.createParser(input)) {
            if (!parser.hasNext() || parser.next() != Event.START_ARRAY) throw invalid();
            List<ReferencePoint> points = new ArrayList<>(Math.min(maxRows, 256));
            Set<Long> ids = new HashSet<>();
            while (parser.hasNext()) {
                Event e = parser.next();
                if (e == Event.END_ARRAY) {
                    if (parser.hasNext()) throw invalid();
                    return List.copyOf(points);
                }
                if (e != Event.START_OBJECT || points.size() >= maxRows) throw invalid();
                ReferencePoint point = readPoint(parser);
                if (!ids.add(point.id())) throw invalid();
                points.add(point);
            }
            throw invalid();
        } catch (IllegalArgumentException ex) {
            if (INVALID.equals(ex.getMessage()) && ex.getCause() == null) throw ex;
            throw invalid();
        } catch (RuntimeException ex) {
            throw invalid();
        }
    }

    private static ReferencePoint readPoint(JsonParser p) {
        Long id = null, timestamp = null;
        Double latitude = null, longitude = null;
        Set<String> seen = new HashSet<>();
        int nested = 0;
        String key = null;
        while (p.hasNext()) {
            Event e = p.next();
            if (nested > 0) {
                if (e == Event.START_ARRAY || e == Event.START_OBJECT) {
                    if (++nested > 32) throw invalid();
                } else if (e == Event.END_ARRAY || e == Event.END_OBJECT) nested--;
                continue;
            }
            if (e == Event.KEY_NAME) { key = p.getString(); continue; }
            if (e == Event.END_OBJECT) {
                if (id == null || latitude == null || longitude == null || timestamp == null) throw invalid();
                return new ReferencePoint(id, latitude, longitude, timestamp);
            }
            if (key == null) {
                if (e == Event.START_ARRAY || e == Event.START_OBJECT) nested = 1;
                continue;
            }
            String current = key; key = null;
            if (!REQUIRED.contains(current)) {
                if (e == Event.START_ARRAY || e == Event.START_OBJECT) nested = 1;
                continue;
            }
            if (!seen.add(current)) throw invalid();
            switch (current) {
                case "id" -> id = exactPositiveLong(p, e, true);
                case "timestamp" -> timestamp = exactPositiveLong(p, e, false);
                case "latitude" -> latitude = coordinate(p, e, 90);
                case "longitude" -> longitude = coordinate(p, e, 180);
                default -> throw invalid();
            }
        }
        throw invalid();
    }

    private static Long exactPositiveLong(JsonParser p, Event e, boolean positive) {
        try {
            BigDecimal value;
            if (e == Event.VALUE_NUMBER) value = p.getBigDecimal();
            else if (e == Event.VALUE_STRING) {
                String text = p.getString();
                if (!text.matches("0|[1-9][0-9]*")) throw invalid();
                value = new BigDecimal(text);
            } else throw invalid();
            long n = value.longValueExact();
            if ((positive && n <= 0) || n < 0) throw invalid();
            return n;
        } catch (ArithmeticException ex) { throw invalid(); }
    }

    private static Double coordinate(JsonParser p, Event e, double limit) {
        try {
            BigDecimal value;
            if (e == Event.VALUE_NUMBER) value = p.getBigDecimal();
            else if (e == Event.VALUE_STRING) value = new BigDecimal(p.getString());
            else throw invalid();
            double d = value.doubleValue();
            if (!Double.isFinite(d) || d < -limit || d > limit) throw invalid();
            return d;
        } catch (NumberFormatException ex) { throw invalid(); }
    }

    private static IllegalArgumentException invalid() { return new IllegalArgumentException(INVALID); }
}
