package theflogat.technomancy.common.tiles.technom.existence;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import theflogat.technomancy.common.energy.EnergyHolder;
import theflogat.technomancy.common.energy.EnergyLimits;
import theflogat.technomancy.common.energy.EnergyPorts;
import theflogat.technomancy.common.energy.MachineEnergy;
import theflogat.technomancy.common.machines.RedstoneMode;
import theflogat.technomancy.common.registry.TechnomBlockEntities;
import theflogat.technomancy.common.tiles.base.RedstoneControl;
import theflogat.technomancy.compat.gtceu.EuRating;

/**
 * {@code TileExistenceBurner} and {@code TileExistenceDynamicBurner}: kills the living around it and
 * turns each into Existence power. One block entity serves both, switched by {@link #dynamic()}.
 *
 * <p>The two variants differ in more than their cap. The static one is free and pays 15 for a
 * villager, 1 for a monster and 2 for anything else; the dynamic one has a 100,000 Q buffer, spends
 * 10,000 Q per kill, and pays 20 / 2 / 4. Only the dynamic variant takes energy at all, and only
 * through its bottom face.</p>
 */
public final class ExistenceBurnerBlockEntity extends BlockEntity
        implements IExistenceProducer, EnergyHolder {

    /** {@code TileExistenceBurner.maxPower}. */
    private static final int STATIC_CAP = 100;
    /** {@code TileExistenceDynamicBurner.maxPower}. */
    private static final int DYNAMIC_CAP = 150;
    /** {@code TileMachineBase(100000, ...)} on the dynamic burner. */
    private static final long DYNAMIC_ENERGY_CAPACITY = 100_000;
    /** {@code TileExistenceDynamicBurner}: {@code energy -= 10000} per kill. */
    private static final long DYNAMIC_ENERGY_PER_KILL = 10_000;
    private static final String TAG_POWER = "power";
    private static final String TAG_ENERGY = "Energy";
    /** {@code TileExistenceBurner}: {@code super(RedstoneSet.LOW)}. */
    private static final RedstoneMode DEFAULT_REDSTONE = RedstoneMode.LOW;

    private final MachineEnergy energy;
    private final RedstoneControl redstone = new RedstoneControl(DEFAULT_REDSTONE);
    private int power;
    private final boolean dynamic;

    public ExistenceBurnerBlockEntity(BlockPos pos, BlockState state) {
        super(TechnomBlockEntities.EXISTENCE_BURNER.get(), pos, state);
        this.dynamic = state.getBlock()
                instanceof theflogat.technomancy.common.blocks.technom.existence.ExistenceBurnerBlock burner
                && burner.dynamic();
        // TileExistenceBurner has no energy at all - only the dynamic variant does, and upstream it
        // is reachable from the bottom face alone (TileExistenceDynamicBurner.canConnectEnergy:
        // `from == ForgeDirection.DOWN`). NONE leaves the static variant a present but inert
        // capability, so nothing can be piped into a machine that would never spend it.
        energy = new MachineEnergy(this, limits(),
                dynamic ? EnergyPorts.consumer(EnergyPorts.mask(Direction.DOWN)) : EnergyPorts.NONE);
        redstone.setListener(this::setChanged);
    }

    /**
     * The dearest tick of work is the whole buffer: the sweep pays one charge per entity it finds
     * and only stops when the buffer cannot cover the next one, so a crowded pen can empty it in
     * a single tick. Rating against one kill instead would understate that. The static variant
     * takes no energy at all, and the rating is harmless there because {@link EnergyPorts#NONE}
     * grants it no face to arrive on.
     */
    private static EnergyLimits limits() {
        EnergyLimits base = EnergyLimits.fe(DYNAMIC_ENERGY_CAPACITY, DYNAMIC_ENERGY_CAPACITY, 0);
        long voltage = EuRating.inputVoltage(DYNAMIC_ENERGY_CAPACITY, DYNAMIC_ENERGY_CAPACITY);
        return voltage > 0 ? base.withEuInput(voltage, EuRating.CONSUMER_AMPS) : base;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ExistenceBurnerBlockEntity burner) {
        if (burner.power >= burner.getPowerCap()) {
            return;
        }
        // Only the static burner is redstone-sensitive upstream: TileExistenceBurner checks
        // set.canRun before its sweep, while TileExistenceDynamicBurner never does.
        if (!burner.dynamic && !burner.redstone.canRun(level, pos)) {
            return;
        }
        AABB box = new AABB(pos.getX() - 3, pos.getY() - 3, pos.getZ() - 3,
                pos.getX() + 4, pos.getY() + 4, pos.getZ() + 4);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (entity instanceof Player || entity.isInvulnerable() || !entity.isAlive()) {
                continue;
            }
            // The original checked `energy >= 10000` once before the loop and then deducted per
            // entity, so a crowded pen could drive the buffer negative. Checking per kill instead
            // is the same rule without the debt.
            if (burner.dynamic && !burner.energy.ledger().tryConsume(DYNAMIC_ENERGY_PER_KILL)) {
                break;
            }
            burner.power += burner.value(entity);
            entity.kill();
        }
        if (burner.power > burner.getPowerCap()) {
            burner.power = burner.getPowerCap();
        }
        burner.setChanged();
    }

    /**
     * The original asked {@code EnumCreatureType.monster}'s class, which is the {@code IMob} marker,
     * not "any {@code EntityMob}". In 1.20.1 the matching marker is {@link Enemy} - {@code Mob}
     * would be wrong, because {@link Animal} extends it and every cow would be priced as a monster.
     */
    private int value(LivingEntity entity) {
        if (entity instanceof AbstractVillager) {
            return dynamic ? 20 : 15;
        }
        if (entity instanceof Enemy) {
            return dynamic ? 2 : 1;
        }
        return dynamic ? 4 : 2;
    }

    public boolean dynamic() {
        return dynamic;
    }

    public MachineEnergy energy() {
        return energy;
    }

    public RedstoneControl redstone() {
        return redstone;
    }

    @Override
    public int getPower() {
        return power;
    }

    @Override
    public int getPowerCap() {
        return dynamic ? DYNAMIC_CAP : STATIC_CAP;
    }

    /**
     * Both upstream burners hardcode 4 ({@code TileExistenceBurner.getMaxRate} and
     * {@code TileExistenceDynamicBurner.getMaxRate}). This is not the users' {@code maxPower / 50}
     * rule: the burners extend {@code TileTechnomancyRedstone} / {@code TileMachineRedstone}, not
     * {@code TileExistenceRedstoneBase}, so their rate stays a constant.
     */
    @Override
    public int getMaxRate() {
        return 4;
    }

    @Override
    public void addPower(int value) {
        power += value;
        setChanged();
    }

    @Override
    public boolean canInput() {
        return false;
    }

    @Override
    public boolean canOutput() {
        return dynamic ? power > 0 : true;
    }

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

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(TAG_POWER, power);
        tag.put(TAG_ENERGY, energy.save());
        redstone.save(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        power = tag.getInt(TAG_POWER);
        energy.load(tag.getCompound(TAG_ENERGY));
        redstone.load(tag);
    }
}
