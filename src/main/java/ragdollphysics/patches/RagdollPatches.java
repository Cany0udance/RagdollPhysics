package ragdollphysics.patches;

import basemod.BaseMod;
import basemod.abstracts.CustomMonster;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.screens.DeathScreen;

import ragdollphysics.actions.PlayerRagdollWaitAction;
import ragdollphysics.effects.PlayerRagdollVFX;
import ragdollphysics.ragdollutil.OverkillTracker;
import ragdollphysics.ragdollutil.RagdollManager;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class RagdollPatches {
    private static final RagdollManager ragdollManager = new RagdollManager();

    private static final Set<String> RAGDOLL_BLACKLIST = new HashSet<>(Arrays.asList(
            "ruina:JesterOfNihil"
    ));

    // Blacklist of player class names that should not have ragdoll physics
    private static final Set<String> PLAYER_RAGDOLL_BLACKLIST = new HashSet<>(Arrays.asList(
            // Add problematic player class names here
    ));

    // Blacklist of mod packages - blocks ALL players from these mods
    private static final Set<String> BLOCKED_MOD_PACKAGES = new HashSet<>(Arrays.asList(
            "BlueArchive_ProblemSolver",
            "Hraesvelgr"
    ));

    private static boolean isBlacklisted(AbstractMonster monster) {
        return RAGDOLL_BLACKLIST.contains(monster.id);
    }

    private static boolean isPlayerBlacklisted(AbstractPlayer player) {
        // Check individual player blacklist first
        if (PLAYER_RAGDOLL_BLACKLIST.contains(player.getClass().getSimpleName())) {
            return true;
        }

        // Check if player is from a blocked mod package
        String packageName = player.getClass().getPackage() != null ?
                player.getClass().getPackage().getName() : "";
        for (String blockedPackage : BLOCKED_MOD_PACKAGES) {
            if (packageName.contains(blockedPackage)) {
                return true;
            }
        }

        return false;
    }

    @SpirePatch(clz = AbstractMonster.class, method = "updateDeathAnimation")
    public static class DeathAnimationPatch {
        @SpirePrefixPatch
        public static SpireReturn<Void> prefix(AbstractMonster __instance) {
            // Skip ragdoll physics for blacklisted monsters
            if (isBlacklisted(__instance)) {
                return SpireReturn.Continue();
            }
            return ragdollManager.handleDeathAnimation(__instance);
        }
    }

    @SpirePatch(clz = AbstractMonster.class, method = "render")
    public static class RenderPatch {
        @SpirePrefixPatch
        public static SpireReturn<Void> prefix(AbstractMonster __instance, SpriteBatch sb) {
            // Skip ragdoll rendering for blacklisted monsters
            if (isBlacklisted(__instance)) {
                return SpireReturn.Continue();
            }
            return ragdollManager.handleRender(__instance, sb);
        }
    }

    @SpirePatch(clz = CustomMonster.class, method = "render")
    public static class CustomMonsterRenderPatch {
        @SpirePrefixPatch
        public static SpireReturn<Void> prefix(AbstractMonster __instance, SpriteBatch sb) {
            // Skip ragdoll rendering for blacklisted monsters
            if (isBlacklisted(__instance)) {
                return SpireReturn.Continue();
            }
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
                //    BaseMod.logger.info("Monster " + __instance.getClass().getSimpleName()
                 //           + " killed with " + String.format("%.1f", overkillDamage) + " overkill damage");
                }
            }
        }
    }

    @SpirePatch(clz = AbstractPlayer.class, method = "damage")
    public static class PlayerOverkillCapturePatch {

        @SpirePrefixPatch
        public static void prefix(AbstractPlayer __instance, DamageInfo info) {

            if (!__instance.isDead && info.output > 0) {
                OverkillTracker.storePreDamageState(__instance, __instance.currentHealth, info.output);
            }
        }

        @SpirePostfixPatch
        public static void postfix(AbstractPlayer __instance, DamageInfo info) {

            // Check if this damage should have killed the player
            float preHealth = OverkillTracker.getPreDamageHealth(__instance);
            boolean shouldHaveDied = (preHealth > 0 && preHealth - info.output <= 0);

            if (shouldHaveDied || __instance.isDead) {
                float overkillDamage = OverkillTracker.calculateAndRecordOverkill(__instance);

                if (overkillDamage >= 0) {
                //    BaseMod.logger.info("Player killed with " + String.format("%.1f", overkillDamage) + " overkill damage");
                }
            }
        }
    }

    @SpirePatch(clz = AbstractPlayer.class, method = "render")
    public static class PlayerRenderPatch {
        @SpirePrefixPatch
        public static SpireReturn<Void> prefix(AbstractPlayer __instance, SpriteBatch sb) {
            if (isPlayerBlacklisted(__instance)) {
                return SpireReturn.Continue();
            }
            return ragdollManager.handlePlayerRender(__instance, sb);
        }
    }

    @SpirePatch(clz = AbstractPlayer.class, method = "renderPlayerImage")
    public static class PlayerImageRenderPatch {
        @SpirePrefixPatch
        public static SpireReturn<Void> prefix(AbstractPlayer __instance, SpriteBatch sb) {
            if (isPlayerBlacklisted(__instance)) {
                return SpireReturn.Continue();
            }
            return ragdollManager.handlePlayerRenderImage(__instance, sb);
        }
    }

    @SpirePatch(clz = AbstractPlayer.class, method = "update")
    public static class PlayerUpdatePatch {
        @SpirePostfixPatch
        public static void postfix(AbstractPlayer __instance) {
            if (ragdollManager.hasActivePlayerRagdoll(__instance)) {
                // Update ragdoll physics every frame while dead
                ragdollManager.updatePlayerRagdollLogic(__instance);
            }
        }
    }

    @SpirePatch(clz = DeathScreen.class, method = "update")
    public static class DeathScreenUpdatePatch {
        @SpirePostfixPatch
        public static void postfix(DeathScreen __instance) {
            // Force player ragdoll updates even during death screen
            AbstractPlayer player = AbstractDungeon.player;
            if (player != null && player.isDead && ragdollManager.hasActivePlayerRagdoll(player)) {
                ragdollManager.updatePlayerRagdollLogic(player);
            }
        }
    }

    @SpirePatch(clz = AbstractPlayer.class, method = "playDeathAnimation")
    public static class PlayerDeathAnimationPatch {
        @SpirePrefixPatch
        public static SpireReturn<Void> prefix(AbstractPlayer __instance) {
            if (isPlayerBlacklisted(__instance)) {
                return SpireReturn.Continue();
            }
            return ragdollManager.handlePlayerDeathAnimation(__instance);
        }
    }

    // Add this patch to your RagdollPatches class

    @SpirePatch(clz = AbstractCreature.class, method = "healthBarUpdatedEvent")
    public static class HealthBarUpdatePatch {
        @SpirePostfixPatch
        public static void postfix(AbstractCreature __instance) {
            if (__instance instanceof AbstractPlayer) {
                AbstractPlayer player = (AbstractPlayer) __instance;

                if (player.currentHealth <= 0 && !isPlayerBlacklisted(player)) {
                    AbstractDungeon.effectsQueue.add(new PlayerRagdollVFX(player, ragdollManager));
                    AbstractDungeon.actionManager.addToTop(new PlayerRagdollWaitAction(player, ragdollManager));
                }
            }
        }
    }
}
