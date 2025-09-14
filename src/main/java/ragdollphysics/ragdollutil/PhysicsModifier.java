package ragdollphysics.ragdollutil;

import basemod.BaseMod;
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

    public enum EnemyWeight {
        LIGHT(1.5f),    // Gets knocked around easily
        MEDIUM(1.0f),   // Baseline behavior
        HEAVY(0.6f);    // Barely budges

        public final float modifier;

        EnemyWeight(float modifier) {
            this.modifier = modifier;
        }
    }

    private static final HashMap<String, EnemyWeight> ENEMY_WEIGHTS = new HashMap<>();

    static {
        // === LIGHT ENEMIES (get knocked around easily) ===
        ENEMY_WEIGHTS.put(LouseDefensive.ID, EnemyWeight.LIGHT);
        ENEMY_WEIGHTS.put(LouseNormal.ID, EnemyWeight.LIGHT);
        ENEMY_WEIGHTS.put(AcidSlime_S.ID, EnemyWeight.LIGHT);
        ENEMY_WEIGHTS.put(SpikeSlime_S.ID, EnemyWeight.LIGHT);
        ENEMY_WEIGHTS.put(GremlinWarrior.ID, EnemyWeight.LIGHT);
        ENEMY_WEIGHTS.put(GremlinThief.ID, EnemyWeight.LIGHT);
        ENEMY_WEIGHTS.put(GremlinFat.ID, EnemyWeight.LIGHT);
        ENEMY_WEIGHTS.put(GremlinTsundere.ID, EnemyWeight.LIGHT);
        ENEMY_WEIGHTS.put(GremlinWizard.ID, EnemyWeight.LIGHT);
        ENEMY_WEIGHTS.put(Byrd.ID, EnemyWeight.LIGHT);
        ENEMY_WEIGHTS.put(Repulsor.ID, EnemyWeight.LIGHT);
        ENEMY_WEIGHTS.put(Spiker.ID, EnemyWeight.LIGHT);
        ENEMY_WEIGHTS.put(Exploder.ID, EnemyWeight.LIGHT);
        ENEMY_WEIGHTS.put(BronzeOrb.ID, EnemyWeight.LIGHT);
        ENEMY_WEIGHTS.put(SnakeDagger.ID, EnemyWeight.LIGHT);

        // === MEDIUM ENEMIES (baseline behavior) ===
        ENEMY_WEIGHTS.put(Cultist.ID, EnemyWeight.MEDIUM);
        ENEMY_WEIGHTS.put(JawWorm.ID, EnemyWeight.MEDIUM);
        ENEMY_WEIGHTS.put(AcidSlime_M.ID, EnemyWeight.MEDIUM);
        ENEMY_WEIGHTS.put(SpikeSlime_M.ID, EnemyWeight.MEDIUM);
        ENEMY_WEIGHTS.put(FungiBeast.ID, EnemyWeight.MEDIUM);
        ENEMY_WEIGHTS.put(SlaverRed.ID, EnemyWeight.MEDIUM);
        ENEMY_WEIGHTS.put(SlaverBlue.ID, EnemyWeight.MEDIUM);
        ENEMY_WEIGHTS.put(Looter.ID, EnemyWeight.MEDIUM);
        ENEMY_WEIGHTS.put(Lagavulin.ID, EnemyWeight.MEDIUM);
        ENEMY_WEIGHTS.put(Mugger.ID, EnemyWeight.MEDIUM);
        ENEMY_WEIGHTS.put(BanditLeader.ID, EnemyWeight.MEDIUM);
        ENEMY_WEIGHTS.put(BanditPointy.ID, EnemyWeight.MEDIUM);
        ENEMY_WEIGHTS.put(SphericGuardian.ID, EnemyWeight.MEDIUM);
        ENEMY_WEIGHTS.put(Chosen.ID, EnemyWeight.MEDIUM);
        ENEMY_WEIGHTS.put(ShelledParasite.ID, EnemyWeight.MEDIUM);
        ENEMY_WEIGHTS.put(SnakePlant.ID, EnemyWeight.MEDIUM);
        ENEMY_WEIGHTS.put(Snecko.ID, EnemyWeight.MEDIUM);
        ENEMY_WEIGHTS.put(Centurion.ID, EnemyWeight.MEDIUM);
        ENEMY_WEIGHTS.put(Healer.ID, EnemyWeight.MEDIUM);
        ENEMY_WEIGHTS.put(BookOfStabbing.ID, EnemyWeight.MEDIUM);
        ENEMY_WEIGHTS.put(GremlinLeader.ID, EnemyWeight.MEDIUM);
        ENEMY_WEIGHTS.put(Taskmaster.ID, EnemyWeight.MEDIUM);
        ENEMY_WEIGHTS.put(OrbWalker.ID, EnemyWeight.MEDIUM);
        ENEMY_WEIGHTS.put(Darkling.ID, EnemyWeight.MEDIUM);
        ENEMY_WEIGHTS.put(Reptomancer.ID, EnemyWeight.MEDIUM);
        ENEMY_WEIGHTS.put(Nemesis.ID, EnemyWeight.MEDIUM);

        // === HEAVY ENEMIES (barely budge) ===
        ENEMY_WEIGHTS.put(SpikeSlime_L.ID, EnemyWeight.HEAVY);
        ENEMY_WEIGHTS.put(AcidSlime_L.ID, EnemyWeight.HEAVY);
        ENEMY_WEIGHTS.put(GremlinNob.ID, EnemyWeight.HEAVY);
        ENEMY_WEIGHTS.put(BanditBear.ID, EnemyWeight.HEAVY);
        ENEMY_WEIGHTS.put(Maw.ID, EnemyWeight.HEAVY);
        ENEMY_WEIGHTS.put(WrithingMass.ID, EnemyWeight.HEAVY);
        ENEMY_WEIGHTS.put(SpireGrowth.ID, EnemyWeight.HEAVY);
        ENEMY_WEIGHTS.put(Transient.ID, EnemyWeight.HEAVY);
        ENEMY_WEIGHTS.put(TheGuardian.ID, EnemyWeight.HEAVY);
        ENEMY_WEIGHTS.put(SlimeBoss.ID, EnemyWeight.HEAVY);
        ENEMY_WEIGHTS.put(Champ.ID, EnemyWeight.HEAVY);
        ENEMY_WEIGHTS.put(BronzeAutomaton.ID, EnemyWeight.HEAVY);
        ENEMY_WEIGHTS.put(GiantHead.ID, EnemyWeight.HEAVY);
        ENEMY_WEIGHTS.put(Donu.ID, EnemyWeight.HEAVY);
        ENEMY_WEIGHTS.put(Deca.ID, EnemyWeight.HEAVY);
        ENEMY_WEIGHTS.put(TimeEater.ID, EnemyWeight.HEAVY);
        ENEMY_WEIGHTS.put(AwakenedOne.ID, EnemyWeight.HEAVY);
        ENEMY_WEIGHTS.put(CorruptHeart.ID, EnemyWeight.HEAVY);
        ENEMY_WEIGHTS.put(SpireSpear.ID, EnemyWeight.HEAVY);
        ENEMY_WEIGHTS.put(SpireShield.ID, EnemyWeight.HEAVY);
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
    public static VelocityModifiers calculateModifiers(AbstractMonster monster) {
        float overkillDamage = OverkillTracker.getOverkillDamage(monster);
        EnemyWeight weight = getWeight(monster);

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
    private static EnemyWeight getWeight(AbstractMonster monster) {
        String monsterID = getMonsterID(monster);
        return ENEMY_WEIGHTS.getOrDefault(monsterID, EnemyWeight.MEDIUM);
    }

    /**
     * Get monster ID using reflection, fallback to class name
     */
    private static String getMonsterID(AbstractMonster monster) {
        try {
            Field idField = monster.getClass().getField("ID");
            return (String) idField.get(null);
        } catch (Exception e) {
            return monster.getClass().getSimpleName();
        }
    }
}