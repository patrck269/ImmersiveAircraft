package immersive_aircraft;

import immersive_aircraft.block.CatapultBlock;
import immersive_aircraft.cobalt.registration.Registration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.function.Supplier;

public interface Blocks {
    Supplier<Block> CATAPULT = Registration.register(
            BuiltInRegistries.BLOCK,
            Main.locate("catapult"),
            () -> new CatapultBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f)
                    .sound(SoundType.METAL)
                    .noOcclusion())
    );

    static void bootstrap() {
    }
}
