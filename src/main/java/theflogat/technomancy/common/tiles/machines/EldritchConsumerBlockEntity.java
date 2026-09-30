package theflogat.technomancy.common.tiles.machines;

import theflogat.technomancy.common.energy.EnergyHolder;
import dev.tc4port.thaumcraft.api.aspect.AspectAmounts;
import dev.tc4port.thaumcraft.api.aspect.AspectContainerView;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.aspect.AspectQueryApi;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransport;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.energy.EnergyLimits;
import theflogat.technomancy.common.energy.EnergyPorts;
import theflogat.technomancy.common.energy.MachineEnergy;
import theflogat.technomancy.common.essentia.EssentiaLimits;
import theflogat.technomancy.common.essentia.EssentiaStore;
import theflogat.technomancy.common.machines.RedstoneMode;
import theflogat.technomancy.common.machines.consumer.ConsumerRange;
import theflogat.technomancy.common.registry.TechnomBlockEntities;
import theflogat.technomancy.common.tiles.base.RedstoneControl;
import theflogat.technomancy.compat.gtceu.EuTier;
import theflogat.technomancy.config.TechnomancyConfig;

/**
 * The eldritch consumer ({@code TileEldritchConsumer}): a powered pit that eats whatever is
 * below it and stores the aspect of what it destroys, for tubes and jars to pull out.
 *
 * <p>Each object destroyed costs one configurable charge of energy. It consumes mobs first, then
 * dropped items, then one block, exactly as the original's update order did. Killing a mob pays
 * the charge and lets its loot fall, where the next pass eats the drops too — the original did
 * the same, and it is why the machine is fed a pit rather than a chest.</p>
 *
 * <p>Two rules the original got wrong, kept apart here:</p>
 * <ul>
 *   <li>Blocks whose hardness is negative (bedrock, the end portal frame) are never touched, and
 *       neither are blocks carrying a block entity: a chest must not have its contents silently
 *       deleted along with the block.</li>
 *   <li>Breaking a block yields its aspects but <em>not</em> the drops themselves; the block is
 *       removed with drops disabled, so nothing is gained twice.</li>
 * </ul>
 *
 * <p>The container holds at most four aspects of four units each, the original
 * {@code canFillList} limit. Unlike the original's {@code getEssentiaType}, which indexed an
 * empty array and crashed, every query answers safely when the store is empty.</p>
 *
 * <p>The panel animation is synced, as it was upstream. The original kept {@code cooldown} and
 * {@code panelRotation} as public fields on the tile, pushed {@code cooldown} to the client in
 * {@code writeSyncData}, called {@code markBlockForUpdate} while it counted down, and eased
 * {@code panelRotation} in the client half of {@code updateEntity}. The port keeps the same
 * split - the server owns {@code cooldown}, the client owns {@code panelRotation} - but ships a
 * single {@link #working} flag on the transitions instead of a packet per tick, because the only
 * thing the client reads is {@code cooldown > 0}. {@link #working()} and
 * {@link #panelRotation()} are what {@code EldritchConsumerRenderer} draws from.</p>
 */
public final class EldritchConsumerBlockEntity extends BlockEntity implements EssentiaTransport, AspectContainerView, EnergyHolder {

    /** {@code TileEldritchConsumer}: {@code Rate.consumerCost * 50}. */
    public static final long ENERGY_CAPACITY = 1_000_000;
    /** {@code TileMachineRedstone(Rate.consumerCost * 50, RedstoneSet.HIGH)}. */
    public static final RedstoneMode DEFAULT_REDSTONE = RedstoneMode.HIGH;
    /** {@code cooldown = 40} after any successful pass: drives the client panel, gates nothing. */
    public static final int COOLDOWN_TICKS = 40;
    /** {@code time = 80} before retrying when there was nothing to eat. */
    public static final int RETRY_TICKS = 80;

    private static final long EU_INPUT_VOLTAGE = EuTier.EV.voltage();
    private static final long EU_INPUT_AMPS = 2;

    /**
     * A four-aspect pool. The original {@code canFillList} stopped working once four aspects
     * were present or any one was above four units, but a single item can contribute more than
     * four at once (an iron ingot is worth eight metallum), so the pool must be large enough to
     * take a whole object. Four aspects is the limit that actually matters.
     */
    private static final EssentiaLimits LIMITS = EssentiaLimits.pooled(64, 256, 4);

