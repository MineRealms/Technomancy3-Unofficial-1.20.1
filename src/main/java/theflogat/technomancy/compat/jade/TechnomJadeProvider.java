package theflogat.technomancy.compat.jade;

import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.aspect.AspectDefinition;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.energy.EnergyHolder;
import theflogat.technomancy.common.energy.EnergyPorts;
import theflogat.technomancy.common.rituals.Ritual;
import theflogat.technomancy.common.tiles.essentia.CreativeJarBlockEntity;
import theflogat.technomancy.common.tiles.essentia.EssentiaReservoirBlockEntity;
import theflogat.technomancy.common.tiles.essentia.QuantumJarBlockEntity;
import theflogat.technomancy.common.tiles.machines.ProcessorBlockEntity;
import theflogat.technomancy.common.tiles.nodes.NodeDynamoBlockEntity;
import theflogat.technomancy.common.tiles.technom.CatalystBlockEntity;
import theflogat.technomancy.common.tiles.technom.existence.ExistenceFountainBlockEntity;
import theflogat.technomancy.common.tiles.technom.existence.ExistencePylonBlockEntity;

/**
 * Collects a machine's numbers on the server and draws them on the client.
 *
 * <p>The split matters. {@link #appendServerData} runs on the server and writes only what the
 * machine really holds; {@link #appendTooltip} runs on the client and reads that tag back. A
 * provider that read the block entity directly on the client would show whatever the client's
 * copy happens to hold, which for a machine the player is not currently ticking can be stale or
 * simply wrong.</p>
 *
 * <p>Nothing is sent for a block entity that is not ours, so the registration against
 * {@code Block.class} costs one {@code instanceof} and nothing else.</p>
 */
