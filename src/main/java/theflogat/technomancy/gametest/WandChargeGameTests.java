package theflogat.technomancy.gametest;

import dev.tc4port.thaumcraft.api.aspect.VisChannel;
import dev.tc4port.thaumcraft.api.wand.WandApi;
import dev.tc4port.thaumcraft.api.wand.WandMaterialId;
import dev.tc4port.thaumcraft.api.wand.WandPartApi;
import dev.tc4port.thaumcraft.api.wand.WandRodSpec;
import dev.tc4port.thaumcraft.api.wand.WandView;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.registry.TechnomItems;
import theflogat.technomancy.common.wands.TechnomWandRods;
import theflogat.technomancy.common.wands.WandCharge;
import theflogat.technomancy.common.wands.WandChargeEvents;

/**
 * The Technomancy wand rods as the game sees them: the data map really registered them, an
 * assembled wand has the right capacity, and one charging pass converts exactly the energy it
 * spends. Written for the merged final run; not yet executed.
 */
@GameTestHolder(Technomancy.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WandChargeGameTests {

    private static final String BATCH = "technom_wands";

    private WandChargeGameTests() {}

    /** The rod definition is a data map entry; an absent one would silently disable everything. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void theEnergizedRodIsRegisteredFromTheDataMap(GameTestHelper helper) {
        WandRodSpec rod = WandPartApi.rod(TechnomWandRods.ELECTRIC)
                .orElseThrow(() -> new AssertionError("technom:electric is not a registered wand rod"));
        helper.assertTrue(rod.capacity() == 25, "capacity is " + rod.capacity() + " whole vis, expected 25");
        helper.assertTrue(rod.craftCost() == 10, "craft cost is " + rod.craftCost());
        helper.assertTrue(!rod.staff(), "the energized core must be a wand rod, not a staff core");
        helper.assertTrue(WandPartApi.rod(new ItemStack(TechnomItems.ENERGIZED_WAND_CORE.get())).isPresent(),
                "the core item is not in #thaumcraft:wand_rods");
        helper.assertTrue(WandPartApi.maximumCentivis(TechnomWandRods.ELECTRIC, false) == 2500,
                "an energized wand should hold 2500 centivis per primal");
        helper.assertTrue(WandPartApi.maximumCentivis(TechnomWandRods.ELECTRIC, true) == 3750,
                "as a sceptre it should hold 1.5x that");
        helper.succeed();
    }

    /** One pass turns buffered Q into Vis at 100 Q per centivis and spends exactly that much. */
    @GameTest(template = GameTestTemplates.EMPTY_5X5X5, batch = BATCH)
    public static void aChargingPassConservesEnergy(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack wand = WandApi.create(TechnomWandRods.ELECTRIC, WandMaterialId.IRON);
        long budget = 60 * WandCharge.Q_PER_CENTIVIS;
        TechnomWandRods.setCharge(wand, budget);

        helper.assertTrue(WandChargeEvents.chargeWand(player, wand), "the charging pass did nothing");
        Optional<WandView> view = WandApi.view(wand);
        long centivis = WandCharge.total(view.orElseThrow().visCentivis());
        long left = TechnomWandRods.charge(wand);
        helper.assertTrue(centivis * WandCharge.Q_PER_CENTIVIS + left == budget,
                "energy is not conserved: " + centivis + " centivis plus " + left + " Q against " + budget);
        helper.assertTrue(centivis == 60, "expected 60 centivis, got " + centivis);
        helper.assertTrue(view.get().visCentivis().get(VisChannel.PERDITIO) == 10,
                "the six primals should fill together");

        // A full wand buffers nothing more and converts nothing: no free vis, no lost energy.
        ItemStack full = WandApi.createFullyCharged(TechnomWandRods.ELECTRIC, WandMaterialId.IRON);
        TechnomWandRods.setCharge(full, 100_000);
        WandChargeEvents.chargeWand(player, full);
        helper.assertTrue(TechnomWandRods.charge(full) == 100_000,
                "a full wand spent buffered energy on nothing");
        helper.succeed();
    }
}
