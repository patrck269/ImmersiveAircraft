package immersive_aircraft.inventory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Break/drop policy for aircraft slots. Cargo uses the same keep-on-item
 * path as weapons and upgrades; world-drop only when the matching config
 * says drop. The live {@code dropInventory()} loop calls this.
 */
public final class VehicleBreakPolicy {
    public static final String CARGO = "inventory";
    public static final String WEAPON = "weapon";
    public static final String UPGRADE = "upgrade";
    public static final String BOOSTER = "booster";

    private VehicleBreakPolicy() {
    }

    public static boolean shouldWorldDrop(String slotType, boolean dropInventory, boolean dropUpgrades) {
        if (CARGO.equals(slotType)) {
            return dropInventory;
        }
        return dropUpgrades;
    }

    /**
     * Slots that stay inside the aircraft item NBT after break (not spawned
     * as world drops).
     */
    public static Map<Integer, String> keptOnItem(
            List<SlotStack> slots,
            boolean dropInventory,
            boolean dropUpgrades
    ) {
        Map<Integer, String> kept = new LinkedHashMap<>();
        for (SlotStack slot : slots) {
            if (slot.stackId == null || slot.stackId.isEmpty()) {
                continue;
            }
            if (!shouldWorldDrop(slot.type, dropInventory, dropUpgrades)) {
                kept.put(slot.index, slot.stackId);
            }
        }
        return kept;
    }

    public static final class SlotStack {
        public final String type;
        public final int index;
        public final String stackId;

        public SlotStack(String type, int index, String stackId) {
            this.type = type;
            this.index = index;
            this.stackId = stackId;
        }
    }
}
