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

    private CatapultLaunch() {
    }

    /** AABB minX,minY,minZ,maxX,maxY,maxZ in block units for a pad at the origin. */
    public static double[] collisionBox() {
        return new double[] {0.0, 0.0, 0.0, WIDTH, HEIGHT, LENGTH};
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
