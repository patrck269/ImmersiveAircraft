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
        Vec3 local = new Vec3(pad.getX() + 0.5, pad.getY() + CatapultLaunch.HEIGHT, pad.getZ() + 0.5);
        return toWorldCoordinates(level, local);
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
