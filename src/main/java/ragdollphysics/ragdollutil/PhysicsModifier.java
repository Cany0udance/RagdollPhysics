package ragdollphysics.ragdollutil;

import basemod.BaseMod;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.monsters.beyond.*;
import com.megacrit.cardcrawl.monsters.city.*;
import com.megacrit.cardcrawl.monsters.ending.CorruptHeart;
import com.megacrit.cardcrawl.monsters.ending.SpireShield;
import com.megacrit.cardcrawl.monsters.ending.SpireSpear;
import com.megacrit.cardcrawl.monsters.exordium.*;

import java.lang.reflect.Field;
import java.util.HashMap;

/**
 * Calculates physics adjustments based on overkill damage and enemy weight.
 * Modifies velocity multipliers to create more dramatic or subtle ragdoll effects.
 */
public class PhysicsModifier {

    // ================================
    // SCALING PARAMETERS
    // ================================

    // Overkill damage scaling thresholds
    private static final float OVERKILL_BASELINE = 20f;
    private static final float OVERKILL_MAX = 50f;

    // Horizontal velocity scaling (linear interpolation)
    private static final float MIN_HORIZONTAL_SCALE = 0.2f;
    private static final float MAX_HORIZONTAL_SCALE = 1.5f;

    // Vertical velocity scaling (tiered system)
    private static final float VERTICAL_TINY_HOP_SCALE = 0.3f;      // 0-5 overkill
    private static final float VERTICAL_SMALL_HOP_SCALE = 0.4f;     // 6-19 overkill
    private static final float VERTICAL_BASELINE_SCALE = 1.0f;      // 20 overkill
    private static final float VERTICAL_MAX_SCALE = 1.3f;           // 21-50 overkill

    // Vertical scaling breakpoints
    private static final float VERTICAL_TINY_THRESHOLD = 5f;
    private static final float VERTICAL_SMALL_THRESHOLD = 19f;

    // Angular velocity scaling (linear interpolation)
    private static final float MIN_ANGULAR_SCALE = 0.3f;
    private static final float MAX_ANGULAR_SCALE = 1.4f;

    // Weight impact on different velocity types
    private static final float WEIGHT_HORIZONTAL_IMPACT = 1.0f;
    private static final float WEIGHT_VERTICAL_IMPACT = 0.7f;
    private static final float WEIGHT_ANGULAR_IMPACT = 0.8f;

    // ================================
    // ENEMY WEIGHT SYSTEM
    // ================================

    public enum EntityWeight {
        LIGHT(1.5f),    // Gets knocked around easily
        MEDIUM(1.0f),   // Baseline behavior
        HEAVY(0.6f);    // Barely budges

        public final float modifier;

        EntityWeight(float modifier) {
            this.modifier = modifier;
        }
    }

    private static final HashMap<String, EntityWeight> ENTITY_WEIGHTS = new HashMap<>();

