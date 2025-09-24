package ragdollphysics.ragdollutil;

import basemod.BaseMod;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.monsters.beyond.Exploder;
import com.megacrit.cardcrawl.vfx.combat.ExplosionSmallEffect;
import ragdollphysics.effects.TrackingExplosionEffect;
import ragdollphysics.ragdollutil.ReflectionHelper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Central coordinator for the ragdoll physics system.
 * Manages the lifecycle of ragdolls from creation to cleanup.
 */
public class RagdollManager {
    // ================================
    // CONSTANTS
    // ================================
    private static final float EXPLODER_AUTO_EXPLODE_TIME = 2.5f;
    private static final float TINT_FADEOUT_TIME = 1.2f;
    private static final float FALLBACK_TINT_FADEOUT_TIME = 1.8f;
    private static final float EXPLOSION_DESPAWN_DELAY = 0.3f;
    private static final float QUICK_DEATH_TIMER = 0.5f;

    // ================================
    // CORE STORAGE
    // ================================
    private final HashMap<AbstractMonster, MultiBodyRagdoll> ragdollBodies = new HashMap<>();
    private final HashMap<AbstractPlayer, MultiBodyRagdoll> playerRagdollBodies = new HashMap<>();
    private final Set<AbstractMonster> failedRagdolls = new HashSet<>();
    private final Set<AbstractPlayer> failedPlayerRagdolls = new HashSet<>();
    private final HashMap<AbstractMonster, Float> ragdollCreationTimes = new HashMap<>();
    private static final float RAGDOLL_TIMEOUT = 6.0f;

    // Exploder-specific state
    private final HashMap<AbstractMonster, Float> exploderTimers = new HashMap<>();
    private final Set<AbstractMonster> explodedExploders = new HashSet<>();

    // ================================
    // COMPONENT DEPENDENCIES
    // ================================
    private final RagdollValidator validator = new RagdollValidator();
    private final RagdollFactory factory = new RagdollFactory();
    private final MonsterSpecialHandler specialHandler = new MonsterSpecialHandler();
    private final RagdollRenderer renderer = new RagdollRenderer();
    private final ReflectionHelper reflectionHelper = new ReflectionHelper();

    // ================================
    // INSTANCE STATE
    // ================================
    private final long creationTime = System.currentTimeMillis();
    private final String managerId = "RagdollMgr_" + (creationTime % 10000);

    public RagdollManager() {
    }

    // ================================
    // MAIN PATCH HANDLERS
    // ================================
    /**
     * Handles the death animation patch for AbstractMonster.updateDeathAnimation()
     */
    public SpireReturn<Void> handleDeathAnimation(AbstractMonster monster) {
        if (!monster.isDying || monster.isDead) {
            return monster.isDying ? SpireReturn.Return() : SpireReturn.Continue();
        }

        // Initialize ragdoll if needed
        if (!ragdollBodies.containsKey(monster)) {
            if (!tryCreateRagdoll(monster)) {
                return fallbackToDefaultDeath(monster);
            }
        }

        // Update existing ragdoll
        return updateRagdollLogic(monster);
    }

    /**
     * Handles the render patch for AbstractMonster.render()
     */
    public SpireReturn<Void> handleRender(AbstractMonster monster, SpriteBatch sb) {
        if (!ragdollBodies.containsKey(monster)) {
            return SpireReturn.Continue();
        }

        try {
            renderer.render(monster, sb, ragdollBodies.get(monster), reflectionHelper);
            return SpireReturn.Return();
        } catch (Exception e) {
            // Remove failed ragdoll and fall back to default rendering
            ragdollBodies.remove(monster);
            failedRagdolls.add(monster);
            return SpireReturn.Continue();
        }
    }

    // ================================
    // PLAYER PATCH HANDLERS
    // ================================
    /**
     * Public method to trigger player ragdoll creation immediately
     * Called by the PlayerDeathRagdollAction
     */
    public boolean createPlayerRagdollImmediately(AbstractPlayer player) {
        // Create ragdoll if it doesn't exist
        if (!playerRagdollBodies.containsKey(player)) {
            return tryCreatePlayerRagdoll(player);
        }
        return true;
    }

