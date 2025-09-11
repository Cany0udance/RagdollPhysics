package ragdollphysics.ragdollutil;

import com.megacrit.cardcrawl.monsters.beyond.SnakeDagger;
import com.megacrit.cardcrawl.monsters.city.SnakePlant;

import java.util.HashMap;

/**
 * Configuration for parts that should fade out during ragdoll physics
 */
public class FadeablePartsConfig {

    // Map of monster ID to list of slot/attachment names that should fade
    private static final HashMap<String, String[]> FADEABLE_PARTS = new HashMap<>();

    static {
        // Define which parts fade for each enemy
        FADEABLE_PARTS.put(SnakeDagger.ID, new String[]{"glow"});
        FADEABLE_PARTS.put(SnakePlant.ID, new String[]{"leafShadow"});
        // Add more enemies as needed
    }

    /**
     * Get the list of part names that should fade for a given monster
     */
    public static String[] getFadeableParts(String monsterClassName) {
        return FADEABLE_PARTS.get(monsterClassName);
    }

    /**
     * Check if a specific part should fade for a given monster
     */
    public static boolean shouldFadePart(String monsterClassName, String partName) {
        String[] fadeableParts = FADEABLE_PARTS.get(monsterClassName);
        if (fadeableParts == null) return false;

        for (String fadeablePart : fadeableParts) {
            if (partName.toLowerCase().contains(fadeablePart.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}