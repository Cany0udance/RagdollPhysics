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

// PhysicsModifier.java - Calculates physics adjustments based on overkill and weight
public class PhysicsModifier {
    // === ENEMY WEIGHT SYSTEM ===
    public enum EnemyWeight {
        LIGHT(1.5f),    // Gets knocked around easily
        MEDIUM(1.0f),   // Baseline behavior
        HEAVY(0.6f);    // Barely budges

        public final float modifier;

        EnemyWeight(float modifier) {
            this.modifier = modifier;
        }
    }

    // Enemy weight database - add entries as needed
    private static final HashMap<String, EnemyWeight> ENEMY_WEIGHTS = new HashMap<>();
    static {
        // Light enemies (get knocked around easily)
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

        // Medium enemies (baseline behavior)
        ENEMY_WEIGHTS.put(Cultist.ID, EnemyWeight.MEDIUM);
        ENEMY_WEIGHTS.put(JawWorm.ID, EnemyWeight.MEDIUM);
        ENEMY_WEIGHTS.put(AcidSlime_M.ID, EnemyWeight.MEDIUM);
        ENEMY_WEIGHTS.put(SpikeSlime_M.ID, EnemyWeight.MEDIUM);
        ENEMY_WEIGHTS.put(FungiBeast.ID, EnemyWeight.MEDIUM);
        ENEMY_WEIGHTS.put(SlaverRed.ID, EnemyWeight.MEDIUM);
        ENEMY_WEIGHTS.put(SlaverBlue.ID, EnemyWeight.MEDIUM);
        ENEMY_WEIGHTS.put(Looter.ID, EnemyWeight.MEDIUM);
        ENEMY_WEIGHTS.put(Mugger.ID, EnemyWeight.MEDIUM);
        ENEMY_WEIGHTS.put(SphericGuardian.ID, EnemyWeight.MEDIUM);
        ENEMY_WEIGHTS.put(Chosen.ID, EnemyWeight.MEDIUM);
        ENEMY_WEIGHTS.put(ShelledParasite.ID, EnemyWeight.MEDIUM);
        ENEMY_WEIGHTS.put(SnakePlant.ID, EnemyWeight.MEDIUM);
        ENEMY_WEIGHTS.put(Snecko.ID, EnemyWeight.MEDIUM);
        ENEMY_WEIGHTS.put(Centurion.ID, EnemyWeight.MEDIUM);
        ENEMY_WEIGHTS.put(Healer.ID, EnemyWeight.MEDIUM);
        ENEMY_WEIGHTS.put(OrbWalker.ID, EnemyWeight.MEDIUM);
        ENEMY_WEIGHTS.put(Darkling.ID, EnemyWeight.MEDIUM);

        // Heavy enemies (barely budge)
        ENEMY_WEIGHTS.put(SpikeSlime_L.ID, EnemyWeight.HEAVY);
        ENEMY_WEIGHTS.put(AcidSlime_L.ID, EnemyWeight.HEAVY);
        ENEMY_WEIGHTS.put(Maw.ID, EnemyWeight.HEAVY);
        ENEMY_WEIGHTS.put(WrithingMass.ID, EnemyWeight.HEAVY);
        ENEMY_WEIGHTS.put(SpireGrowth.ID, EnemyWeight.HEAVY);
        ENEMY_WEIGHTS.put(Transient.ID, EnemyWeight.HEAVY);
        ENEMY_WEIGHTS.put(TheGuardian.ID, EnemyWeight.HEAVY);
        ENEMY_WEIGHTS.put(SlimeBoss.ID, EnemyWeight.HEAVY);
        ENEMY_WEIGHTS.put(Champ.ID, EnemyWeight.HEAVY);
        ENEMY_WEIGHTS.put(BronzeAutomaton.ID, EnemyWeight.HEAVY);
        ENEMY_WEIGHTS.put(Donu.ID, EnemyWeight.HEAVY);
        ENEMY_WEIGHTS.put(Deca.ID, EnemyWeight.HEAVY);
        ENEMY_WEIGHTS.put(TimeEater.ID, EnemyWeight.HEAVY);
        ENEMY_WEIGHTS.put(AwakenedOne.ID, EnemyWeight.HEAVY);
        ENEMY_WEIGHTS.put(CorruptHeart.ID, EnemyWeight.HEAVY);
        ENEMY_WEIGHTS.put(SpireSpear.ID, EnemyWeight.HEAVY);
        ENEMY_WEIGHTS.put(SpireShield.ID, EnemyWeight.HEAVY);
    }

    private static EnemyWeight getWeight(AbstractMonster monster) {
        String monsterID = getMonsterID(monster);
        return ENEMY_WEIGHTS.getOrDefault(monsterID, EnemyWeight.MEDIUM);
    }

    private static String getMonsterID(AbstractMonster monster) {
        // Try to get the ID field via reflection
        try {
            Field idField = monster.getClass().getField("ID");
            return (String) idField.get(null);
        } catch (Exception e) {
            // Fallback to class name if ID field not accessible
            return monster.getClass().getSimpleName();
        }
    }
    // === TUNABLE PARAMETERS ===
    // Overkill damage scaling (0 = no overkill, 20 = baseline, 50 = maximum)
    private static final float OVERKILL_BASELINE = 20f;
    private static final float OVERKILL_MAX = 50f;
    private static final float OVERKILL_MIN = 0f;

    // Horizontal velocity scaling (linear from min to max)
    private static final float MIN_HORIZONTAL_SCALE = 0.2f;  // At 0 overkill, reduce to 20% of normal
    private static final float MAX_HORIZONTAL_SCALE = 1.5f;  // At 50 overkill, increase to 150% of normal

