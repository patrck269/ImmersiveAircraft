package immersive_aircraft.block.entity;

import immersive_aircraft.BlockEntityTypes;
import immersive_aircraft.Sounds;
import immersive_aircraft.catapult.CatapultLaunch;
import immersive_aircraft.entity.VehicleEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class CatapultBlockEntity extends BlockEntity {
    private boolean wasPowered;
    private int cooldown;

    public CatapultBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityTypes.CATAPULT.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CatapultBlockEntity be) {
        boolean powered = level.hasNeighborSignal(pos);
        if (CatapultLaunch.shouldFire(powered, be.wasPowered, be.cooldown)) {
            if (be.launchVehicles(level, pos)) {
                be.cooldown = CatapultLaunch.COOLDOWN_TICKS;
            }
        }
        be.wasPowered = powered;
        if (be.cooldown > 0) {
            be.cooldown--;
        }
    }

    private boolean launchVehicles(Level level, BlockPos pos) {
        AABB pad = new AABB(
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                pos.getX() + CatapultLaunch.WIDTH,
                pos.getY() + CatapultLaunch.HEIGHT,
                pos.getZ() + CatapultLaunch.LENGTH
        );
        List<VehicleEntity> vehicles = level.getEntitiesOfClass(VehicleEntity.class, pad);
        boolean fired = false;
        for (VehicleEntity vehicle : vehicles) {
            AABB box = vehicle.getBoundingBox();
            if (!CatapultLaunch.intersectsPad(
                    box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
                    pad.minX, pad.minY, pad.minZ, pad.maxX, pad.maxY, pad.maxZ
            )) {
                continue;
            }
            Vec3 vel = vehicle.getDeltaMovement();
            Vec3 look = vehicle.getLookAngle();
            double[] next = CatapultLaunch.launchVelocity(vel.x, vel.y, vel.z, look.x, look.y, look.z);
            vehicle.setDeltaMovement(next[0], next[1], next[2]);
            vehicle.hasImpulse = true;
            vehicle.setOnGround(false);
            level.playSound(null, pos, Sounds.WOOSH.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
            fired = true;
        }
        return fired;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("WasPowered", this.wasPowered);
        tag.putInt("Cooldown", this.cooldown);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.wasPowered = tag.getBoolean("WasPowered");
        this.cooldown = tag.getInt("Cooldown");
    }
}
