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

    /**
     * World yaw, pitch, roll for a non-pilot client. Null on the dedicated server,
     * for the pilot, and off the deck — those sides must not overwrite rotation.
     * The angles are the deck-relative rest (flat on the deck is pitch 0, roll 0),
     * not world angles and not a roll that was zeroed in world space.
     */
    public static double[] remoteObserverPose(
            boolean clientSide,
            boolean occupied,
            boolean onShipDeck,
            boolean locallyControlled,
            double[] shipRot,
            double deckYawDeg,
            double deckPitchDeg,
            double deckRollDeg
    ) {
        if (!clientSide || shipRot == null) {
            return null;
        }
        if (!skipWorldGroundOrientation(occupied, onShipDeck, locallyControlled)) {
            return null;
        }
        return compose(shipRot, deckYawDeg, deckPitchDeg, deckRollDeg);
    }

    /**
     * Heading on the deck, sitting flat. Pitch and roll stay 0 in ship space.
     * The deck yaw is the heading whose forward, carried by the ship, matches
     * {@code worldYawDeg} on the horizontal. It is not world yaw minus the
     * folded euler yaw of the ship: that yaw only spans ±90° and inverts the
     * aircraft once the ship yaws past 90° while pitched or rolled.
     */
    public static double[] deckFlatRest(double[] shipRot, double worldYawDeg) {
        double yaw = Math.toRadians(worldYawDeg);
        double dx = -Math.sin(yaw);
        double dz = Math.cos(yaw);
        double c0x = shipRot[0];
        double c0z = shipRot[6];
        double c2x = shipRot[2];
        double c2z = shipRot[8];
        double ax = c2x;
        double az = c2z;
        double bx = -c0x;
        double bz = -c0z;
        double p = ax * dz - az * dx;
        double q = bx * dz - bz * dx;
        if (p * p + q * q < 1.0e-12) {
            return new double[] { wrap(worldYawDeg), 0.0, 0.0 };
        }
        double d = Math.atan2(p, -q);
        double sd = Math.sin(d);
        double cd = Math.cos(d);
        double hx = cd * ax + sd * bx;
        double hz = cd * az + sd * bz;
        if (hx * dx + hz * dz < 0.0) {
            d += Math.PI;
        }
        return new double[] { wrap(Math.toDegrees(d)), 0.0, 0.0 };
    }

    /** Stick roll must not run for the remote on-deck view; it writes world roll 0. */
    public static boolean applyControlRoll(boolean onGround, boolean occupied, boolean onShipDeck, boolean locallyControlled) {
        if (skipWorldGroundOrientation(occupied, onShipDeck, locallyControlled)) {
            return false;
        }
        return !onGround;
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

    /**
     * Yaw, pitch, roll whose {@link #iaRotation} rebuilds {@code m}.
     * Pitch stays in ±90°. An asin yaw only spans ±90° and, once the ship yaws
     * past that while pitched or rolled, rebuilds a pose whose up is opposite
     * the deck.
     */
    static double[] extractIa(double[] m) {
        double cp = Math.hypot(m[3], m[4]);
        double pitch;
        double roll;
        double yaw;
        if (cp > 1.0e-8) {
            pitch = Math.atan2(-m[5], cp);
            roll = Math.atan2(m[3], m[4]);
            yaw = Math.atan2(m[2], m[8]);
        } else {
            pitch = Math.atan2(-m[5], cp);
            roll = 0.0;
            yaw = -m[5] >= 0.0 ? Math.atan2(m[1], m[0]) : Math.atan2(-m[1], m[0]);
        }
        return new double[] {
                wrap(-Math.toDegrees(yaw)),
                wrap(Math.toDegrees(pitch)),
                wrap(Math.toDegrees(roll))
        };
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

    private static double wrap(double angle) {
        double a = angle % 360.0;
        if (a > 180.0) a -= 360.0;
        if (a <= -180.0) a += 360.0;
        return a;
    }
}
