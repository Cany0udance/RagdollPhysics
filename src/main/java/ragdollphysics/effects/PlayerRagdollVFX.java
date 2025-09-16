package ragdollphysics.effects;

import basemod.BaseMod;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import ragdollphysics.ragdollutil.RagdollManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class PlayerRagdollVFX extends AbstractGameEffect {
    private static final float RAGDOLL_TIMEOUT = 5.0f;
    private static final float SETTLE_CHECK_INTERVAL = 0.1f;

    private final AbstractPlayer player;
    private final RagdollManager ragdollManager;
    private boolean ragdollCreated = false;
    private float settleCheckTimer = 0f;
    private float totalTime = 0f;

    public PlayerRagdollVFX(AbstractPlayer player, RagdollManager ragdollManager) {
        this.player = player;
        this.ragdollManager = ragdollManager;
        this.duration = RAGDOLL_TIMEOUT;
        BaseMod.logger.info("[PlayerRagdollVFX] VFX created");
    }

    @Override
    public void update() {
        if (!ragdollCreated) {
            BaseMod.logger.info("[PlayerRagdollVFX] Creating player ragdoll immediately");
            ragdollManager.createPlayerRagdollImmediately(player);
            ragdollCreated = true;
        }

        totalTime += Gdx.graphics.getDeltaTime();
        settleCheckTimer += Gdx.graphics.getDeltaTime();

        // Only check if ragdoll has settled - no revival checking
        if (settleCheckTimer >= SETTLE_CHECK_INTERVAL) {
            settleCheckTimer = 0f;

            if (ragdollManager.isPlayerRagdollSettled(player)) {
                BaseMod.logger.info("[PlayerRagdollVFX] Ragdoll settled after " +
                        String.format("%.2f", totalTime) + "s, ending VFX");
                this.isDone = true;
                return;
            }
        }

        // Failsafe timeout
        if (totalTime >= RAGDOLL_TIMEOUT) {
            BaseMod.logger.info("[PlayerRagdollVFX] Timeout reached, ending VFX");
            this.isDone = true;
        }
    }

    @Override
    public void render(SpriteBatch sb) {}

    @Override
    public void dispose() {}
}