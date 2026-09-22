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
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
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

    public static void tick(Level level, BlockPos pos, BlockState state, CatapultBlockEntity be) {
        if (level.isClientSide) {
            if (be.cooldown > 0) {
                be.cooldown--;
            }
            be.clientClamp(level, pos, state);
            return;
        }
        be.maintainDock(level, pos, state);
        boolean powered = level.hasNeighborSignal(pos);
        if (CatapultLaunch.shouldFire(powered, be.wasPowered, be.cooldown)) {
            if (be.fire(level, pos, state)) {
                be.cooldown = CatapultLaunch.COOLDOWN_TICKS;
                be.sync();
            }
        }
        be.wasPowered = powered;
        if (be.cooldown > 0) {
            be.cooldown--;
        }
    }

    public static boolean tryLaunchFromKey(VehicleEntity vehicle) {
        CatapultBlockEntity be = DOCKED.get(vehicle.getUUID());
        if (be == null || be.isRemoved() || be.cooldown > 0 || be.getLevel() == null) {
            return false;
        }
        if (be.fire(be.getLevel(), be.getBlockPos(), be.getBlockState())) {
            be.cooldown = CatapultLaunch.COOLDOWN_TICKS;
            be.sync();
            return true;
        }
        return false;
    }

    private void clientClamp(Level level, BlockPos pos, BlockState state) {
        for (VehicleEntity vehicle : vehiclesOnPad(level, pos)) {
            if (!mayClamp(vehicle)) {
                continue;
            }
            this.dockedId = vehicle.getUUID();
            DOCKED.put(vehicle.getUUID(), this);
            vehicle.lockToPad();
            clamp(vehicle, level, pos, state);
            return;
        }
        unlockDocked(level, pos);
    }

    private void maintainDock(Level level, BlockPos pos, BlockState state) {
        VehicleEntity docked = findDocked(level, pos);
        if (docked != null && onPad(docked, level, pos)) {
            if (mayClamp(docked)) {
                docked.lockToPad();
                clamp(docked, level, pos, state);
                DOCKED.put(docked.getUUID(), this);
            } else {
                docked.unlockFromPad();
            }
            return;
        }
        if (docked != null) {
            docked.unlockFromPad();
        }
        clearDock();
        for (VehicleEntity vehicle : vehiclesOnPad(level, pos)) {
            if (!mayClamp(vehicle)) {
                continue;
            }
            this.dockedId = vehicle.getUUID();
            DOCKED.put(vehicle.getUUID(), this);
            vehicle.lockToPad();
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
        Vec3 dir = CatapultVs.worldFacing(level, pos, facing);
        Vec3 vel = vehicle.getDeltaMovement();
        double[] next = CatapultLaunch.launchVelocity(vel.x, vel.y, vel.z, dir.x, dir.z);
        vehicle.unlockFromPad();
        clearDock();
        vehicle.addTag(CatapultLaunch.LAUNCH_TAG);
        vehicle.setDeltaMovement(next[0], next[1], next[2]);
        vehicle.hasImpulse = true;
        vehicle.setOnGround(false);
        Vec3 sound = CatapultVs.worldDock(level, pos);
        if (level.isClientSide) {
            level.playLocalSound(sound.x, sound.y, sound.z, Sounds.WOOSH.get(), SoundSource.BLOCKS, 1.0f, 1.0f, false);
        } else {
            level.playSound(null, sound.x, sound.y, sound.z, Sounds.WOOSH.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
        }
        setChanged();
        return true;
    }

    private boolean mayClamp(VehicleEntity vehicle) {
        return CatapultLaunch.shouldClampDock(
                this.cooldown,
                vehicle.getTags().contains(CatapultLaunch.LAUNCH_TAG),
                vehicle.getDeltaMovement().horizontalDistance()
        );
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private void clearDock() {
        if (this.dockedId != null) {
            DOCKED.remove(this.dockedId, this);
        }
        this.dockedId = null;
    }

    private void unlockDocked(Level level, BlockPos pos) {
        VehicleEntity docked = findDocked(level, pos);
        if (docked != null) {
            docked.unlockFromPad();
        }
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
        AABB search = CatapultVs.worldSearch(level, pos).inflate(8.0);
        List<VehicleEntity> extras = level.getEntitiesOfClass(VehicleEntity.class, search, v -> this.dockedId.equals(v.getUUID()));
        return extras.isEmpty() ? null : extras.get(0);
    }

    private List<VehicleEntity> vehiclesOnPad(Level level, BlockPos pos) {
        return level.getEntitiesOfClass(VehicleEntity.class, CatapultVs.worldSearch(level, pos), v -> onPad(v, level, pos));
    }

    private static boolean onPad(VehicleEntity vehicle, Level level, BlockPos pos) {
        AABB box = vehicle.getBoundingBox();
        AABB pad = CatapultVs.worldSearch(level, pos);
        return CatapultLaunch.intersectsPad(
                box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
                pad.minX, pad.minY, pad.minZ, pad.maxX, pad.maxY, pad.maxZ
        );
    }

    private static void clamp(VehicleEntity vehicle, Level level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(CatapultBlock.FACING);
        Vec3 dock = CatapultVs.worldDock(level, pos);
        Vec3 dir = CatapultVs.worldFacing(level, pos, facing);
        vehicle.lockToPad();
        vehicle.cancelInterpolation();
        vehicle.setPos(dock.x, dock.y, dock.z);
        vehicle.setYRot((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
        vehicle.setDeltaMovement(Vec3.ZERO);
        vehicle.setOnGround(true);
        vehicle.removeTag(CatapultLaunch.LAUNCH_TAG);
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

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
