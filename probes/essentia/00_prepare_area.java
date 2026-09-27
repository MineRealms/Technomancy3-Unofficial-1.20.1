// Prepares a clean, TICKING work area. Everything downstream depends on this.
//
// With no player online no chunk ticks, so block entities never run and a
// tick-driven test reads as broken logic when it is simply frozen. Forcing the
// chunk is therefore not a convenience, it is a precondition.
net.minecraft.server.level.ServerLevel level =
        net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().overworld();
StringBuilder out = new StringBuilder();

boolean forced = level.setChunkForced(0, 0, true);
out.append("setChunkForced(0,0) -> ").append(forced)
   .append(", forced set now ").append(level.getForcedChunks()).append("\n");

int floor = level.getMinBuildHeight() + 20;
for (int x = 0; x <= 8; x++) {
    for (int z = 0; z <= 8; z++) {
        for (int y = floor - 1; y <= floor + 4; y++) {
            level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x, y, z),
                    net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }
    }
}
out.append("cleared 9x6x9 work area with its floor at y=").append(floor).append("\n");
out.append(level.getForcedChunks().contains(0L) || forced ? "PASS" : "FAIL: chunk not forced").append("\n");
return out.toString();
