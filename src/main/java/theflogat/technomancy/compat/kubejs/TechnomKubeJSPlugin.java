package theflogat.technomancy.compat.kubejs;

import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingsEvent;

/**
 * KubeJS integration.
 *
 * <p>Found through {@code kubejs.plugins.txt} at the root of this jar rather than through an
 * annotation, which is how KubeJS discovers plugins - including its own. The file is inert in a
 * game without KubeJS, and so is this class, which is the point: KubeJS is a {@code compileOnly}
 * dependency here and is never on the development runtime.</p>
 *
 * <p>Only {@link #registerBindings} is overridden. There is nothing to add to the recipe system
 * (every recipe this mod ships is a plain data-pack recipe a pack can already replace) and no
 * event of this mod's own that a script could listen for, so an empty override for either would
 * only be a claim that something is there.</p>
 */
public final class TechnomKubeJSPlugin extends KubeJSPlugin {

    @Override
    public void registerBindings(BindingsEvent event) {
        // The class, not an instance: everything on it is static, and KubeJS binds its own
        // helpers (ResourceLocation, Duration) the same way.
        event.add("Technom", TechnomJS.class);
    }
}
