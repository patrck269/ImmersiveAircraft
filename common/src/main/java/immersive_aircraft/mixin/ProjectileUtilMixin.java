package immersive_aircraft.mixin;

import immersive_aircraft.entity.VehicleEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.function.Predicate;

@Mixin(ProjectileUtil.class)
public class ProjectileUtilMixin {
    @Inject(method = "getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;", at = @At("RETURN"), cancellable = true)
    private static void ia$getEntityHitResult(Entity shooter, Vec3 startVec, Vec3 endVec, AABB boundingBox, Predicate<Entity> filter, double distance, CallbackInfoReturnable<EntityHitResult> cir) {
        ia$vehicleTrace(cir.getReturnValue(), shooter.level(), shooter, startVec, endVec, boundingBox,filter, 0.0f, distance).ifPresent(cir::setReturnValue);
    }

    @Inject(method = "getEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;F)Lnet/minecraft/world/phys/EntityHitResult;", at = @At("RETURN"), cancellable = true)
    private static void ia$getEntityHitResult(Level level, Entity projectile, Vec3 startVec, Vec3 endVec, AABB boundingBox, Predicate<Entity> filter, float inflationAmount, CallbackInfoReturnable<EntityHitResult> cir) {
        ia$vehicleTrace(cir.getReturnValue(), level, projectile, startVec, endVec, boundingBox, filter, inflationAmount, Double.MAX_VALUE).ifPresent(cir::setReturnValue);
    }

    /**
     * Extra vehicle shapes only. A non-finite or oversized sweep makes
     * getEntities ask Valkyrien Skies for every intersecting ship. A CIWS
     * round stuck the server thread in that list. Vanilla hits still apply.
     */
    @Unique
    private static boolean ia$sweepFitsVehicleScan(@Nullable AABB inflated) {
        if (inflated == null) {
            return false;
        }
        final double dx = inflated.maxX - inflated.minX;
        final double dy = inflated.maxY - inflated.minY;
        final double dz = inflated.maxZ - inflated.minZ;
        if (!Double.isFinite(dx) || !Double.isFinite(dy) || !Double.isFinite(dz)) {
            return false;
        }
        return dx <= 64.0D && dy <= 64.0D && dz <= 64.0D;
    }

    @Unique
    private static Optional<EntityHitResult> ia$vehicleTrace(@Nullable EntityHitResult previous, Level level, @Nullable Entity source, Vec3 startVec, Vec3 endVec, AABB boundingBox, Predicate<Entity> filter, float inflationAmount, double distance) {
        final AABB scan = boundingBox == null ? null : boundingBox.inflate(16.0D);
        if (!ia$sweepFitsVehicleScan(scan)) {
            return Optional.empty();
        }

        double bestDistance = previous == null ? distance : previous.getLocation().distanceToSqr(startVec);
        Entity entity = null;
        Vec3 collision = null;

        for (Entity e : level.getEntities(source, scan, VehicleEntity.class::isInstance)) {
            if (e instanceof VehicleEntity vehicle && filter.test(vehicle)) {
                for (AABB aabb : vehicle.getAdditionalShapes()) {
                    Optional<Vec3> optionalCollision = aabb.inflate(inflationAmount).clip(startVec, endVec);
                    if (optionalCollision.isPresent()) {
                        Vec3 newCollision = optionalCollision.get();
                        double dist = startVec.distanceToSqr(newCollision);
                        if (dist < bestDistance) {
                            entity = vehicle;
                            collision = newCollision;
                            bestDistance = dist;
                        }
                    }
                }
            }
        }

        return entity == null ? Optional.empty() : Optional.of(new EntityHitResult(entity, collision));
    }
}
