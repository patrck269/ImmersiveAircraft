package immersive_aircraft.catapult;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Optional Valkyrien Skies hook. Uses VS's own world-coordinate helpers so a
 * shipyard block entity can find vehicles in world space.
 */
public final class CatapultVs {
    private CatapultVs() {
    }

    public static AABB worldSearch(Level level, BlockPos pad) {
        double[] d = CatapultLaunch.detectionBox();
        AABB local = new AABB(
                pad.getX() + d[0], pad.getY() + d[1], pad.getZ() + d[2],
                pad.getX() + d[3], pad.getY() + d[4], pad.getZ() + d[5]
        );
        return transformAabbToWorld(level, local);
    }

    public static Vec3 worldDock(Level level, BlockPos pad) {
        Vec3 local = new Vec3(pad.getX() + 0.5, CatapultLaunch.dockY(pad.getY()), pad.getZ() + 0.5);
        return toWorldCoordinates(level, local);
    }

    /**
     * Ship velocity in blocks/tick. VS reports blocks/second; catapult speeds
     * are blocks/tick (the same 0.05 factor VS uses for entity drag).
     */
    public static Vec3 shipTickVelocity(Level level, BlockPos pad) {
        try {
            Object ship = shipAt(level, pad);
            if (ship == null) {
                return Vec3.ZERO;
            }
            Object vel = ship.getClass().getMethod("getVelocity").invoke(ship);
            double x = ((Number) vel.getClass().getMethod("x").invoke(vel)).doubleValue();
            double y = ((Number) vel.getClass().getMethod("y").invoke(vel)).doubleValue();
            double z = ((Number) vel.getClass().getMethod("z").invoke(vel)).doubleValue();
            return new Vec3(x, y, z).scale(0.05);
        } catch (Throwable ignored) {
            return Vec3.ZERO;
        }
    }

    private static Object shipAt(Level level, BlockPos pad) throws Exception {
        Class<?> kt = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt");
        return kt.getMethod("getShipManagingPos", Level.class, double.class, double.class, double.class)
                .invoke(null, level, pad.getX() + 0.5, pad.getY() + 0.5, pad.getZ() + 0.5);
    }

    public static Vec3 worldFacing(Level level, BlockPos pad, Direction facing) {
        Vec3 origin = toWorldCoordinates(level, Vec3.atCenterOf(pad));
        Vec3 tip = toWorldCoordinates(level, Vec3.atCenterOf(pad).add(facing.getStepX(), 0.0, facing.getStepZ()));
        Vec3 d = tip.subtract(origin);
        if (d.lengthSqr() < 1.0e-8) {
            return new Vec3(facing.getStepX(), 0.0, facing.getStepZ());
        }
        return d.normalize();
    }

    public static Vec3 toWorldCoordinates(Level level, Vec3 pos) {
        try {
            Object v = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt")
                    .getMethod("toWorldCoordinates", Level.class, Vec3.class)
                    .invoke(null, level, pos);
            if (v instanceof Vec3 vec) {
                return vec;
            }
        } catch (Throwable ignored) {
        }
        return pos;
    }

    public static Vec3 toWorldCoordinates(Level level, BlockPos pos) {
        try {
            Object v = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt")
                    .getMethod("toWorldCoordinates", Level.class, BlockPos.class)
                    .invoke(null, level, pos);
            if (v instanceof Vec3 vec) {
                return vec;
            }
        } catch (Throwable ignored) {
        }
        return Vec3.atLowerCornerOf(pos);
    }

    public static AABB transformAabbToWorld(Level level, AABB box) {
        try {
            Object v = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt")
                    .getMethod("transformAabbToWorld", Level.class, AABB.class)
                    .invoke(null, level, box);
            if (v instanceof AABB aabb) {
                return aabb;
            }
        } catch (Throwable ignored) {
        }
        return box;
    }
}