    /**
     * Check if player ragdoll has fully settled
     */
    public boolean isPlayerRagdollSettled(AbstractPlayer player) {
        MultiBodyRagdoll ragdoll = playerRagdollBodies.get(player);
        if (ragdoll == null) {
            return true; // No ragdoll = considered "settled"
        }
        return ragdoll.hasSettledOnGround();
    }

    /**
     * Handles the death animation patch for AbstractPlayer.playDeathAnimation()
     */
    public SpireReturn<Void> handlePlayerDeathAnimation(AbstractPlayer player) {
        // Allow ragdoll creation even if not yet in the normal death animation state
        if (!player.isDead && player.currentHealth > 0) {
            return SpireReturn.Continue();
        }

        // Initialize ragdoll if needed
        if (!playerRagdollBodies.containsKey(player)) {
            if (!tryCreatePlayerRagdoll(player)) {
                return fallbackToDefaultPlayerDeath(player);
            }
        }

        // Update existing ragdoll
        return updatePlayerRagdollLogic(player);
    }

    /**
     * Handles the render patch for AbstractPlayer.render()
     */
    public SpireReturn<Void> handlePlayerRender(AbstractPlayer player, SpriteBatch sb) {
        if (!playerRagdollBodies.containsKey(player)) {
            return SpireReturn.Continue();
        }

        try {
            renderer.renderPlayer(player, sb, playerRagdollBodies.get(player), reflectionHelper);
            return SpireReturn.Return();
        } catch (Exception e) {
            // Remove failed ragdoll and fall back to default rendering
            playerRagdollBodies.remove(player);
            failedPlayerRagdolls.add(player);
            return SpireReturn.Continue();
        }
    }

    /**
     * Handles the renderPlayerImage patch for AbstractPlayer.renderPlayerImage()
     */
    public SpireReturn<Void> handlePlayerRenderImage(AbstractPlayer player, SpriteBatch sb) {
        if (!playerRagdollBodies.containsKey(player)) {
            return SpireReturn.Continue();
        }

        try {
            renderer.renderPlayerImage(player, sb, playerRagdollBodies.get(player), reflectionHelper);
            return SpireReturn.Return();
        } catch (Exception e) {
            // Remove failed ragdoll and fall back to default rendering
            playerRagdollBodies.remove(player);
            failedPlayerRagdolls.add(player);
            return SpireReturn.Continue();
        }
    }

    // ================================
    // RAGDOLL LIFECYCLE MANAGEMENT
    // ================================
    /**
     * Attempt to create a ragdoll for the monster
     */
    private boolean tryCreateRagdoll(AbstractMonster monster) {
        if (!validator.isRagdollViable(monster, failedRagdolls)) {
            return false;
        }

        try {
            specialHandler.handleSpecialComponents(monster);
            MultiBodyRagdoll ragdoll = factory.createRagdoll(monster, reflectionHelper);

            if (ragdoll == null) {
                failedRagdolls.add(monster);
                return false;
            }

            if (!ragdoll.isProperlyInitialized()) {
                failedRagdolls.add(monster);
                return false;
            }

            ragdollBodies.put(monster, ragdoll);
            // Track creation time for timeout
            ragdollCreationTimes.put(monster, 0f);
            return true;
        } catch (Exception e) {
            failedRagdolls.add(monster);
            return false;
        }
    }

