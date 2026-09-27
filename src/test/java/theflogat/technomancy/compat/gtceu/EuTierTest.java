package theflogat.technomancy.compat.gtceu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EuTierTest {

    /**
     * The literal table. {@code GtceuEnergyGameTests} proves it still matches the installed
     * GTCEu; this test only guards against an accidental edit here.
     */
    @Test
    void tableMatchesGregTechVoltages() {
        long[] expected = {8, 32, 128, 512, 2048, 8192, 32_768, 131_072, 524_288, 2_097_152,
                8_388_608, 33_554_432, 134_217_728, 536_870_912, 2_147_483_648L};
        String[] names = {"ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV",
                "UEV", "UIV", "UXV", "OpV", "MAX"};
        assertEquals(expected.length, EuTier.count());
        assertEquals(names.length, EuTier.count());
        for (int tier = 0; tier < expected.length; tier++) {
            EuTier value = EuTier.byIndex(tier);
            assertEquals(expected[tier], value.voltage(), names[tier] + " voltage");
            assertEquals(names[tier], value.name(), "tier " + tier + " name");
            assertEquals(tier, value.index());
        }
        // Every step is exactly four times the previous one, which is what keeps the table a
        // power-of-two ladder GT's tier lookup can invert.
        for (int tier = 1; tier < expected.length; tier++) {
            assertEquals(expected[tier - 1] * 4, expected[tier], "tier " + tier + " step");
        }
    }

    @Test
    void forVoltageRoundsUpToTheTierThatCanCarryIt() {
        assertSame(EuTier.ULV, EuTier.forVoltage(1));
        assertSame(EuTier.ULV, EuTier.forVoltage(8));
        assertSame(EuTier.LV, EuTier.forVoltage(9));
        assertSame(EuTier.LV, EuTier.forVoltage(32));
        assertSame(EuTier.MV, EuTier.forVoltage(33));
        assertSame(EuTier.MAX, EuTier.forVoltage(2_147_483_648L));
        assertSame(EuTier.MAX, EuTier.forVoltage(Long.MAX_VALUE), "clamped, never out of range");
        assertSame(EuTier.ULV, EuTier.forVoltage(0));
        assertSame(EuTier.ULV, EuTier.forVoltage(-5));
    }

    @Test
    void indexLookupRejectsUnknownTiers() {
        assertThrows(IllegalArgumentException.class, () -> EuTier.byIndex(-1));
        assertThrows(IllegalArgumentException.class, () -> EuTier.byIndex(EuTier.count()));
    }
}
