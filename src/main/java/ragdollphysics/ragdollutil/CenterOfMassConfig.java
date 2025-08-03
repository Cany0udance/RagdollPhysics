package ragdollphysics.ragdollutil;

import basemod.BaseMod;
import com.esotericsoftware.spine.Bone;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.Slot;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.monsters.exordium.*;

import java.util.HashMap;

public class CenterOfMassConfig {
    // Control logging for center of mass calculations
    private static boolean printCenterOfMassLogs = true; // Set to true to enable logs

    // Map of monster class names to their specific body attachment names
    public static final HashMap<String, String> customBodyAttachments = new HashMap<>();
    static {
        // Define custom body attachment names for specific monsters
        // The system will find the bone associated with these attachments
        customBodyAttachments.put(Cultist.ID, "body");
        customBodyAttachments.put(SlaverBlue.ID, "cloakblue");
      //  customBodyAttachments.put(LouseNormal.ID, "seg3");
      //  customBodyAttachments.put(LouseDefensive.ID, "seg3");
    }

    // Calculate center of mass offset based on actual skeleton body bone position
    public static CenterOffset calculateCenterOffset(Skeleton skeleton, String monsterClassName) {
        if (printCenterOfMassLogs) {
            BaseMod.logger.info("[CenterOfMass] === CALCULATE CENTER OFFSET CALLED ===");
            BaseMod.logger.info("[CenterOfMass] Monster: '" + monsterClassName + "'");
            BaseMod.logger.info("[CenterOfMass] Skeleton: " + (skeleton != null ? "NOT NULL" : "NULL"));
        }

        if (skeleton == null) {
            if (printCenterOfMassLogs) {
                BaseMod.logger.warn("[CenterOfMass] Skeleton is NULL, returning zero offset");
            }
            return new CenterOffset(0f, 0f);
        }

        if (printCenterOfMassLogs) {
            BaseMod.logger.info("[CenterOfMass] Skeleton has " + skeleton.getBones().size + " bones");
        }

        // Find the body bone (or similar)
        Bone bodyBone = findBodyBone(skeleton, monsterClassName);
        if (bodyBone != null) {
            // The offset should position the physics center at the body bone's world position
            float bodyWorldX = bodyBone.getWorldX() * Settings.scale;
            float bodyWorldY = bodyBone.getWorldY() * Settings.scale;
            if (printCenterOfMassLogs) {
                BaseMod.logger.info("[CenterOfMass] " + monsterClassName + " body bone '"
                        + bodyBone.getData().getName() + "' at world pos: ("
                        + String.format("%.1f", bodyWorldX) + ", " + String.format("%.1f", bodyWorldY) + ")");
            }
            return new CenterOffset(bodyWorldX, bodyWorldY);
        } else {
            // Fallback to static offsets if no body bone found
            if (printCenterOfMassLogs) {
                BaseMod.logger.warn("[CenterOfMass] No body bone found for " + monsterClassName + ", using fallback offset");
            }
            return getFallbackOffset(monsterClassName);
        }
    }

    private static Bone findBodyBone(Skeleton skeleton, String monsterClassName) {
        if (skeleton == null) return null;

        // First, try to find bone by attachment name
        String customBodyAttachment = customBodyAttachments.get(monsterClassName);
        if (customBodyAttachment != null) {
            if (printCenterOfMassLogs) {
                BaseMod.logger.info("[CenterOfMass] Looking for bone associated with attachment: '" + customBodyAttachment + "'");
            }

            Bone boneFromAttachment = findBoneByAttachmentName(skeleton, customBodyAttachment);
            if (boneFromAttachment != null) {
                if (printCenterOfMassLogs) {
                    BaseMod.logger.info("[CenterOfMass] Found bone '" + boneFromAttachment.getData().getName()
                            + "' associated with attachment '" + customBodyAttachment + "' for " + monsterClassName);
                }
                return boneFromAttachment;
            } else {
                if (printCenterOfMassLogs) {
                    BaseMod.logger.warn("[CenterOfMass] No bone found for attachment '" + customBodyAttachment
                            + "' for " + monsterClassName + ", trying direct bone name match");
                }

                // Fallback: try the attachment name as a direct bone name
                Bone directBone = skeleton.findBone(customBodyAttachment);
                if (directBone != null) {
                    if (printCenterOfMassLogs) {
                        BaseMod.logger.info("[CenterOfMass] Found bone directly named '" + customBodyAttachment + "'");
                    }
                    return directBone;
                }
            }
        }

        // Fallback to generic body bone names
        if (printCenterOfMassLogs) {
            BaseMod.logger.info("[CenterOfMass] Trying default body bone names for " + monsterClassName);
        }
        String[] defaultBodyBoneNames = {"body", "torso", "chest", "spine", "hip", "pelvis", "trunk"};
        for (String boneName : defaultBodyBoneNames) {
            Bone bone = skeleton.findBone(boneName);
            if (bone != null) {
                if (printCenterOfMassLogs) {
                    BaseMod.logger.info("[CenterOfMass] Found DEFAULT body-like bone for " + monsterClassName + ": " + boneName);
                }
                return bone;
            }
        }

        if (printCenterOfMassLogs) {
            BaseMod.logger.warn("[CenterOfMass] No suitable body bone found for " + monsterClassName);
        }
        return null;
    }

    // Find bone by searching for which bone has the specified attachment
    private static Bone findBoneByAttachmentName(Skeleton skeleton, String attachmentName) {
        if (printCenterOfMassLogs) {
            BaseMod.logger.info("[CenterOfMass] Searching for bone with attachment: '" + attachmentName + "'");
        }

        // Search through all slots to find one with the matching attachment
        for (Slot slot : skeleton.getSlots()) {
            if (slot.getAttachment() != null) {
                String slotAttachmentName = slot.getAttachment().getName();
                if (printCenterOfMassLogs) {
                    BaseMod.logger.info("[CenterOfMass] Checking slot '" + slot.getData().getName()
                            + "' with attachment '" + slotAttachmentName + "' on bone '" + slot.getBone().getData().getName() + "'");
                }

                if (attachmentName.equals(slotAttachmentName)) {
                    if (printCenterOfMassLogs) {
                        BaseMod.logger.info("[CenterOfMass] MATCH! Found attachment '" + attachmentName
                                + "' on bone '" + slot.getBone().getData().getName() + "'");
                    }
                    return slot.getBone();
                }
            }
        }

        if (printCenterOfMassLogs) {
            BaseMod.logger.warn("[CenterOfMass] No bone found with attachment: '" + attachmentName + "'");
        }
        return null;
    }

    private static CenterOffset getFallbackOffset(String monsterClassName) {
        // Fallback static offsets for monsters without clear body bones
        switch (monsterClassName) {
            case TheGuardian.ID:
                return new CenterOffset(0f, -60f * Settings.scale);
            default:
                return new CenterOffset(0f, 60f * Settings.scale);
        }
    }

    // Updated method to add custom body attachment mapping
    public static void setCustomBodyAttachment(String monsterClassName, String attachmentName) {
        customBodyAttachments.put(monsterClassName, attachmentName);
        if (printCenterOfMassLogs) {
            BaseMod.logger.info("[CenterOfMass] Set custom body attachment for " + monsterClassName + ": " + attachmentName);
        }
    }

    // Public method to get the body attachment name for a monster (for debugging)
    public static String getBodyAttachmentName(String monsterClassName) {
        return customBodyAttachments.getOrDefault(monsterClassName, "default search");
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