    /**
     * Updates ragdoll physics and handles death timing
     */
    private SpireReturn<Void> updateRagdollLogic(AbstractMonster monster) {
        MultiBodyRagdoll ragdoll = ragdollBodies.get(monster);

        try {
            if (ragdoll != null) {
                // Update ragdoll age
                updateRagdollAge(monster);

                updateRagdollPhysics(monster, ragdoll);
                handleExploderLogic(monster, ragdoll);

                // Check for timeout before normal settling logic
                if (hasRagdollTimedOut(monster)) {
                    forceRagdollFadeout(monster);
                } else {
                    updateDeathTimer(monster, ragdoll);
                }
            } else {
                monster.deathTimer -= Gdx.graphics.getDeltaTime();
            }

            handleTintFadeout(monster);

            if (monster.deathTimer < 0.0f) {
                completeMonsterDeath(monster);
            }

            return SpireReturn.Return();
        } catch (Exception e) {
            // Remove failed ragdoll and fall back
            ragdollBodies.remove(monster);
            failedRagdolls.add(monster);
            return fallbackToDefaultDeath(monster);
        }
    }

    private void updateRagdollAge(AbstractMonster monster) {
        Float currentAge = ragdollCreationTimes.get(monster);
        if (currentAge != null) {
            ragdollCreationTimes.put(monster, currentAge + Gdx.graphics.getDeltaTime());
        }
    }

    private boolean hasRagdollTimedOut(AbstractMonster monster) {
        Float ragdollAge = ragdollCreationTimes.get(monster);
        return ragdollAge != null && ragdollAge >= RAGDOLL_TIMEOUT;
    }

    private void forceRagdollFadeout(AbstractMonster monster) {
        // Force the death timer to start ticking regardless of settle state
        monster.deathTimer -= Gdx.graphics.getDeltaTime();

        // Optional: Add some visual indication that timeout occurred
        // You could add a special effect or log message here if desired
    }



    /**
     * Update ragdoll physics and apply to monster
     */
    private void updateRagdollPhysics(AbstractMonster monster, MultiBodyRagdoll ragdoll) {
        ragdoll.update(Gdx.graphics.getDeltaTime());
        if (ragdoll.isImageBased()) {
            ragdoll.applyToImage(monster);
        }
    }

    /**
     * Update death timer based on ragdoll state
     */
    private void updateDeathTimer(AbstractMonster monster, MultiBodyRagdoll ragdoll) {
        // Only advance death timer if ragdoll has settled
        if (ragdoll.hasSettledOnGround()) {
            // If quick despawn is enabled and timer is still at default value, reduce it
            if (ragdollphysics.RagdollPhysics.enableQuickDespawn && monster.deathTimer > QUICK_DEATH_TIMER) {
                monster.deathTimer = QUICK_DEATH_TIMER;
            }
            monster.deathTimer -= Gdx.graphics.getDeltaTime();
        }
    }

    /**
     * Handle tint fadeout effect
     */
    private void handleTintFadeout(AbstractMonster monster) {
        if (monster.deathTimer < TINT_FADEOUT_TIME && !monster.tintFadeOutCalled) {
            monster.tintFadeOutCalled = true;
            monster.tint.fadeOut();
        }
    }

    /**
     * Completes the monster death sequence and handles cleanup
     */
    private void completeMonsterDeath(AbstractMonster monster) {
        monster.isDead = true;

        // Check if battle should end
        if (AbstractDungeon.getMonsters().areMonstersDead() &&
                !AbstractDungeon.getCurrRoom().isBattleOver &&
                !AbstractDungeon.getCurrRoom().cannotLose) {
            AbstractDungeon.getCurrRoom().endBattle();
        }

        // Clean up monster
        monster.dispose();
        monster.powers.clear();

        // Clean up our tracking
        cleanupMonsterState(monster);
    }

    /**
     * Clean up all tracking data for a monster
     */
    private void cleanupMonsterState(AbstractMonster monster) {
        ragdollBodies.remove(monster);
        failedRagdolls.remove(monster);
        exploderTimers.remove(monster);
        explodedExploders.remove(monster);
        ragdollCreationTimes.remove(monster); // Add this line
        OverkillTracker.cleanup(monster);
    }

