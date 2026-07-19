package com.borealopeltapatch.client;

import com.borealopeltapatch.entity.EntityBorealopelta;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import ru.astemir.astemirlib.client.bedrock.animation.Animation;
import ru.astemir.astemirlib.client.bedrock.animation.data.AnimationBlending;
import ru.astemir.astemirlib.client.bedrock.animation.data.Animator;
import ru.astemir.astemirlib.client.bedrock.renderer.EntityRenderData;
import ru.astemir.astemirlib.common.action.ActionState;
import ru.astemir.astemirlib.common.math.EasingType;
import ru.astemir.astemirlib.common.math.InterpolationType;
import ru.xishnikus.thedawnera.client.render.entity.TDEEntityModel;
import ru.xishnikus.thedawnera.client.render.entity.TDELivingRenderer;
import ru.xishnikus.thedawnera.common.entity.data.GenderType;

/**
 * Copiado 1:1 del patrón de SturgeonModel/TriceratopsModel del mod original
 * (decompilado para confirmar la API exacta). Usa el sistema de renderizado
 * "Bedrock genérico" de AstemirLib -- consume directo el geo.json y
 * animation.json que ya armamos, no hace falta modelar nada a mano en Java.
 */
public class BorealopeltaModel extends TDEEntityModel<EntityBorealopelta> {

    private static final String ID = "borealopelta";
    private static final ResourceLocation TEXTURE_MALE = textureLocation(ID, "male.png");
    private static final ResourceLocation TEXTURE_FEMALE = textureLocation(ID, "female.png");
    private static final ResourceLocation TEXTURE_BABY = textureLocation(ID, "baby.png");
    private static final ResourceLocation TEXTURE_SADDLED = textureLocation(ID, "saddled_0.png");

    public BorealopeltaModel(ResourceLocation model, ResourceLocation animations) {
        super(model, animations);
    }

    @Override
    public void animate(Animator animator, EntityBorealopelta entity, EntityRenderData renderData) {
        Animation animation;

        // Prioridad: acciones puntuales (attack/scream/eat/down-dig) por encima
        // del movimiento base (idle/walk/run/swim), igual que hace Sturgeon.
        if (entity.actionController.is(new ActionState[]{entity.actionAttack})) {
            animation = animator.getAnimation("animation.model.attack");
        } else if (entity.actionController.is(new ActionState[]{entity.actionScream})) {
            animation = animator.getAnimation("animation.model.scream");
        } else if (entity.actionController.is(new ActionState[]{entity.actionEat})) {
            animation = animator.getAnimation("animation.model.eat");
        } else if (entity.actionController.is(new ActionState[]{entity.actionDown})) {
            animation = animator.getAnimation("animation.model.down");
        } else if (entity.isInWater()) {
            animation = animator.getAnimation("animation.model.swim");
        } else if (entity.stateController.is(new ActionState[]{entity.stateSleep})) {
            animation = animator.getAnimation("animation.model.sleep");
        } else if (entity.stateController.is(new ActionState[]{entity.stateRest})) {
            // Sentado/descansando y quieto (dawnera:is_not_sleeping_or_resting
            // deja de cumplirse -> los goals de movimiento no lo mandan a
            // caminar) -> pose de reposo en vez del idle parado normal.
            animation = animator.getAnimation("animation.model.rest");
        } else {
            // Usamos una combinación de velocidad delta + posición anterior para
            // detectar movimiento real incluso cuando el pathfinding aplica
            // aceleración gradual. Esto fija el bug donde no se reproducía
            // walk/run al seguir al jugador o al moverse por IA.
            double moveLen = entity.getDeltaMovement().horizontalDistance();
            double dx = entity.getX() - entity.xo;
            double dz = entity.getZ() - entity.zo;
            double actualMove = Math.sqrt(dx * dx + dz * dz);
            
            if (entity.isSprinting()) {
                animation = animator.getAnimation("animation.model.run");
            } else if (!entity.isCrouching() && (moveLen > 0.001 || actualMove > 0.001)) {
                animation = animator.getAnimation("animation.model.walk");
            } else {
                animation = animator.getAnimation("animation.model.idle");
            }
        }

        animator.setAnimation(animation,
                AnimationBlending.create((InterpolationType) InterpolationType.CATMULLROM, (EasingType) EasingType.NONE, (double) 0.15),
                1.0, 1);

        if (entity.actionController.is(entity.actionEat)) {
            this.renderFoodParticles(entity, this, renderData, "head");
        }
    }

    @Override
    public ResourceLocation getTexture(EntityBorealopelta entity) {
        if (entity.isBaby()) {
            return TEXTURE_BABY;
        }
        if (entity.isSaddled()) {
            return TEXTURE_SADDLED;
        }
        if (entity.getGender() == GenderType.FEMALE) {
            return TEXTURE_FEMALE;
        }
        return TEXTURE_MALE;
    }

    public static TDELivingRenderer<EntityBorealopelta> createRenderer(EntityRendererProvider.Context context) {
        BorealopeltaModel model = new BorealopeltaModel(
                modelLocation(ID, "geo.json"),
                animationsLocation(ID, "animation.json"));
        BorealopeltaModel babyModel = new BorealopeltaModel(
                modelLocation(ID, "baby_geo.json"),
                animationsLocation(ID, "animation_baby.json"));
        return TDELivingRenderer.createRendererGendered(context, mob -> model, mob -> babyModel, 1.25f, 1.0f);
    }
}
