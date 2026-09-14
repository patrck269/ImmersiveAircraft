package immersive_aircraft.catapult;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatapultLaunchTest {

    @Test
    void placementFacingIsPlayerLookNotOpposite() {
        // Look +Z (south): pad must face +Z (away), not −Z (toward the player).
        int[] south = CatapultLaunch.placementFacing(0, 1);
        assertEquals(0, south[0]);
        assertEquals(1, south[1], "look +Z must face +Z, not getOpposite()");
        int[] north = CatapultLaunch.placementFacing(0, -1);
        assertEquals(0, north[0]);
        assertEquals(-1, north[1]);
        int[] east = CatapultLaunch.placementFacing(1, 0);
        assertEquals(1, east[0]);
        assertEquals(0, east[1]);
        int[] west = CatapultLaunch.placementFacing(-1, 0);
        assertEquals(-1, west[0]);
        assertEquals(0, west[1]);
        assertFalse(
                south[1] == -1,
                "must not be 180° from look (old furnace-style toward-player facing)"
        );
    }

    @Test
    void unoccupiedDockYIsHalfBlockPadTopNotFullBlock() {
        assertEquals(0.5, CatapultLaunch.dockY(0.0), 1e-9);
        assertEquals(64.5, CatapultLaunch.dockY(64.0), 1e-9);
        assertEquals(CatapultLaunch.HEIGHT, CatapultLaunch.dockY(0.0), 1e-9);
        assertFalse(
                Math.abs(CatapultLaunch.dockY(0.0) - 1.0) < 1e-9,
                "must not rest at padY+1 on a 0.5-high pad"
        );
    }

    @Test
    void collisionIsOneByOneByHalf() {
        assertEquals(1.0, CatapultLaunch.WIDTH, 1e-9);
        assertEquals(1.0, CatapultLaunch.LENGTH, 1e-9);
        assertEquals(0.5, CatapultLaunch.HEIGHT, 1e-9);
        double[] box = CatapultLaunch.collisionBox();
        assertEquals(0.0, box[0], 1e-9);
        assertEquals(0.0, box[1], 1e-9);
        assertEquals(0.0, box[2], 1e-9);
        assertEquals(CatapultLaunch.WIDTH, box[3], 1e-9);
        assertEquals(CatapultLaunch.HEIGHT, box[4], 1e-9);
        assertEquals(CatapultLaunch.LENGTH, box[5], 1e-9);
    }

    @Test
    void restOnPadGetsLaunchImpulseAlongPadFacing() {
        // Pad facing +Z (south). Impulse is pad facing, not vehicle look.
        double[] launched = CatapultLaunch.launchVelocity(0.0, 0.0, 0.0, 0.0, 1.0);
        assertEquals(0.0, launched[0], 1e-9);
        assertTrue(launched[2] > 0.0, "forward impulse must be non-zero, was " + launched[2]);
        assertEquals(3.0, CatapultLaunch.FORWARD, 1e-9, "forward push was doubled from 1.5");
        assertEquals(0.2, CatapultLaunch.UP, 1e-9, "up push was reduced from 0.4");
        assertEquals(CatapultLaunch.FORWARD, launched[2], 1e-9);
        assertTrue(launched[1] > 0.0, "up impulse must leave the deck, was " + launched[1]);
        assertTrue(launched[1] < 0.4, "up must be weaker than the old 0.4 pop, was " + launched[1]);
        assertEquals(CatapultLaunch.UP, launched[1], 1e-9);
        double[] west = CatapultLaunch.launchVelocity(0.0, 0.0, 0.0, -1.0, 0.0);
        assertEquals(-CatapultLaunch.FORWARD, west[0], 1e-9);
        assertEquals(CatapultLaunch.UP, west[1], 1e-9);
        assertEquals(0.0, west[2], 1e-9);
    }

    @Test
    void dockClampStopsAfterKeyLaunchSoImpulseIsNotZeroed() {
        assertTrue(
                CatapultLaunch.shouldClampDock(0, false, 0.0),
                "idle pad must still lock"
        );
        assertTrue(
                CatapultLaunch.shouldClampDock(0, false, 0.1),
                "slow taxi onto the pad must still lock"
        );
        assertFalse(
                CatapultLaunch.shouldClampDock(CatapultLaunch.COOLDOWN_TICKS, false, 0.0),
                "cooldown after fire must not re-clamp and eat the launch"
        );
        assertFalse(
                CatapultLaunch.shouldClampDock(0, true, 0.0),
                "launch tag must not re-clamp"
        );
        assertFalse(
                CatapultLaunch.shouldClampDock(0, false, CatapultLaunch.FORWARD),
                "already-thrown speed must not re-clamp"
        );
    }

    @Test
    void risingEdgeFiresOnceThenCoolsDown() {
        assertTrue(CatapultLaunch.shouldFire(true, false, 0));
        assertFalse(CatapultLaunch.shouldFire(true, true, 0), "held signal must not re-fire");
        assertFalse(CatapultLaunch.shouldFire(true, false, 1), "cooldown blocks fire");
        assertFalse(CatapultLaunch.shouldFire(false, false, 0));
    }

    @Test
    void restOnPadTopIsDetectedFlyerAtTwoIsNot() {
        // Legal spawn sits on the collision top (minY = HEIGHT), not clipped into the pad.
        assertTrue(
                CatapultLaunch.vehicleOnPad(
                        0.1, CatapultLaunch.HEIGHT, 0.1,
                        0.9, CatapultLaunch.HEIGHT + 0.8, 0.9,
                        0.0, 0.0, 0.0
                ),
                "feet on pad top must count as on-pad"
        );
        assertFalse(
                CatapultLaunch.vehicleOnPad(
                        0.2, 2.0, 0.2,
                        0.8, 2.6, 0.8,
                        0.0, 0.0, 0.0
                ),
                "flyer at y=2 must not count as on-pad"
        );
    }

    @Test
    void shipTransformMapsShipyardPadOntoWorldVehicle() {
        // Identity: world == ship space.
        double[] id = CatapultLaunch.identityMatrix();
        double[] local = CatapultLaunch.worldDetectionAabb(0, 64, 0, id);
        assertEquals(0.0, local[0], 1e-9);
        assertEquals(64.0, local[1], 1e-9);
        assertEquals(1.0, local[3], 1e-9);
        assertEquals(64.0 + CatapultLaunch.HEIGHT + CatapultLaunch.DETECT_ABOVE, local[4], 1e-9);

        // Translate shipyard pad (1_875_000, 100, 10) to world (20, 70, -40).
        double[] m = CatapultLaunch.translationMatrix(20.0 - 1_875_000.0, 70.0 - 100.0, -40.0 - 10.0);
        double[] worldPad = CatapultLaunch.worldDetectionAabb(1_875_000, 100, 10, m);
        // Vehicle sitting on the world pad top.
        assertTrue(
                CatapultLaunch.intersectsPad(
                        20.1, 70.0 + CatapultLaunch.HEIGHT, -39.9,
                        20.9, 70.0 + CatapultLaunch.HEIGHT + 0.8, -39.1,
                        worldPad[0], worldPad[1], worldPad[2],
                        worldPad[3], worldPad[4], worldPad[5]
                ),
                "vehicle in world space must lock to a ship-mounted pad"
        );
        assertFalse(
                CatapultLaunch.intersectsPad(
                        20.2, 72.0, -39.8,
                        20.8, 72.6, -39.2,
                        worldPad[0], worldPad[1], worldPad[2],
                        worldPad[3], worldPad[4], worldPad[5]
                ),
                "flyer above the world pad still ignored"
        );

        double[] dock = CatapultLaunch.transformPoint(1_875_000.5, 100.0 + CatapultLaunch.HEIGHT, 10.5, m);
        assertEquals(20.5, dock[0], 1e-6);
        assertEquals(70.0 + CatapultLaunch.HEIGHT, dock[1], 1e-6);
        assertEquals(-39.5, dock[2], 1e-6);

        double[] dir = CatapultLaunch.transformDirection(0.0, 0.0, 1.0, m);
        assertEquals(0.0, dir[0], 1e-9);
        assertEquals(0.0, dir[1], 1e-9);
        assertEquals(1.0, dir[2], 1e-9);
    }
}