    // Vertical velocity scaling (tiered system for more dramatic differences)
    private static final float VERTICAL_TINY_HOP_SCALE = 0.15f;     // 0-5 overkill: barely hop
    private static final float VERTICAL_SMALL_HOP_SCALE = 0.4f;     // 6-19 overkill: small jump
    private static final float VERTICAL_BASELINE_SCALE = 1.0f;      // 20 overkill: baseline
    private static final float VERTICAL_MAX_SCALE = 1.3f;           // 21-50 overkill: bigger jump (capped)

    // Overkill breakpoints for vertical velocity
    private static final float VERTICAL_TINY_THRESHOLD = 5f;
    private static final float VERTICAL_SMALL_THRESHOLD = 19f;

    // Angular velocity scaling
    private static final float MIN_ANGULAR_SCALE = 0.3f;   // At 0 overkill, reduce to 30% of normal
    private static final float MAX_ANGULAR_SCALE = 1.4f;   // At 50 overkill, increase to 140% of normal

    // Weight impact on different velocity types
    private static final float WEIGHT_HORIZONTAL_IMPACT = 1.0f;    // Full weight impact on horizontal
    private static final float WEIGHT_VERTICAL_IMPACT = 0.7f;      // Significant weight impact on vertical
    private static final float WEIGHT_ANGULAR_IMPACT = 0.8f;       // Most weight impact on angular

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

    public static VelocityModifiers calculateModifiers(AbstractMonster monster) {
        float overkillDamage = OverkillTracker.getOverkillDamage(monster);
        EnemyWeight weight = getWeight(monster);

        // Calculate horizontal scaling (linear from 0-50 overkill)
        float overkillRatio = Math.max(0f, Math.min(1f, overkillDamage / OVERKILL_MAX));
        float horizontalOverkillScale = MIN_HORIZONTAL_SCALE + (MAX_HORIZONTAL_SCALE - MIN_HORIZONTAL_SCALE) * overkillRatio;

        // Calculate vertical scaling (tiered system)
        float verticalOverkillScale;
        if (overkillDamage <= VERTICAL_TINY_THRESHOLD) {
            // 0-5 overkill: tiny hop
            verticalOverkillScale = VERTICAL_TINY_HOP_SCALE;
        } else if (overkillDamage <= VERTICAL_SMALL_THRESHOLD) {
            // 6-19 overkill: interpolate from tiny to small
            float progress = (overkillDamage - VERTICAL_TINY_THRESHOLD) / (VERTICAL_SMALL_THRESHOLD - VERTICAL_TINY_THRESHOLD);
            verticalOverkillScale = VERTICAL_TINY_HOP_SCALE + (VERTICAL_SMALL_HOP_SCALE - VERTICAL_TINY_HOP_SCALE) * progress;
        } else if (overkillDamage == OVERKILL_BASELINE) {
            // Exactly 20 overkill: baseline
            verticalOverkillScale = VERTICAL_BASELINE_SCALE;
        } else if (overkillDamage < OVERKILL_BASELINE) {
            // 19.1-19.9 overkill: interpolate from small to baseline
            float progress = (overkillDamage - VERTICAL_SMALL_THRESHOLD) / (OVERKILL_BASELINE - VERTICAL_SMALL_THRESHOLD);
            verticalOverkillScale = VERTICAL_SMALL_HOP_SCALE + (VERTICAL_BASELINE_SCALE - VERTICAL_SMALL_HOP_SCALE) * progress;
        } else {
            // 21-50 overkill: interpolate from baseline to max (capped at 1.3x)
            float progress = Math.min(1f, (overkillDamage - OVERKILL_BASELINE) / (OVERKILL_MAX - OVERKILL_BASELINE));
            verticalOverkillScale = VERTICAL_BASELINE_SCALE + (VERTICAL_MAX_SCALE - VERTICAL_BASELINE_SCALE) * progress;
        }

        // Calculate angular scaling (linear like horizontal)
        float angularOverkillScale = MIN_ANGULAR_SCALE + (MAX_ANGULAR_SCALE - MIN_ANGULAR_SCALE) * overkillRatio;

        // Get weight modifier
        float weightScale = weight.modifier;

        // Calculate final multipliers for each velocity type
        float horizontalMult = horizontalOverkillScale * calculateWeightMultiplier(weightScale, WEIGHT_HORIZONTAL_IMPACT);
        float verticalMult = verticalOverkillScale * calculateWeightMultiplier(weightScale, WEIGHT_VERTICAL_IMPACT);
        float angularMult = angularOverkillScale * calculateWeightMultiplier(weightScale, WEIGHT_ANGULAR_IMPACT);

        VelocityModifiers modifiers = new VelocityModifiers(horizontalMult, verticalMult, angularMult);

        BaseMod.logger.info("Physics modifiers for " + monster.getClass().getSimpleName()
                + " - overkill: " + String.format("%.1f", overkillDamage)
                + " (h:" + String.format("%.2f", horizontalOverkillScale)
                + ", v:" + String.format("%.2f", verticalOverkillScale)
                + ", a:" + String.format("%.2f", angularOverkillScale) + ")"
                + ", weight: " + weight.name() + " (" + String.format("%.1f", weightScale) + ")"
                + ", result: " + modifiers);

        return modifiers;
    }

    private static float calculateWeightMultiplier(float weightScale, float weightImpact) {
        // Apply weight scaling with the specified impact level
        return 1.0f + (weightScale - 1.0f) * weightImpact;
    }
}
