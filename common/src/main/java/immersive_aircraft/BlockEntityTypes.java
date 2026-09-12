package immersive_aircraft;

import immersive_aircraft.block.entity.CatapultBlockEntity;
import immersive_aircraft.cobalt.registration.Registration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.function.Supplier;

public interface BlockEntityTypes {
    Supplier<BlockEntityType<CatapultBlockEntity>> CATAPULT = Registration.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Main.locate("catapult"),
            () -> BlockEntityType.Builder.of(CatapultBlockEntity::new, Blocks.CATAPULT.get()).build(null)
    );

    static void bootstrap() {
    }
}
