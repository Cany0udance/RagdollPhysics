package ragdollphysics.ragdollutil;

import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.monsters.exordium.Cultist;

import java.util.HashMap;

public class CenterOfMassConfig {
    private static final HashMap<String, CenterOffset> centerOffsets = new HashMap<>();

    static {
        // Define center of mass offsets for different monsters
        // Positive Y moves the physics center UP from the original position
        // Positive X moves it RIGHT

        // Cultist - move physics center up to roughly chest/torso level
        centerOffsets.put(Cultist.ID, new CenterOffset(0f, -80f * Settings.scale));

        // Add more as needed:
        // centerOffsets.put("JawWorm", new CenterOffset(0f, 60f * Settings.scale));
        // centerOffsets.put("Looter", new CenterOffset(0f, 70f * Settings.scale));
        // centerOffsets.put("FungiBeast", new CenterOffset(0f, 90f * Settings.scale));
    }

    public static CenterOffset getCenterOffset(String monsterClassName) {
        return centerOffsets.getOrDefault(monsterClassName, new CenterOffset(0f, 0f));
    }

    public static class CenterOffset {
        public final float x;
        public final float y;

        public CenterOffset(float x, float y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public String toString() {
            return "CenterOffset(" + String.format("%.1f", x) + ", " + String.format("%.1f", y) + ")";
        }
    }
}

