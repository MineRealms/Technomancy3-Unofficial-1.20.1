package theflogat.technomancy.common.machines.processing;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * One family of ore processor — the magic system that pays for it.
 *
 * <p>The original tagged each purified ore with a per-module counter named after the mod
 * ({@code "Thaumcraft"}, {@code "Botania"}, {@code "Blood Magic"}; {@code TileProcessorBase}
 * :11) and refused a third pass by the same module ({@code isProcessable}: {@code < 2}, :91).
 * The ids here are stable lower-case keys instead of display names, because the record is
 * persisted in item NBT and a display name is not a stable identity.</p>
 *
 * <p>The Blood Magic and Botania processors are S3; when they arrive they add their constants
 * here and to {@link #ALL}, and nothing else about the record has to change.</p>
 *
 * @param id        key in the ore's pass record; never shown to players
 * @param maxPasses how many times this module may process the same ore
 */
public record ProcessingModule(String id, int maxPasses) {

    /** {@code TileTCProcessor}: {@code super(0)}, i.e. {@code "Thaumcraft"}. */
    public static final ProcessingModule THAUMCRAFT = new ProcessingModule("thaumcraft", 2);

    /** Every module this build knows; used to render a pass record. */
    public static final ProcessingModule BOTANIA = new ProcessingModule("botania", 2);

    /** Every module this build knows; used to render a pass record. */
    public static final List<ProcessingModule> ALL = List.of(THAUMCRAFT, BOTANIA);

    public ProcessingModule {
        Objects.requireNonNull(id, "id");
        if (id.isEmpty() || !id.equals(id.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("module id must be a non-empty lower-case key: " + id);
        }
        if (maxPasses <= 0) {
            throw new IllegalArgumentException("maxPasses must be positive: " + maxPasses);
        }
    }

    /** The module with this id, or {@code null} for a record written by a build we do not have. */
    @Nullable
    public static ProcessingModule byId(String id) {
        for (ProcessingModule module : ALL) {
            if (module.id().equals(id)) {
                return module;
            }
        }
        return null;
    }

    /** Lang key for the module name shown in a purified ore's tooltip. */
    public String translationKey() {
        return "technom.processing.module." + id;
    }
}
