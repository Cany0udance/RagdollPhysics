package ragdollphysics.ragdollutil;

import basemod.BaseMod;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

import java.util.HashMap;

public class OverkillTracker {
    private static final HashMap<AbstractCreature, PreDamageState> preDamageStates = new HashMap<>();
    private static final HashMap<AbstractCreature, Float> overkillValues = new HashMap<>();

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

    public static void storePreDamageState(AbstractCreature entity, float currentHealth, float damageAmount) {
        preDamageStates.put(entity, new PreDamageState(currentHealth, damageAmount));
    }

    public static float calculateAndRecordOverkill(AbstractCreature entity) {
        PreDamageState preState = preDamageStates.get(entity);
        if (preState == null) {
            return 20f; // Default to baseline if no data
        }

        // Calculate overkill: damage that exceeded what was needed to kill
        float overkillDamage = Math.max(0, preState.damageAmount - preState.health);

        // Cap overkill at 50 for physics calculations
        overkillDamage = Math.min(overkillDamage, 50f);

        overkillValues.put(entity, overkillDamage);

        return overkillDamage;
    }

    public static float getOverkillDamage(AbstractCreature entity) {
        Float overkill = overkillValues.get(entity);
        return overkill != null ? overkill : 20f; // Default to baseline
    }

    public static void cleanup(AbstractCreature entity) {
        preDamageStates.remove(entity);
        overkillValues.remove(entity);
    }

    public static void cleanupAll() {
        preDamageStates.clear();
        overkillValues.clear();
    }
}