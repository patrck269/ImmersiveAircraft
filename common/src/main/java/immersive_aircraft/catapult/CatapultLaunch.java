package immersive_aircraft.catapult;

/**
 * Pure launch/collision kinematics for the catapult block. No Minecraft types
 * so JVM unit tests can drive the same functions the block uses.
 */
public final class CatapultLaunch {
    public static final double WIDTH = 1.0;
    public static final double LENGTH = 1.0;
    public static final double HEIGHT = 0.5;
    public static final double FORWARD = 1.5;
    public static final double UP = 0.4;
    public static final int COOLDOWN_TICKS = 40;
    /** Extra height above the collision top so a vehicle with minY = HEIGHT is on-pad. */
    public static final double DETECT_ABOVE = 1.0;

    private CatapultLaunch() {
    }

    /** AABB minX,minY,minZ,maxX,maxY,maxZ in block units for a pad at the origin. */
    public static double[] collisionBox() {
        return new double[] {0.0, 0.0, 0.0, WIDTH, HEIGHT, LENGTH};
    }

    /**
     * Search volume including vehicles resting on the pad top (minY = HEIGHT).
     * Minecraft AABB.intersects is strict, so collisionBox alone misses that pose.
     */
    public static double[] detectionBox() {
        return new double[] {0.0, 0.0, 0.0, WIDTH, HEIGHT + DETECT_ABOVE, LENGTH};
    }

    public static boolean vehicleOnPad(
            double vminx, double vminy, double vminz,
            double vmaxx, double vmaxy, double vmaxz,
            double padX, double padY, double padZ
    ) {
        double[] d = detectionBox();
        return intersectsPad(
                vminx, vminy, vminz, vmaxx, vmaxy, vmaxz,
                padX + d[0], padY + d[1], padZ + d[2],
                padX + d[3], padY + d[4], padZ + d[5]
        );
    }

    /**
     * @param vx,vy,vz current velocity (blocks/tick)
     * @param fwdX,fwdY,fwdZ vehicle look vector (need not be unit)
     * @return new velocity (blocks/tick)
     */
    public static double[] launchVelocity(
            double vx, double vy, double vz,
            double fwdX, double fwdY, double fwdZ
    ) {
        double len = Math.sqrt(fwdX * fwdX + fwdY * fwdY + fwdZ * fwdZ);
        if (len < 1.0e-8) {
            fwdX = 0.0;
            fwdY = 0.0;
            fwdZ = 1.0;
            len = 1.0;
        }
        fwdX /= len;
        fwdY /= len;
        fwdZ /= len;
        return new double[] {
                vx + fwdX * FORWARD,
                vy + fwdY * FORWARD + UP,
                vz + fwdZ * FORWARD
        };
    }

    public static boolean shouldFire(boolean signalNow, boolean signalWas, int cooldownRemaining) {
        return signalNow && !signalWas && cooldownRemaining <= 0;
    }

    public static boolean intersectsPad(
            double vminx, double vminy, double vminz,
            double vmaxx, double vmaxy, double vmaxz,
            double pminx, double pminy, double pminz,
            double pmaxx, double pmaxy, double pmaxz
    ) {
        return vminx < pmaxx && vmaxx > pminx
                && vminy < pmaxy && vmaxy > pminy
                && vminz < pmaxz && vmaxz > pminz;
    }
}