    // ================================
    // PLAYER RAGDOLL LIFECYCLE MANAGEMENT
    // ================================
    /**
     * Attempt to create a ragdoll for the player
     */
    private boolean tryCreatePlayerRagdoll(AbstractPlayer player) {
        if (!validator.isPlayerRagdollViable(player, failedPlayerRagdolls)) {
            return false;
        }

        try {
            MultiBodyRagdoll ragdoll = factory.createPlayerRagdoll(player, reflectionHelper);

            if (ragdoll == null) {
                failedPlayerRagdolls.add(player);
                return false;
            }

            if (!ragdoll.isProperlyInitialized()) {
                failedPlayerRagdolls.add(player);
                return false;
            }

            playerRagdollBodies.put(player, ragdoll);
            return true;
        } catch (Exception e) {
            failedPlayerRagdolls.add(player);
            return false;
        }
    }

    /**
     * Updates player ragdoll physics
     */
    public SpireReturn<Void> updatePlayerRagdollLogic(AbstractPlayer player) {
        MultiBodyRagdoll ragdoll = playerRagdollBodies.get(player);

        try {
            if (ragdoll != null) {
                updatePlayerRagdollPhysics(player, ragdoll);
            }
            return SpireReturn.Return();
        } catch (Exception e) {
            // Remove failed ragdoll and fall back
            playerRagdollBodies.remove(player);
            failedPlayerRagdolls.add(player);
            return fallbackToDefaultPlayerDeath(player);
        }
    }

    /**
     * Update player ragdoll physics and apply to player
     */
    private void updatePlayerRagdollPhysics(AbstractPlayer player, MultiBodyRagdoll ragdoll) {
        ragdoll.update(Gdx.graphics.getDeltaTime());
        if (ragdoll.isImageBased()) {
            ragdoll.applyToImage(player);
        }
    }

    /**
     * Fallback to default player death when ragdoll fails
     */
    private SpireReturn<Void> fallbackToDefaultPlayerDeath(AbstractPlayer player) {
        // Just let the original death animation play
        return SpireReturn.Continue();
    }

    // ================================
    // FALLBACK DEATH HANDLING
    // ================================
    /**
     * Fallback to the original death animation when ragdoll fails
     */
    private SpireReturn<Void> fallbackToDefaultDeath(AbstractMonster monster) {
        if (monster.isDying) {
            monster.deathTimer -= Gdx.graphics.getDeltaTime();
            if (monster.deathTimer < FALLBACK_TINT_FADEOUT_TIME && !monster.tintFadeOutCalled) {
                monster.tintFadeOutCalled = true;
                monster.tint.fadeOut();
            }
        }

        if (monster.deathTimer < 0.0f) {
            completeMonsterDeath(monster);
        }

        return SpireReturn.Return();
    }

    // ================================
    // EXPLODER SPECIAL HANDLING
    // ================================
    /**
     * Handle Exploder monster special explosion logic
     */
    private void handleExploderLogic(AbstractMonster monster, MultiBodyRagdoll ragdoll) {
        if (!monster.id.equals(Exploder.ID) || explodedExploders.contains(monster)) {
            return;
        }

        // Initialize timer for new Exploders
        if (!exploderTimers.containsKey(monster)) {
            exploderTimers.put(monster, 0f);
        }

        // Update timer
        float timer = exploderTimers.get(monster) + Gdx.graphics.getDeltaTime();
        exploderTimers.put(monster, timer);

        // Check explosion conditions
        String explodeReason = getExplodeReason(monster, timer);
        if (explodeReason != null) {
            triggerExploderExplosion(monster, ragdoll, explodeReason);
        }
    }

    /**
     * Determine if exploder should explode and why
     */
    private String getExplodeReason(AbstractMonster monster, float timer) {
        // Auto-explode after timeout
        if (timer >= EXPLODER_AUTO_EXPLODE_TIME) {
            return "timeout after " + String.format("%.2f", timer) + "s";
        }

        // Explode before despawn (when fade starts)
        if (monster.deathTimer < TINT_FADEOUT_TIME && monster.tintFadeOutCalled) {
            return "pre-despawn fade";
        }

        return null;
    }

