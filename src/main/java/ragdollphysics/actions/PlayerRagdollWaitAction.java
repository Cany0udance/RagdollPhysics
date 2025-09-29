
package ragdollphysics.actions;

import basemod.BaseMod;
import com.badlogic.gdx.Gdx;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import ragdollphysics.ragdollutil.MultiBodyRagdoll;
import ragdollphysics.ragdollutil.RagdollManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class PlayerRagdollWaitAction extends AbstractGameAction {
    private static final float RAGDOLL_TIMEOUT = 5.0f;
    private static final float SETTLE_CHECK_INTERVAL = 0.1f;

    private final AbstractPlayer player;
    private final RagdollManager ragdollManager;
    private float settleCheckTimer = 0f;
    private float totalTime = 0f;

    // Store original player position for image-based restoration
    private final float originalDrawX;
    private final float originalDrawY;

    public PlayerRagdollWaitAction(AbstractPlayer player, RagdollManager ragdollManager) {
        this.player = player;
        this.ragdollManager = ragdollManager;
        this.actionType = ActionType.SPECIAL;

        // Store original position for image-based players
        this.originalDrawX = player.drawX;
        this.originalDrawY = player.drawY;
    }

    @Override
    public void update() {
        totalTime += Gdx.graphics.getDeltaTime();
        settleCheckTimer += Gdx.graphics.getDeltaTime();

        // Check if ragdoll has settled
        if (settleCheckTimer >= SETTLE_CHECK_INTERVAL) {
            settleCheckTimer = 0f;
            if (ragdollManager.isPlayerRagdollSettled(player)) {
                finishAction();
                return;
            }
        }

        // Failsafe timeout
        if (totalTime >= RAGDOLL_TIMEOUT) {
            finishAction();
            return;
        }
    }

    /**
     * Finishes the action and handles cleanup/restoration
     */
    private void finishAction() {
        // If player was revived during ragdoll (Lizard Tail, Fairy Potion, etc.)
        if (!player.isDead) {
            // Check ragdoll type BEFORE cleanup
            MultiBodyRagdoll ragdoll = ragdollManager.getPlayerRagdoll(player);
            boolean wasImageBased = (ragdoll != null && ragdoll.isImageBased());

            // Remove the player ragdoll
            ragdollManager.cleanupPlayerRagdoll(player);

            // Restore player visual state based on ragdoll type
            restorePlayerState(wasImageBased);
        }
        this.isDone = true;
    }

    /**
     * Restores the player's visual state after ragdoll cleanup
     * Now handles both skeleton-based and image-based players
     */
    private void restorePlayerState(boolean wasImageBased) {
        if (wasImageBased) {
            // Image-based restoration: reset position and opacity
            player.drawX = originalDrawX;
            player.drawY = originalDrawY;
        } else {
            // Original skeleton-based restoration (unchanged)
            try {
                Field skeletonField = AbstractCreature.class.getDeclaredField("skeleton");
                skeletonField.setAccessible(true);
                Object skeleton = skeletonField.get(player);

                if (skeleton != null) {
                    Method setToSetupPose = skeleton.getClass().getMethod("setToSetupPose");
                    setToSetupPose.invoke(skeleton);
                    Method updateWorldTransform = skeleton.getClass().getMethod("updateWorldTransform");
                    updateWorldTransform.invoke(skeleton);
                }
            } catch (Exception e) {

            }
        }
    }
}