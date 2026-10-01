//@wait 1
// Reports what a running client actually believes about Jade: whether the mod is in the mod list
// (the same list the Mods screen draws) and what its live display switches are set to. The boot log
// already proves the plugin loader ran; this proves the overlay is allowed to draw.
net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
StringBuilder out = new StringBuilder();

net.minecraftforge.fml.ModList mods = net.minecraftforge.fml.ModList.get();
java.util.Optional<? extends net.minecraftforge.fml.ModContainer> jade = mods.getModContainerById("jade");
out.append("jade in mod list: ").append(jade.isPresent()).append("\n");
out.append("jade version: ")
   .append(jade.map(c -> c.getModInfo().getVersion().toString()).orElse("-")).append("\n");
out.append("total mods: ").append(mods.getMods().size()).append("\n");
out.append("mod ids: ")
   .append(mods.getMods().stream().map(m -> m.getModId()).sorted()
           .reduce((a, b) -> a + " " + b).orElse(""))
   .append("\n");

// Jade freezes its config once the client is up; a frozen config is a loaded config.
out.append("config frozen: ").append(snownee.jade.Jade.FROZEN).append("\n");
out.append("client level joined: ").append(mc.level != null).append("\n");
out.append("overlay currently shown: ").append(snownee.jade.overlay.OverlayRenderer.shown).append("\n");
out.append("overlay keys: showOverlay=").append(snownee.jade.JadeClient.showOverlay.getKey().getName())
   .append(" config=").append(snownee.jade.JadeClient.openConfig.getKey().getName()).append("\n");
try {
    snownee.jade.api.config.IWailaConfig.IConfigGeneral g =
            snownee.jade.api.config.IWailaConfig.get().getGeneral();
    out.append("displayTooltip: ").append(g.shouldDisplayTooltip()).append("\n");
    out.append("displayBlocks: ").append(g.getDisplayBlocks()).append("\n");
    out.append("displayEntities: ").append(g.getDisplayEntities()).append("\n");
    out.append("displayMode: ").append(g.getDisplayMode()).append("\n");
    out.append("hideFromDebug: ").append(g.shouldHideFromDebug()).append("\n");
    out.append("hideFromTabList: ").append(g.shouldHideFromTabList()).append("\n");
    out.append("debug: ").append(g.isDebug()).append("\n");
    out.append("reachDistance: ").append(g.getReachDistance()).append("\n");
    out.append("builtinCamouflage: ").append(g.getBuiltinCamouflage()).append("\n");
} catch (Throwable t) {
    out.append("config read threw: ").append(t).append("\n");
}
return out.toString();
