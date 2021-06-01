package dnj.aerobatic_elytra.client.render.model;

import dnj.aerobatic_elytra.common.capability.AerobaticDataCapability;
import dnj.aerobatic_elytra.common.capability.IAerobaticData;
import dnj.aerobatic_elytra.common.config.Config;
import dnj.endor8util.math.Vec3f;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static dnj.aerobatic_elytra.client.render.model.AerobaticElytraModelPose.ModelRotation.*;
import static net.minecraft.util.math.MathHelper.lerp;

/**
 * Describes a pose for the {@link AerobaticElytraModel}
 * and how should this pose transition to/from other poses
 */
public interface IElytraPose {
	AerobaticElytraModelPose DEFAULT_POSE = new AerobaticElytraModelPose();
	
	/**
	 * Custom fade-in time, used when replacing the angles of
	 * a previous pose.<br>
	 * Returning NaN or a negative value uses the default time
	 * or the previous' pose fade out time<br>
	 * When switching poses, the new pose's fade-in time takes
	 * priority over the latest's fade-out<br>
	 * <p>
	 * The default time is
	 * {@link AerobaticElytraModel#DEFAULT_ANIMATION_LENGTH}<br>
	 * This feature is probably only ever useful to disable
	 * interpolation completely by returning 0.
	 *
	 * @return The time in ticks that the transition should length.
	 */
	default float getFadeInTime() {
		return Float.NaN;
	}
	
	/**
	 * Custom fade-out time, used when a new pose replaces this
	 * pose's angles.<br>
	 * Returning NaN or a negative value uses the default time.<br>
	 * The next pose's fade-in time has priority over this value, if it
	 * isn't the default NaN or a negative value.<br>
	 * <p>
	 * The default time is {@link AerobaticElytraModel#DEFAULT_ANIMATION_LENGTH}<br>
	 * <p>
	 * This feature is probably only ever useful to disable interpolation completely
	 * by returning 0.
	 *
	 * @return The time in ticks that the transition should length.
	 */
	default float getFadeOutTime() {
		return Float.NaN;
	}
	
	default void modifyPrevious(AerobaticElytraModelPose previousPose) {}
	
	/**
	 * Provide the {@link AerobaticElytraModelPose} containing the angles
	 * and rotation points that each part of the model should use for this
	 * frame.<br>
	 * If null is returned, the default pose will be used.
	 */
	@Nullable AerobaticElytraModelPose getPose(
	  LivingEntity entity, float limbSwing, float limbSwingAmount,
	  float netHeadYaw, float headPitch, float ageInTicks
	);
	
	default @NotNull AerobaticElytraModelPose getNonNullPose(
	  LivingEntity entity, float limbSwing, float limbSwingAmount,
	  float netHeadYaw, float headPitch, float ageInTicks
	) {
		AerobaticElytraModelPose pose = getPose(
		  entity, limbSwing, limbSwingAmount, netHeadYaw, headPitch,
		  ageInTicks);
		return pose == null ? DEFAULT_POSE : pose;
	}
	
	// I don't feel like declaring so many singleton classes
	abstract class ElytraPose implements IElytraPose {
		protected final AerobaticElytraModelPose pose = new AerobaticElytraModelPose();
		
		protected ElytraPose() { build(); }
		
		/**
		 * Called on construction
		 */
		protected void build() {}
		
		@Override public @Nullable AerobaticElytraModelPose getPose(
		  LivingEntity entity, float limbSwing, float limbSwingAmount,
		  float netHeadYaw, float headPitch, float ageInTicks) {
			return pose;
		}
	}
	
