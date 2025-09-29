package ragdollphysics.ragdollutil;

import basemod.BaseMod;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

public class MonsterSpecialHandler {
    private final String handlerId;

    public MonsterSpecialHandler() {
        this.handlerId = "SpecialHandler_" + System.currentTimeMillis() % 10000;
      //  BaseMod.logger.info("[" + handlerId + "] MonsterSpecialHandler initialized");
    }

    /**
     * Main entry point - handles special components for any monster that needs it
     *
     * @param monster The monster to handle special components for
     */
    public void handleSpecialComponents(AbstractMonster monster) {
        String monsterClass = monster.getClass().getSimpleName();

        if (monsterClass.equals("Hexaghost")) {
            handleHexaghostSpecialComponents(monster);
        }
        // Add other special cases here as needed
    }

    /**
     * Handle Hexaghost's special orb and body components
     */
    private void handleHexaghostSpecialComponents(AbstractMonster monster) {
        try {
            // Access Hexaghost's orbs through reflection
            Field orbsField = monster.getClass().getDeclaredField("orbs");
            orbsField.setAccessible(true);
            ArrayList<?> orbs = (ArrayList<?>) orbsField.get(monster);

            // Deactivate and hide the orbs
            if (orbs != null) {
                for (Object orb : orbs) {
                    try {
                        // Try deactivate first
                        Method deactivateMethod = orb.getClass().getMethod("deactivate");
                        deactivateMethod.invoke(orb);

                        // Then hide
                        Method hideMethod = orb.getClass().getMethod("hide");
                        hideMethod.invoke(orb);

                    } catch (Exception e) {
                    }
                }
            }

        } catch (Exception e) {
            BaseMod.logger.info("[" + handlerId + "] Could not handle Hexaghost special components: " + e.getMessage());
            // Not critical - ragdoll can still proceed
        }
    }
}