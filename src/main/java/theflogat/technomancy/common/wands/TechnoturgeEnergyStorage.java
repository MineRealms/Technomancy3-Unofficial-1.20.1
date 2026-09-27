package theflogat.technomancy.common.wands;

import dev.tc4port.thaumcraft.api.aspect.VisChannel;
import dev.tc4port.thaumcraft.api.wand.WandView;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.energy.IEnergyStorage;

/**
 * Forge Energy face of a wand carrying the technoturge rod: receive only, into the stack's charge
 * buffer, never more than the Vis it can still hold.
 *
 * <p>Replaces {@code ItemTechnoturgeScepter implements IEnergyContainerItem}. The original's
 * {@code receiveEnergy} added Vis and cleared its remainder even when {@code simulate} was true,
 * so a charger merely asking "how much would you take" charged the scepter; here simulate is
 * read-only. The buffer becomes Vis on the owner's next charging pass
 * ({@link WandChargeEvents}), because TC4R's Vis transfer needs a server player or machine
 * context that an item capability does not have.</p>
 */
public final class TechnoturgeEnergyStorage implements IEnergyStorage {

    private final ItemStack stack;

    public TechnoturgeEnergyStorage(ItemStack stack) {
        this.stack = stack;
    }

    private Optional<WandView> view() {
        return TechnomWandRods.view(stack).filter(v -> v.rod().equals(TechnomWandRods.TECHNOTURGE));
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        if (maxReceive <= 0 || stack.getCount() != 1) {
            return 0;
        }
        Optional<WandView> view = view();
        if (view.isEmpty()) {
            return 0;
        }
        long buffered = TechnomWandRods.charge(stack);
        long room = WandCharge.roomQ(WandCharge.room(view.get().visCentivis(), view.get().capacityCentivis())) - buffered;
        int accepted = (int) Math.max(0, Math.min(maxReceive, room));
        if (!simulate && accepted > 0) {
            TechnomWandRods.setCharge(stack, buffered + accepted);
        }
        return accepted;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return 0;
    }

    /** Stored Vis at 100 Q per centivis plus the unconverted buffer, as the original reported. */
    @Override
    public int getEnergyStored() {
        return view().map(v -> clamp(sum(v.visCentivis()) * WandCharge.Q_PER_CENTIVIS + TechnomWandRods.charge(stack)))
                .orElse(0);
    }

    @Override
    public int getMaxEnergyStored() {
        return view().map(v -> clamp(sum(v.capacityCentivis()) * WandCharge.Q_PER_CENTIVIS)).orElse(0);
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canReceive() {
        return view().isPresent();
    }

    private static long sum(Map<VisChannel, Integer> values) {
        return WandCharge.total(values);
    }

    private static int clamp(long value) {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, value));
    }
}