	IElytraPose STANDING_POSE =
	  new ElytraPose() {
		  @Override protected void build() {
			  pose.leftWing.x = DEG_15;
			  pose.leftWing.z = -DEG_15;
			  pose.leftWing.origin.x = 5F;
			  pose.leftTip = -DEG_180;
			  pose.leftRoll = -DEG_180;
			  pose.leftPitch = -DEG_180;
			  pose.leftRocket.x = DEG_5;
			  pose.leftRocket.z = DEG_15;
			  pose.copyRightFromLeft();
		  }
		  
		  @Override public AerobaticElytraModelPose getPose(
			 LivingEntity entity, float limbSwing, float limbSwingAmount,
			 float netHeadYaw, float headPitch, float ageInTicks) {
			  pose.leftWing.x = DEG_15 + DEG_20 * limbSwingAmount;
			  pose.leftWing.z = -DEG_15 - DEG_10 * limbSwingAmount;
			  pose.rightWing.x = pose.leftWing.x;
			  pose.rightWing.z = -pose.leftWing.z;
			  return pose;
		  }
	  };
	IElytraPose CROUCHING_POSE =
	  new ElytraPose() {
		  @Override
		  protected void build() {
			  pose.leftWing.x = DEG_40 + DEG_20;
			  pose.leftWing.y = DEG_5;
			  pose.leftWing.z = -DEG_45 - DEG_20;
			  pose.leftWing.origin.x = 6F;
			  pose.leftWing.origin.y = 3F;
			  pose.leftRocket.z = -pose.leftWing.z + DEG_10;
			  pose.copyRightFromLeft();
		  }
		  
		  @Override public void modifyPrevious(AerobaticElytraModelPose prev) {
			  // Apply part of the rotation instantly to prevent the
			  // rockets from clipping through the wings
			  if (prev.leftWing.x <= DEG_30)
				  prev.leftWing.x += DEG_5;
			  // Don't assume the position was symmetric
			  if (prev.rightWing.x <= DEG_30)
				  prev.rightWing.x += DEG_5;
		  }
	  };
	IElytraPose FLYING_POSE = new ElytraPose() {
		@Override protected void build() {
			pose.leftWing.x = DEG_15;
			pose.leftWing.y = -0.2F;
			pose.leftWing.z = -DEG_15;
			pose.leftWing.origin.x = 5F;
			pose.rightWing.x = pose.leftWing.x;
			pose.rightWing.y = -pose.leftWing.y;
			pose.rightWing.z = -pose.leftWing.z;
			pose.rightWing.origin.x = -pose.leftWing.origin.x;
		}
		
		@Override public @Nullable AerobaticElytraModelPose getPose(
		  LivingEntity entity, float limbSwing, float limbSwingAmount,
		  float netHeadYaw, float headPitch, float ageInTicks
		) {
			if (!(entity instanceof PlayerEntity))
				return pose;
			PlayerEntity player = (PlayerEntity) entity;
			IAerobaticData data = AerobaticDataCapability.getAerobaticDataOrDefault(player);
			float scaledPitch = data.getTiltPitch() / Config.tilt_range_pitch;
			float scaledRoll = data.getTiltRoll() / Config.tilt_range_roll;
			
			float tipBase = DEG_5;
			float tipLift = -(float) player.getLookVec().y * 0.5F;
			float tipRoll = scaledRoll * 0.3F;
			
			float pitchSens = 0.8F;
			float rollSens = 0.8F;
			
			float yTilt = 1F;
			Vec3f motionVec = new Vec3f(player.getMotion());
			if (motionVec.y < 0D) {
				yTilt = 1F - (float) Math.pow(-motionVec.y / motionVec.norm(), 1.5D);
			}
			
			yTilt = Math.max(yTilt, 0.3F);
			yTilt = 1F - (1F - yTilt) * (1F - yTilt);
			float targetX = lerp(yTilt, DEG_15, DEG_20);
			float targetZ = lerp(yTilt, -DEG_15, -DEG_90);
			pose.leftWing.x = lerp(0.1F, pose.leftWing.x, targetX);
			pose.leftWing.z = lerp(0.1F, pose.leftWing.z, targetZ);
			pose.rightWing.x = pose.leftWing.x;
			pose.rightWing.z = -pose.leftWing.z;
			
			pose.leftRocket.z = -pose.leftWing.z - 8F * TO_RAD;
			pose.rightRocket.z = -pose.rightWing.z + 8F * TO_RAD;
			
			pose.leftTip = tipLift - tipRoll + tipBase;
			pose.rightTip = tipLift + tipRoll + tipBase;
			pose.leftPitch = scaledPitch * pitchSens;
			pose.rightPitch = -scaledPitch * pitchSens;
			pose.leftRoll = -scaledRoll * rollSens;
			pose.rightRoll = -scaledRoll * rollSens;
			return pose;
		}
	};
}
