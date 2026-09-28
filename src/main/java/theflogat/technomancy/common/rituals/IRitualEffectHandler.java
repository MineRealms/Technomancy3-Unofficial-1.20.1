package theflogat.technomancy.common.rituals;

import theflogat.technomancy.common.tiles.technom.CatalystBlockEntity;

/**
 * A ritual that keeps working after it was activated ({@code IRitualEffectHandler}): the
 * catalyst calls {@link #applyEffect(CatalystBlockEntity)} every server tick until the effect
 * removes it.
 */
public interface IRitualEffectHandler {

    void applyEffect(CatalystBlockEntity catalyst);
}
