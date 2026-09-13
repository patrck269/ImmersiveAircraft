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
    public static final String LAUNCH_TAG = "ia_catapult_launch";
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
     * @param faceX,faceZ pad horizontal facing (need not be unit; Y is ignored)
     * @return new velocity (blocks/tick)
     */
    public static double[] launchVelocity(
            double vx, double vy, double vz,
            double faceX, double faceZ
    ) {
        double len = Math.sqrt(faceX * faceX + faceZ * faceZ);
        if (len < 1.0e-8) {
            faceX = 0.0;
            faceZ = 1.0;
            len = 1.0;
        }
        faceX /= len;
        faceZ /= len;
        return new double[] {
                vx + faceX * FORWARD,
                vy + UP,
                vz + faceZ * FORWARD
        };
    }

    public static boolean shouldFire(boolean signalNow, boolean signalWas, int cooldownRemaining) {
        return signalNow && !signalWas && cooldownRemaining <= 0;
    }

    /** Column-major 3x4 affine: x' = m0*x + m1*y + m2*z + m3, etc. */
    public static double[] identityMatrix() {
        return new double[] {
                1, 0, 0, 0,
                0, 1, 0, 0,
                0, 0, 1, 0
        };
    }

    public static double[] translationMatrix(double tx, double ty, double tz) {
        return new double[] {
                1, 0, 0, tx,
                0, 1, 0, ty,
                0, 0, 1, tz
        };
    }

    public static double[] transformPoint(double x, double y, double z, double[] m) {
        return new double[] {
                m[0] * x + m[1] * y + m[2] * z + m[3],
                m[4] * x + m[5] * y + m[6] * z + m[7],
                m[8] * x + m[9] * y + m[10] * z + m[11]
        };
    }

    public static double[] transformDirection(double x, double y, double z, double[] m) {
        return new double[] {
                m[0] * x + m[1] * y + m[2] * z,
                m[4] * x + m[5] * y + m[6] * z,
                m[8] * x + m[9] * y + m[10] * z
        };
    }

    public static double[] worldDetectionAabb(int padX, int padY, int padZ, double[] m) {
        double[] d = detectionBox();
        double minx = padX + d[0], miny = padY + d[1], minz = padZ + d[2];
        double maxx = padX + d[3], maxy = padY + d[4], maxz = padZ + d[5];
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 8; i++) {
            double x = (i & 1) == 0 ? minx : maxx;
            double y = (i & 2) == 0 ? miny : maxy;
            double z = (i & 4) == 0 ? minz : maxz;
            double[] p = transformPoint(x, y, z, m);
            minX = Math.min(minX, p[0]);
            minY = Math.min(minY, p[1]);
            minZ = Math.min(minZ, p[2]);
            maxX = Math.max(maxX, p[0]);
            maxY = Math.max(maxY, p[1]);
            maxZ = Math.max(maxZ, p[2]);
        }
        return new double[] {minX, minY, minZ, maxX, maxY, maxZ};
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
