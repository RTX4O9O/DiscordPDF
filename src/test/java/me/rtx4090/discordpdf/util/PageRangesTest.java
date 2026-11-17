package me.rtx4090.discordpdf.util;

import me.rtx4090.discordpdf.core.UserFacingException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PageRangesTest {

    @Test
    void parsesSinglesRangesAndOpenEnds() {
        List<PageRanges.Range> r = PageRanges.parse("1-3, 5,8-", 10);
        assertEquals(List.of(new PageRanges.Range(1, 3), new PageRanges.Range(5, 5), new PageRanges.Range(8, 10)), r);
    }

    @Test
    void acceptsOpenStartFullWidthSeparatorsAndSpacesAroundDash() {
        assertEquals(List.of(new PageRanges.Range(1, 3)), PageRanges.parse("-3", 9));
        assertEquals(List.of(new PageRanges.Range(2, 4), new PageRanges.Range(6, 6)), PageRanges.parse("2 - 4，6", 9));
        assertEquals(List.of(new PageRanges.Range(2, 4)), PageRanges.parse("2~4", 9));
    }

    @Test
    void rejectsBadInput() {
        assertThrows(UserFacingException.class, () -> PageRanges.parse("", 5));
        assertThrows(UserFacingException.class, () -> PageRanges.parse("a-b", 5));
        assertThrows(UserFacingException.class, () -> PageRanges.parse("0", 5));
        assertThrows(UserFacingException.class, () -> PageRanges.parse("4-2", 5));
        UserFacingException e = assertThrows(UserFacingException.class, () -> PageRanges.parse("3-9", 5));
        assertEquals("error.range_out_of_bounds", e.key());
    }

    @Test
    void everyChunksAndIndexes() {
        List<PageRanges.Range> r = PageRanges.every(4, 10);
        assertEquals(List.of(new PageRanges.Range(1, 4), new PageRanges.Range(5, 8), new PageRanges.Range(9, 10)), r);
        assertEquals(List.of(0, 1, 4), PageRanges.indexes(List.of(new PageRanges.Range(1, 2), new PageRanges.Range(5, 5))));
        assertEquals("p1-4", r.get(0).label());
        assertEquals("p5", new PageRanges.Range(5, 5).label());
    }
}
