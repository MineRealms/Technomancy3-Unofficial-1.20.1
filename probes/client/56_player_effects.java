//@wait 1
// Dumps every active mob effect on the player from both sides, with amplifier, duration and the
// ambient/hidden flags, plus the gravity and flight flags that can look like levitation without
// being the effect at all.
net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
if (mc.player == null) {
    return "FAIL: no client player\n";
}
StringBuilder out = new StringBuilder();
out.append("client player ").append(mc.player.getName().getString())
   .append("  pos=").append(mc.player.blockPosition())
   .append(" onGround=").append(mc.player.onGround())
   .append(" noGravity=").append(mc.player.isNoGravity())
   .append(" flying=").append(mc.player.getAbilities().flying)
   .append(" mayfly=").append(mc.player.getAbilities().mayfly).append("\n");
out.append("client effects: ").append(mc.player.getActiveEffects().size()).append("\n");
for (net.minecraft.world.effect.MobEffectInstance e : mc.player.getActiveEffects()) {
    out.append("  ")
       .append(net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(e.getEffect()))
       .append(" amp=").append(e.getAmplifier())
       .append(" dur=").append(e.getDuration())
       .append(" ambient=").append(e.isAmbient())
       .append(" visible=").append(e.isVisible())
       .append(" showIcon=").append(e.showIcon()).append("\n");
}

net.minecraft.server.MinecraftServer srv = mc.getSingleplayerServer();
if (srv == null) {
    return out.append("no integrated server\n").toString();
}
net.minecraft.server.level.ServerPlayer sp = srv.getPlayerList().getPlayer(mc.player.getUUID());
if (sp == null) {
    return out.append("player not found on the server\n").toString();
}
out.append("server effects: ").append(sp.getActiveEffects().size()).append("\n");
for (net.minecraft.world.effect.MobEffectInstance e : sp.getActiveEffects()) {
    out.append("  ")
       .append(net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(e.getEffect()))
       .append(" amp=").append(e.getAmplifier())
       .append(" dur=").append(e.getDuration())
       .append(" ambient=").append(e.isAmbient())
       .append(" visible=").append(e.isVisible()).append("\n");
}
out.append("server noGravity=").append(sp.isNoGravity())
   .append(" flying=").append(sp.getAbilities().flying)
   .append(" invulnerable=").append(sp.isInvulnerable()).append("\n");
out.append("persistent NBT ActiveEffects=")
   .append(sp.getPersistentData().get("ActiveEffects")).append("\n");
return out.toString();
