package theflogat.technomancy.common.tiles.dynamo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;
import theflogat.technomancy.common.essentia.fuel.EssentiaFuelTable;
import theflogat.technomancy.common.tiles.dynamo.DynamoFuelBank.Burn;

/**
 * The dynamo's fuel accounting: the full-buffer boundary of defect A-14 and the invariant that
 * the potency gem is throughput only.
 */
class DynamoFuelBankTest {

    private static final int LOOKAHEAD = 32;
    private static final long BASE_RATE = EssentiaDynamoBlockEntity.BASE_RATE;
    private static final long BOOSTED_RATE = EssentiaDynamoBlockEntity.BOOSTED_RATE;
    private static final int BOOSTED_UNITS = EssentiaDynamoBlockEntity.BOOSTED_UNITS;
    private static final long UNLIMITED = Long.MAX_VALUE / 4;
    /** One unit of ignis at the default fuel scale. */
    private static final long IGNIS_Q = EssentiaFuelTable.energyPerUnit(800, 0.25);

    private static LongSupplier priced(long perUnit, AtomicInteger calls) {
        return () -> {
            calls.incrementAndGet();
            return perUnit;
        };
    }

    // ---- defect A-14: the full-buffer boundary ----

    @Test
    void aFullBufferBuysNoFuelAndDoesNotEvenPriceIt() {
        DynamoFuelBank bank = new DynamoFuelBank(LOOKAHEAD);
        AtomicInteger priceLookups = new AtomicInteger();

        Burn burn = bank.tick(BASE_RATE, 1, 64, priced(IGNIS_Q, priceLookups), 0);

        assertEquals(0, burn.unitsConsumed(), "a full buffer must not take essentia");
        assertEquals(0, burn.energyProduced());
        assertEquals(0, bank.banked(), "and must not bank fuel it never paid for");
        assertEquals(0, priceLookups.get(),
                "the original got a whole fuel value here for ceil(0) = 0 units of essentia");
    }

    @Test
    void aFullBufferBurnsNoneOfTheFuelItAlreadyHas() {
        DynamoFuelBank bank = new DynamoFuelBank(LOOKAHEAD);
        AtomicInteger priceLookups = new AtomicInteger();
        bank.tick(BASE_RATE, 1, 64, priced(IGNIS_Q, priceLookups), UNLIMITED);
        long banked = bank.banked();
        assertTrue(banked > 0, "the first tick should have bought a charge");
        assertEquals(1, priceLookups.get());

        Burn blocked = bank.tick(BASE_RATE, 1, 64, priced(IGNIS_Q, priceLookups), 0);

        assertEquals(0, blocked.energyProduced());
        assertEquals(0, blocked.unitsConsumed());
        assertEquals(banked, bank.banked(),
                "the original kept decrementing fuel while producing nothing, losing all of it");
        assertEquals(1, priceLookups.get(), "no second charge was needed either");
    }

    @Test
    void productionNeverExceedsTheRoomLeft() {
        DynamoFuelBank bank = new DynamoFuelBank(LOOKAHEAD);
        bank.tick(BASE_RATE, 1, 64, () -> IGNIS_Q, UNLIMITED);
        long banked = bank.banked();

        Burn burn = bank.tick(BASE_RATE, 1, 64, () -> IGNIS_Q, 17);

        assertEquals(17, burn.energyProduced(), "only what fits may be produced");
        assertEquals(banked - 17, bank.banked(), "the rest of the tick's fuel stays banked");
    }

    @Test
    void refusedEnergyGoesBackIntoTheBank() {
        DynamoFuelBank bank = new DynamoFuelBank(LOOKAHEAD);
        bank.tick(BASE_RATE, 1, 64, () -> IGNIS_Q, UNLIMITED);
        long banked = bank.banked();

        bank.refund(30);
        assertEquals(banked + 30, bank.banked());
        bank.refund(0);
        bank.refund(-5);
        assertEquals(banked + 30, bank.banked(), "a refund cannot be negative");
    }

    // ---- charge purchasing ----

    @Test
    void aPartialChargeIsNotBurnedAtAll() {
        DynamoFuelBank bank = new DynamoFuelBank(LOOKAHEAD);
        AtomicInteger priceLookups = new AtomicInteger();

        // Three units in a boosted dynamo: the original's 'if (ratio > amount) return 0'.
        Burn burn = bank.tick(BOOSTED_RATE, BOOSTED_UNITS, 3, priced(IGNIS_Q, priceLookups), UNLIMITED);

        assertEquals(0, burn.unitsConsumed());
        assertEquals(0, burn.energyProduced());
        assertEquals(0, bank.banked());
        assertEquals(0, priceLookups.get());

        Burn enough = bank.tick(BOOSTED_RATE, BOOSTED_UNITS, 4, priced(IGNIS_Q, priceLookups), UNLIMITED);
        assertEquals(BOOSTED_UNITS, enough.unitsConsumed());
    }

    @Test
    void worthlessEssentiaIsNeverConsumed() {
        DynamoFuelBank bank = new DynamoFuelBank(LOOKAHEAD);

        Burn burn = bank.tick(BASE_RATE, 1, 64, () -> 0, UNLIMITED);

        assertEquals(0, burn.unitsConsumed(),
                "the original's Permutatio roll of 0 charged a unit of essentia for no fuel");
        assertEquals(0, burn.energyProduced());
        assertEquals(0, bank.banked());
    }

