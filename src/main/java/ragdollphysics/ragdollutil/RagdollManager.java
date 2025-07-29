package ragdollphysics.ragdollutil;

import basemod.BaseMod;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import ragdollphysics.ragdollutil.ReflectionHelper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Central coordinator for the ragdoll physics system.
 * Manages the lifecycle of ragdolls from creation to cleanup.
 */
public class RagdollManager {
    // Core storage
    private final HashMap<AbstractMonster, MultiBodyRagdoll> ragdollBodies = new HashMap<>();
    private final Set<AbstractMonster> failedRagdolls = new HashSet<>();

    // Component dependencies
    private final RagdollValidator validator = new RagdollValidator();
    private final RagdollFactory factory = new RagdollFactory();
    private final MonsterSpecialHandler specialHandler = new MonsterSpecialHandler();
    private final RagdollRenderer renderer = new RagdollRenderer();
    private final ReflectionHelper reflectionHelper = new ReflectionHelper();

    // Logging and debugging
    private final long creationTime = System.currentTimeMillis();
    private final String managerId = "RagdollMgr_" + (creationTime % 10000);

    public RagdollManager() {
        BaseMod.logger.info("[" + managerId + "] RagdollManager initialized");
    }

    /**
     * Handles the death animation patch for AbstractMonster.updateDeathAnimation()
     */
    public SpireReturn<Void> handleDeathAnimation(AbstractMonster monster) {
        if (!monster.isDying) {
            return SpireReturn.Continue();
        }

        if (monster.isDead) {
            return SpireReturn.Return();
        }

        // Check if we need to initialize a new ragdoll
        if (!ragdollBodies.containsKey(monster)) {
            if (!validator.isRagdollViable(monster, failedRagdolls)) {
                return fallbackToDefaultDeath(monster);
            }

            try {
                // Handle any special monster components before ragdoll creation
                specialHandler.handleSpecialComponents(monster);

                // Create the ragdoll
                MultiBodyRagdoll ragdoll = factory.createRagdoll(monster, reflectionHelper);

                // Verify ragdoll was created successfully
                if (ragdoll == null || !ragdoll.isProperlyInitialized()) {
                    BaseMod.logger.info("[" + managerId + "] Ragdoll creation failed for "
                            + monster.getClass().getSimpleName() + " - null or uninitialized");
                    failedRagdolls.add(monster);
                    return fallbackToDefaultDeath(monster);
                }

                ragdollBodies.put(monster, ragdoll);
                BaseMod.logger.info("[" + managerId + "] Successfully created ragdoll for "
                        + monster.getClass().getSimpleName());

            } catch (Exception e) {
                BaseMod.logger.error("[" + managerId + "] Ragdoll initialization failed for "
                        + monster.getClass().getSimpleName() + ": " + e.getMessage());
                failedRagdolls.add(monster);
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
        if (ragdollBodies.containsKey(monster)) {
            try {
                renderer.render(monster, sb, ragdollBodies.get(monster), reflectionHelper);
                return SpireReturn.Return();
            } catch (Exception e) {
                BaseMod.logger.error("[" + managerId + "] Ragdoll rendering failed for "
                        + monster.getClass().getSimpleName() + ": " + e.getMessage());

                // Remove failed ragdoll and fall back to default rendering
                ragdollBodies.remove(monster);
                failedRagdolls.add(monster);
                return SpireReturn.Continue();
            }
        }
        return SpireReturn.Continue();
    }

    /**
     * Updates ragdoll physics and handles death timing
     */
    private SpireReturn<Void> updateRagdollLogic(AbstractMonster monster) {
        MultiBodyRagdoll ragdoll = ragdollBodies.get(monster);

        if (ragdoll != null) {
            try {
                ragdoll.update(Gdx.graphics.getDeltaTime());

                // Only advance death timer if ragdoll has settled
                if (ragdoll.hasSettledOnGround()) {
                    monster.deathTimer -= Gdx.graphics.getDeltaTime();
                }

            } catch (Exception e) {
                BaseMod.logger.error("[" + managerId + "] Ragdoll update failed for "
                        + monster.getClass().getSimpleName() + ": " + e.getMessage());

                // Remove failed ragdoll and continue with default behavior
                ragdollBodies.remove(monster);
                failedRagdolls.add(monster);
                return fallbackToDefaultDeath(monster);
            }
        } else {
            // No ragdoll - use normal death timer
            monster.deathTimer -= Gdx.graphics.getDeltaTime();
        }

        // Handle tint fade-out
        if (monster.deathTimer < 1.2f && !monster.tintFadeOutCalled) {
            monster.tintFadeOutCalled = true;
            monster.tint.fadeOut();
        }

        // Check if death sequence is complete
        if (monster.deathTimer < 0.0f) {
            completeMonsterDeath(monster);
        }

        return SpireReturn.Return();
    }

    /**
     * Fallback to the original death animation when ragdoll fails
     */
    private SpireReturn<Void> fallbackToDefaultDeath(AbstractMonster monster) {
        BaseMod.logger.info("[" + managerId + "] Using fallback death for "
                + monster.getClass().getSimpleName());

        // Replicate the original updateDeathAnimation logic exactly
        if (monster.isDying) {
            monster.deathTimer -= Gdx.graphics.getDeltaTime();
            if (monster.deathTimer < 1.8f && !monster.tintFadeOutCalled) {
                monster.tintFadeOutCalled = true;
                monster.tint.fadeOut();
            }
        }

        if (monster.deathTimer < 0.0f) {
            completeMonsterDeath(monster);
        }

        return SpireReturn.Return();
    }

    /**
     * Completes the monster death sequence and handles cleanup
     */
    private void completeMonsterDeath(AbstractMonster monster) {
        monster.isDead = true;

        // Check if battle should end
        if (AbstractDungeon.getMonsters().areMonstersDead()
                && !AbstractDungeon.getCurrRoom().isBattleOver
                && !AbstractDungeon.getCurrRoom().cannotLose) {
            AbstractDungeon.getCurrRoom().endBattle();
        }

        // Clean up monster
        monster.dispose();
        monster.powers.clear();

        // Clean up our tracking
        MultiBodyRagdoll removedRagdoll = ragdollBodies.remove(monster);
        if (removedRagdoll != null) {
            BaseMod.logger.info("[" + managerId + "] Cleaned up ragdoll for "
                    + monster.getClass().getSimpleName());
        }

        // Clean up failed tracking (allow future attempts if monster is somehow revived)
        failedRagdolls.remove(monster);
    }

    /**
     * Force cleanup of a specific monster's ragdoll (for external use)
     */
    public void cleanupRagdoll(AbstractMonster monster) {
        MultiBodyRagdoll removed = ragdollBodies.remove(monster);
        if (removed != null) {
            BaseMod.logger.info("[" + managerId + "] Force cleanup ragdoll for "
                    + monster.getClass().getSimpleName());
        }
        failedRagdolls.remove(monster);
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
     * Force a monster to be marked as failed (for debugging/override)
     */
    public void markRagdollFailed(AbstractMonster monster) {
        failedRagdolls.add(monster);
        ragdollBodies.remove(monster);
        BaseMod.logger.info("[" + managerId + "] Force marked ragdoll failed for "
                + monster.getClass().getSimpleName());
    }

    /**
     * Clear the failed status for a monster (allow retry)
     */
    public void clearFailedStatus(AbstractMonster monster) {
        boolean wasRemoved = failedRagdolls.remove(monster);
        if (wasRemoved) {
            BaseMod.logger.info("[" + managerId + "] Cleared failed status for "
                    + monster.getClass().getSimpleName());
        }
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

    /**
     * Complete cleanup - removes all ragdolls and failed markers
     * Useful for combat end or scene transitions
     */
    public void cleanupAll() {
        int ragdollCount = ragdollBodies.size();
        int failedCount = failedRagdolls.size();

        ragdollBodies.clear();
        failedRagdolls.clear();

        BaseMod.logger.info("[" + managerId + "] Complete cleanup - removed "
                + ragdollCount + " ragdolls and " + failedCount + " failed markers");
    }

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