    static {
// === LIGHT ENTITIES (get knocked around easily) ===
        ENTITY_WEIGHTS.put(LouseDefensive.ID, EntityWeight.LIGHT);
        ENTITY_WEIGHTS.put(LouseNormal.ID, EntityWeight.LIGHT);
        ENTITY_WEIGHTS.put(AcidSlime_S.ID, EntityWeight.LIGHT);
        ENTITY_WEIGHTS.put(SpikeSlime_S.ID, EntityWeight.LIGHT);
        ENTITY_WEIGHTS.put(GremlinWarrior.ID, EntityWeight.LIGHT);
        ENTITY_WEIGHTS.put(GremlinThief.ID, EntityWeight.LIGHT);
        ENTITY_WEIGHTS.put(GremlinFat.ID, EntityWeight.LIGHT);
        ENTITY_WEIGHTS.put(GremlinTsundere.ID, EntityWeight.LIGHT);
        ENTITY_WEIGHTS.put(GremlinWizard.ID, EntityWeight.LIGHT);
        ENTITY_WEIGHTS.put(Byrd.ID, EntityWeight.LIGHT);
        ENTITY_WEIGHTS.put(Repulsor.ID, EntityWeight.LIGHT);
        ENTITY_WEIGHTS.put(Spiker.ID, EntityWeight.LIGHT);
        ENTITY_WEIGHTS.put(Exploder.ID, EntityWeight.LIGHT);
        ENTITY_WEIGHTS.put(BronzeOrb.ID, EntityWeight.LIGHT);
        ENTITY_WEIGHTS.put(SnakeDagger.ID, EntityWeight.LIGHT);

        // === MEDIUM ENTITIES (baseline behavior) ===
        ENTITY_WEIGHTS.put(Cultist.ID, EntityWeight.MEDIUM);
        ENTITY_WEIGHTS.put(JawWorm.ID, EntityWeight.MEDIUM);
        ENTITY_WEIGHTS.put(AcidSlime_M.ID, EntityWeight.MEDIUM);
        ENTITY_WEIGHTS.put(SpikeSlime_M.ID, EntityWeight.MEDIUM);
        ENTITY_WEIGHTS.put(FungiBeast.ID, EntityWeight.MEDIUM);
        ENTITY_WEIGHTS.put(SlaverRed.ID, EntityWeight.MEDIUM);
        ENTITY_WEIGHTS.put(SlaverBlue.ID, EntityWeight.MEDIUM);
        ENTITY_WEIGHTS.put(Looter.ID, EntityWeight.MEDIUM);
        ENTITY_WEIGHTS.put(Lagavulin.ID, EntityWeight.MEDIUM);
        ENTITY_WEIGHTS.put(Mugger.ID, EntityWeight.MEDIUM);
        ENTITY_WEIGHTS.put(BanditLeader.ID, EntityWeight.MEDIUM);
        ENTITY_WEIGHTS.put(BanditPointy.ID, EntityWeight.MEDIUM);
        ENTITY_WEIGHTS.put(SphericGuardian.ID, EntityWeight.MEDIUM);
        ENTITY_WEIGHTS.put(Chosen.ID, EntityWeight.MEDIUM);
        ENTITY_WEIGHTS.put(ShelledParasite.ID, EntityWeight.MEDIUM);
        ENTITY_WEIGHTS.put(SnakePlant.ID, EntityWeight.MEDIUM);
        ENTITY_WEIGHTS.put(Snecko.ID, EntityWeight.MEDIUM);
        ENTITY_WEIGHTS.put(Centurion.ID, EntityWeight.MEDIUM);
        ENTITY_WEIGHTS.put(Healer.ID, EntityWeight.MEDIUM);
        ENTITY_WEIGHTS.put(BookOfStabbing.ID, EntityWeight.MEDIUM);
        ENTITY_WEIGHTS.put(GremlinLeader.ID, EntityWeight.MEDIUM);
        ENTITY_WEIGHTS.put(Taskmaster.ID, EntityWeight.MEDIUM);
        ENTITY_WEIGHTS.put(OrbWalker.ID, EntityWeight.MEDIUM);
        ENTITY_WEIGHTS.put(Darkling.ID, EntityWeight.MEDIUM);
        ENTITY_WEIGHTS.put(Reptomancer.ID, EntityWeight.MEDIUM);
        ENTITY_WEIGHTS.put(Nemesis.ID, EntityWeight.MEDIUM);

        // === HEAVY ENTITIES (barely budge) ===
        ENTITY_WEIGHTS.put(SpikeSlime_L.ID, EntityWeight.HEAVY);
        ENTITY_WEIGHTS.put(AcidSlime_L.ID, EntityWeight.HEAVY);
        ENTITY_WEIGHTS.put(GremlinNob.ID, EntityWeight.HEAVY);
        ENTITY_WEIGHTS.put(BanditBear.ID, EntityWeight.HEAVY);
        ENTITY_WEIGHTS.put(Maw.ID, EntityWeight.HEAVY);
        ENTITY_WEIGHTS.put(WrithingMass.ID, EntityWeight.HEAVY);
        ENTITY_WEIGHTS.put(SpireGrowth.ID, EntityWeight.HEAVY);
        ENTITY_WEIGHTS.put(Transient.ID, EntityWeight.HEAVY);
        ENTITY_WEIGHTS.put(TheGuardian.ID, EntityWeight.HEAVY);
        ENTITY_WEIGHTS.put(SlimeBoss.ID, EntityWeight.HEAVY);
        ENTITY_WEIGHTS.put(Champ.ID, EntityWeight.HEAVY);
        ENTITY_WEIGHTS.put(BronzeAutomaton.ID, EntityWeight.HEAVY);
        ENTITY_WEIGHTS.put(GiantHead.ID, EntityWeight.HEAVY);
        ENTITY_WEIGHTS.put(Donu.ID, EntityWeight.HEAVY);
        ENTITY_WEIGHTS.put(Deca.ID, EntityWeight.HEAVY);
        ENTITY_WEIGHTS.put(TimeEater.ID, EntityWeight.HEAVY);
        ENTITY_WEIGHTS.put(AwakenedOne.ID, EntityWeight.HEAVY);
        ENTITY_WEIGHTS.put(CorruptHeart.ID, EntityWeight.HEAVY);
        ENTITY_WEIGHTS.put(SpireSpear.ID, EntityWeight.HEAVY);
        ENTITY_WEIGHTS.put(SpireShield.ID, EntityWeight.HEAVY);
    }

    // ================================
    // VELOCITY MODIFIER DATA CLASS
    // ================================

    public static class VelocityModifiers {
        public final float horizontalMultiplier;
        public final float verticalMultiplier;
        public final float angularMultiplier;

        public VelocityModifiers(float horizontal, float vertical, float angular) {
            this.horizontalMultiplier = horizontal;
            this.verticalMultiplier = vertical;
            this.angularMultiplier = angular;
        }

        @Override
        public String toString() {
            return String.format("VelMod{h=%.2f, v=%.2f, a=%.2f}",
                    horizontalMultiplier, verticalMultiplier, angularMultiplier);
        }
    }