    @Test
    void noEssentiaMeansNoChargeButBankedFuelStillBurns() {
        DynamoFuelBank bank = new DynamoFuelBank(LOOKAHEAD);
        bank.tick(BASE_RATE, 1, 64, () -> IGNIS_Q, UNLIMITED);
        long banked = bank.banked();

        Burn burn = bank.tick(BASE_RATE, 1, 0, () -> IGNIS_Q, UNLIMITED);

        assertEquals(0, burn.unitsConsumed());
        assertEquals(BASE_RATE, burn.energyProduced(), "an empty essentia buffer does not stop the burn");
        assertEquals(banked - BASE_RATE, bank.banked());
    }

    @Test
    void chargesStopOnceTheLookaheadIsCovered() {
        DynamoFuelBank bank = new DynamoFuelBank(LOOKAHEAD);
        AtomicInteger charges = new AtomicInteger();
        // One unit of ignis banks 16000 Q = 200 ticks at 80 Q/t, far beyond 32 ticks of
        // lookahead, so exactly one charge must be bought until it is nearly spent.
        for (int tick = 0; tick < 100; tick++) {
            Burn burn = bank.tick(BASE_RATE, 1, 64, () -> IGNIS_Q, UNLIMITED);
            if (burn.unitsConsumed() > 0) {
                charges.incrementAndGet();
            }
        }
        assertEquals(1, charges.get(), "banked " + bank.banked() + " Q after 100 ticks");
        assertEquals(IGNIS_Q - 100 * BASE_RATE, bank.banked());
    }

    // ---- the potency gem is throughput, not efficiency ----

    @Test
    void thePotencyGemChangesThroughputAndNotEnergyPerUnit() {
        for (int fuelValue : List.of(800, 300, 75, 40, 30, 25, 1)) {
            long perUnit = EssentiaFuelTable.energyPerUnit(fuelValue, 0.25);
            Run plain = drain(BASE_RATE, 1, perUnit);
            Run boosted = drain(BOOSTED_RATE, BOOSTED_UNITS, perUnit);

            assertEquals(64, plain.units, "fuel value " + fuelValue + ": unboosted essentia left over");
            assertEquals(64, boosted.units, "fuel value " + fuelValue + ": boosted essentia left over");
            assertEquals(64 * perUnit, plain.energy,
                    "fuel value " + fuelValue + ": unboosted energy per unit is not " + perUnit);
            assertEquals(plain.energy, boosted.energy,
                    "fuel value " + fuelValue + ": the gem changed total energy, so it changed efficiency");
            if (perUnit > 0) {
                assertTrue(boosted.ticks * 4 <= plain.ticks + 4 && boosted.ticks * 4 >= plain.ticks - 4,
                        "fuel value " + fuelValue + ": the gem should take about a quarter of the time, "
                                + boosted.ticks + " vs " + plain.ticks);
            }
        }
    }

    private record Run(int units, long energy, int ticks) {}

    /** Burns a full 64-unit buffer dry and reports what it cost and produced. */
    private static Run drain(long rate, int unitsPerCharge, long energyPerUnit) {
        DynamoFuelBank bank = new DynamoFuelBank(LOOKAHEAD);
        int supply = 64;
        int consumed = 0;
        long produced = 0;
        int ticks = 0;
        // Bounded so a stuck bank fails the test instead of hanging it.
        for (; ticks < 1_000_000; ticks++) {
            Burn burn = bank.tick(rate, unitsPerCharge, supply, () -> energyPerUnit, UNLIMITED);
            supply -= burn.unitsConsumed();
            consumed += burn.unitsConsumed();
            produced += burn.energyProduced();
            if (burn.unitsConsumed() == 0 && burn.energyProduced() == 0) {
                break;
            }
        }
        return new Run(consumed, produced, ticks);
    }

    @Test
    void aNonPositiveLookaheadIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new DynamoFuelBank(0));
        assertThrows(IllegalArgumentException.class, () -> new DynamoFuelBank(-1));
    }

    @Test
    void savedFuelIsClampedOnLoad() {
        DynamoFuelBank bank = new DynamoFuelBank(LOOKAHEAD);
        bank.load(500);
        assertEquals(500, bank.banked());
        bank.load(-7);
        assertEquals(0, bank.banked(), "a corrupt save must not become negative fuel");
    }

    @Test
    void theRatesAreTheOnesTheOriginalUsed() {
        assertEquals(80, BASE_RATE);
        assertEquals(320, BOOSTED_RATE);
        assertEquals(4, BOOSTED_UNITS, "the gem is a factor of four in both rate and fuel");
        assertEquals(320, EssentiaDynamoBlockEntity.MAX_OUTPUT);
        assertEquals(40_000, EssentiaDynamoBlockEntity.ENERGY_CAPACITY);
        assertEquals(64, EssentiaDynamoBlockEntity.ESSENTIA_CAPACITY);
        assertEquals(128, EssentiaDynamoBlockEntity.SUCTION);
    }
}
