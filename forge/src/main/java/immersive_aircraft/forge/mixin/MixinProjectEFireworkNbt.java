package immersive_aircraft.forge.mixin;

import immersive_aircraft.emc.FireworkEmcIdentity;
import java.util.ArrayList;
import java.util.List;
import moze_intel.projecte.api.ItemInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "moze_intel.projecte.emc.nbt.NBTManager", remap = false)
public abstract class MixinProjectEFireworkNbt {

    @Inject(method = "getPersistentInfo", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ia$keepFireworkNbt(ItemInfo info, CallbackInfoReturnable<ItemInfo> cir) {
        if (isFirework(info) && info.hasNBT()) {
            cir.setReturnValue(info);
        }
    }

    @Inject(method = "getEmcValue", at = @At("RETURN"), cancellable = true, remap = false)
    private static void ia$fireworkEmc(ItemInfo info, CallbackInfoReturnable<Long> cir) {
        if (!isFirework(info)) {
            return;
        }
        long bonus = FireworkEmcIdentity.emcBonus(flightOf(info), explosionsOf(info));
        if (bonus > 0) {
            cir.setReturnValue(Math.addExact(cir.getReturnValue(), bonus));
        }
    }

    private static boolean isFirework(ItemInfo info) {
        return FireworkEmcIdentity.isFireworkItem(itemId(info));
    }

    private static String itemId(ItemInfo info) {
        CompoundTag nbt = info.getNBT();
        if (nbt != null && nbt.contains("Fireworks")) {
            return FireworkEmcIdentity.FIREWORK_ROCKET;
        }
        if (nbt != null && nbt.contains("Explosion")) {
            return FireworkEmcIdentity.FIREWORK_STAR;
        }
        return BuiltInRegistries.ITEM.getKey(info.getItem()).toString();
    }

    private static int flightOf(ItemInfo info) {
        CompoundTag nbt = info.getNBT();
        if (nbt == null || !nbt.contains("Fireworks")) {
            return 1;
        }
        int flight = nbt.getCompound("Fireworks").getByte("Flight");
        return flight <= 0 ? 1 : flight;
    }

    private static String explosionsOf(ItemInfo info) {
        CompoundTag nbt = info.getNBT();
        if (nbt == null) {
            return "";
        }
        if (nbt.contains("Explosion") && !nbt.contains("Fireworks")) {
            return FireworkEmcIdentity.canonicalExplosions(List.of(nbt.getCompound("Explosion").toString()));
        }
        if (!nbt.contains("Fireworks")) {
            return "";
        }
        ListTag list = nbt.getCompound("Fireworks").getList("Explosions", Tag.TAG_COMPOUND);
        List<String> entries = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            entries.add(list.getCompound(i).toString());
        }
        return FireworkEmcIdentity.canonicalExplosions(entries);
    }
}
