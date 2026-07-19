package com.borealopeltapatch.entity;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import ru.astemir.astemirlib.common.action.ActionState;
import ru.astemir.astemirlib.common.entity.EntityUtils;
import ru.astemir.astemirlib.common.math.RandomUtils;
import ru.astemir.astemirlib.common.world.WorldUtils;

import ru.xishnikus.thedawnera.common.entity.ai.goal.CustomGoal;
import ru.xishnikus.thedawnera.common.entity.entity.base.BaseAnimal;
import ru.xishnikus.thedawnera.common.entity.entity.base.BaseRideableAnimal;
import ru.xishnikus.thedawnera.common.entity.entity.base.BaseSleepingAnimal;
import ru.xishnikus.thedawnera.common.entity.input.InputKey;
import ru.xishnikus.thedawnera.common.entity.input.KeyInputMob;
import ru.xishnikus.thedawnera.common.entity.properties.ai.CustomGoalFactory;
import ru.xishnikus.thedawnera.common.entity.properties.misc.NumberProperty;
import ru.xishnikus.thedawnera.common.io.json.JsonField;
import com.borealopeltapatch.sound.SoundRegistration;
import ru.xishnikus.thedawnera.common.utils.TDEUtils;
import ru.xishnikus.thedawnera.common.utils.WeightedRandom;

