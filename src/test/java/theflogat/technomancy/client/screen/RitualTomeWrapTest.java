package theflogat.technomancy.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;

/**
 * The ritual tome's line wrapping, which is the one part of the book that can be checked without
 * a game.
 *
 * <p>The book used to split a page on spaces alone. That was fine while every page was English
 * and every word had a space after it, and it was the reason the book could not be translated:
 * Chinese has no spaces, so a whole page would have been emitted as a single line running off the
 * edge of the book. {@link RitualTomePage#wrap} breaks between characters when there is no space
 * to break at, and these tests pin both behaviours plus the two cases that used to go wrong - a
 * character wider than the whole line, and a space landing exactly on the break.</p>
 *
 * <p>Widths are injected rather than read from a {@code Font}, so the arithmetic is exact and the
 * test needs no client.</p>
 */
class RitualTomeWrapTest {

    /** Every character is one unit wide, so a line's width is its length. */
    private static final ToIntFunction<String> WIDTH = value -> value.length();

    private static List<String> wrap(String paragraph, int maxLength) {
        return RitualTomePage.wrap(paragraph, maxLength, WIDTH);
    }

    @Test
    void englishBreaksAtTheLastSpaceOnTheLine() {
        assertEquals(List.of("one two", "three", "four"), wrap("one two three four", 9));
    }

    /** No space anywhere: the break has to fall between characters or the text runs off. */
    @Test
    void chineseBreaksBetweenCharacters() {
        List<String> lines = wrap("\u4eea\u5f0f\u662f\u5bf9\u81ea\u7136\u529b\u91cf\u7684"
                + "\u5fae\u5f31\u53ec\u5524", 5);
        assertEquals(List.of("\u4eea\u5f0f\u662f\u5bf9\u81ea", "\u7136\u529b\u91cf\u7684\u5fae",
                "\u5f31\u53ec\u5524"), lines);
        for (String line : lines) {
            assertTrue(line.length() <= 5, "line is too long: " + line);
        }
    }

    /** A space that lands exactly on the break must not become a line of its own. */
    @Test
    void aSpaceOnTheBreakDoesNotProduceAnEmptyLine() {
        assertEquals(List.of("abcd", "efgh"), wrap("abcd efgh", 4));
    }

    /**
     * A character wider than the line still gets emitted, on a line of its own. The old code
     * could not make progress here and would have looped forever.
     */
    @Test
    void aCharacterWiderThanTheLineIsStillEmitted() {
        assertEquals(List.of("a", "\u4e2d", "\u6587"), wrap("a\u4e2d\u6587", 1));
    }

    /** Wrapping must not lose or reorder anything. */
    @Test
    void nothingIsLostOrDuplicated() {
        String paragraph = "\u4eea\u5f0f\u662f\u5bf9\u81ea\u7136\u529b\u91cf\u7684\u5fae\u5f31"
                + "\u53ec\u5524\u3002\u5b83\u4eec\u642d\u5efa\u7b80\u5355\uff0c\u8017\u8d39\u7684"
                + "\u8d44\u6e90\u4e5f\u4e0d\u591a\u3002";
        assertEquals(paragraph, String.join("", wrap(paragraph, 7)));
    }

    @Test
    void anEmptyParagraphYieldsOneEmptyLine() {
        assertEquals(List.of(""), wrap("", 10));
    }
}
