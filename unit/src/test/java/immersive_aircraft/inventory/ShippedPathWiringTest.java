package immersive_aircraft.inventory;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShippedPathWiringTest {

    @Test
    void liveBreakLoopAndProjectEMixinCallShippedHelpers() throws IOException {
        String drop = Files.readString(firstExisting(
                Path.of("..", "common", "src", "main", "java", "immersive_aircraft", "entity", "InventoryVehicleEntity.java"),
                Path.of("common", "src", "main", "java", "immersive_aircraft", "entity", "InventoryVehicleEntity.java")
        ));
        assertTrue(drop.contains("VehicleBreakPolicy.shouldWorldDrop"));
        assertTrue(drop.contains("addItemTag"));
        assertFalse(
                drop.contains("isCargo && Config.getInstance().dropInventory"),
                "inline cargo-drop must not bypass VehicleBreakPolicy"
        );

        String mixin = Files.readString(firstExisting(
                Path.of("..", "forge", "src", "main", "java", "immersive_aircraft", "forge", "mixin", "MixinProjectEFireworkNbt.java"),
                Path.of("forge", "src", "main", "java", "immersive_aircraft", "forge", "mixin", "MixinProjectEFireworkNbt.java")
        ));
        assertTrue(mixin.contains("FireworkEmcIdentity.emcBonus"));
        assertTrue(mixin.contains("getPersistentInfo"));
        assertTrue(mixin.contains("getEmcValue"));
        assertTrue(mixin.contains("moze_intel.projecte.emc.nbt.NBTManager"));
    }

    private static Path firstExisting(Path... candidates) {
        for (Path path : candidates) {
            if (Files.exists(path)) {
                return path;
            }
        }
        return candidates[0];
    }
}
