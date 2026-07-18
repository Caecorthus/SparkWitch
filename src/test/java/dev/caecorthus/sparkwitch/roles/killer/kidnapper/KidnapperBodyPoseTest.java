package dev.caecorthus.sparkwitch.roles.killer.kidnapper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KidnapperBodyPoseTest {
    private static final float EPSILON = 1.0E-5F;

    @Test
    void alignsTheGroundLevelBodyToTheCarrierViewYaw() {
        assertPose(0.0F, 0.0F, 15.0F,
                KidnapperBodyPose.fromCarrierRotation(0.0F, 0.0F, 15.0F, 15.0F, 0.5F));
        assertPose(90.0F, 0.0F, 15.0F,
                KidnapperBodyPose.fromCarrierRotation(0.0F, 90.0F, 15.0F, 15.0F, 1.0F));
        assertPose(180.0F, 0.0F, 15.0F,
                KidnapperBodyPose.fromCarrierRotation(90.0F, 180.0F, 15.0F, 15.0F, 1.0F));
    }

    @Test
    void interpolatesYawAcrossTheWrapAndPreservesCarrierPitch() {
        KidnapperBodyPose positiveWrap = KidnapperBodyPose.fromCarrierRotation(
                170.0F, -170.0F, -20.0F, 40.0F, 0.5F
        );
        KidnapperBodyPose negativeWrap = KidnapperBodyPose.fromCarrierRotation(
                -170.0F, 170.0F, -20.0F, 40.0F, 0.5F
        );

        assertPose(180.0F, 0.0F, 10.0F, positiveWrap);
        assertPose(-180.0F, 0.0F, 10.0F, negativeWrap);
    }

    @Test
    void appliesOnlyToAnExactKidnapperWithADirectPlayerCarrier() {
        assertFalse(KidnapperBodyPose.isEligible(false, false));
        assertFalse(KidnapperBodyPose.isEligible(false, true));
        assertFalse(KidnapperBodyPose.isEligible(true, false));

        assertTrue(KidnapperBodyPose.isEligible(true, true));
    }

    private static void assertPose(
            float bodyYaw,
            float relativeHeadYaw,
            float headPitch,
            KidnapperBodyPose pose
    ) {
        assertEquals(bodyYaw, pose.bodyYaw(), EPSILON);
        assertEquals(relativeHeadYaw, pose.relativeHeadYaw(), EPSILON);
        assertEquals(headPitch, pose.headPitch(), EPSILON);
    }
}
