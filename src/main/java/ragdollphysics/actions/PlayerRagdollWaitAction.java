
package ragdollphysics.actions;

import basemod.BaseMod;
import com.badlogic.gdx.Gdx;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
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

    public PlayerRagdollWaitAction(AbstractPlayer player, RagdollManager ragdollManager) {
        this.player = player;
        this.ragdollManager = ragdollManager;
        this.actionType = ActionType.SPECIAL;
    }

    @Override
    public void update() {
        totalTime += Gdx.graphics.getDeltaTime();
        settleCheckTimer += Gdx.graphics.getDeltaTime();

        // Check if ragdoll has settled
        if (settleCheckTimer >= SETTLE_CHECK_INTERVAL) {
            settleCheckTimer = 0f;

            if (ragdollManager.isPlayerRagdollSettled(player)) {
                finishAction(); // Call finishAction instead of just setting isDone
                return;
            }
        }

        // Failsafe timeout
        if (totalTime >= RAGDOLL_TIMEOUT) {
            finishAction(); // Call finishAction instead of just setting isDone
            return;
        }
    }

    /**
     * Finishes the action and handles cleanup/restoration
     */
    private void finishAction() {
        // If player was revived during ragdoll (Lizard Tail, Fairy Potion, etc.)
        if (!player.isDead) {
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
            BaseMod.logger.info("[PlayerRagdollWaitAction] Could not reset skeleton: " + e.getMessage());
        }
    }
}