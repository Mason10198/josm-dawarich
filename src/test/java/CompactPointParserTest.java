package org.openstreetmap.josm.plugins.dawarich;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompactPointParserTest {
    private static List<ReferencePoint> parse(String json, int maxRows) {
        return CompactPointParser.parse(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), maxRows);
    }

    @Test void parsesExactIdsRawCoordinatesAndUnknownNestedFields() {
        List<ReferencePoint> points = parse("[{\"id\":9007199254740993,\"latitude\":\"0.12345678901234567\",\"longitude\":-0.0000000000000001,\"timestamp\":1700000000,\"other\":{\"x\":[1,true,null]}},{\"id\":\"2\",\"latitude\":0,\"longitude\":\"0\",\"timestamp\":0}]", 2);
        assertEquals(2, points.size());
        assertEquals(9007199254740993L, points.get(0).id());
        assertEquals(Double.parseDouble("0.12345678901234567"), points.get(0).latitude());
        assertEquals(-0.0000000000000001d, points.get(0).longitude());
        assertEquals(1700000000L, points.get(0).timestamp());
        assertEquals(2L, points.get(1).id());
        assertThrows(UnsupportedOperationException.class, () -> points.add(points.get(0)));
    }

    @Test void acceptsEmptyArrayAndZeroLimit() { assertTrue(parse("[]", 0).isEmpty()); }

    @Test void rejectsMalformedTopLevelAndTrailingContent() {
        for (String json : List.of("{}", "<html>", "[] {}", "[] true"))
            assertThrows(IllegalArgumentException.class, () -> parse(json, 4), json);
    }

    @Test void rejectsMissingNullDuplicateAndWrongTypedRequiredFields() {
        for (String row : List.of("{\"id\":1,\"latitude\":0,\"longitude\":0}",
                "{\"id\":1,\"latitude\":null,\"longitude\":0,\"timestamp\":0}",
                "{\"id\":1,\"id\":2,\"latitude\":0,\"longitude\":0,\"timestamp\":0}",
                "{\"id\":true,\"latitude\":0,\"longitude\":0,\"timestamp\":0}"))
            assertThrows(IllegalArgumentException.class, () -> parse("[" + row + "]", 1));
    }

    @Test void rejectsFractionalOverflowAndNonpositiveIdsAndTimestamps() {
        for (String id : List.of("1.5", "9223372036854775808", "0", "-1", "\"01\""))
            assertThrows(IllegalArgumentException.class, () -> parse(row(id, "0", "0", "0"), 1));
        for (String timestamp : List.of("1.2", "9223372036854775808", "-1"))
            assertThrows(IllegalArgumentException.class, () -> parse(row("1", "0", "0", timestamp), 1));
    }

    @Test void rejectsInvalidCoordinates() {
        for (String coordinate : List.of("null", "\"NaN\"", "\"Infinity\"", "91", "-91", "181", "-181"))
            assertThrows(IllegalArgumentException.class, () -> parse(row("1", coordinate, "0", "0"), 1));
    }

    @Test void enforcesRowLimitWithoutTruncationAndRejectsDuplicateIds() {
        assertThrows(IllegalArgumentException.class, () -> parse("[" + rowBody("1") + "," + rowBody("2") + "]", 1));
        assertThrows(IllegalArgumentException.class, () -> parse("[" + rowBody("1") + "," + rowBody("1") + "]", 2));
    }

    @Test void doesNotExposeInputValuesInPointStringOrParseError() {
        ReferencePoint p = parse("[" + rowBody("1") + "]", 1).get(0);
        assertFalse(p.toString().contains("latitude"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> parse("[\"secret coordinate 123\"]", 1));
        assertFalse(ex.getMessage().contains("secret"));
        assertNull(ex.getCause());
    }

    private static String row(String id, String lat, String lon, String time) {
        return "[{\"id\":" + id + ",\"latitude\":" + lat + ",\"longitude\":" + lon + ",\"timestamp\":" + time + "]";
    }
    private static String rowBody(String id) { return "{\"id\":" + id + ",\"latitude\":\"0\",\"longitude\":\"0\",\"timestamp\":0}"; }
}
