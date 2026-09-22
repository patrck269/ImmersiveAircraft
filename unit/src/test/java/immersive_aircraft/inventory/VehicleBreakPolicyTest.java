package immersive_aircraft.inventory;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleBreakPolicyTest {

    @Test
    void cargoStaysOnItemLikeWeaponsAndUpgrades() {
        assertFalse(
                VehicleBreakPolicy.shouldWorldDrop(VehicleBreakPolicy.CARGO, false, false),
                "cargo must not world-drop when dropInventory is off"
        );
        assertFalse(VehicleBreakPolicy.shouldWorldDrop(VehicleBreakPolicy.WEAPON, false, false));
        assertFalse(VehicleBreakPolicy.shouldWorldDrop(VehicleBreakPolicy.UPGRADE, false, false));
        assertTrue(
                VehicleBreakPolicy.shouldWorldDrop(VehicleBreakPolicy.WEAPON, false, true),
                "weapons/upgrades still honor dropUpgrades"
        );

        VehicleBreakPolicy.SlotStack cargo = new VehicleBreakPolicy.SlotStack(
                VehicleBreakPolicy.CARGO, 4, "minecraft:iron_ingot"
        );
        VehicleBreakPolicy.SlotStack weapon = new VehicleBreakPolicy.SlotStack(
                VehicleBreakPolicy.WEAPON, 0, "immersive_aircraft:heavy_crossbow"
        );
        VehicleBreakPolicy.SlotStack upgrade = new VehicleBreakPolicy.SlotStack(
                VehicleBreakPolicy.UPGRADE, 1, "immersive_aircraft:enhanced_propeller"
        );
        Map<Integer, String> kept = VehicleBreakPolicy.keptOnItem(
                List.of(cargo, weapon, upgrade),
                false,
                false
        );
        assertEquals("minecraft:iron_ingot", kept.get(4), "cargo written before break is on the item tag");
        assertEquals("immersive_aircraft:heavy_crossbow", kept.get(0));
        assertEquals("immersive_aircraft:enhanced_propeller", kept.get(1));
        assertEquals(3, kept.size());

        Map<Integer, String> cargoDumped = VehicleBreakPolicy.keptOnItem(
                List.of(cargo, weapon, upgrade),
                true,
                false
        );
        assertFalse(cargoDumped.containsKey(4), "dropInventory true still world-drops cargo");
        assertTrue(cargoDumped.containsKey(0));
        assertTrue(cargoDumped.containsKey(1));
    }
}
