package theflogat.technomancy.gametest;

/**
 * Shared GameTest structure templates, resolved as {@code technom:<name>} because the test
 * holders use {@code @PrefixGameTestTemplate(false)}.
 *
 * <p>Files live in {@code data/technom/structures/gametest/}, are produced by
 * {@code tools/gen_gametest_structures.py} and are excluded from the release JAR.</p>
 */
final class GameTestTemplates {
    /** 5x5x5 room: smooth-stone floor at y=0, air above. */
    static final String EMPTY_5X5X5 = "gametest/empty_5x5x5";
    /** 7x5x9 room: fits a facing node-fabricator pair and both of its slabs. */
    static final String EMPTY_7X5X9 = "gametest/empty_7x5x9";

    private GameTestTemplates() {}
}
