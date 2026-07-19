package com.borealopeltapatch.sound;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class SoundRegistration {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "dawnera");

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name,
                () -> SoundEvent.createVariableRangeEvent(
                        new ResourceLocation("dawnera", name)));
    }

    public static final RegistryObject<SoundEvent> ATTACK1 = register("attack1");
    public static final RegistryObject<SoundEvent> ATTACK2 = register("attack2");
    public static final RegistryObject<SoundEvent> ATTACK3 = register("attack3");

    public static final RegistryObject<SoundEvent> DOWN = register("down");

    public static final RegistryObject<SoundEvent> EAT1 = register("eat1");
    public static final RegistryObject<SoundEvent> EAT2 = register("eat2");

    public static final RegistryObject<SoundEvent> IDLE1 = register("idle1");
    public static final RegistryObject<SoundEvent> IDLE2 = register("idle2");
    public static final RegistryObject<SoundEvent> IDLE3 = register("idle3");
    public static final RegistryObject<SoundEvent> IDLE4 = register("idle4");

    public static final RegistryObject<SoundEvent> ROAR = register("roar");

    public static final RegistryObject<SoundEvent> SPIKES1 = register("spikes1");
    public static final RegistryObject<SoundEvent> SPIKES2 = register("spikes2");
    public static final RegistryObject<SoundEvent> SPIKES3 = register("spikes3");

    // Alias para la entidad
    public static final RegistryObject<SoundEvent> BOREALOPELTA_ATTACK = ATTACK1;
    public static final RegistryObject<SoundEvent> BOREALOPELTA_IDLE = IDLE1;
    public static final RegistryObject<SoundEvent> BOREALOPELTA_HURT = IDLE2;
    public static final RegistryObject<SoundEvent> BOREALOPELTA_DEATH = DOWN;
    public static final RegistryObject<SoundEvent> BOREALOPELTA_ROAR = ROAR;
    public static final RegistryObject<SoundEvent> EAT_GRASS = EAT1;

    public static void register(IEventBus bus) {
        SOUND_EVENTS.register(bus);
    }
}