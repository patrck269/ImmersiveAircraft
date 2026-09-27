package immersive_aircraft.entity;

import immersive_aircraft.catapult.CatapultLaunch;

/**
 * Deck-relative orientation for an aircraft on a Valkyrien Skies ship.
 * No Minecraft types, so unit tests drive the same decisions the entity uses.
 */
public final class DeckPose {
    private DeckPose() {
    }

    /**
     * Remote viewers must not slam pitch toward world-level ground pitch or
     * roll toward world zero. The pilot's own client keeps local control.
     */
    public static boolean skipWorldGroundOrientation(boolean occupied, boolean onShipDeck, boolean locallyControlled) {
        return occupied && onShipDeck && !locallyControlled;
    }

    public static boolean shouldLerpToServerPose(
            boolean padLocked,
            boolean landedOnShip,
            boolean locallyControlled,
            boolean occupied,
            boolean onShipDeck
    ) {
        if (skipWorldGroundOrientation(occupied, onShipDeck, locallyControlled)) {
            return false;
        }
        return CatapultLaunch.shouldLerpToServerPose(padLocked, landedOnShip, locallyControlled);
    }

    /**
     * One world-space ground step: pitch toward {@code -groundPitchDeg}, roll toward 0.
     * Returns pitch, roll in degrees.
     */
    public static double[] worldGroundStep(double pitchDeg, double rollDeg, double groundPitchDeg) {
        return new double[] {
                (pitchDeg + groundPitchDeg) * 0.9 - groundPitchDeg,
                rollDeg * 0.9
        };
    }

    /** World yaw, pitch, roll from a row-major ship rotation and deck-relative IA angles. */
    public static double[] compose(double[] shipRot, double deckYawDeg, double deckPitchDeg, double deckRollDeg) {
        return extractIa(mul(shipRot, iaRotation(deckYawDeg, deckPitchDeg, deckRollDeg)));
    }

    /** Deck-relative yaw, pitch, roll from a world pose and the same ship rotation. */
    public static double[] deckRelative(double[] shipRot, double yawDeg, double pitchDeg, double rollDeg) {
        return extractIa(mul(transpose(shipRot), iaRotation(yawDeg, pitchDeg, rollDeg)));
    }

    /** IA vehicle rotation: yaw, then pitch, then roll. Row-major 3x3. */
    public static double[] iaRotation(double yawDeg, double pitchDeg, double rollDeg) {
        return mul(mul(rotY(Math.toRadians(-yawDeg)), rotX(Math.toRadians(pitchDeg))), rotZ(Math.toRadians(rollDeg)));
    }

    static double[] extractIa(double[] m) {
        double[] q = matToQuat(m);
        double w = q[0], x = q[1], y = q[2], z = q[3];
        double pitch = Math.atan2(2.0 * (y * z + w * x), w * w - x * x - y * y + z * z);
        double yaw = Math.asin(clamp(-2.0 * (x * z - w * y), -1.0, 1.0));
        double roll = Math.atan2(2.0 * (x * y + w * z), w * w + x * x - y * y - z * z);
        return new double[] {
                wrap(-Math.toDegrees(yaw)),
                wrap(Math.toDegrees(pitch)),
                wrap(Math.toDegrees(roll))
        };
    }

    private static double[] matToQuat(double[] m) {
        double trace = m[0] + m[4] + m[8];
        double w, x, y, z;
        if (trace > 0.0) {
            double s = Math.sqrt(trace + 1.0) * 2.0;
            w = 0.25 * s;
            x = (m[7] - m[5]) / s;
            y = (m[2] - m[6]) / s;
            z = (m[3] - m[1]) / s;
        } else if (m[0] > m[4] && m[0] > m[8]) {
            double s = Math.sqrt(1.0 + m[0] - m[4] - m[8]) * 2.0;
            w = (m[7] - m[5]) / s;
            x = 0.25 * s;
            y = (m[1] + m[3]) / s;
            z = (m[2] + m[6]) / s;
        } else if (m[4] > m[8]) {
            double s = Math.sqrt(1.0 + m[4] - m[0] - m[8]) * 2.0;
            w = (m[2] - m[6]) / s;
            x = (m[1] + m[3]) / s;
            y = 0.25 * s;
            z = (m[5] + m[7]) / s;
        } else {
            double s = Math.sqrt(1.0 + m[8] - m[0] - m[4]) * 2.0;
            w = (m[3] - m[1]) / s;
            x = (m[2] + m[6]) / s;
            y = (m[5] + m[7]) / s;
            z = 0.25 * s;
        }
        double n = Math.sqrt(w * w + x * x + y * y + z * z);
        return new double[] {w / n, x / n, y / n, z / n};
    }

    private static double[] rotX(double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new double[] {1, 0, 0, 0, c, -s, 0, s, c};
    }

    private static double[] rotY(double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new double[] {c, 0, s, 0, 1, 0, -s, 0, c};
    }

    private static double[] rotZ(double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new double[] {c, -s, 0, s, c, 0, 0, 0, 1};
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

    private static double[] transpose(double[] a) {
        return new double[] {a[0], a[3], a[6], a[1], a[4], a[7], a[2], a[5], a[8]};
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double wrap(double angle) {
        double a = angle % 360.0;
        if (a > 180.0) a -= 360.0;
        if (a <= -180.0) a += 360.0;
        return a;
    }
}
