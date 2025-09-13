package ragdollphysics.ragdollutil;

import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.monsters.exordium.TheGuardian;

import java.util.HashMap;
import java.util.Map;

public class AttachmentScaleConfig {
    // Define specific scale overrides for monsters that need them
    private static final Map<String, Float> MONSTER_SCALE_OVERRIDES = new HashMap<>();

    static {
        // Guardian appears to be designed to render at half scale
        MONSTER_SCALE_OVERRIDES.put(TheGuardian.ID, 0.5f);

        // Add other monsters as needed
        // MONSTER_SCALE_OVERRIDES.put(SomeOtherBoss.ID, 0.75f);
    }

    /**
     * Get the scale multiplier for a specific monster's attachments
     * @param monsterClassName The monster class ID
     * @return Scale multiplier (1.0f = normal size, 0.5f = half size, etc.)
     */
    public static float getAttachmentScaleMultiplier(String monsterClassName) {
        return MONSTER_SCALE_OVERRIDES.getOrDefault(monsterClassName, 1.0f);
    }

    /**
     * Check if a monster has custom scale settings
     */
    public static boolean hasCustomScale(String monsterClassName) {
        return MONSTER_SCALE_OVERRIDES.containsKey(monsterClassName);
    }

    /**
     * Calculate the final render dimensions for an attachment
     */
    public static float[] calculateRenderDimensions(String monsterClassName, float baseWidth, float baseHeight,
                                                    float originalScaleX, float originalScaleY) {
        float scaleMultiplier = getAttachmentScaleMultiplier(monsterClassName);

        float finalWidth = baseWidth * Math.abs(originalScaleX) * scaleMultiplier * Settings.scale;
        float finalHeight = baseHeight * Math.abs(originalScaleY) * scaleMultiplier * Settings.scale;

        return new float[]{finalWidth, finalHeight};
    }
}