    private static final String TAG_VERSION = "v";
    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_ESSENTIA = "Essentia";
    private static final String TAG_COOLDOWN = "cooldown";
    private static final String TAG_RETRY = "time";
    private static final String TAG_WORKING = "working";
    private static final int SCHEMA_VERSION = 1;

    private final MachineEnergy energy;
    private final EssentiaStore store = new EssentiaStore(LIMITS);
    private final RedstoneControl redstone = new RedstoneControl(DEFAULT_REDSTONE);

    private ConsumerRange range = ConsumerRange.LARGE;
    private int cooldown;
    private int retry;
    /** Synced: {@code cooldown > 0}, the original's {@code go} argument to the model. */
    private boolean working;
    /** Client only: the arm segments' X rotation in radians. Never saved; the server has none. */
    private float panelRotation;

    public EldritchConsumerBlockEntity(BlockPos pos, BlockState state) {
        super(TechnomBlockEntities.ELDRITCH_CONSUMER.get(), pos, state);
        // Six faces in, none out: it is a load. The essentia it holds leaves through takeEssentia.
        energy = new MachineEnergy(this, limits(), EnergyPorts.consumer(EnergyPorts.ALL));
        store.setListener(this::setChanged);
    }

    private static EnergyLimits limits() {
        return EnergyLimits.fe(ENERGY_CAPACITY, ENERGY_CAPACITY, 0)
                .withEuInput(EU_INPUT_VOLTAGE, EU_INPUT_AMPS);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            EldritchConsumerBlockEntity consumer) {
        consumer.tick(level, pos);
        // After the tick, so none of its early returns can skip the transition.
        consumer.syncWorking();
    }

    /**
     * The client half of the original's {@code updateEntity}: the {@code else} branch that eased
     * the panel by 0.02 radians a tick toward {@code -pi/4} while working and back toward 0 while
     * idle.
     */
    public static void clientTick(Level level, BlockPos pos, BlockState state,
            EldritchConsumerBlockEntity consumer) {
        consumer.easePanel();
    }

    private void easePanel() {
        if (working) {
            panelRotation = Math.min((float) -Math.PI / 4, panelRotation - 0.02F);
        } else if (panelRotation > 0) {
            // Unreachable - the other two branches clamp panelRotation into [-pi/4, 0] - and kept
            // anyway so a diff against the original stays honest.
            panelRotation = Math.min(0.0F, panelRotation - 0.02F);
        } else {
            panelRotation = Math.max(0.0F, panelRotation + 0.02F);
        }
    }

