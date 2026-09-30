package theflogat.technomancy.compat.jei;

import dev.tc4port.thaumcraft.api.aspect.AspectId;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import theflogat.technomancy.common.essentia.fuel.EssentiaFuelEntry;
import theflogat.technomancy.common.essentia.fuel.EssentiaFuelTable;

/**
 * One JEI page: an aspect, and what a dynamo pays for one unit of it.
 *
 * <p>This is a view of the data-driven fuel table rather than a recipe. There is nothing to
 * craft and nothing to lay out in slots - what a player cannot otherwise discover is the
 * <em>numbers</em>, because the table lives in a data pack and the dynamo only ever shows its own
 * buffer.</p>
 *
 * <p>One page per aspect rather than one per table row, even though a row can cover a dozen
 * aspects (the shipped table gives {@code machina}, {@code metallum} and six more a single
 * value). A player looks up an aspect, not a row, and a page carrying twelve icons would have
 * nowhere to put its conditions.</p>
 *
 * <p>{@code aspect == null} is the fallback page: the value every aspect the table does not name
 * is worth, including aspects added by other mods. It has no conditions of its own by
 * construction.</p>
 */
public record EssentiaFuelRecipe(@Nullable AspectId aspect, int value,
        List<EssentiaFuelEntry.ConditionalValue> conditions, int randomBonus) {

    public EssentiaFuelRecipe {
        conditions = List.copyOf(conditions);
    }

    /** Every page the table implies, in the table's own aspect order, fallback last. */
    public static List<EssentiaFuelRecipe> all(EssentiaFuelTable table) {
        List<EssentiaFuelRecipe> pages = new ArrayList<>();
        for (AspectId aspect : table.listedAspects()) {
            EssentiaFuelEntry entry = table.entryFor(aspect);
            if (entry != null) {
                pages.add(new EssentiaFuelRecipe(aspect, entry.value(), entry.conditions(),
                        entry.randomBonus()));
            }
        }
        pages.add(new EssentiaFuelRecipe(null, table.fallback(), List.of(), 0));
        return pages;
    }
}
