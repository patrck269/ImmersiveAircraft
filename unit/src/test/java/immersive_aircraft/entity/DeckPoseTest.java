package immersive_aircraft.entity;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeckPoseTest {

    @Test
    void remoteOccupiedOnDeckSkipsWorldLandingAndUsesShipPose() throws Exception {
        assertTrue(DeckPose.skipWorldGroundOrientation(true, true, false));
        assertFalse(DeckPose.shouldLerpToServerPose(false, false, false, true, true));
        assertFalse(
                DeckPose.skipWorldGroundOrientation(false, false, false),
                "a free remote aircraft still takes the world-space ground step"
        );
        assertTrue(
                DeckPose.shouldLerpToServerPose(false, false, false, false, false),
                "a free remote aircraft still lerps toward the server pose"
        );
        assertFalse(
                DeckPose.skipWorldGroundOrientation(true, true, true),
                "the pilot keeps local control"
        );

        double[] ship = mul(rotY(Math.toRadians(40.0)), rotX(Math.toRadians(25.0)));
        double deckYaw = 12.0;
        double deckPitch = -4.0;
        double deckRoll = 6.0;
        double[] once = DeckPose.compose(ship, deckYaw, deckPitch, deckRoll);
        double[] twice = DeckPose.compose(ship, deckYaw, deckPitch, deckRoll);
        assertEquals(once[0], twice[0], 1.0e-6);
        assertEquals(once[1], twice[1], 1.0e-6);
        assertEquals(once[2], twice[2], 1.0e-6);

        double[] back = DeckPose.deckRelative(ship, once[0], once[1], once[2]);
        assertEquals(deckYaw, back[0], 1.0);
        assertEquals(deckPitch, back[1], 1.0);
        assertEquals(deckRoll, back[2], 1.0);

        double groundPitch = -8.0;
        double[] slammed = DeckPose.worldGroundStep(once[1], once[2], groundPitch);
        assertTrue(
                Math.abs(slammed[0] - once[1]) > 1.0 || Math.abs(slammed[1] - once[2]) > 1.0,
                "world-space landing pitch/roll is not the deck-composed pose"
        );

        double[] bank = rotZ(Math.toRadians(80.0));
        double[] shipFlat = DeckPose.compose(bank, 0.0, 0.0, 0.0);
        assertTrue(Math.abs(shipFlat[2]) > 70.0, "fixture bank is a world roll, not pitch");
        double[] observer = DeckPose.remoteObserverPose(true, true, true, false, bank, 0.0, 0.0, 0.0);
        assertEquals(shipFlat[0], observer[0], 1.0, "observer yaw tracks the ship");
        assertEquals(shipFlat[1], observer[1], 1.0, "observer pitch tracks the ship");
        assertEquals(shipFlat[2], observer[2], 1.0, "observer roll tracks the ship");
        assertTrue(Math.abs(observer[2]) > 70.0, "an 80 degree deck bank must not come out world roll 0");
        double[] rollSlam = DeckPose.worldGroundStep(observer[1], observer[2], 0.0);
        assertTrue(Math.abs(rollSlam[1]) < Math.abs(observer[2]),
                "the world-space roll slam pulls toward 0 and is not the deck pose");
        double[] pitched = rotX(Math.toRadians(35.0));
        double[] shipPitch = DeckPose.compose(pitched, 0.0, 0.0, 0.0);
        double[] pitchedObserver = DeckPose.remoteObserverPose(true, true, true, false, pitched, 0.0, 0.0, 0.0);
        assertEquals(shipPitch[1], pitchedObserver[1], 1.0, "observer pitch tracks a pitched deck");
        assertTrue(Math.abs(pitchedObserver[1]) > 20.0);
        double[] yawed = mul(rotY(Math.toRadians(-100.0)), rotX(Math.toRadians(70.0)));
        assertDeckFlatHeading(yawed, 25.0);
        double[] yawedRolled = mul(rotY(Math.toRadians(-120.0)), rotZ(Math.toRadians(40.0)));
        assertDeckFlatHeading(yawedRolled, 15.0);

        assertNull(DeckPose.remoteObserverPose(false, true, true, false, bank, 0.0, 0.0, 0.0),
                "the dedicated server must not overwrite the pilot pose");
        assertNull(DeckPose.remoteObserverPose(true, true, true, true, bank, 0.0, 0.0, 0.0),
                "the pilot keeps local control");
        assertFalse(DeckPose.applyControlRoll(false, true, true, false),
                "remote on-deck must not write stick roll 0");

        String aircraft = Files.readString(Path.of(
                "..", "common", "src", "main", "java", "immersive_aircraft", "entity", "AircraftEntity.java"
        ));
        String vehicle = Files.readString(Path.of(
                "..", "common", "src", "main", "java", "immersive_aircraft", "entity", "VehicleEntity.java"
        ));
        assertTrue(aircraft.contains("DeckPose.skipWorldGroundOrientation"));
        assertTrue(aircraft.contains("DeckPose.applyControlRoll"));
        assertTrue(aircraft.contains("DeckPose.worldGroundStep"));
        assertTrue(vehicle.contains("DeckPose.remoteObserverPose"));
        assertTrue(vehicle.contains("DeckPose.deckFlatRest"));
        assertTrue(vehicle.contains("level().isClientSide()"));
        assertFalse(vehicle.contains("deckRelative(ship, serverYRot"));
    }

    /**
     * Ship yawed past 90° and pitched or rolled. The deck-flat rest is solved from
     * the true horizontal heading, then the observer pose must match composing that
     * real deck yaw. A folded euler subtraction inverts the aircraft (up opposite
     * the ship) on these poses.
     */
    private static void assertDeckFlatHeading(double[] ship, double realDeckYaw) {
        double[] trueRot = mul(ship, DeckPose.iaRotation(realDeckYaw, 0.0, 0.0));
        double worldYaw = Math.toDegrees(Math.atan2(-trueRot[2], trueRot[8]));
        double[] rest = DeckPose.deckFlatRest(ship, worldYaw);
        assertEquals(0.0, rest[1], 0.0, "deck-flat rest pitch");
        assertEquals(0.0, rest[2], 0.0, "deck-flat rest roll");
        double[] solved = mul(ship, DeckPose.iaRotation(rest[0], 0.0, 0.0));
        assertMatrixNear(trueRot, solved);

        double upDot = solved[1] * ship[1] + solved[4] * ship[4] + solved[7] * ship[7];
        assertTrue(upDot > 0.99, "aircraft up must stay parallel to the ship, dot=" + upDot);

        double[] observer = DeckPose.remoteObserverPose(
                true, true, true, false, ship, rest[0], rest[1], rest[2]
        );
        double[] expected = DeckPose.compose(ship, realDeckYaw, 0.0, 0.0);
        assertAngleNear(expected[0], observer[0], 2.0);
        assertAngleNear(expected[1], observer[1], 2.0);
        assertAngleNear(expected[2], observer[2], 2.0);

        double[] rendered = DeckPose.iaRotation(observer[0], observer[1], observer[2]);
        assertMatrixNear(trueRot, rendered);

        double[] deck = DeckPose.deckRelative(ship, observer[0], observer[1], observer[2]);
        assertAngleNear(realDeckYaw, deck[0], 3.0);
        assertTrue(Math.abs(wrapDiff(0.0, deck[1])) < 3.0, "deck pitch " + deck[1]);
        assertTrue(Math.abs(wrapDiff(0.0, deck[2])) < 3.0,
                "deck-relative roll stays near 0, not inverted: " + deck[2]);
    }

    private static void assertMatrixNear(double[] expected, double[] actual) {
        for (int i = 0; i < 9; i++) {
            assertEquals(expected[i], actual[i], 1.0e-4, "element " + i);
        }
    }

    private static void assertAngleNear(double expected, double actual, double tol) {
        double d = wrapDiff(expected, actual);
        assertTrue(Math.abs(d) <= tol, "expected " + expected + " got " + actual + " (delta " + d + ")");
    }

    private static double wrapDiff(double expected, double actual) {
        double d = (expected - actual) % 360.0;
        if (d > 180.0) {
            d -= 360.0;
        }
        if (d <= -180.0) {
            d += 360.0;
        }
        return d;
    }

    private static double[] rotX(double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new double[] {1, 0, 0, 0, c, -s, 0, s, c};
    }

    private static double[] rotZ(double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new double[] {c, -s, 0, s, c, 0, 0, 0, 1};
    }

    private static double[] rotY(double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new double[] {c, 0, s, 0, 1, 0, -s, 0, c};
    }

    private static double[] mul(double[] a, double[] b) {
        double[] o = new double[9];
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                o[r * 3 + c] = a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c];
            }
        }
        return o;
    }
}
