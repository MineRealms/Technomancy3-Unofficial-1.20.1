package theflogat.technomancy.common.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import theflogat.technomancy.Technomancy;
import theflogat.technomancy.common.tiles.essentia.QuantumJarBlockEntity;
import theflogat.technomancy.common.tiles.machines.EnergyCondenserBlockEntity;

/** Block entity types. */
public final class TechnomBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Technomancy.MOD_ID);

    public static final RegistryObject<BlockEntityType<QuantumJarBlockEntity>> QUANTUM_JAR =
            TYPES.register("quantum_jar", () -> BlockEntityType.Builder
                    .of(QuantumJarBlockEntity::new, TechnomBlocks.QUANTUM_JAR.get())
                    .build(null));

    public static final RegistryObject<BlockEntityType<EnergyCondenserBlockEntity>> ENERGY_CONDENSER =
            TYPES.register("energy_condenser", () -> BlockEntityType.Builder
                    .of(EnergyCondenserBlockEntity::new, TechnomBlocks.ENERGY_CONDENSER.get())
                    .build(null));

    private TechnomBlockEntities() {
    }
}
