package com.borealopeltapatch;

import com.borealopeltapatch.sound.SoundRegistration;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(BorealopeltaPatch.MOD_ID)
public class BorealopeltaPatch {

    public static final String MOD_ID = "borealopeltapatch";

    public BorealopeltaPatch() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        SoundRegistration.register(modEventBus);
        // Fuerza la carga de la clase (y por lo tanto sus static final:
        // EntityType, MobProperties enganchado a TDE, y el goal de cavar)
        // y engancha atributos + spawn placement en el mismo event bus.
        BorealopeltaRegistration.init(modEventBus);
        // El renderer (BorealopeltaClientSetup) se autoregistra solo vía
        // @Mod.EventBusSubscriber(value = Dist.CLIENT) -- no hace falta
        // tocarlo acá, y así nunca se carga en un servidor dedicado.
    }
}