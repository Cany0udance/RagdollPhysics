package ragdollphysics.effects;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.DarkSmokePuffEffect;
import com.megacrit.cardcrawl.vfx.combat.SmokingEmberEffect;
import ragdollphysics.ragdollutil.MultiBodyRagdoll;

// OPTION 1: Create a tracking explosion effect that follows the Exploder
public class TrackingExplosionEffect extends AbstractGameEffect {
    private AbstractMonster target;
    private MultiBodyRagdoll ragdoll;
    private float timer = 0f;
    private static final float EXPLOSION_DURATION = 0.5f;
    private boolean hasTriggeredInitialExplosion = false;

    public TrackingExplosionEffect(AbstractMonster target, MultiBodyRagdoll ragdoll) {
        this.target = target;
        this.ragdoll = ragdoll;
    }

    @Override
    public void update() {
        timer += Gdx.graphics.getDeltaTime();

        // Trigger initial explosion
        if (!hasTriggeredInitialExplosion) {
            triggerExplosionAtCurrentPosition();
            hasTriggeredInitialExplosion = true;
        }

        // Continue creating smaller effects that follow
        if (timer < EXPLOSION_DURATION && ragdoll != null) {
            // Add trailing smoke/ember effects at current position
            if (MathUtils.random() < 0.3f) { // 30% chance per frame
                float x = ragdoll.getCenterX();
                float y = ragdoll.getCenterY();
                AbstractDungeon.effectsQueue.add(new SmokingEmberEffect(
                        x + MathUtils.random(-30f, 30f) * Settings.scale,
                        y + MathUtils.random(-30f, 30f) * Settings.scale
                ));
            }
        }

        if (timer >= EXPLOSION_DURATION) {
            this.isDone = true;
        }
    }

    private void triggerExplosionAtCurrentPosition() {
        float x = ragdoll.getCenterX();
        float y = ragdoll.getCenterY();

        AbstractDungeon.effectsQueue.add(new DarkSmokePuffEffect(x, y));

        for(int i = 0; i < 12; ++i) {
            AbstractDungeon.effectsQueue.add(new SmokingEmberEffect(
                    x + MathUtils.random(-50.0f, 50.0f) * Settings.scale,
                    y + MathUtils.random(-50.0f, 50.0f) * Settings.scale
            ));
        }

        CardCrawlGame.sound.playA("ATTACK_FIRE", MathUtils.random(-0.2f, -0.1f));
    }

    @Override
    public void render(SpriteBatch sb) {}

    @Override
    public void dispose() {}
}