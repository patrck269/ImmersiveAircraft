package immersive_aircraft.emc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ProjectE treats all {@code minecraft:firework_rocket} stacks as one NSS
 * unless persistent NBT / EMC includes Flight and explosions. This is the
 * shipped mapping both the mixin and unit tests call.
 */
public final class FireworkEmcIdentity {
    public static final String FIREWORK_ROCKET = "minecraft:firework_rocket";
    public static final String FIREWORK_STAR = "minecraft:firework_star";
    /** Extra gunpowder in the vanilla rocket recipe per Flight above 1. */
    public static final long GUNPOWDER_EMC = 192L;
    /** Vanilla firework star is paper+gunpowder+dye; bonus per explosion. */
    public static final long EXPLOSION_EMC = 256L;

    private FireworkEmcIdentity() {
    }

    public static boolean isFireworkItem(String itemId) {
        return FIREWORK_ROCKET.equals(itemId) || FIREWORK_STAR.equals(itemId);
    }

    public static String identityKey(String itemId, int flight, String explosionsCanonical) {
        return itemId + "|F" + flight + "|E" + (explosionsCanonical == null ? "" : explosionsCanonical);
    }

    public static String canonicalExplosions(List<String> explosionEntries) {
        if (explosionEntries == null || explosionEntries.isEmpty()) {
            return "";
        }
        List<String> copy = new ArrayList<>(explosionEntries);
        Collections.sort(copy);
        return String.join(";", copy);
    }

    public static long emc(long baseEmc, int flight, String explosionsCanonical) {
        return Math.addExact(baseEmc, emcBonus(flight, explosionsCanonical));
    }

    public static long emcBonus(int flight, String explosionsCanonical) {
        int extraPowder = Math.max(0, flight - 1);
        int explosions = 0;
        if (explosionsCanonical != null && !explosionsCanonical.isEmpty()) {
            explosions = explosionsCanonical.split(";", -1).length;
        }
        return Math.addExact(
                Math.multiplyExact((long) extraPowder, GUNPOWDER_EMC),
                Math.multiplyExact((long) explosions, EXPLOSION_EMC)
        );
    }
}
