package immersive_aircraft.catapult;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatapultLaunchTest {

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
    void restOnPadGetsLaunchImpulseAlongLook() {
        // Vehicle at rest on the pad, looking +Z (north in Minecraft is -Z; +Z is south).
        double[] launched = CatapultLaunch.launchVelocity(0.0, 0.0, 0.0, 0.0, 0.0, 1.0);
        assertEquals(0.0, launched[0], 1e-9);
        assertTrue(launched[2] > 0.0, "forward impulse must be non-zero, was " + launched[2]);
        assertEquals(CatapultLaunch.FORWARD, launched[2], 1e-9);
        assertTrue(launched[1] > 0.0, "up impulse must leave the deck, was " + launched[1]);
        assertEquals(CatapultLaunch.UP, launched[1], 1e-9);
    }

    @Test
    void risingEdgeFiresOnceThenCoolsDown() {
        assertTrue(CatapultLaunch.shouldFire(true, false, 0));
        assertFalse(CatapultLaunch.shouldFire(true, true, 0), "held signal must not re-fire");
        assertFalse(CatapultLaunch.shouldFire(true, false, 1), "cooldown blocks fire");
        assertFalse(CatapultLaunch.shouldFire(false, false, 0));
    }

    @Test
    void flyingPastDoesNotIntersectHalfHighPad() {
        // Pad at origin: y 0..0.5. Vehicle flying at y=2.
        assertFalse(CatapultLaunch.intersectsPad(
                0.2, 2.0, 0.2, 0.8, 2.6, 0.8,
                0.0, 0.0, 0.0, 1.0, 0.5, 1.0
        ));
        // Vehicle sitting on the pad.
        assertTrue(CatapultLaunch.intersectsPad(
                0.1, 0.0, 0.1, 0.9, 0.8, 0.9,
                0.0, 0.0, 0.0, 1.0, 0.5, 1.0
        ));
    }
}
