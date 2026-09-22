package immersive_aircraft.emc;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FireworkEmcIdentityTest {

    @Test
    void flightAndExplosionVariantsHaveDistinctIdentitiesAndEmc() {
        String id = FireworkEmcIdentity.FIREWORK_ROCKET;
        String none = FireworkEmcIdentity.canonicalExplosions(List.of());
        String burst = FireworkEmcIdentity.canonicalExplosions(List.of("{Type:1}"));
        String flight1 = FireworkEmcIdentity.identityKey(id, 1, none);
        String alsoFlight1 = FireworkEmcIdentity.identityKey(id, 1, none);
        String flight2 = FireworkEmcIdentity.identityKey(id, 2, none);
        String flight3 = FireworkEmcIdentity.identityKey(id, 3, none);
        String withStar = FireworkEmcIdentity.identityKey(id, 1, burst);

        assertEquals(flight1, alsoFlight1, "identical Flight-1 stacks must share identity");
        assertNotEquals(flight1, flight2, "Flight 1 vs 2 must not share transmutation identity");
        assertNotEquals(flight2, flight3, "Flight 2 vs 3 must not share transmutation identity");
        assertNotEquals(flight1, withStar, "rocket with vs without explosion stars must differ");

        long base = 768L;
        long emc1 = FireworkEmcIdentity.emc(base, 1, none);
        long emc1again = FireworkEmcIdentity.emc(base, 1, none);
        long emc2 = FireworkEmcIdentity.emc(base, 2, none);
        long emc3 = FireworkEmcIdentity.emc(base, 3, none);
        long emcStar = FireworkEmcIdentity.emc(base, 1, burst);
        assertEquals(emc1, emc1again);
        assertEquals(base, emc1, "Flight-1 empty rocket is the vanilla recipe base");
        assertNotEquals(emc1, emc2);
        assertNotEquals(emc2, emc3);
        assertNotEquals(emc1, emcStar);
        assertTrue(emc2 > emc1, "extra gunpowder for Flight 2");
        assertTrue(emcStar > emc1, "explosion star adds EMC");
        assertFalse(FireworkEmcIdentity.isFireworkItem("minecraft:paper"));
        assertTrue(FireworkEmcIdentity.isFireworkItem(id));
    }
}
