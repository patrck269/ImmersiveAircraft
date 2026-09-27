package immersive_aircraft.catapult;

import java.lang.reflect.Method;
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
    private static final Method SHIP_AT = vsMethod("getShipManagingPos", Level.class, double.class, double.class, double.class);
    private static final Method TO_WORLD_VEC = vsMethod("toWorldCoordinates", Level.class, Vec3.class);
    private static final Method TO_WORLD_BLOCK = vsMethod("toWorldCoordinates", Level.class, BlockPos.class);
    private static final Method TRANSFORM_AABB = vsMethod("transformAabbToWorld", Level.class, AABB.class);
    private static Method velocity;
    private static Method componentX;
    private static Method componentY;
    private static Method componentZ;

    private CatapultVs() {
    }

    private static Method vsMethod(String name, Class<?>... args) {
        try {
            return Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt").getMethod(name, args);
        } catch (Throwable ignored) {
            return null;
        }
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
            if (velocity == null) {
                velocity = ship.getClass().getMethod("getVelocity");
            }
            Object vel = velocity.invoke(ship);
            if (componentX == null) {
                componentX = vel.getClass().getMethod("x");
                componentY = vel.getClass().getMethod("y");
                componentZ = vel.getClass().getMethod("z");
            }
            double x = ((Number) componentX.invoke(vel)).doubleValue();
            double y = ((Number) componentY.invoke(vel)).doubleValue();
            double z = ((Number) componentZ.invoke(vel)).doubleValue();
            return new Vec3(x, y, z).scale(0.05);
        } catch (Throwable ignored) {
            return Vec3.ZERO;
        }
    }

    private static Object shipAt(Level level, BlockPos pad) throws Exception {
        if (SHIP_AT == null) {
            return null;
        }
        return SHIP_AT.invoke(null, level, pad.getX() + 0.5, pad.getY() + 0.5, pad.getZ() + 0.5);
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
        if (TO_WORLD_VEC == null) {
            return pos;
        }
        try {
            Object v = TO_WORLD_VEC.invoke(null, level, pos);
            if (v instanceof Vec3 vec) {
                return vec;
            }
        } catch (Throwable ignored) {
        }
        return pos;
    }

    public static Vec3 toWorldCoordinates(Level level, BlockPos pos) {
        if (TO_WORLD_BLOCK == null) {
            return Vec3.atLowerCornerOf(pos);
        }
        try {
            Object v = TO_WORLD_BLOCK.invoke(null, level, pos);
            if (v instanceof Vec3 vec) {
                return vec;
            }
        } catch (Throwable ignored) {
        }
        return Vec3.atLowerCornerOf(pos);
    }

    public static AABB transformAabbToWorld(Level level, AABB box) {
        if (TRANSFORM_AABB == null) {
            return box;
        }
        try {
            Object v = TRANSFORM_AABB.invoke(null, level, box);
            if (v instanceof AABB aabb) {
                return aabb;
            }
        } catch (Throwable ignored) {
        }
        return box;
    }
}
