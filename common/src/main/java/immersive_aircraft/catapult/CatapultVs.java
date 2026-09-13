package immersive_aircraft.catapult;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Optional Valkyrien Skies hook. No compile dep on VS or Eureka.
 * Shipyard block entities live at huge coords; vehicles live in world space.
 */
public final class CatapultVs {
    private CatapultVs() {
    }

    public static double[] shipToWorldOrIdentity(Level level, BlockPos pos) {
        double[] m = readShipToWorld(level, pos);
        return m != null ? m : CatapultLaunch.identityMatrix();
    }

    private static double[] readShipToWorld(Level level, BlockPos pos) {
        try {
            Class<?> kt = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt");
            Object ship;
            try {
                ship = kt.getMethod("getShipManagingPos", Level.class, BlockPos.class)
                        .invoke(null, level, pos);
            } catch (NoSuchMethodException e) {
                ship = kt.getMethod("getShipManagingPos", Level.class, int.class, int.class)
                        .invoke(null, level, pos.getX() >> 4, pos.getZ() >> 4);
            }
            if (ship == null) {
                return null;
            }
            Object mat;
            try {
                mat = ship.getClass().getMethod("getShipToWorld").invoke(ship);
            } catch (NoSuchMethodException e) {
                Object transform = ship.getClass().getMethod("getTransform").invoke(ship);
                mat = transform.getClass().getMethod("getShipToWorld").invoke(transform);
            }
            return packMatrix(mat);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static double[] packMatrix(Object mat) throws Exception {
        return new double[] {
                d(mat, "m00"), d(mat, "m10"), d(mat, "m20"), d(mat, "m30"),
                d(mat, "m01"), d(mat, "m11"), d(mat, "m21"), d(mat, "m31"),
                d(mat, "m02"), d(mat, "m12"), d(mat, "m22"), d(mat, "m32")
        };
    }

    private static double d(Object mat, String name) throws Exception {
        Class<?> c = mat.getClass();
        while (c != null) {
            try {
                return ((Number) c.getMethod(name).invoke(mat)).doubleValue();
            } catch (NoSuchMethodException e) {
                for (Class<?> iface : c.getInterfaces()) {
                    try {
                        return ((Number) iface.getMethod(name).invoke(mat)).doubleValue();
                    } catch (NoSuchMethodException ignored) {
                    }
                }
                c = c.getSuperclass();
            }
        }
        throw new NoSuchMethodException(name);
    }
}
