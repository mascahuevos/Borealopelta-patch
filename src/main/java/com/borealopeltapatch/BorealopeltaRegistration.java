package com.borealopeltapatch;

import com.borealopeltapatch.entity.EntityBorealopelta;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import ru.xishnikus.thedawnera.common.entity.entity.base.BaseRideableAnimal;
import ru.xishnikus.thedawnera.common.entity.properties.MobProperties;
import ru.xishnikus.thedawnera.common.entity.properties.TDEMobProperties;
import ru.xishnikus.thedawnera.common.entity.properties.ai.TDECustomGoalFactories;

import java.util.function.Supplier;

/**
 * Todo el "pegamento" que le faltaba al addon para que el Borealopelta exista
 * de verdad en el juego, más allá de compilar. Sin esto: EntityType nunca
 * registrado, MobProperties nunca asociado (this.properties queda null y
 * revienta la entidad al spawnear), sin atributos, sin spawn natural, y sin
 * el goal de cavar comida registrado.
 *
 * POR QUÉ SE HACE ASÍ (no es el patrón obvio de "cada mod registra lo suyo"):
 *
 * "dawnera:mob_properties" es un Forge Registry CUSTOM que crea The Dawn Era.
 * BaseAnimal busca las properties de cada mob con TDEMobProperties.getResource(id),
 * que internamente recorre TDEMobProperties.MOB_PROPERTIES.getEntries() -- o sea,
 * la lista INTERNA de esa instancia puntual de DeferredRegister, no una consulta
 * al registry real de Forge. Si este addon creara su PROPIO
 * DeferredRegister<MobProperties> separado, la entrada de Borealopelta jamás
 * aparecería para getResource() aunque ambos apunten al mismo registry por
 * debajo -- BaseAnimal se quedaría con this.properties = null y crashearía
 * al spawnear o al cargar el mundo.
 *
 * La solución (confirmada decompilando TDEMobProperties.java del jar original):
 * el campo MOB_PROPERTIES de TDE es público, y TDE llama
 * TDEMobProperties.MOB_PROPERTIES.register(suPropioModEventBus) en su propio
 * constructor -- ANTES de que se dispare el RegisterEvent real (los
 * constructores de TODOS los mods corren antes de que se disparen los eventos
 * de registro, sin importar el orden de carga entre mods). Como nuestro addon
 * carga después (ordering=AFTER dawnera) pero su propio constructor SIGUE
 * corriendo antes de esa fase de eventos, podemos llamar directamente
 * TDEMobProperties.MOB_PROPERTIES.register(...) sobre la instancia de TDE y
 * queda enganchado en el mismo ciclo de vida sin problema.
 *
 * (EntityType sí puede ir por nuestro propio DeferredRegister normal, porque
 * ForgeRegistries.ENTITY_TYPES es un registry estándar de Forge -- no tiene
 * este problema de lookup local por instancia.)
 */
public class BorealopeltaRegistration {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "dawnera");

    public static final RegistryObject<EntityType<EntityBorealopelta>> BOREALOPELTA =
            ENTITY_TYPES.register("borealopelta", () -> EntityType.Builder
                    .of(EntityBorealopelta::new, MobCategory.CREATURE)
                    .sized(1.25f, 1.7f)
                    .clientTrackingRange(10)
                    .build(new ResourceLocation("dawnera", "borealopelta").toString()));

    // Se registra directo sobre la instancia de TDE -- ver el comentario de
    // arriba. Como es un campo static final, se ejecuta al cargar esta clase,
    // que forzamos desde el constructor del mod ANTES de que dispare el
    // RegisterEvent real (ver BorealopeltaPatch.java).
    public static final RegistryObject<MobProperties> BOREALOPELTA_PROPERTIES =
            TDEMobProperties.MOB_PROPERTIES.register("borealopelta",
                    MobProperties.build(BaseRideableAnimal.Properties.class,
                            new ResourceLocation("dawnera", "mobs/borealopelta.json")));

    // El goal de cavar comida NO pasa por el sistema de DeferredRegister/eventos
    // de Forge -- es un método estático simple que agrega a un mapa interno al
    // vuelo, así que se puede llamar en cualquier momento antes de que se
    // cargue el JSON de configuración del mob (que referencia
    // "dawnera:borealopelta.dig_for_food").
    public static final Supplier<EntityBorealopelta.Goals.DigForFood.Builder> DIG_FOR_FOOD =
            TDECustomGoalFactories.register(new ResourceLocation("dawnera", "borealopelta.dig_for_food"),
                    EntityBorealopelta.Goals.DigForFood.Builder::new);

    public static void init(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(BorealopeltaRegistration::onAttributeCreate);
        modEventBus.addListener(BorealopeltaRegistration::onSpawnPlacementRegister);
    }

    private static void onAttributeCreate(EntityAttributeCreationEvent event) {
        event.put(BOREALOPELTA.get(), BOREALOPELTA_PROPERTIES.get().buildAttributes());
    }

    // Mismo patrón exacto que usa TDEEntities.onRegisterSpawnPlacements() en el
    // mod original: la llamada real va por el SpawnPlacements estático de
    // vanilla, el evento de Forge solo se usa como "gancho" de timing.
    private static void onSpawnPlacementRegister(SpawnPlacementRegisterEvent event) {
        SpawnPlacements.register(BOREALOPELTA.get(), SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Animal::checkAnimalSpawnRules);
    }
}
