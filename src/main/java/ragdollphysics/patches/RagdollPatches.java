package ragdollphysics.patches;

import basemod.BaseMod;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import ragdollphysics.ragdollutil.OverkillTracker;
import ragdollphysics.ragdollutil.RagdollManager;

public class RagdollPatches {
    private static final RagdollManager ragdollManager = new RagdollManager();

    @SpirePatch(clz = AbstractMonster.class, method = "updateDeathAnimation")
    public static class DeathAnimationPatch {
        @SpirePrefixPatch
        public static SpireReturn<Void> prefix(AbstractMonster __instance) {
            return ragdollManager.handleDeathAnimation(__instance);
        }
    }

    @SpirePatch(clz = AbstractMonster.class, method = "render")
    public static class RenderPatch {
        @SpirePrefixPatch
        public static SpireReturn<Void> prefix(AbstractMonster __instance, SpriteBatch sb) {
            return ragdollManager.handleRender(__instance, sb);
        }
    }

    @SpirePatch(clz = AbstractMonster.class, method = "damage")
    public static class OverkillCapturePatch {

        @SpirePrefixPatch
        public static void prefix(AbstractMonster __instance, DamageInfo info) {
            // Store the health before damage is applied
            if (!__instance.isDying && !__instance.isEscaping && info.output > 0) {
                // Store pre-damage state for overkill calculation
                OverkillTracker.storePreDamageState(__instance, __instance.currentHealth, info.output);
            }
        }

        @SpirePostfixPatch
        public static void postfix(AbstractMonster __instance, DamageInfo info) {
            // Only calculate overkill if the monster just died from this damage
            if (__instance.isDying && __instance.currentHealth <= 0) {
                float overkillDamage = OverkillTracker.calculateAndRecordOverkill(__instance);

                if (overkillDamage >= 0) { // Only log if we have valid data
                    BaseMod.logger.info("Monster " + __instance.getClass().getSimpleName()
                            + " killed with " + String.format("%.1f", overkillDamage) + " overkill damage");
                }
            }
        }
    }
}
