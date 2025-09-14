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
    // ================================
    // CORE STORAGE
    // ================================
    private final HashMap<AbstractMonster, MultiBodyRagdoll> ragdollBodies = new HashMap<>();
    private final HashMap<AbstractPlayer, MultiBodyRagdoll> playerRagdollBodies = new HashMap<>();
    private final Set<AbstractMonster> failedRagdolls = new HashSet<>();
    private final Set<AbstractPlayer> failedPlayerRagdolls = new HashSet<>();
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
        BaseMod.logger.info("[RagdollManager] Initializing RagdollManager with ID: " + managerId);
    }

    // ================================
    // MAIN PATCH HANDLERS
    // ================================
    /**
     * Handles the death animation patch for AbstractMonster.updateDeathAnimation()
     */
    public SpireReturn<Void> handleDeathAnimation(AbstractMonster monster) {
        BaseMod.logger.info("[RagdollManager] handleDeathAnimation called for monster: " + monster.name +
                " (isDying=" + monster.isDying + ", isDead=" + monster.isDead + ")");

        if (!monster.isDying || monster.isDead) {
            BaseMod.logger.info("[RagdollManager] Monster not in dying state or already dead, " +
                    (monster.isDying ? "returning early" : "continuing"));
            return monster.isDying ? SpireReturn.Return() : SpireReturn.Continue();
        }

        // Initialize ragdoll if needed
        if (!ragdollBodies.containsKey(monster)) {
            BaseMod.logger.info("[RagdollManager] No existing ragdoll for " + monster.name + ", attempting to create");
            if (!tryCreateRagdoll(monster)) {
                BaseMod.logger.info("[RagdollManager] Failed to create ragdoll for " + monster.name + ", falling back to default death");
                return fallbackToDefaultDeath(monster);
            }
            BaseMod.logger.info("[RagdollManager] Successfully created ragdoll for " + monster.name);
        }

        // Update existing ragdoll
        BaseMod.logger.info("[RagdollManager] Updating ragdoll logic for " + monster.name);
        return updateRagdollLogic(monster);
    }

    /**
     * Handles the render patch for AbstractMonster.render()
     */
    public SpireReturn<Void> handleRender(AbstractMonster monster, SpriteBatch sb) {
        if (!ragdollBodies.containsKey(monster)) {
            return SpireReturn.Continue();
        }

        BaseMod.logger.info("[RagdollManager] Rendering ragdoll for monster: " + monster.name);
        try {
            renderer.render(monster, sb, ragdollBodies.get(monster), reflectionHelper);
            return SpireReturn.Return();
        } catch (Exception e) {
            BaseMod.logger.info("[RagdollManager] ERROR: Render failed for " + monster.name + ": " + e.getMessage());
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
     * Handles the death animation patch for AbstractPlayer.playDeathAnimation()
     */
    public SpireReturn<Void> handlePlayerDeathAnimation(AbstractPlayer player) {
        BaseMod.logger.info("[RagdollManager] handlePlayerDeathAnimation called for player (isDead=" + player.isDead + ")");

        if (!player.isDead) {
            BaseMod.logger.info("[RagdollManager] Player not dead, continuing with normal behavior");
            return SpireReturn.Continue();
        }

        // Initialize ragdoll if needed
        if (!playerRagdollBodies.containsKey(player)) {
            BaseMod.logger.info("[RagdollManager] No existing player ragdoll, attempting to create");
            if (!tryCreatePlayerRagdoll(player)) {
                BaseMod.logger.info("[RagdollManager] Failed to create player ragdoll, falling back to default death");
                return fallbackToDefaultPlayerDeath(player);
            }
            BaseMod.logger.info("[RagdollManager] Successfully created player ragdoll");
        }

        // Update existing ragdoll
        BaseMod.logger.info("[RagdollManager] Updating player ragdoll logic");
        return updatePlayerRagdollLogic(player);
    }

    /**
     * Handles the render patch for AbstractPlayer.render()
     */
    public SpireReturn<Void> handlePlayerRender(AbstractPlayer player, SpriteBatch sb) {
        if (!playerRagdollBodies.containsKey(player)) {
            return SpireReturn.Continue();
        }

        BaseMod.logger.info("[RagdollManager] Rendering player ragdoll");
        try {
            renderer.renderPlayer(player, sb, playerRagdollBodies.get(player), reflectionHelper);
            return SpireReturn.Return();
        } catch (Exception e) {
            BaseMod.logger.info("[RagdollManager] ERROR: Player render failed: " + e.getMessage());
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
        BaseMod.logger.info("[RagdollManager] handlePlayerRenderImage called (isDead=" + player.isDead +
                ", hasRagdoll=" + playerRagdollBodies.containsKey(player) + ")");

        if (!playerRagdollBodies.containsKey(player)) {
            BaseMod.logger.info("[RagdollManager] No player ragdoll for renderPlayerImage, continuing with normal render");
            return SpireReturn.Continue();
        }

        BaseMod.logger.info("[RagdollManager] Player has ragdoll for renderPlayerImage, attempting to render");
        try {
            renderer.renderPlayerImage(player, sb, playerRagdollBodies.get(player), reflectionHelper);
            BaseMod.logger.info("[RagdollManager] Successfully rendered player ragdoll image, returning");
            return SpireReturn.Return();
        } catch (Exception e) {
            BaseMod.logger.info("[RagdollManager] ERROR: Player image render failed: " + e.getMessage());
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
        BaseMod.logger.info("[RagdollManager] tryCreateRagdoll for " + monster.name + " (ID: " + monster.id + ")");

        if (!validator.isRagdollViable(monster, failedRagdolls)) {
            BaseMod.logger.info("[RagdollManager] Ragdoll not viable for " + monster.name);
            return false;
        }

        BaseMod.logger.info("[RagdollManager] Ragdoll viable, proceeding with creation");
        try {
            BaseMod.logger.info("[RagdollManager] Handling special components for " + monster.name);
            specialHandler.handleSpecialComponents(monster);

            BaseMod.logger.info("[RagdollManager] Creating ragdoll via factory");
            MultiBodyRagdoll ragdoll = factory.createRagdoll(monster, reflectionHelper);

            if (ragdoll == null) {
                BaseMod.logger.info("[RagdollManager] Factory returned null ragdoll for " + monster.name);
                failedRagdolls.add(monster);
                return false;
            }

            if (!ragdoll.isProperlyInitialized()) {
                BaseMod.logger.info("[RagdollManager] Ragdoll not properly initialized for " + monster.name);
                failedRagdolls.add(monster);
                return false;
            }

            ragdollBodies.put(monster, ragdoll);
            BaseMod.logger.info("[RagdollManager] Successfully created and stored ragdoll for " + monster.name);
            return true;
        } catch (Exception e) {
            BaseMod.logger.info("[RagdollManager] ERROR: Exception during ragdoll creation for " + monster.name + ": " + e.getMessage());
            failedRagdolls.add(monster);
            return false;
        }
    }

    /**
     * Updates ragdoll physics and handles death timing
     */
    private SpireReturn<Void> updateRagdollLogic(AbstractMonster monster) {
        MultiBodyRagdoll ragdoll = ragdollBodies.get(monster);
        BaseMod.logger.info("[RagdollManager] updateRagdollLogic for " + monster.name +
                " (ragdoll=" + (ragdoll != null ? "present" : "null") +
                ", deathTimer=" + String.format("%.2f", monster.deathTimer) + ")");

        try {
            if (ragdoll != null) {
                updateRagdollPhysics(monster, ragdoll);
                handleExploderLogic(monster, ragdoll);
                updateDeathTimer(monster, ragdoll);
            } else {
                BaseMod.logger.info("[RagdollManager] No ragdoll present, using standard death timer");
                monster.deathTimer -= Gdx.graphics.getDeltaTime();
            }

            handleTintFadeout(monster);

            if (monster.deathTimer < 0.0f) {
                BaseMod.logger.info("[RagdollManager] Death timer expired for " + monster.name + ", completing death");
                completeMonsterDeath(monster);
            }

            return SpireReturn.Return();
        } catch (Exception e) {
            BaseMod.logger.info("[RagdollManager] ERROR: Exception in updateRagdollLogic for " + monster.name + ": " + e.getMessage());
            // Remove failed ragdoll and fall back
            ragdollBodies.remove(monster);
            failedRagdolls.add(monster);
            return fallbackToDefaultDeath(monster);
        }
    }

    /**
     * Update ragdoll physics and apply to monster
     */
    private void updateRagdollPhysics(AbstractMonster monster, MultiBodyRagdoll ragdoll) {
        BaseMod.logger.info("[RagdollManager] Updating physics for " + monster.name);
        ragdoll.update(Gdx.graphics.getDeltaTime());
        if (ragdoll.isImageBased()) {
            BaseMod.logger.info("[RagdollManager] Applying image-based ragdoll to " + monster.name);
            ragdoll.applyToImage(monster);
        }
    }

    /**
     * Update death timer based on ragdoll state
     */
    private void updateDeathTimer(AbstractMonster monster, MultiBodyRagdoll ragdoll) {
        // Only advance death timer if ragdoll has settled
        if (ragdoll.hasSettledOnGround()) {
            BaseMod.logger.info("[RagdollManager] Ragdoll settled, advancing death timer for " + monster.name);
            monster.deathTimer -= Gdx.graphics.getDeltaTime();
        } else {
            BaseMod.logger.info("[RagdollManager] Ragdoll still active, holding death timer for " + monster.name);
        }
    }

    /**
     * Handle tint fadeout effect
     */
    private void handleTintFadeout(AbstractMonster monster) {
        if (monster.deathTimer < TINT_FADEOUT_TIME && !monster.tintFadeOutCalled) {
            BaseMod.logger.info("[RagdollManager] Triggering tint fadeout for " + monster.name);
            monster.tintFadeOutCalled = true;
            monster.tint.fadeOut();
        }
    }

    // ================================
    // PLAYER RAGDOLL LIFECYCLE MANAGEMENT
    // ================================
    /**
     * Attempt to create a ragdoll for the player
     */
    private boolean tryCreatePlayerRagdoll(AbstractPlayer player) {
        BaseMod.logger.info("[RagdollManager] tryCreatePlayerRagdoll");

        if (!validator.isPlayerRagdollViable(player, failedPlayerRagdolls)) {
            BaseMod.logger.info("[RagdollManager] Player ragdoll not viable");
            return false;
        }

        BaseMod.logger.info("[RagdollManager] Player ragdoll viable, proceeding with creation");
        try {
            BaseMod.logger.info("[RagdollManager] Creating player ragdoll via factory");
            MultiBodyRagdoll ragdoll = factory.createPlayerRagdoll(player, reflectionHelper);

            if (ragdoll == null) {
                BaseMod.logger.info("[RagdollManager] Factory returned null player ragdoll");
                failedPlayerRagdolls.add(player);
                return false;
            }

            if (!ragdoll.isProperlyInitialized()) {
                BaseMod.logger.info("[RagdollManager] Player ragdoll not properly initialized");
                failedPlayerRagdolls.add(player);
                return false;
            }

            playerRagdollBodies.put(player, ragdoll);
            BaseMod.logger.info("[RagdollManager] Successfully created and stored player ragdoll");
            return true;
        } catch (Exception e) {
            BaseMod.logger.info("[RagdollManager] ERROR: Exception during player ragdoll creation: " + e.getMessage());
            failedPlayerRagdolls.add(player);
            return false;
        }
    }

    /**
     * Updates player ragdoll physics
     */
    public SpireReturn<Void> updatePlayerRagdollLogic(AbstractPlayer player) {
        MultiBodyRagdoll ragdoll = playerRagdollBodies.get(player);
        BaseMod.logger.info("[RagdollManager] updatePlayerRagdollLogic (ragdoll=" + (ragdoll != null ? "present" : "null") + ")");

        try {
            if (ragdoll != null) {
                updatePlayerRagdollPhysics(player, ragdoll);
            }
            return SpireReturn.Return();
        } catch (Exception e) {
            BaseMod.logger.info("[RagdollManager] ERROR: Exception in updatePlayerRagdollLogic: " + e.getMessage());
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
        BaseMod.logger.info("[RagdollManager] Updating player ragdoll physics");
        ragdoll.update(Gdx.graphics.getDeltaTime());
        if (ragdoll.isImageBased()) {
            BaseMod.logger.info("[RagdollManager] Applying image-based player ragdoll");
            ragdoll.applyToImage(player);
        }
    }

    /**
     * Fallback to default player death when ragdoll fails
     */
    private SpireReturn<Void> fallbackToDefaultPlayerDeath(AbstractPlayer player) {
        BaseMod.logger.info("[RagdollManager] Falling back to default player death animation");
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
        BaseMod.logger.info("[RagdollManager] fallbackToDefaultDeath for " + monster.name +
                " (isDying=" + monster.isDying + ", deathTimer=" + String.format("%.2f", monster.deathTimer) + ")");

        if (monster.isDying) {
            monster.deathTimer -= Gdx.graphics.getDeltaTime();
            if (monster.deathTimer < FALLBACK_TINT_FADEOUT_TIME && !monster.tintFadeOutCalled) {
                BaseMod.logger.info("[RagdollManager] Triggering fallback tint fadeout for " + monster.name);
                monster.tintFadeOutCalled = true;
                monster.tint.fadeOut();
            }
        }

        if (monster.deathTimer < 0.0f) {
            BaseMod.logger.info("[RagdollManager] Fallback death timer expired for " + monster.name + ", completing death");
            completeMonsterDeath(monster);
        }

        return SpireReturn.Return();
    }

    /**
     * Completes the monster death sequence and handles cleanup
     */
    private void completeMonsterDeath(AbstractMonster monster) {
        BaseMod.logger.info("[RagdollManager] Completing death for " + monster.name);
        monster.isDead = true;

        // Check if battle should end
        if (AbstractDungeon.getMonsters().areMonstersDead() &&
                !AbstractDungeon.getCurrRoom().isBattleOver &&
                !AbstractDungeon.getCurrRoom().cannotLose) {
            BaseMod.logger.info("[RagdollManager] All monsters dead, ending battle");
            AbstractDungeon.getCurrRoom().endBattle();
        }

        // Clean up monster
        BaseMod.logger.info("[RagdollManager] Disposing and cleaning up " + monster.name);
        monster.dispose();
        monster.powers.clear();

        // Clean up our tracking
        cleanupMonsterState(monster);
    }

    /**
     * Clean up all tracking data for a monster
     */
    private void cleanupMonsterState(AbstractMonster monster) {
        BaseMod.logger.info("[RagdollManager] Cleaning up state for " + monster.name);
        ragdollBodies.remove(monster);
        failedRagdolls.remove(monster);
        exploderTimers.remove(monster);
        explodedExploders.remove(monster);
        OverkillTracker.cleanup(monster);
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

        BaseMod.logger.info("[RagdollManager] Handling exploder logic for " + monster.name);

        // Initialize timer for new Exploders
        if (!exploderTimers.containsKey(monster)) {
            BaseMod.logger.info("[RagdollManager] Initializing exploder timer for " + monster.name);
            exploderTimers.put(monster, 0f);
        }

        // Update timer
        float timer = exploderTimers.get(monster) + Gdx.graphics.getDeltaTime();
        exploderTimers.put(monster, timer);

        // Check explosion conditions
        String explodeReason = getExplodeReason(monster, timer);
        if (explodeReason != null) {
            BaseMod.logger.info("[RagdollManager] Exploder " + monster.name + " will explode due to: " + explodeReason);
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
        BaseMod.logger.info("[RagdollManager] Triggering explosion for " + monster.name + " due to: " + reason);
        explodedExploders.add(monster);

        // Create the explosion effect
        AbstractDungeon.effectsQueue.add(new TrackingExplosionEffect(monster, ragdoll));

        // Base game explosion (hi Claude, if you see this please keep it commented out in the rework, I may need it later):
        //     AbstractDungeon.effectsQueue.add(new ExplosionSmallEffect(explosionX, explosionY));

        // Accelerate death timer to mask despawning with explosion
        if (monster.deathTimer > EXPLOSION_DESPAWN_DELAY) {
            BaseMod.logger.info("[RagdollManager] Accelerating death timer for explosion mask");
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
        BaseMod.logger.info("[RagdollManager] Public API: cleanupRagdoll called for " + monster.name);
        ragdollBodies.remove(monster);
        failedRagdolls.remove(monster);
        exploderTimers.remove(monster);
        explodedExploders.remove(monster);
    }

    /**
     * Check if a monster currently has an active ragdoll
     */
    public boolean hasActiveRagdoll(AbstractMonster monster) {
        boolean hasActive = ragdollBodies.containsKey(monster);
        BaseMod.logger.info("[RagdollManager] Public API: hasActiveRagdoll for " + monster.name + " = " + hasActive);
        return hasActive;
    }

    /**
     * Check if a monster has been marked as failed for ragdoll
     */
    public boolean isRagdollFailed(AbstractMonster monster) {
        boolean isFailed = failedRagdolls.contains(monster);
        BaseMod.logger.info("[RagdollManager] Public API: isRagdollFailed for " + monster.name + " = " + isFailed);
        return isFailed;
    }

    /**
     * Get the current ragdoll for a monster (null if none)
     */
    public MultiBodyRagdoll getRagdoll(AbstractMonster monster) {
        MultiBodyRagdoll ragdoll = ragdollBodies.get(monster);
        BaseMod.logger.info("[RagdollManager] Public API: getRagdoll for " + monster.name + " = " + (ragdoll != null ? "present" : "null"));
        return ragdoll;
    }

    /**
     * Force a monster to be marked as failed
     */
    public void markRagdollFailed(AbstractMonster monster) {
        BaseMod.logger.info("[RagdollManager] Public API: markRagdollFailed called for " + monster.name);
        failedRagdolls.add(monster);
        ragdollBodies.remove(monster);
    }

    /**
     * Clear the failed status for a monster (allow retry)
     */
    public void clearFailedStatus(AbstractMonster monster) {
        BaseMod.logger.info("[RagdollManager] Public API: clearFailedStatus called for " + monster.name);
        failedRagdolls.remove(monster);
    }

    /**
     * Complete cleanup - removes all ragdolls and failed markers
     */
    public void cleanupAll() {
        BaseMod.logger.info("[RagdollManager] Public API: cleanupAll called - clearing " + ragdollBodies.size() + " ragdolls and " + failedRagdolls.size() + " failed entries");
        ragdollBodies.clear();
        failedRagdolls.clear();
        exploderTimers.clear();
        explodedExploders.clear();
    }

    /**
     * Check if a player currently has an active ragdoll
     */
    public boolean hasActivePlayerRagdoll(AbstractPlayer player) {
        boolean hasActive = playerRagdollBodies.containsKey(player);
        BaseMod.logger.info("[RagdollManager] Public API: hasActivePlayerRagdoll = " + hasActive);
        return hasActive;
    }

    /**
     * Get the current ragdoll for a player (null if none)
     */
    public MultiBodyRagdoll getPlayerRagdoll(AbstractPlayer player) {
        MultiBodyRagdoll ragdoll = playerRagdollBodies.get(player);
        BaseMod.logger.info("[RagdollManager] Public API: getPlayerRagdoll = " + (ragdoll != null ? "present" : "null"));
        return ragdoll;
    }

    /**
     * Force a player to be marked as failed
     */
    public void markPlayerRagdollFailed(AbstractPlayer player) {
        BaseMod.logger.info("[RagdollManager] Public API: markPlayerRagdollFailed called");
        failedPlayerRagdolls.add(player);
        playerRagdollBodies.remove(player);
    }

    /**
     * Get statistics about the current state
     */
    public RagdollStats getStats() {
        RagdollStats stats = new RagdollStats(
                ragdollBodies.size(),
                failedRagdolls.size(),
                System.currentTimeMillis() - creationTime
        );
        BaseMod.logger.info("[RagdollManager] Public API: getStats = " + stats.toString());
        return stats;
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