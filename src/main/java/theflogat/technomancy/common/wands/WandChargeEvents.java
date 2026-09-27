package theflogat.technomancy.common.wands;

import dev.tc4port.thaumcraft.api.aspect.VisAction;
import dev.tc4port.thaumcraft.api.aspect.VisChannel;
import dev.tc4port.thaumcraft.api.aspect.VisCost;
import dev.tc4port.thaumcraft.api.wand.VisTransferResult;
import dev.tc4port.thaumcraft.api.wand.WandApi;
import dev.tc4port.thaumcraft.api.wand.WandCarrierItem;
import dev.tc4port.thaumcraft.api.wand.WandView;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import theflogat.technomancy.Technomancy;

/**
 * Server-side charging of wands that carry a Technomancy rod.
 *
 * <p>Every {@value #CHARGE_INTERVAL} ticks each wand in a player's inventory with one of the two
 * rods turns its energy buffer into Vis through {@link WandApi#insert(ServerPlayer, ItemStack,
 * VisCost, VisAction)}; an energized-rod wand first tops the buffer up from Forge Energy items in
 * the same inventory ({@code ElectricWandUpdate}). Inventory-wide rather than TC4R's held-only
 * {@code WandBehaviorApi}, because TC4's {@code ItemWandCasting.onUpdate} drove the rod update
 * from any inventory slot.</p>
 *
 * <p>Conservation: FE is extracted only up to the Q the wand can still turn into Vis; the buffer
 * is debited by exactly what TC4R reports as transferred; nothing that was paid for is dropped.</p>
 */
public final class WandChargeEvents {

    /** TC4R's own rod behaviour cadence. */
    public static final int CHARGE_INTERVAL = 20;
    private static final ResourceLocation CAPABILITY_KEY = new ResourceLocation(Technomancy.MOD_ID, "wand_energy");

    private WandChargeEvents() {
    }

    public static void register(IEventBus forgeBus) {
        forgeBus.addGenericListener(ItemStack.class, WandChargeEvents::attach);
        forgeBus.addListener(WandChargeEvents::onPlayerTick);
    }

    private static void attach(AttachCapabilitiesEvent<ItemStack> event) {
        ItemStack stack = event.getObject();
        if (stack.getItem() instanceof WandCarrierItem) {
            event.addCapability(CAPABILITY_KEY, new Provider(stack));
        }
    }

    /** Only answers for a technoturge rod; the rod is re-read on every query because it can change. */
    private static final class Provider implements ICapabilityProvider {
        private final ItemStack stack;
        private final LazyOptional<IEnergyStorage> energy;

        Provider(ItemStack stack) {
            this.stack = stack;
            this.energy = LazyOptional.of(() -> new TechnoturgeEnergyStorage(stack));
        }

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
            if (cap == ForgeCapabilities.ENERGY && TechnomWandRods.hasRod(stack, TechnomWandRods.TECHNOTURGE)) {
                return energy.cast();
            }
            return LazyOptional.empty();
        }
    }

    private static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player.tickCount % CHARGE_INTERVAL != 0 || player.isSpectator()) {
            return;
        }
        Inventory inventory = player.getInventory();
        for (List<ItemStack> section : List.<List<ItemStack>>of(inventory.items, inventory.offhand)) {
            for (ItemStack stack : section) {
                chargeWand(player, stack);
            }
        }
    }

    /**
     * One charging pass for one stack: top the buffer up from the player's batteries if this is an
     * energized rod, then turn as much of the buffer as fits into Vis.
     *
     * @return {@code true} if the stack changed
     */
    public static boolean chargeWand(ServerPlayer player, ItemStack wand) {
        if (!(wand.getItem() instanceof WandCarrierItem) || wand.getCount() != 1) {
            return false;
        }
        Optional<WandView> view = TechnomWandRods.view(wand);
        if (view.isEmpty()) {
            return false;
        }
        boolean electric = view.get().rod().equals(TechnomWandRods.ELECTRIC);
        if (!electric && !view.get().rod().equals(TechnomWandRods.TECHNOTURGE)) {
            return false;
        }
        Map<VisChannel, Integer> room = WandCharge.room(view.get().visCentivis(), view.get().capacityCentivis());
        room.replaceAll((channel, free) -> Math.min(free, WandCharge.MAX_CENTIVIS_PER_CHANNEL_PER_PASS));
        long before = TechnomWandRods.charge(wand);
        long buffer = before;
        long wanted = WandCharge.roomQ(room) - buffer;
        if (electric && wanted > 0) {
            buffer += pullFromBatteries(player.getInventory(), wanted);
        }
        Map<VisChannel, Integer> plan = WandCharge.plan(room, buffer, WandCharge.MAX_CENTIVIS_PER_CHANNEL_PER_PASS);
        boolean moved = false;
        if (!plan.isEmpty()) {
            VisTransferResult result = WandApi.insert(player, wand, VisCost.ofCentivis(plan), VisAction.EXECUTE);
            long spent = WandCharge.total(result.transferredCentivis()) * WandCharge.Q_PER_CENTIVIS;
            buffer -= spent;
            moved = spent > 0;
        }
        if (buffer != before) {
            TechnomWandRods.setCharge(wand, buffer);
            return true;
        }
        return moved;
    }

    /**
     * Takes up to {@code wanted} Q from Forge Energy items in the inventory, never from another
     * wand. Unlike {@code ElectricWandUpdate}, whose {@code while (energy < maxEner)} loop kept
     * calling one capacitor and trusted its simulate answer, this takes exactly what the item's
     * execute call returns and stops at what is needed.
     */
    private static long pullFromBatteries(Inventory inventory, long wanted) {
        long got = 0;
        NonNullList<ItemStack> items = inventory.items;
        for (ItemStack battery : items) {
            if (got >= wanted) {
                break;
            }
            if (battery.isEmpty() || battery.getItem() instanceof WandCarrierItem) {
                continue;
            }
            Optional<IEnergyStorage> storage = battery.getCapability(ForgeCapabilities.ENERGY).resolve();
            if (storage.isEmpty() || !storage.get().canExtract()) {
                continue;
            }
            int ask = (int) Math.min(Integer.MAX_VALUE, wanted - got);
            int taken = storage.get().extractEnergy(ask, false);
            got += Math.max(0, Math.min(taken, ask));
        }
        return got;
    }
}