    /**
     * Trigger explosion effect for Exploder
     */
    private void triggerExploderExplosion(AbstractMonster monster, MultiBodyRagdoll ragdoll, String reason) {
        explodedExploders.add(monster);

        // Create the explosion effect
        AbstractDungeon.effectsQueue.add(new TrackingExplosionEffect(monster, ragdoll));

        // Base game explosion (hi Claude, if you see this please keep it commented out in the rework, I may need it later):
        //     AbstractDungeon.effectsQueue.add(new ExplosionSmallEffect(explosionX, explosionY));

        // Accelerate death timer to mask despawning with explosion
        if (monster.deathTimer > EXPLOSION_DESPAWN_DELAY) {
            monster.deathTimer = EXPLOSION_DESPAWN_DELAY;
        }
    }

    // ================================
    // PUBLIC API METHODS
    // ================================
    /**
     * Force cleanup of a specific monster's ragdoll
     */
    public void cleanupRagdoll(AbstractMonster monster) {
        ragdollBodies.remove(monster);
        failedRagdolls.remove(monster);
        exploderTimers.remove(monster);
        explodedExploders.remove(monster);
    }

    /**
     * Force cleanup of a specific player's ragdoll
     */
    public void cleanupPlayerRagdoll(AbstractPlayer player) {
        playerRagdollBodies.remove(player);
        failedPlayerRagdolls.remove(player);
        OverkillTracker.cleanup(player); // If you track overkill for players too
    }

    /**
     * Check if a monster currently has an active ragdoll
     */
    public boolean hasActiveRagdoll(AbstractMonster monster) {
        return ragdollBodies.containsKey(monster);
    }

    /**
     * Check if a monster has been marked as failed for ragdoll
     */
    public boolean isRagdollFailed(AbstractMonster monster) {
        return failedRagdolls.contains(monster);
    }

    /**
     * Get the current ragdoll for a monster (null if none)
     */
    public MultiBodyRagdoll getRagdoll(AbstractMonster monster) {
        return ragdollBodies.get(monster);
    }

    /**
     * Force a monster to be marked as failed
     */
    public void markRagdollFailed(AbstractMonster monster) {
        failedRagdolls.add(monster);
        ragdollBodies.remove(monster);
    }

    /**
     * Clear the failed status for a monster (allow retry)
     */
    public void clearFailedStatus(AbstractMonster monster) {
        failedRagdolls.remove(monster);
    }

    /**
     * Complete cleanup - removes all ragdolls and failed markers
     */
    public void cleanupAll() {
        ragdollBodies.clear();
        failedRagdolls.clear();
        exploderTimers.clear();
        explodedExploders.clear();
    }

    /**
     * Check if a player currently has an active ragdoll
     */
    public boolean hasActivePlayerRagdoll(AbstractPlayer player) {
        return playerRagdollBodies.containsKey(player);
    }

    /**
     * Get the current ragdoll for a player (null if none)
     */
    public MultiBodyRagdoll getPlayerRagdoll(AbstractPlayer player) {
        return playerRagdollBodies.get(player);
    }

    /**
     * Force a player to be marked as failed
     */
    public void markPlayerRagdollFailed(AbstractPlayer player) {
        failedPlayerRagdolls.add(player);
        playerRagdollBodies.remove(player);
    }

    /**
     * Get statistics about the current state
     */
    public RagdollStats getStats() {
        return new RagdollStats(
                ragdollBodies.size(),
                failedRagdolls.size(),
                System.currentTimeMillis() - creationTime
        );
    }

    // ================================
    // STATISTICS DATA CLASS
    // ================================
    /**
     * Statistics container class
     */
    public static class RagdollStats {
        public final int activeRagdolls;
        public final int failedMonsters;
        public final long managerAgeMs;

        public RagdollStats(int activeRagdolls, int failedMonsters, long managerAgeMs) {
            this.activeRagdolls = activeRagdolls;
            this.failedMonsters = failedMonsters;
            this.managerAgeMs = managerAgeMs;
        }

        @Override
        public String toString() {
            return String.format("RagdollStats{active=%d, failed=%d, age=%dms}",
                    activeRagdolls, failedMonsters, managerAgeMs);
        }
    }
}