public final class TechnomJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    public static final TechnomJadeProvider INSTANCE = new TechnomJadeProvider();

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(Technomancy.MOD_ID, "machine_data");

    private static final String ENERGY = "energy";
    private static final String ENERGY_CAP = "energy_cap";
    private static final String BOOSTED = "boosted";
    private static final String ASPECT = "aspect";
    private static final String AMOUNT = "amount";
    private static final String CAPACITY = "capacity";
    private static final String PROGRESS = "progress";
    private static final String RITUAL_ELEMENT = "ritual_element";
    private static final String RITUAL_TIER = "ritual_tier";
    private static final String RITUAL_LEFT = "ritual_left";
    private static final String VIS = "vis";
    private static final String FUEL = "fuel";
    private static final String EXISTENCE = "existence";
    private static final String EXISTENCE_CAP = "existence_cap";

    private TechnomJadeProvider() {
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    // ---- server half ----

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        BlockEntity be = accessor.getBlockEntity();
        if (be == null) {
            return;
        }
        // A machine with NONE ports holds an energy component but has no way to move energy - the
        // static Existence burner is one - so a bar would invent a stat the player cannot use.
        if (be instanceof EnergyHolder holder && !holder.energy().ports().equals(EnergyPorts.NONE)) {
            tag.putLong(ENERGY, holder.energy().ledger().stored());
            tag.putLong(ENERGY_CAP, holder.energy().ledger().capacity());
        }
        if (be instanceof QuantumJarBlockEntity jar) {
            aspect(tag, jar.aspect());
            tag.putInt(AMOUNT, jar.amount());
            tag.putInt(CAPACITY, QuantumJarBlockEntity.CAPACITY);
        } else if (be instanceof EssentiaReservoirBlockEntity reservoir) {
            aspect(tag, reservoir.aspect());
            tag.putInt(AMOUNT, reservoir.amount());
            tag.putInt(CAPACITY, EssentiaReservoirBlockEntity.CAPACITY);
        } else if (be instanceof CreativeJarBlockEntity creative) {
            aspect(tag, creative.aspect());
        }
        if (be instanceof ProcessorBlockEntity processor) {
            tag.putInt(PROGRESS, processor.progress());
        }
        if (be instanceof NodeDynamoBlockEntity dynamo) {
            tag.putInt(VIS, dynamo.vis());
            tag.putLong(FUEL, dynamo.fuel());
        }
        if (be instanceof CatalystBlockEntity catalyst && catalyst.isRunning()) {
            Ritual ritual = catalyst.running();
            if (ritual != null) {
                tag.putString(RITUAL_ELEMENT, ritual.core().name());
                tag.putInt(RITUAL_TIER, tierOf(ritual));
            }
            tag.putInt(RITUAL_LEFT, catalyst.remCount());
        }
        if (be instanceof ExistenceFountainBlockEntity fountain) {
            tag.putInt(EXISTENCE, fountain.power());
            tag.putInt(EXISTENCE_CAP, fountain.powerCap());
        } else if (be instanceof ExistencePylonBlockEntity pylon) {
            tag.putInt(EXISTENCE, pylon.getPower());
        }
    }

    /** The tier the ritual's own class name ends in, or {@code 0} for the untiered ones. */
    private static int tierOf(Ritual ritual) {
        String name = ritual.getClass().getSimpleName();
        if (name.endsWith("T1")) {
            return 1;
        }
        if (name.endsWith("T2")) {
            return 2;
        }
        if (name.endsWith("T3")) {
            return 3;
        }
        return 0;
    }

    private static void aspect(CompoundTag tag, @javax.annotation.Nullable AspectId aspect) {
        if (aspect != null) {
            tag.putString(ASPECT, aspect.serialized());
        }
    }

    // ---- client half ----

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag tag = accessor.getServerData();
        if (tag.isEmpty()) {
            return;
        }
        if (tag.contains(ENERGY)) {
            line(tooltip, "technom.jade.energy",
                    format(tag.getLong(ENERGY)) + " / " + format(tag.getLong(ENERGY_CAP)) + " Q");
        }
        if (tag.contains(ASPECT)) {
            String amount = tag.contains(AMOUNT)
                    ? tag.getInt(AMOUNT) + (tag.contains(CAPACITY) ? " / " + tag.getInt(CAPACITY) : "")
                    : "";
            line(tooltip, "technom.jade.essentia", aspectName(tag.getString(ASPECT)) + " " + amount);
        }
        if (tag.contains(PROGRESS)) {
            line(tooltip, "technom.jade.progress", tag.getInt(PROGRESS) + "%");
        }
        if (tag.contains(VIS)) {
            line(tooltip, "technom.jade.vis", Integer.toString(tag.getInt(VIS)));
        }
        if (tag.contains(FUEL)) {
            line(tooltip, "technom.jade.buffer", format(tag.getLong(FUEL)) + " Q");
        }
        if (tag.contains(RITUAL_ELEMENT)) {
            String element = Component.translatable(
                    "technom.jade.element." + tag.getString(RITUAL_ELEMENT).toLowerCase()).getString();
            int tier = tag.getInt(RITUAL_TIER);
            line(tooltip, "technom.jade.ritual", tier > 0 ? element + " T" + tier : element);
        }
        if (tag.contains(RITUAL_LEFT) && tag.getInt(RITUAL_LEFT) >= 0) {
            line(tooltip, "technom.jade.remaining", tag.getInt(RITUAL_LEFT) + " t");
        }
        if (tag.contains(EXISTENCE)) {
            String value = Integer.toString(tag.getInt(EXISTENCE));
            if (tag.contains(EXISTENCE_CAP)) {
                value += " / " + tag.getInt(EXISTENCE_CAP);
            }
            line(tooltip, "technom.jade.existence", value);
        }
    }

    private static void line(ITooltip tooltip, String key, String value) {
        tooltip.add(Component.translatable(key, value).withStyle(ChatFormatting.GRAY));
    }

    /** The aspect's own display name, or the raw id if the registry has not answered. */
    private static String aspectName(String serialized) {
        AspectId id = AspectId.parse(serialized);
        return AspectApi.registry().get(id).map(AspectDefinition::name).orElse(serialized);
    }

    /** Longs can exceed what a player reads at a glance; group them. */
    private static String format(long value) {
        return String.format("%,d", value);
    }
}
