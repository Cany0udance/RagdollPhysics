package ragdollphysics.actions;

import basemod.BaseMod;
import com.badlogic.gdx.Gdx;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import ragdollphysics.ragdollutil.MultiBodyRagdoll;
import ragdollphysics.ragdollutil.RagdollManager;

/**
 * Action that pauses the game flow while the player ragdoll settles after death.
 * This action is queued immediately when the player's health hits 0.
 */
public class PlayerDeathRagdollAction extends AbstractGameAction {

    private static final float RAGDOLL_TIMEOUT = 5.0f; // Failsafe timeout
    private static final float SETTLE_CHECK_INTERVAL = 0.1f; // How often to check if settled

    private final AbstractPlayer player;
    private final RagdollManager ragdollManager;
    private boolean ragdollCreated = false;
    private float settleCheckTimer = 0f;
    private float totalTime = 0f;

    public PlayerDeathRagdollAction(AbstractPlayer player, RagdollManager ragdollManager) {
        this.player = player;
        this.ragdollManager = ragdollManager;
        this.actionType = ActionType.SPECIAL;
        this.duration = 0.1f; // Initial duration, will be extended as needed
    }

    @Override
    public void update() {
        totalTime += Gdx.graphics.getDeltaTime();
        settleCheckTimer += Gdx.graphics.getDeltaTime();

        // Create ragdoll on first update
        if (!ragdollCreated) {
            BaseMod.logger.info("[PlayerDeathRagdollAction] Creating player ragdoll");

            // Try to create the ragdoll immediately
            if (ragdollManager.createPlayerRagdollImmediately(player)) {
                ragdollCreated = true;
                BaseMod.logger.info("[PlayerDeathRagdollAction] Player ragdoll created successfully");
            } else {
                BaseMod.logger.info("[PlayerDeathRagdollAction] Failed to create player ragdoll, ending action");
                this.isDone = true;
                return;
            }
        }

        // Check if ragdoll has settled (periodically to avoid constant checks)
        if (settleCheckTimer >= SETTLE_CHECK_INTERVAL) {
            settleCheckTimer = 0f;

            if (ragdollManager.isPlayerRagdollSettled(player)) {
                BaseMod.logger.info("[PlayerDeathRagdollAction] Player ragdoll has settled after " +
                        String.format("%.2f", totalTime) + "s, ending action");
                finishAction();
                return;
            }
        }

        // Failsafe timeout
        if (totalTime >= RAGDOLL_TIMEOUT) {
            BaseMod.logger.info("[PlayerDeathRagdollAction] Ragdoll timeout reached, ending action");
            finishAction();
            return;
        }

        // Keep the action alive by not setting isDone
        // The ragdoll will continue updating through the existing patches
    }

    /**
     * Finishes the action and handles cleanup/restoration
     */
    private void finishAction() {
        BaseMod.logger.info("[PlayerDeathRagdollAction] Finishing action (player.isDead=" + player.isDead + ")");

        // If player was revived during ragdoll (Lizard Tail, Fairy Potion, etc.)
        if (!player.isDead) {
            BaseMod.logger.info("[PlayerDeathRagdollAction] Player was revived, cleaning up ragdoll and restoring state");

            // Remove the player ragdoll
            ragdollManager.cleanupPlayerRagdoll(player);

            // Restore player visual state if needed
            restorePlayerState();
        }

        this.isDone = true;
    }

    /**
     * Restores the player's visual state after ragdoll cleanup
     */
    private void restorePlayerState() {
        // Reset any visual transformations that might have been applied by the ragdoll
        // This ensures the player looks normal when they're revived
        BaseMod.logger.info("[PlayerDeathRagdollAction] Restoring player visual state");

        // You might need additional restoration logic here depending on what
        // your ragdoll system modifies on the player
        // For example, resetting position, rotation, scale, etc.
    }
}