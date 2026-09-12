package immersive_aircraft.block.entity;

import immersive_aircraft.BlockEntityTypes;
import immersive_aircraft.Sounds;
import immersive_aircraft.block.CatapultBlock;
import immersive_aircraft.catapult.CatapultLaunch;
import immersive_aircraft.entity.VehicleEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

public class CatapultBlockEntity extends BlockEntity {
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
        BlockPos origin = vehicle.blockPosition();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 0; dy++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (vehicle.level().getBlockEntity(pos) instanceof CatapultBlockEntity be
                            && vehicle.getUUID().equals(be.dockedId)
                            && be.cooldown <= 0) {
                        if (be.fire(vehicle.level(), pos, vehicle.level().getBlockState(pos))) {
                            be.cooldown = CatapultLaunch.COOLDOWN_TICKS;
                            be.setChanged();
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private void maintainDock(Level level, BlockPos pos, BlockState state) {
        VehicleEntity docked = findDocked(level);
        if (docked != null && onPad(docked, pos)) {
            clamp(docked, pos, state);
            return;
        }
        this.dockedId = null;
        for (VehicleEntity vehicle : vehiclesOnPad(level, pos)) {
            this.dockedId = vehicle.getUUID();
            clamp(vehicle, pos, state);
            setChanged();
            return;
        }
    }

    private boolean fire(Level level, BlockPos pos, BlockState state) {
        VehicleEntity vehicle = findDocked(level);
        if (vehicle == null || !onPad(vehicle, pos)) {
            this.dockedId = null;
            return false;
        }
        Direction facing = state.getValue(CatapultBlock.FACING);
        Vec3 vel = vehicle.getDeltaMovement();
        double[] next = CatapultLaunch.launchVelocity(
                vel.x, vel.y, vel.z,
                facing.getStepX(), facing.getStepZ()
        );
        this.dockedId = null;
        vehicle.setDeltaMovement(next[0], next[1], next[2]);
        vehicle.hasImpulse = true;
        vehicle.setOnGround(false);
        level.playSound(null, pos, Sounds.WOOSH.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
        setChanged();
        return true;
    }

    private VehicleEntity findDocked(Level level) {
        if (this.dockedId == null) {
            return null;
        }
        List<VehicleEntity> found = vehiclesOnPad(level, this.worldPosition);
        for (VehicleEntity vehicle : found) {
            if (this.dockedId.equals(vehicle.getUUID())) {
                return vehicle;
            }
        }
        for (Entity entity : level.getEntities((Entity) null, searchBox(this.worldPosition), e -> this.dockedId.equals(e.getUUID()))) {
            if (entity instanceof VehicleEntity vehicle) {
                return vehicle;
            }
        }
        return null;
    }

    private List<VehicleEntity> vehiclesOnPad(Level level, BlockPos pos) {
        return level.getEntitiesOfClass(VehicleEntity.class, searchBox(pos), v -> onPad(v, pos));
    }

    private static AABB searchBox(BlockPos pos) {
        double[] detect = CatapultLaunch.detectionBox();
        return new AABB(
                pos.getX() + detect[0],
                pos.getY() + detect[1],
                pos.getZ() + detect[2],
                pos.getX() + detect[3],
                pos.getY() + detect[4],
                pos.getZ() + detect[5]
        );
    }

    private static boolean onPad(VehicleEntity vehicle, BlockPos pos) {
        AABB box = vehicle.getBoundingBox();
        return CatapultLaunch.vehicleOnPad(
                box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
                pos.getX(), pos.getY(), pos.getZ()
        );
    }

    private static void clamp(VehicleEntity vehicle, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(CatapultBlock.FACING);
        vehicle.setPos(pos.getX() + 0.5, pos.getY() + CatapultLaunch.HEIGHT, pos.getZ() + 0.5);
        vehicle.setYRot(facing.toYRot());
        vehicle.setDeltaMovement(Vec3.ZERO);
        vehicle.setOnGround(true);
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