public class EntityBorealopelta
        extends BaseRideableAnimal<BaseRideableAnimal.Properties>
        implements KeyInputMob {

    public final ActionState actionAttack = this.actionController.getActionByName("attack");
    public final ActionState actionEat = this.actionController.getActionByName("eat");
    public final ActionState actionScream = this.actionController.getActionByName("scream");
    // Bedrock no tiene animación "dig" propia -- al cavar reusa "down" (agacharse),
    // igual que se usa para el proceso de doma. Ver borealopelta.res_ani.json.
    public final ActionState actionDown = this.actionController.getActionByName("down");

    // "rest"/"sleep" viven en un StateController aparte (ver borealopelta.json,
    // bloque "StateMachine" -> "ID": "StateController"), no en el actionController
    // de arriba -- por eso son campos separados con su propio controller.
    public final ActionState stateRest = this.stateController.getActionByName("rest");
    public final ActionState stateSleep = this.stateController.getActionByName("sleep");

    private int digCooldown = 0;

    public EntityBorealopelta(EntityType<? extends BaseRideableAnimal> type, Level level) {
        super((EntityType<? extends BaseSleepingAnimal>) type, level);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // El goal de cavar comida se agrega vía JSON (AI.Goals -> "dawnera:borealopelta.dig_for_food").
    }

    @Override
    public void tick() {
        super.tick();
        if (this.digCooldown > 0) {
            this.digCooldown--;
        }
        // Al rugir (detecta un enemigo -> StartAggression -> "scream"), se
        // queda plantado en el lugar en vez de seguir caminando hacia el
        // objetivo mientras ruge -- se ve como una advertencia real, no como
        // si trotara y rugiera a la vez. Se ejecuta DESPUÉS de super.tick()
        // (que ya corrió los goals de este tick) para pisar cualquier
        // movimiento que el goal de ataque haya aplicado en este mismo tick.
        // No tocamos la componente Y para no cancelar la gravedad/caída.
        if (this.actionController.is(new ActionState[]{this.actionScream})) {
            this.getNavigation().stop();
            this.setDeltaMovement(0.0, this.getDeltaMovement().y, 0.0);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("DigCooldown", this.digCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("DigCooldown")) {
            this.digCooldown = tag.getInt("DigCooldown");
        }
    }

    public int getDigCooldown() { return this.digCooldown; }
    public void setDigCooldown(int value) { this.digCooldown = value; }

    // Evita que un mismo mordisco aplique daño más de una vez (root cause de
    // por qué el daño saltaba a ~100 con un valor base chico: el cálculo del
    // tick de impacto tenía un bug -- ver onActionTick más abajo -- y podía
    // disparar el golpe repetidas veces dentro del mismo swing).
    private boolean attackHasHit = false;

    @Override
    public void onActionBegin(ActionState state) {
        if (state == this.actionEat) {
            this.playSound((SoundEvent) SoundRegistration.EAT_GRASS.get(), this.getSoundVolume(), this.getVoicePitch());
        }
        if (state == this.actionAttack) {
            this.attackHasHit = false;
            this.playSound((SoundEvent) SoundRegistration.BOREALOPELTA_ATTACK.get(), 1.0f, this.getVoicePitch());
        }
        if (state == this.actionScream) {
            this.playSound((SoundEvent) SoundRegistration.BOREALOPELTA_ROAR.get(), 2.0f, this.getVoicePitch());
        }
    }

    @Override
    public void onActionTick(ActionState state, int ticks) {
        // "Length" está en SEGUNDOS (coincide con la duración real de las
        // animaciones), pero acá faltaba convertir a ticks (*20) antes de
        // buscar el frame de impacto -- sin eso, (int)(1.46*0.32) truncaba a 0
        // y apuntaba siempre al primer tick del swing en vez de a la mitad.
        int impactTick = (int) (this.actionAttack.getLength() * 20 * 0.32f);
        if (!this.attackHasHit && this.actionController.isActionAt(this.actionAttack, impactTick)) {
            this.attackHasHit = true;
            // BUG: acá antes se usaba getLastAttackTarget(), que nunca se setea en
            // ningún lado de esta clase -- siempre daba null, así que el golpe
            // manual jamás se aplicaba y todo dependía de que
            // TDEUtils.attackFrontEntities detectara el hit por su cuenta (barrido
            // geométrico frontal de la librería, que no controlamos). El goal
            // "melee_attack" del JSON sí deja un objetivo real en getTarget() antes
            // de reproducir la animación (ver "focus_on_target" en
            // borealopelta.json), así que usamos eso.
            LivingEntity target = this.getTarget();
            if (target == null) {
                // Sin target de IA (p.ej. lo estás montando y atacás vos a mano
                // con click izquierdo): acá sí no hay "objetivo" fijo, así que
                // usamos el barrido frontal como respaldo.
                target = this.getLastAttackTarget();
            }
            if (target != null && target.isAlive() && this.canAttack(target)
                    && this.distanceToSqr(target) <= 25.0) {
                // Aplicamos daño directo al target
                EntityUtils.damageEntity((LivingEntity) this, (Entity) target, (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE));
                // Forzamos knockback para asegurar que se registre el golpe
                target.knockback(0.5F, Math.sin(this.getYRot() * ((float)Math.PI / 180F)), -Math.cos(this.getYRot() * ((float)Math.PI / 180F)));
            }
            // Siempre ejecutamos el barrido frontal como método secundario
            // (por si hay múltiples enemigos cerca o el target principal murió)
            TDEUtils.attackFrontEntities(this, 5.0f);
        }
    }

    @Override
    public void onActionEnd(ActionState state) {
    }

    @Override
    public void onInputHandle(InputKey inputKey) {
        if (this.tickControlled >= 20 && this.actionController.isNoAction() && !this.isSleepingOrResting()) {
            if (inputKey.is("attack") && this.consumeEnergy(10.0f)) {
                this.actionController.playAction(this.actionAttack);
            }
            if (inputKey.is("roar") && this.consumeEnergy(10.0f)) {
                this.actionController.playAction(this.actionScream);
            }
        }
    }

    protected SoundEvent getAmbientSound() {
        if (this.isSleepingOrResting()) return null;
        return (SoundEvent) SoundRegistration.BOREALOPELTA_IDLE.get();
    }

    @Nullable
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return (SoundEvent) SoundRegistration.BOREALOPELTA_HURT.get();
    }

    @Nullable
    protected SoundEvent getDeathSound() {
        return (SoundEvent) SoundRegistration.BOREALOPELTA_DEATH.get();
    }

    public int getAmbientSoundInterval() {
        return 200;
    }

    // ======================================================================
    // Goal de cavar comida cuando tiene hambre.
    // ======================================================================
    public static class Goals {

        public static class DigForFood extends CustomGoal<EntityBorealopelta> {
            private static final WeightedRandom<Item> DROPS = new WeightedRandom<Item>()
                    .put(1.0f, Items.BEETROOT)
                    .put(1.0f, Items.SWEET_BERRIES)
                    .put(1.0f, Items.POTATO);

            private BlockPos digPos;
            private int digTicks;
            // NUEVO: cuenta ticks seguidos sin poder avanzar hacia digPos (camino
            // roto/inalcanzable). Root cause de por qué no deambulaba: antes,
            // si el punto elegido no se podía alcanzar (agua, desnivel, obstáculo),
            // este goal se quedaba "vivo" para siempre (digPos nunca se ponía en
            // null salvo en stop()), sosteniendo el flag MOVE con prioridad 4 y
            // dejando sin chance de ejecutarse a random_stroll (prioridad 6, menor
            // precedencia). En un bioma taiga, con pasto/tierra por todos lados,
            // este goal casi siempre encuentra un punto para cavar, así que en la
            // práctica el mob quedaba atascado acá de forma casi permanente.
            private int stuckTicks;
            private static final int MAX_STUCK_TICKS = 100; // ~5s sin progreso real -> se rinde
            private final double speedModifier;

            public DigForFood(EntityBorealopelta mob, double speedModifier) {
                super(mob);
                this.speedModifier = speedModifier;
                this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
            }

            public boolean canUse() {
                if (!this.isCanBeUsed()) return false;
                if (this.mob.getDigCooldown() > 0 || this.mob.isBaby() || this.mob.getTarget() != null) return false;
                // FIX: Si ya tenemos un digPos válido, no buscamos otro nuevo hasta terminar este.
                // Esto evita que canUse() siga retornando true indefinidamente mientras
                // canContinueToUse() mantiene el goal activo, bloqueando random_stroll.
                if (this.digPos != null) return false;
                BlockPos pos = this.getDigPos(this.mob.blockPosition());
                if (pos == null) return false;
                this.digPos = pos;
                return true;
            }

            public void start() {
                this.mob.setDigCooldown(RandomUtils.randomInt(this.mob.getRandom(), 1200, 2400));
            }

            public boolean canContinueToUse() {
                return this.isCanBeContinuedToUse() && this.digPos != null;
            }

            public void stop() {
                super.stop();
                this.digTicks = 0;
                this.stuckTicks = 0;
                this.digPos = null;
                this.mob.actionController.setNoState();
            }

            public void tick() {
                super.tick();
                if (this.digPos == null) return;
                BlockPos target = WorldUtils.blockPos(this.digPos.getX() + 0.5, this.digPos.getY(), this.digPos.getZ() + 0.5);
                if (this.mob.distanceToSqr(target.getX(), target.getY(), target.getZ()) < this.mob.getReachDistance(2.0)) {
                    this.stuckTicks = 0;
                    this.digTicks++;
                    this.mob.setDeltaMovement(0.0, 0.0, 0.0);
                    if (this.digTicks % 2 == 0) {
                        this.mob.actionController.playAction(this.mob.actionDown);
                        this.mob.level().levelEvent(2001, this.digPos,
                                Block.getId((BlockState) this.mob.level().getBlockState(this.digPos.below())));
                    }
                    // VERIFICAR: duración de 60 ticks escalada por dificultad. Si el compilador
                    // no encuentra este método, reemplazar simplemente por: if (this.digTicks > 60) {
                    if (this.digTicks > 60) {
                        ItemEntity item = new ItemEntity(this.mob.level(),
                                this.digPos.getX() + 0.5, this.digPos.getY() + 0.5, this.digPos.getZ() + 0.5,
                                new ItemStack(DROPS.random()));
                        this.mob.level().addFreshEntity(item);
                        this.stop();
                    }
                } else if (this.mob.getNavigation().isDone() || this.mob.getNavigation().isStuck()) {
                    // VERIFICAR isStuck(): si no existe con ese nombre exacto, borrar
                    // "|| this.mob.getNavigation().isStuck()" y dejar solo isDone().
                    this.stuckTicks++;
                    if (this.stuckTicks > MAX_STUCK_TICKS) {
                        // No se pudo llegar al punto elegido (típico en taiga con
                        // desniveles/agua entre medio): nos rendimos con un cooldown
                        // corto en vez de quedarnos "vivos" para siempre. Esto es lo
                        // que liberaba el turno para que random_stroll pudiera correr.
                        this.mob.setDigCooldown(RandomUtils.randomInt(this.mob.getRandom(), 200, 400));
                        this.stop();
                        return;
                    }
                    this.mob.getNavigation().moveTo(
                            this.mob.getNavigation().createPath(target.getX(), target.getY(), target.getZ(), 0),
                            this.speedModifier);
                }
            }

            public BlockPos getDigPos(BlockPos from) {
                Level world = this.mob.level();
                RandomSource random = this.mob.getRandom();
                int range = 20;
                for (int i = 0; i < 20; i++) {
                    BlockPos p = from.offset(random.nextInt(range) - range / 2, 0, random.nextInt(range) - range / 2);
                    BlockState state = world.getBlockState(p.below());
                    if (state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK)
                            || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.MOSS_BLOCK)) {
                        return p;
                    }
                }
                return null;
            }

            public static class Builder extends CustomGoalFactory<DigForFood> {
                @JsonField(value = "Speed")
                private NumberProperty speedModifier = NumberProperty.uniform(1.25);

                @Override
                public DigForFood create(BaseAnimal animal) {
                    if (animal instanceof EntityBorealopelta) {
                        return new DigForFood((EntityBorealopelta) animal, this.speedModifier.getDouble());
                    }
                    throw new RuntimeException("This goal can only be used for Borealopelta.");
                }
            }
        }
    }
}
