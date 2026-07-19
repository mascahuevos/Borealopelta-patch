package com.borealopeltapatch.client;

import com.borealopeltapatch.BorealopeltaPatch;
import com.borealopeltapatch.BorealopeltaRegistration;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

// value = Dist.CLIENT es lo que evita que FML cargue esta clase en un
// servidor dedicado -- sin esto, aunque nunca "se llame" al método, el
// classloading de la clase (por las importaciones de net.minecraft.client.*)
// igual rompería un servidor dedicado con NoClassDefFoundError.
@Mod.EventBusSubscriber(modid = BorealopeltaPatch.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class BorealopeltaClientSetup {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() ->
                EntityRenderers.register(BorealopeltaRegistration.BOREALOPELTA.get(), BorealopeltaModel::createRenderer)
        );
    }
}