    // ================================
    // PUBLIC API
    // ================================

    /**
     * Calculate velocity modifiers for a monster based on overkill damage and weight
     */
    public static VelocityModifiers calculateModifiers(AbstractCreature entity) {
        float overkillDamage = OverkillTracker.getOverkillDamage(entity);
        EntityWeight weight = getWeight(entity);

        // Calculate base scaling factors from overkill damage
        float horizontalOverkillScale = calculateHorizontalScaling(overkillDamage);
        float verticalOverkillScale = calculateVerticalScaling(overkillDamage);
        float angularOverkillScale = calculateAngularScaling(overkillDamage);

        // Apply weight modifiers to each velocity type
        float horizontalMult = horizontalOverkillScale * calculateWeightMultiplier(weight.modifier, WEIGHT_HORIZONTAL_IMPACT);
        float verticalMult = verticalOverkillScale * calculateWeightMultiplier(weight.modifier, WEIGHT_VERTICAL_IMPACT);
        float angularMult = angularOverkillScale * calculateWeightMultiplier(weight.modifier, WEIGHT_ANGULAR_IMPACT);

        return new VelocityModifiers(horizontalMult, verticalMult, angularMult);
    }

    // ================================
    // SCALING CALCULATIONS
    // ================================

    /**
     * Calculate horizontal velocity scaling (linear interpolation)
     */
    private static float calculateHorizontalScaling(float overkillDamage) {
        float overkillRatio = Math.max(0f, Math.min(1f, overkillDamage / OVERKILL_MAX));
        return MIN_HORIZONTAL_SCALE + (MAX_HORIZONTAL_SCALE - MIN_HORIZONTAL_SCALE) * overkillRatio;
    }

    /**
     * Calculate vertical velocity scaling (tiered system)
     */
    private static float calculateVerticalScaling(float overkillDamage) {
        if (overkillDamage <= VERTICAL_TINY_THRESHOLD) {
            // 0-5 overkill: tiny hop
            return VERTICAL_TINY_HOP_SCALE;
        } else if (overkillDamage <= VERTICAL_SMALL_THRESHOLD) {
            // 6-19 overkill: interpolate from tiny to small
            float progress = (overkillDamage - VERTICAL_TINY_THRESHOLD) / (VERTICAL_SMALL_THRESHOLD - VERTICAL_TINY_THRESHOLD);
            return VERTICAL_TINY_HOP_SCALE + (VERTICAL_SMALL_HOP_SCALE - VERTICAL_TINY_HOP_SCALE) * progress;
        } else if (overkillDamage == OVERKILL_BASELINE) {
            // Exactly 20 overkill: baseline
            return VERTICAL_BASELINE_SCALE;
        } else if (overkillDamage < OVERKILL_BASELINE) {
            // 19.1-19.9 overkill: interpolate from small to baseline
            float progress = (overkillDamage - VERTICAL_SMALL_THRESHOLD) / (OVERKILL_BASELINE - VERTICAL_SMALL_THRESHOLD);
            return VERTICAL_SMALL_HOP_SCALE + (VERTICAL_BASELINE_SCALE - VERTICAL_SMALL_HOP_SCALE) * progress;
        } else {
            // 21-50 overkill: interpolate from baseline to max
            float progress = Math.min(1f, (overkillDamage - OVERKILL_BASELINE) / (OVERKILL_MAX - OVERKILL_BASELINE));
            return VERTICAL_BASELINE_SCALE + (VERTICAL_MAX_SCALE - VERTICAL_BASELINE_SCALE) * progress;
        }
    }

    /**
     * Calculate angular velocity scaling (linear interpolation)
     */
    private static float calculateAngularScaling(float overkillDamage) {
        float overkillRatio = Math.max(0f, Math.min(1f, overkillDamage / OVERKILL_MAX));
        return MIN_ANGULAR_SCALE + (MAX_ANGULAR_SCALE - MIN_ANGULAR_SCALE) * overkillRatio;
    }

    // ================================
    // HELPER METHODS
    // ================================

    /**
     * Apply weight scaling with specified impact level
     */
    private static float calculateWeightMultiplier(float weightScale, float weightImpact) {
        return 1.0f + (weightScale - 1.0f) * weightImpact;
    }

    /**
     * Get the weight classification for a monster
     */
    private static EntityWeight getWeight(AbstractCreature entity) {
        String entityID = getEntityID(entity);
        return ENTITY_WEIGHTS.getOrDefault(entityID, EntityWeight.MEDIUM);
    }

    /**
     * Get monster ID using reflection, fallback to class name
     */
    private static String getEntityID(AbstractCreature entity) {
        try {
            // Try to get monster ID first
            if (entity instanceof AbstractMonster) {
                Field idField = entity.getClass().getField("ID");
                return (String) idField.get(null);
            } else if (entity instanceof AbstractPlayer) {
                // For players, use class simple name
                return entity.getClass().getSimpleName();
            }
        } catch (Exception e) {
            // Fallback to class name
        }
        return entity.getClass().getSimpleName();
    }
}