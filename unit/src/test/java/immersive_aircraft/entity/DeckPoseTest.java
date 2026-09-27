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
