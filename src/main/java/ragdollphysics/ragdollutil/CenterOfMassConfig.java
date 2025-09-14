package ragdollphysics.ragdollutil;

import basemod.BaseMod;
import com.esotericsoftware.spine.Bone;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.Slot;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.monsters.beyond.*;
import com.megacrit.cardcrawl.monsters.city.*;
import com.megacrit.cardcrawl.monsters.ending.CorruptHeart;
import com.megacrit.cardcrawl.monsters.exordium.*;

import java.util.Arrays;
import java.util.HashMap;

/**
 * Configuration for calculating center of mass offsets for monster ragdolls.
 * Maps monsters to their primary body attachments and provides fallback calculations.
 */
public class CenterOfMassConfig {

    // ================================
    // MONSTER BODY ATTACHMENT MAPPING
    // ================================

    /** Maps monster IDs to their primary body attachment names */
    public static final HashMap<String, String[]> customBodyAttachments = new HashMap<>();

    static {
        customBodyAttachments.put(Cultist.ID, new String[]{"body"});
        customBodyAttachments.put(LouseNormal.ID, new String[]{"seg3"});
        customBodyAttachments.put(LouseDefensive.ID, new String[]{"seg3"});
        customBodyAttachments.put(SlaverBlue.ID, new String[]{"cloakblue"});
        customBodyAttachments.put(SlaverRed.ID, new String[]{"cloackred"});
        customBodyAttachments.put(Byrd.ID, new String[]{"flying/torso", "grounded/torso"});
        customBodyAttachments.put(BanditBear.ID, new String[]{"Torso"});
        customBodyAttachments.put(BanditLeader.ID, new String[]{"tunic"});
        customBodyAttachments.put(BanditPointy.ID, new String[]{"shirt"});
        customBodyAttachments.put(Chosen.ID, new String[]{"skin"});
        customBodyAttachments.put(Centurion.ID, new String[]{"chestArmor"});
        customBodyAttachments.put(Healer.ID, new String[]{"coat"});
        customBodyAttachments.put(Exploder.ID, new String[]{"inside"});
        customBodyAttachments.put(SphericGuardian.ID, new String[]{"orb_main"});
        customBodyAttachments.put(ShelledParasite.ID, new String[]{"upperBody"});
        customBodyAttachments.put(Lagavulin.ID, new String[]{"Shell1", "Shell2"});
        customBodyAttachments.put(Taskmaster.ID, new String[]{"middle"});
        customBodyAttachments.put(Nemesis.ID, new String[]{"cloak"});
        customBodyAttachments.put(WrithingMass.ID, new String[]{"leftMass"});
        customBodyAttachments.put(SpireGrowth.ID, new String[]{"tentacle_center"});
        customBodyAttachments.put(Champ.ID, new String[]{"lowerChest"});
        customBodyAttachments.put(BronzeAutomaton.ID, new String[]{"chest"});
        customBodyAttachments.put(GiantHead.ID, new String[]{"head"});
        customBodyAttachments.put(TimeEater.ID, new String[]{"cloak_fg"});
        customBodyAttachments.put(CorruptHeart.ID, new String[]{"cloak_fg"});
    }

    // ================================
    // CENTER OFFSET CALCULATION
    // ================================

    /**
     * Calculate center of mass offset based on skeleton body bone position
     */
    public static CenterOffset calculateCenterOffset(Skeleton skeleton, String monsterClassName) {
        if (skeleton == null) {
            return new CenterOffset(0f, 0f);
        }

        // Find the primary body bone for this monster
        Bone bodyBone = findBodyBone(skeleton, monsterClassName);
        if (bodyBone != null) {
            // Position physics center at the body bone's world position
            float bodyWorldX = bodyBone.getWorldX() * Settings.scale;
            float bodyWorldY = bodyBone.getWorldY() * Settings.scale;
            return new CenterOffset(bodyWorldX, bodyWorldY);
        } else {
            // Use static fallback offsets if no body bone found
            return getFallbackOffset(monsterClassName);
        }
    }

    /**
     * Find the primary body bone for a monster
     */
    private static Bone findBodyBone(Skeleton skeleton, String monsterClassName) {
        if (skeleton == null) return null;

        // Try monster-specific body attachments
        String[] customAttachments = customBodyAttachments.get(monsterClassName);
        if (customAttachments != null) {
            for (String attachmentName : customAttachments) {
                // First try finding bone by attachment name
                Bone boneFromAttachment = findBoneByAttachmentName(skeleton, attachmentName);
                if (boneFromAttachment != null) {
                    return boneFromAttachment;
                }

                // Fallback to direct bone name search
                Bone directBone = skeleton.findBone(attachmentName);
                if (directBone != null) {
                    return directBone;
                }
            }
        }

        // Final fallback to generic body bone names
        String[] defaultBodyBoneNames = {"body", "torso", "chest", "spine", "hip", "pelvis", "trunk"};
        for (String boneName : defaultBodyBoneNames) {
            Bone bone = skeleton.findBone(boneName);
            if (bone != null) {
                return bone;
            }
        }

        return null;
    }

    /**
     * Find bone by searching for which bone has the specified attachment
     */
    private static Bone findBoneByAttachmentName(Skeleton skeleton, String attachmentName) {
        for (Slot slot : skeleton.getSlots()) {
            if (slot.getAttachment() != null) {
                String slotAttachmentName = slot.getAttachment().getName();
                if (attachmentName.equals(slotAttachmentName)) {
                    return slot.getBone();
                }
            }
        }
        return null;
    }

    /**
     * Get fallback static offsets for monsters without clear body bones
     */
    private static CenterOffset getFallbackOffset(String monsterClassName) {
        switch (monsterClassName) {
            case TheGuardian.ID:
                return new CenterOffset(0f, -60f * Settings.scale);
            default:
                return new CenterOffset(0f, 60f * Settings.scale);
        }
    }

    // ================================
    // PUBLIC UTILITY METHODS
    // ================================

    /**
     * Get the body attachment names for a monster (for debugging)
     */
    public static String[] getBodyAttachmentNames(String monsterClassName) {
        return customBodyAttachments.getOrDefault(monsterClassName, new String[]{"default search"});
    }

    // ================================
    // CENTER OFFSET DATA CLASS
    // ================================

    /**
     * Represents a 2D offset for center of mass positioning
     */
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