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
        BaseMod.logger.info("[" + handlerId + "] MonsterSpecialHandler initialized");
    }

    /**
     * Main entry point - handles special components for any monster that needs it
     *
     * @param monster The monster to handle special components for
     */
    public void handleSpecialComponents(AbstractMonster monster) {
        String monsterClass = monster.getClass().getSimpleName();
        BaseMod.logger.info("[" + handlerId + "] Checking for special handling for monster: " + monsterClass);

        if (monsterClass.equals("Hexaghost")) {
            BaseMod.logger.info("[" + handlerId + "] Applying Hexaghost special handling");
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

                        BaseMod.logger.info("[" + handlerId + "] Deactivated and hidden Hexaghost orb");
                    } catch (Exception e) {
                        BaseMod.logger.info("[" + handlerId + "] Could not deactivate/hide Hexaghost orb: " + e.getMessage());
                    }
                }
            }

            // Let the body fade naturally - don't dispose it immediately
            BaseMod.logger.info("[" + handlerId + "] Hexaghost orbs deactivated, body will fade naturally");

        } catch (Exception e) {
            BaseMod.logger.info("[" + handlerId + "] Could not handle Hexaghost special components: " + e.getMessage());
            // Not critical - ragdoll can still proceed
        }
    }
}