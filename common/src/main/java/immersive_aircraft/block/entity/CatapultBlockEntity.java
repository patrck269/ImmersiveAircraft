package immersive_aircraft.block.entity;

import immersive_aircraft.BlockEntityTypes;
import immersive_aircraft.Sounds;
import immersive_aircraft.block.CatapultBlock;
import immersive_aircraft.catapult.CatapultLaunch;
import immersive_aircraft.catapult.CatapultVs;
import immersive_aircraft.entity.VehicleEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CatapultBlockEntity extends BlockEntity {
    private static final Map<UUID, CatapultBlockEntity> DOCKED = new ConcurrentHashMap<>();

    private boolean wasPowered;
    private int cooldown;
    private UUID dockedId;

    public CatapultBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityTypes.CATAPULT.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CatapultBlockEntity be) {
        be.maintainDock(level, pos, state);
        boolean powered = level.hasNeighborSignal(pos);
        if (CatapultLaunch.shouldFire(powered, be.wasPowered, be.cooldown)) {
            if (be.fire(level, pos, state)) {
                be.cooldown = CatapultLaunch.COOLDOWN_TICKS;
            }
        }
        be.wasPowered = powered;
        if (be.cooldown > 0) {
            be.cooldown--;
        }
    }

    public static boolean tryLaunchFromKey(VehicleEntity vehicle) {
        if (vehicle.level().isClientSide) {
            return false;
        }
        CatapultBlockEntity be = DOCKED.get(vehicle.getUUID());
        if (be == null || be.isRemoved() || be.cooldown > 0 || be.getLevel() == null) {
            return false;
        }
        if (be.fire(be.getLevel(), be.getBlockPos(), be.getBlockState())) {
            be.cooldown = CatapultLaunch.COOLDOWN_TICKS;
            be.setChanged();
            return true;
        }
        return false;
    }

    private void maintainDock(Level level, BlockPos pos, BlockState state) {
        VehicleEntity docked = findDocked(level, pos);
        if (docked != null && onPad(docked, level, pos)) {
            clamp(docked, level, pos, state);
            DOCKED.put(docked.getUUID(), this);
            return;
        }
        clearDock();
        for (VehicleEntity vehicle : vehiclesOnPad(level, pos)) {
            this.dockedId = vehicle.getUUID();
            DOCKED.put(vehicle.getUUID(), this);
            clamp(vehicle, level, pos, state);
            setChanged();
            return;
        }
    }

    private boolean fire(Level level, BlockPos pos, BlockState state) {
        VehicleEntity vehicle = findDocked(level, pos);
        if (vehicle == null || !onPad(vehicle, level, pos)) {
            clearDock();
            return false;
        }
        Direction facing = state.getValue(CatapultBlock.FACING);
        double[] m = CatapultVs.shipToWorldOrIdentity(level, pos);
        double[] dir = CatapultLaunch.transformDirection(facing.getStepX(), 0.0, facing.getStepZ(), m);
        Vec3 vel = vehicle.getDeltaMovement();
        double[] next = CatapultLaunch.launchVelocity(vel.x, vel.y, vel.z, dir[0], dir[2]);
        clearDock();
        vehicle.setDeltaMovement(next[0], next[1], next[2]);
        vehicle.hasImpulse = true;
        vehicle.setOnGround(false);
        double[] world = CatapultLaunch.transformPoint(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, m);
        level.playSound(null, world[0], world[1], world[2], Sounds.WOOSH.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
        setChanged();
        return true;
    }

    private void clearDock() {
        if (this.dockedId != null) {
            DOCKED.remove(this.dockedId, this);
        }
        this.dockedId = null;
    }

    private VehicleEntity findDocked(Level level, BlockPos pos) {
        if (this.dockedId == null) {
            return null;
        }
        for (VehicleEntity vehicle : vehiclesOnPad(level, pos)) {
            if (this.dockedId.equals(vehicle.getUUID())) {
                return vehicle;
            }
        }
        AABB search = searchBox(level, pos).inflate(8.0);
        List<VehicleEntity> extras = level.getEntitiesOfClass(VehicleEntity.class, search, v -> this.dockedId.equals(v.getUUID()));
        return extras.isEmpty() ? null : extras.get(0);
    }

    private List<VehicleEntity> vehiclesOnPad(Level level, BlockPos pos) {
        return level.getEntitiesOfClass(VehicleEntity.class, searchBox(level, pos), v -> onPad(v, level, pos));
    }

    private static AABB searchBox(Level level, BlockPos pos) {
        double[] m = CatapultVs.shipToWorldOrIdentity(level, pos);
        double[] a = CatapultLaunch.worldDetectionAabb(pos.getX(), pos.getY(), pos.getZ(), m);
        return new AABB(a[0], a[1], a[2], a[3], a[4], a[5]);
    }

    private static boolean onPad(VehicleEntity vehicle, Level level, BlockPos pos) {
        AABB box = vehicle.getBoundingBox();
        double[] m = CatapultVs.shipToWorldOrIdentity(level, pos);
        double[] a = CatapultLaunch.worldDetectionAabb(pos.getX(), pos.getY(), pos.getZ(), m);
        return CatapultLaunch.intersectsPad(
                box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
                a[0], a[1], a[2], a[3], a[4], a[5]
        );
    }

    private static void clamp(VehicleEntity vehicle, Level level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(CatapultBlock.FACING);
        double[] m = CatapultVs.shipToWorldOrIdentity(level, pos);
        double[] dock = CatapultLaunch.transformPoint(
                pos.getX() + 0.5,
                pos.getY() + CatapultLaunch.HEIGHT,
                pos.getZ() + 0.5,
                m
        );
        double[] dir = CatapultLaunch.transformDirection(facing.getStepX(), 0.0, facing.getStepZ(), m);
        vehicle.setPos(dock[0], dock[1], dock[2]);
        vehicle.setYRot(yawFromDir(dir[0], dir[2]));
        vehicle.setDeltaMovement(Vec3.ZERO);
        vehicle.setOnGround(true);
    }

    private static float yawFromDir(double x, double z) {
        return (float) Math.toDegrees(Math.atan2(-x, z));
    }

    @Override
    public void setRemoved() {
        clearDock();
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("WasPowered", this.wasPowered);
        tag.putInt("Cooldown", this.cooldown);
        if (this.dockedId != null) {
            tag.putUUID("Docked", this.dockedId);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.wasPowered = tag.getBoolean("WasPowered");
        this.cooldown = tag.getInt("Cooldown");
        this.dockedId = tag.hasUUID("Docked") ? tag.getUUID("Docked") : null;
    }
}
