package immersive_aircraft.catapult;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static final Method SHIP_WORLD = vsMethod("getShipObjectWorld", Level.class);
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

    public static boolean isShipAt(Level level, double x, double y, double z) {
        return worldShipAt(level, x, y, z) != null;
    }

    /** Row-major 3x3 of the live ship rotation at a world point, or null off-ship. */
    public static double[] shipRotationAt(Level level, double x, double y, double z) {
        WorldShip ship = worldShipAt(level, x, y, z);
        if (ship == null) {
            return null;
        }
        if (ship.rotation == null) {
            ship.rotation = readShipToWorld(ship.ship);
        }
        return ship.rotation;
    }

    private static final class WorldShip {
        final Object ship;
        final double minX, minY, minZ, maxX, maxY, maxZ;
        double[] rotation;

        WorldShip(Object ship, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this.ship = ship;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        boolean contains(double x, double y, double z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }
    }

    private static final class WorldSnap {
        final Level level;
        final long tick;
        final Map<Long, List<WorldShip>> byChunk;
        final List<WorldShip> overflow;

        WorldSnap(Level level, long tick, Map<Long, List<WorldShip>> byChunk, List<WorldShip> overflow) {
            this.level = level;
            this.tick = tick;
            this.byChunk = byChunk;
            this.overflow = overflow;
        }
    }

    private static WorldSnap worldSnap;
    private static Method allShips;
    private static Method worldAabb;
    private static Method aabbMinX, aabbMinY, aabbMinZ, aabbMaxX, aabbMaxY, aabbMaxZ;
    private static Method shipToWorld;
    private static final Method[] MATRIX = new Method[9];

    /**
     * Entity positions are world-space. getShipManagingPos only sees shipyard
     * chunks, so a deck in the world would miss. One AABB index per tick,
     * not a ship search per aircraft per call.
     */
    private static WorldShip worldShipAt(Level level, double x, double y, double z) {
        WorldSnap snap = worldShips(level);
        if (snap == null) {
            return null;
        }
        int cx = ((int) Math.floor(x)) >> 4;
        int cz = ((int) Math.floor(z)) >> 4;
        List<WorldShip> listed = snap.byChunk.get(chunkKey(cx, cz));
        if (listed != null) {
            for (int i = 0; i < listed.size(); i++) {
                WorldShip ship = listed.get(i);
                if (ship.contains(x, y, z)) {
                    return ship;
                }
            }
        }
        for (int i = 0; i < snap.overflow.size(); i++) {
            WorldShip ship = snap.overflow.get(i);
            if (ship.contains(x, y, z)) {
                return ship;
            }
        }
        return null;
    }

    private static long chunkKey(int cx, int cz) {
        return (((long) cx) << 32) ^ (cz & 0xffffffffL);
    }

    private static WorldSnap worldShips(Level level) {
        long tick = level.getGameTime();
        WorldSnap current = worldSnap;
        if (current != null && current.level == level && current.tick == tick) {
            return current;
        }
        WorldSnap built = buildWorldSnap(level, tick);
        worldSnap = built;
        return built;
    }

    private static WorldSnap buildWorldSnap(Level level, long tick) {
        Map<Long, List<WorldShip>> buckets = new HashMap<>();
        List<WorldShip> overflow = new ArrayList<>();
        try {
            if (SHIP_WORLD == null) {
                return new WorldSnap(level, tick, buckets, overflow);
            }
            Object world = SHIP_WORLD.invoke(null, level);
            if (world == null) {
                return new WorldSnap(level, tick, buckets, overflow);
            }
            if (allShips == null) {
                allShips = world.getClass().getMethod("getAllShips");
            }
            Object data = allShips.invoke(world);
            if (!(data instanceof Iterable<?> ships)) {
                return new WorldSnap(level, tick, buckets, overflow);
            }
            for (Object ship : ships) {
                if (ship == null) {
                    continue;
                }
                if (worldAabb == null) {
                    worldAabb = ship.getClass().getMethod("getWorldAABB");
                }
                Object box = worldAabb.invoke(ship);
                if (box == null) {
                    continue;
                }
                if (aabbMinX == null) {
                    aabbMinX = box.getClass().getMethod("minX");
                    aabbMinY = box.getClass().getMethod("minY");
                    aabbMinZ = box.getClass().getMethod("minZ");
                    aabbMaxX = box.getClass().getMethod("maxX");
                    aabbMaxY = box.getClass().getMethod("maxY");
                    aabbMaxZ = box.getClass().getMethod("maxZ");
                }
                double minX = ((Number) aabbMinX.invoke(box)).doubleValue() - 0.5;
                double minY = ((Number) aabbMinY.invoke(box)).doubleValue() - 0.5;
                double minZ = ((Number) aabbMinZ.invoke(box)).doubleValue() - 0.5;
                double maxX = ((Number) aabbMaxX.invoke(box)).doubleValue() + 0.5;
                double maxY = ((Number) aabbMaxY.invoke(box)).doubleValue() + 0.5;
                double maxZ = ((Number) aabbMaxZ.invoke(box)).doubleValue() + 0.5;
                WorldShip snap = new WorldShip(ship, minX, minY, minZ, maxX, maxY, maxZ);
                int minCx = ((int) Math.floor(minX)) >> 4;
                int maxCx = ((int) Math.floor(maxX)) >> 4;
                int minCz = ((int) Math.floor(minZ)) >> 4;
                int maxCz = ((int) Math.floor(maxZ)) >> 4;
                if (maxCx - minCx > 48 || maxCz - minCz > 48) {
                    overflow.add(snap);
                    continue;
                }
                for (int cx = minCx; cx <= maxCx; cx++) {
                    for (int cz = minCz; cz <= maxCz; cz++) {
                        buckets.computeIfAbsent(chunkKey(cx, cz), key -> new ArrayList<>(1)).add(snap);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return new WorldSnap(level, tick, buckets, overflow);
    }

    private static double[] readShipToWorld(Object ship) {
        try {
            if (shipToWorld == null) {
                shipToWorld = ship.getClass().getMethod("getShipToWorld");
            }
            Object matrix = shipToWorld.invoke(ship);
            if (matrix == null) {
                return null;
            }
            String[] names = {"m00", "m01", "m02", "m10", "m11", "m12", "m20", "m21", "m22"};
            double[] m = new double[9];
            for (int i = 0; i < names.length; i++) {
                if (MATRIX[i] == null) {
                    MATRIX[i] = matrix.getClass().getMethod(names[i]);
                }
                m[i] = ((Number) MATRIX[i].invoke(matrix)).doubleValue();
            }
            return m;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object shipAt(Level level, double x, double y, double z) throws Exception {
        if (SHIP_AT == null) {
            return null;
        }
        return SHIP_AT.invoke(null, level, x, y, z);
    }

    private static Object shipAt(Level level, BlockPos pad) throws Exception {
        return shipAt(level, pad.getX() + 0.5, pad.getY() + 0.5, pad.getZ() + 0.5);
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
