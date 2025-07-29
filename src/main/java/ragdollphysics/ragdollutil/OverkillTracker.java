package ragdollphysics.ragdollutil;

import basemod.BaseMod;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

import java.util.HashMap;

public class OverkillTracker {
    private static final HashMap<AbstractMonster, PreDamageState> preDamageStates = new HashMap<>();
    private static final HashMap<AbstractMonster, Float> overkillValues = new HashMap<>();

    private static class PreDamageState {
        final float health;
        final float damageAmount;
        final long timestamp;

        PreDamageState(float health, float damageAmount) {
            this.health = health;
            this.damageAmount = damageAmount;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public static void storePreDamageState(AbstractMonster monster, float currentHealth, float damageAmount) {
        preDamageStates.put(monster, new PreDamageState(currentHealth, damageAmount));
    }

    public static float calculateAndRecordOverkill(AbstractMonster monster) {
        PreDamageState preState = preDamageStates.get(monster);
        if (preState == null) {
            BaseMod.logger.warn("No pre-damage state found for " + monster.getClass().getSimpleName());
            return 20f; // Default to baseline if no data
        }

        // Calculate overkill: damage that exceeded what was needed to kill
        float overkillDamage = Math.max(0, preState.damageAmount - preState.health);

        // Cap overkill at 50 for physics calculations
        overkillDamage = Math.min(overkillDamage, 50f);

        overkillValues.put(monster, overkillDamage);

        BaseMod.logger.info("Calculated overkill for " + monster.getClass().getSimpleName()
                + ": " + String.format("%.1f", overkillDamage) + " (damage: "
                + preState.damageAmount + ", health: " + preState.health + ")");

        return overkillDamage;
    }

    public static float getOverkillDamage(AbstractMonster monster) {
        Float overkill = overkillValues.get(monster);
        return overkill != null ? overkill : 20f; // Default to baseline
    }

    public static void cleanup(AbstractMonster monster) {
        preDamageStates.remove(monster);
        overkillValues.remove(monster);
    }

    public static void cleanupAll() {
        preDamageStates.clear();
        overkillValues.clear();
    }
}