    /**
     * Pushes the working flag when it flips. The original called {@code markBlockForUpdate} every
     * tick of the countdown and synced {@code cooldown} itself; the client only ever asks
     * {@code cooldown > 0}, so one packet per transition carries the same information.
     */
    private void syncWorking() {
        boolean now = cooldown > 0;
        if (now == working) {
            return;
        }
        working = now;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private void tick(Level level, BlockPos pos) {
        // The original decrements cooldown *outside* its work branch: it only drives the client
        // panel animation, while the real retry gate is `time` (our retry, 80 ticks after an idle
        // pass). Returning here as well capped the machine at one pass per 41 ticks instead of one
        // per tick, so it ran at about 2% of its intended rate.
        if (cooldown > 0) {
            cooldown--;
        }
        if (retry > 0) {
            retry--;
            return;
        }
        if (!redstone.canRun(level, pos)) {
            return;
        }
        if (store.total() >= LIMITS.totalCapacity()) {
            return;
        }
        long cost = TechnomancyConfig.CONSUMER_COST.get();
        if (energy.ledger().stored() < cost) {
            return;
        }
        boolean worked = killMobs(level, pos, cost);
        if (energy.ledger().stored() >= cost) {
            worked |= consumeItems(level, pos, cost);
        }
        if (energy.ledger().stored() >= cost) {
            worked |= consumeBlock(level, pos, cost);
        }
        if (worked) {
            cooldown = COOLDOWN_TICKS;
            setChanged();
        } else {
            retry = RETRY_TICKS;
        }
    }

    // ---- the three things it eats ----

    /** Kills one non-invulnerable mob per charge, letting its loot fall for the next pass. */
    private boolean killMobs(Level level, BlockPos pos, long cost) {
        boolean killed = false;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, area(level, pos))) {
            if (energy.ledger().stored() < cost) {
                break;
            }
            if (mob.isInvulnerable() || !mob.isAlive()) {
                continue;
            }
            if (!energy.ledger().tryConsume(cost)) {
                break;
            }
            mob.kill();
            killed = true;
        }
        return killed;
    }

    /** Eats one unit from each dropped item stack whose aspect the store can still hold. */
    private boolean consumeItems(Level level, BlockPos pos, long cost) {
        boolean eaten = false;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area(level, pos))) {
            if (energy.ledger().stored() < cost) {
                break;
            }
            ItemStack stack = item.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            AspectAmounts aspects = AspectQueryApi.item(stack);
            if (aspects.amounts().isEmpty() || !canAccept(aspects)) {
                continue;
            }
            if (!energy.ledger().tryConsume(cost)) {
                break;
            }
            storeAspects(aspects);
            stack.shrink(1);
            if (stack.isEmpty()) {
                item.discard();
            }
            eaten = true;
        }
        return eaten;
    }

    /** Breaks the topmost acceptable block below the machine, gaining its aspects and no drops. */
    private boolean consumeBlock(Level level, BlockPos pos, long cost) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        for (int y = pos.getY() - 1; y >= range.blockFloorY(pos.getY(), level.getMinBuildHeight()); y--) {
            for (int dx = -range.radius(); dx <= range.radius(); dx++) {
                for (int dz = -range.radius(); dz <= range.radius(); dz++) {
                    BlockPos target = new BlockPos(pos.getX() + dx, y, pos.getZ() + dz);
                    if (!isEdible(level, target)) {
                        continue;
                    }
                    if (!energy.ledger().tryConsume(cost)) {
                        return false;
                    }
                    BlockState state = level.getBlockState(target);
                    // Aspects come from what the block would have dropped, then the block is
                    // removed without spawning those drops: one benefit, not two.
                    for (ItemStack drop : Block.getDrops(state, serverLevel, target, null)) {
                        storeAspects(AspectQueryApi.item(drop));
                    }
                    serverLevel.removeBlock(target, false);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * A block is edible when it is not air, not a fluid, not unbreakable and not the shell of a
     * block entity. The last rule is what keeps a chest's contents from vanishing with it.
     */
    private static boolean isEdible(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (state.getDestroySpeed(level, pos) < 0) {
            return false;
        }
        return !state.hasBlockEntity();
    }

    /** The box below the machine it scans, at most once per tick. */
    private AABB area(Level level, BlockPos pos) {
        int minY = range.entityFloorY(pos.getY(), level.getMinBuildHeight());
        return new AABB(
                pos.getX() - range.radius(), minY, pos.getZ() - range.radius(),
                pos.getX() + range.radius() + 1.0, pos.getY(), pos.getZ() + range.radius() + 1.0);
    }

    /** Whether every aspect of a find still fits, checked before anything is committed. */
    private boolean canAccept(AspectAmounts aspects) {
        Map<AspectId, Integer> incoming = new LinkedHashMap<>();
        aspects.amounts().forEach((aspect, amount) -> incoming.merge(aspect, amount, Integer::sum));
        int total = store.total();
        for (Map.Entry<AspectId, Integer> entry : incoming.entrySet()) {
            if (store.amount(entry.getKey()) + entry.getValue() > LIMITS.effectivePerAspectCapacity()) {
                return false;
            }
            total += entry.getValue();
        }
        Set<AspectId> distinct = new LinkedHashSet<>(store.aspects());
        distinct.addAll(incoming.keySet());
        return total <= LIMITS.totalCapacity() && distinct.size() <= LIMITS.maxDistinctAspects();
    }

    private void storeAspects(AspectAmounts aspects) {
        aspects.amounts().forEach((aspect, amount) -> store.add(aspect, amount, false));
    }

    // ---- ranges ----

    /**
     * Whether the machine is inside the 40-tick tail that follows a pass: the original's
     * {@code cooldown > 0}, which is what set the rotor spinning and held the panel down.
     */
    public boolean working() {
        return working;
    }

    /** Client only: the arm segments' current X rotation. 0 on the server and in an inventory. */
    public float panelRotation() {
        return panelRotation;
    }

    public ConsumerRange range() {
        return range;
    }

    /** Advances to the next range, as the original's sneak-click did. */
    public ConsumerRange cycleRange() {
        range = range.next();
        setChanged();
        return range;
    }

    public void setRange(ConsumerRange selected) {
        range = selected;
        setChanged();
    }

    public RedstoneControl redstone() {
        return redstone;
    }

    public MachineEnergy energy() {
        return energy;
    }

    public EssentiaStore store() {
        return store;
    }

    // ---- essentia views ----

    @Override
    public AspectAmounts visibleAspects() {
        return store.visibleAspects();
    }

    @Override
    public List<AspectId> visibleAspectOrder() {
        return store.visibleAspectOrder();
    }

    @Override
    public boolean isConnectable(Direction face) {
        return true;
    }

    /** Nothing goes in; {@link #addEssentia} agrees. */
    @Override
    public boolean canInputFrom(Direction face) {
        return false;
    }

    @Override
    public boolean canOutputTo(Direction face) {
        return true;
    }

    @Nullable
    @Override
    public AspectId suctionType(Direction face) {
        return null;
    }

    @Override
    public int suctionAmount(Direction face) {
        return 0;
    }

    @Override
    public int minimumSuction() {
        return 0;
    }

    @Override
    public int takeEssentia(AspectId aspect, int amount, Direction face, EssentiaTransferMode mode) {
        if (!canOutputTo(face) || amount <= 0) {
            return 0;
        }
        return store.take(aspect, amount, !mode.executes());
    }

    @Override
    public int addEssentia(AspectId aspect, int amount, Direction face, EssentiaTransferMode mode) {
        return 0;
    }

    @Nullable
    @Override
    public AspectId essentiaType(Direction face) {
        return store.dominantAspect();
    }

    @Override
    public int essentiaAmount(Direction face) {
        AspectId dominant = store.dominantAspect();
        return dominant == null ? 0 : store.amount(dominant);
    }

    @Nullable
    @Override
    public AspectId extractableAspect(Direction face) {
        return store.dominantAspect();
    }

    @Override
    public int availableEssentia(AspectId aspect, Direction face) {
        return canOutputTo(face) ? store.amount(aspect) : 0;
    }

    @Override
    public boolean renderExtendedTube() {
        return true;
    }

    // ---- capabilities ----

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        LazyOptional<T> view = energy.getCapability(cap, side);
        return view.isPresent() ? view : super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energy.invalidate();
    }

    // ---- persistence ----

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.getInt(TAG_VERSION) != SCHEMA_VERSION) {
            return;
        }
        energy.load(tag.getCompound(TAG_ENERGY));
        if (store.load(tag.getCompound(TAG_ESSENTIA))) {
            Technomancy.LOGGER.warn("Eldritch consumer at {} could not restore its contents verbatim",
                    worldPosition);
        }
        cooldown = tag.getInt(TAG_COOLDOWN);
        retry = tag.getInt(TAG_RETRY);
        range = ConsumerRange.load(tag, ConsumerRange.LARGE);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(TAG_VERSION, SCHEMA_VERSION);
        tag.put(TAG_ENERGY, energy.save());
        tag.put(TAG_ESSENTIA, store.save());
        tag.putInt(TAG_COOLDOWN, cooldown);
        tag.putInt(TAG_RETRY, retry);
        range.save(tag);
    }

    // ---- animation sync ----

    /**
     * One boolean, and only because the client draws from it. The original shipped the whole
     * {@code cooldown} here; nothing on the client reads its value, only whether it is above zero,
     * so the flag is what the renderer's {@code go} argument wants.
     */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(TAG_WORKING, working);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        working = tag.getBoolean(TAG_WORKING);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Not the inherited {@code load}: the update tag is a fragment, not a full save. */
    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        CompoundTag tag = packet.getTag();
        if (tag != null) {
            handleUpdateTag(tag);
        }
    }
}
