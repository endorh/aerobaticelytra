package endorh.aerobaticelytra.common.flight;

import endorh.aerobaticelytra.AerobaticElytra;
import endorh.aerobaticelytra.client.sound.AerobaticElytraSound;
import endorh.aerobaticelytra.client.sound.AerobaticSounds;
import endorh.aerobaticelytra.client.trail.AerobaticTrail;
import endorh.aerobaticelytra.common.AerobaticElytraLogic;
import endorh.aerobaticelytra.common.capability.ElytraSpecCapability;
import endorh.aerobaticelytra.common.capability.IAerobaticData;
import endorh.aerobaticelytra.common.capability.IElytraSpec;
import endorh.aerobaticelytra.common.config.Config;
import endorh.aerobaticelytra.common.config.Config.aerobatic.braking;
import endorh.aerobaticelytra.common.config.Config.aerobatic.physics;
import endorh.aerobaticelytra.common.config.Config.aerobatic.propulsion;
import endorh.aerobaticelytra.common.config.Config.aerobatic.tilt;
import endorh.aerobaticelytra.common.config.Config.network;
import endorh.aerobaticelytra.common.config.Config.weather;
import endorh.aerobaticelytra.common.config.Const;
import endorh.aerobaticelytra.common.event.AerobaticElytraFinishFlightEvent;
import endorh.aerobaticelytra.common.event.AerobaticElytraStartFlightEvent;
import endorh.aerobaticelytra.common.event.AerobaticElytraTickEvent;
import endorh.aerobaticelytra.common.event.AerobaticElytraTickEvent.Pre;
import endorh.aerobaticelytra.common.flight.mode.FlightModeTags;
import endorh.aerobaticelytra.common.item.AerobaticElytraWingItem;
import endorh.aerobaticelytra.common.item.IAbility.Ability;
import endorh.aerobaticelytra.network.AerobaticPackets.DAccelerationPacket;
import endorh.aerobaticelytra.network.AerobaticPackets.DRotationPacket;
import endorh.aerobaticelytra.network.AerobaticPackets.DTiltPacket;
import endorh.util.math.Interpolator;
import endorh.util.math.Vec3d;
import endorh.util.math.Vec3f;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

import static endorh.aerobaticelytra.common.capability.AerobaticDataCapability.getAerobaticData;
import static endorh.aerobaticelytra.common.capability.AerobaticDataCapability.getAerobaticDataOrDefault;
import static endorh.aerobaticelytra.common.capability.FlightDataCapability.getFlightDataOrDefault;
import static endorh.aerobaticelytra.common.item.AerobaticElytraWingItem.hasOffhandDebugWing;
import static endorh.util.math.Interpolator.clampedLerp;
import static endorh.util.math.Vec3f.PI;
import static endorh.util.math.Vec3f.PI_HALF;
import static endorh.util.text.TextUtil.stc;
import static endorh.util.text.TextUtil.ttc;
import static java.lang.Math.*;
import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;

/**
 * Handle aerobatic physics
 */
@EventBusSubscriber(modid = AerobaticElytra.MOD_ID)
public class AerobaticFlight {
	private static final Logger LOGGER = LogManager.getLogger();
	
	private static final Vec3f ZERO = Vec3f.ZERO.get();
	
	// Cache vector instances (since Minecraft is not multithreaded)
	private static final Vec3f prevMotionVec = Vec3f.ZERO.get();
	private static final Vec3f motionVec = Vec3f.ZERO.get();
	private static final Vec3f gravAccVec = Vec3f.ZERO.get();
	private static final Vec3f rainAcc = Vec3f.ZERO.get();
	private static final Vec3f propAccVec = Vec3f.ZERO.get();
	private static final Vec3f glideAccVec = Vec3f.ZERO.get();
	private static final Vec3f angularWindVec = Vec3f.ZERO.get();
	
	/**
	 * Apply the physics of a single elytra travel tick of the player<br>
	 * Is called consistently every tick (20Hz)
	 */
	public static boolean onAerobaticTravel(
	  Player player, Vec3 travelVector
	) {
		if (!shouldAerobaticFly(player)) {
			onNonFlightTravel(player, travelVector);
			return false;
		}
		IAerobaticData data = getAerobaticDataOrDefault(player);
		IElytraSpec spec = AerobaticElytraLogic.getElytraSpecOrDefault(player);
		final boolean isRemote = AerobaticElytraLogic.isRemoteLocalPlayer(player);
		
		// Post Pre event
		final AerobaticElytraTickEvent pre =
		  isRemote? new AerobaticElytraTickEvent.Remote.Pre(player, spec, data)
		          : new Pre(player, spec, data);
		if (MinecraftForge.EVENT_BUS.post(pre))
			return pre instanceof Pre p && p.isPreventDefault();
		
		// Get gravity and apply SLOW_FALLING potion effect as needed
		double grav = TravelHandler.travelGravity(player);
		if (player.isInWater())
			grav *= 1F - spec.getAbility(Ability.AQUATIC);
		else grav *= 1F - spec.getAbility(Ability.LIFT);
		float liftCut = data.getLiftCut();
		
		motionVec.set(player.getDeltaMovement());
		prevMotionVec.set(motionVec);
		Vec3f prevMotionVec = motionVec.copy();
		double hSpeedPrev = motionVec.hNorm();
		
		// Cancel fall damage if falling slowly
		if (motionVec.y > -0.5D - 0.5D * spec.getAbility(Ability.LIFT)) {
			player.fallDistance = 1.0F;
		}
		
		// Apply fine acceleration first to avoid losing precision
		applyRotationAcceleration(player);
		
		// Rain and wind
		final boolean affectedByWeather =
		  weather.ignore_cloud_level || player.blockPosition().getY() > weather.cloud_level
		  || player.level.canSeeSkyFromBelowWater(player.blockPosition());
		data.setAffectedByWeather(affectedByWeather);
		final float biomePrecipitation = WeatherData.getBiomePrecipitationStrength(player);
		final float rain = player.level.getRainLevel(1F) * biomePrecipitation;
		final float storm = player.level.getThunderLevel(1F) * biomePrecipitation;
		final boolean useWeather = Config.weather.enabled && rain > 0F && !player.isInWater() && affectedByWeather;
		Vec3f windVec = WeatherData.getWindVector(player);
		rainAcc.set(
		  0F,
		  -rain * Config.weather.rain.rain_strength_tick - storm * Config.weather.storm.rain_strength_tick,
		  0F);
		
		// Update boost
		if (!data.isBoosted()
		    && data.getPropulsionStrength() == propulsion.range_tick.getFloatMax()
		    && data.isSprinting() && data.getBoostHeat() <= 0.2F
		) {
			data.setBoosted(true);
			player.awardStat(FlightStats.AEROBATIC_BOOSTS, 1);
			if (player.level.isClientSide) {
				AerobaticTrail.addBoostParticles(player);
				AerobaticElytraSound.playBoostSound(player);
			}
		} else if (
		  data.isBoosted() && (!data.isSprinting() || data.getBoostHeat() == 1F)
		) {
			data.setBoosted(false);
			if (player.level.isClientSide)
				AerobaticElytraSound.playSlowDownSound(player);
		}
		final float heatStep = data.isBoosted() ? 0.01F : -0.0075F;
		data.setBoostHeat(Mth.clamp(data.getBoostHeat() + heatStep, 0F, 1F));
		final float boostStrength = data.isBoosted() ? 0.04F : 0F;
		
		if (data.isSprinting())
			player.setSprinting(false);
		
		// Update acceleration
		float propAccStrength = propulsion.range_length / 20F; // 1 second
		float propAcc = data.getPropulsionAcceleration();
		data.setPropulsionStrength(Mth.clamp(
		  data.getPropulsionStrength() + propAcc * propAccStrength,
		  propulsion.range_tick.getFloatMin(), propulsion.range_tick.getFloatMax()));
		if (travelVector != null) {
			propAcc = (float) Mth.clamp((propAcc + 2 * Math.signum(travelVector.z)) / 3, -1F, 1F);
			data.setPropulsionAcceleration(propAcc);
		}
		
		// Update braking
		float brakeAcc = 0.1F; // Half a second to brake completely
		data.setBraking(player.isCrouching() && !data.isBrakeCooling());
		if (braking.max_time_ticks > 0) {
			data.setBrakeHeat(
			  Mth.clamp(data.getBrakeHeat() + (data.isBraking()? 1F : -1F) / braking.max_time_ticks, 0F, 1F));
			if (data.getBrakeHeat() >= 1F) {
				data.setBrakeCooling(true);
			} else if (data.getBrakeHeat() <= 0F)
				data.setBrakeCooling(false);
		} else {
			data.setBrakeHeat(0F);
			data.setBrakeCooling(false);
		}
		float brakeStrength = braking.enabled ? Mth.clamp(
		  data.getBrakeStrength() + (data.isBraking() ? brakeAcc : - brakeAcc), 0F, 1F) : 0F;
		data.setBrakeStrength(brakeStrength);
		
		// Get vector base
		VectorBase base = data.getRotationBase();
		if (data.updateFlying(true)) {
			base.init(data);
			MinecraftForge.EVENT_BUS.post(isRemote
			  ? new AerobaticElytraStartFlightEvent.Remote(player, spec, data)
			  : new AerobaticElytraStartFlightEvent(player, spec, data));
		}
		
		// Underwater rotation friction
		float tiltPitch = data.getTiltPitch();
		float tiltRoll = data.getTiltRoll();
		float tiltYaw = data.getTiltYaw();
		if (player.isInWater()) {
			final float underwaterTiltFriction = clampedLerp(
			  Const.UNDERWATER_CONTROLS_TILT_FRICTION_MAX, Const.UNDERWATER_CONTROLS_TILT_FRICTION_MIN,
			  motionVec.norm() / Const.UNDERWATER_CONTROLS_SPEED_THRESHOLD);
			tiltPitch *= underwaterTiltFriction;
			tiltRoll *= underwaterTiltFriction;
			tiltYaw *= underwaterTiltFriction;
			data.setTiltPitch(tiltPitch);
			data.setTiltRoll(tiltRoll);
			data.setTiltYaw(tiltYaw);
		}
		
		// Angular friction
		float angFriction =
		  1F - (1F - physics.friction_angular)
		       * (tiltPitch * tiltPitch + tiltRoll * tiltRoll + 0.5F * tiltYaw * tiltYaw)
		       / tilt.range_pondered;
		
		float propStrength = data.getPropulsionStrength() * spec.getAbility(Ability.SPEED);
		if (data.isBoosted())
			propStrength += boostStrength;
		
		// Gravity acceleration
		gravAccVec.set(
		  0, -(float) grav * physics.gravity_multiplier - brakeStrength * braking.added_gravity_tick, 0);
		float stasis = player.isInWater()? 0F :
		               Interpolator.quadInOut(1F - propStrength / propulsion.range_tick.getFloatMax());
		gravAccVec.y -= stasis * physics.motorless_gravity_tick;
		
		// Friction
		float friction;
		if (player.isInWater()) {
			friction = Mth.lerp(
			  spec.getAbility(Ability.AQUATIC), physics.friction_water_nerf, physics.friction_water);
			friction *= Mth.lerp(brakeStrength, 1F, braking.friction) * angFriction;
		} else {
			friction = Mth.lerp(stasis, physics.friction_base, physics.motorless_friction);
			friction = Mth.lerp(brakeStrength, friction, braking.friction) * angFriction;
		}
		
		// Glide acceleration
		final float glideAcc = -motionVec.dot(base.normal) * physics.glide_multiplier;
		glideAccVec.set(base.normal);
		glideAccVec.mul(glideAcc);
		
		// Propulsion
		propAccVec.set(base.look);
		propAccVec.mul(propStrength);
		
		// Apply lift cut
		gravAccVec.mul(1F + liftCut * 0.8F);
		glideAccVec.mul(1F - liftCut);
		motionVec.mul(1F - liftCut * 0.8F);
		
		// Apply acceleration
		motionVec.add(glideAccVec);
		motionVec.add(gravAccVec);
		motionVec.add(propAccVec);
		if (useWeather) {
			motionVec.add(windVec);
			motionVec.add(rainAcc);
		}
		
		// Apply friction
		motionVec.mul(friction);
		if (useWeather) {
			// Wind drags more when braking
			Vec3f stasisVec = windVec.copy();
			stasisVec.mul(1F - friction);
			motionVec.add(stasisVec);
		}
		
		// Speed cap
		if (player instanceof ServerPlayer) {
			float speed_cap = network.speed_cap_tick;
			if (speed_cap > 0
			    && (motionVec.x > speed_cap || motionVec.y > speed_cap || motionVec.z > speed_cap)) {
				Component chatWarning =
				  ttc("aerobaticelytra.config.warning.speed_cap_broken",
				      stc(format("%.1f", max(max(motionVec.x, motionVec.y), motionVec.z))));
				String warning = format(
				  "Player %s is flying too fast!: %.1f. Aerobatic Elytra config might be broken",
				  player.getScoreboardName(), max(max(motionVec.x, motionVec.y), motionVec.z));
				player.displayClientMessage(chatWarning, false);
				LOGGER.warn(warning);
				motionVec.x = min(motionVec.x, speed_cap);
				motionVec.y = min(motionVec.y, speed_cap);
				motionVec.z = min(motionVec.z, speed_cap);
			}
		}
		
		// Apply simulated inertia
		if (!AerobaticElytraWingItem.hasDebugWing(player)) // Omitting this 'if' can be funny
			motionVec.lerp(prevMotionVec, physics.inertia);
		
		// Apply motion
		player.setDeltaMovement(motionVec.toVector3d());
		if (!isRemote && !AerobaticElytraWingItem.hasDebugWing(player))
			player.move(MoverType.SELF, player.getDeltaMovement());
		
		// Collisions
		if (player.horizontalCollision || player.verticalCollision)
			AerobaticCollision.onAerobaticCollision(player, hSpeedPrev, motionVec);
		else data.setLiftCut(Mth.clamp(liftCut - 0.15F, 0F, 1F));
		
		// Send update packets to the server
		if (AerobaticElytraLogic.isLocalPlayer(player)) {
			new DTiltPacket(data).send();
			new DRotationPacket(data).send();
			new DAccelerationPacket(data).send();
		}
		
		// Landing
		if (player.isOnGround())
			data.land();
		
		// Update player limb swing
		player.calculateEntityAnimation(player, player instanceof FlyingAnimal);
		
		// Add movement stat
		player.awardStat(FlightStats.AEROBATIC_FLIGHT_ONE_CM,
		               (int)Math.round(player.getDeltaMovement().length() * 100F));
		
		// Update sound for remote players
		if (isRemote && data.updatePlayingSound(true))
			new AerobaticElytraSound(player).play();
		
		// Add trail
		if (player.level.isClientSide) {
			if (data.getTicksFlying() > Const.TAKEOFF_ANIMATION_LENGTH_TICKS
			    && !player.verticalCollision && !player.horizontalCollision
			    // Cowardly refuse to smooth trail on bounces
			    && System.currentTimeMillis() - data.getLastBounceTime() > 250L
			    && !hasOffhandDebugWing(player)) {
				AerobaticTrail.addParticles(player, motionVec, prevMotionVec);
			}
		}
		
		// Update prev tick angles
		float prev = data.getPrevTickRotationRoll();
		while (data.getRotationRoll() - prev > 360F)
			prev += 360F;
		while (data.getRotationRoll() - prev < 0F)
			prev -= 360F;
		data.updatePrevTickAngles();
		
		// Post post event
		MinecraftForge.EVENT_BUS.post(isRemote
		  ? new AerobaticElytraTickEvent.Remote.Post(player, spec, data)
		  : new AerobaticElytraTickEvent.Post(player, spec, data));
		
		// Cancel default travel logic
		return true;
	}
	
	public static void onNonFlightTravel(
	  Player player, @SuppressWarnings("unused") Vec3 travelVector
	) {
		IAerobaticData data = getAerobaticDataOrDefault(player);
		if (data.updateBoosted(false)) player.level.playSound(
		  player, player.blockPosition(), AerobaticSounds.AEROBATIC_ELYTRA_SLOWDOWN,
		  SoundSource.PLAYERS, 1F, 1F);
		if (data.updateFlying(false))
			doLand(player, data);
		cooldown(player, data);
	}
	
	/**
	 * Stop braking and decrease propulsion strength until reaching
	 * takeoff propulsion
	 */
	public static void onOtherModeTravel(
	  Player player, @SuppressWarnings("unused") Vec3 travelVector
	) {
		IAerobaticData data = getAerobaticDataOrDefault(player);
		if (data.updateBoosted(false)) {
			player.level.playSound(
			  player, player.blockPosition(), AerobaticSounds.AEROBATIC_ELYTRA_SLOWDOWN,
			  SoundSource.PLAYERS, 1F, 1F);
		}
		if (data.getRotationBase().valid)
			doLand(player, data);
		cooldown(player, data);
	}
	
	public static void doLand(Player player, IAerobaticData data) {
		data.land();
		MinecraftForge.EVENT_BUS.post(
		  AerobaticElytraLogic.isRemoteLocalPlayer(player)
		  ? new AerobaticElytraFinishFlightEvent.Remote(player, data)
		  : new AerobaticElytraFinishFlightEvent(player, data));
	}
	
	public static void onRemoteFlightTravel(
	  Player player
	) {
		onAerobaticTravel(player, null);
	}
	
	public static void cooldown(Player player, IAerobaticData data) {
		float propStrength = data.getPropulsionStrength();
		if (propStrength != propulsion.takeoff_tick) {
			float step = player.isOnGround() ? 0.05F : 0.02F;
			data.setPropulsionStrength(
			  propulsion.takeoff_tick +
			  Mth.sign(propStrength - propulsion.takeoff_tick) *
			  max(0F, abs(propStrength - propulsion.takeoff_tick) -
			          step * max(propulsion.range_tick.getFloatMax(), propulsion.range_tick.getFloatMin())));
		}
		float boostHeat = data.getBoostHeat();
		if (boostHeat > 0F)
			data.setBoostHeat(max(0F, boostHeat - 0.2F));
	}
	
	/**
	 * Shorthand for {@code getAerobaticDataOrDefault(player).isFlying()}
	 */
	public static boolean isAerobaticFlying(Player player) {
		return getAerobaticDataOrDefault(player).isFlying();
	}
	
	private static boolean shouldAerobaticFly(Player player) {
		if (!player.isFallFlying() || player.getAbilities().flying
		    || !getFlightDataOrDefault(player).getFlightMode().is(FlightModeTags.AEROBATIC))
			return false;
		final ItemStack elytra = AerobaticElytraLogic.getAerobaticElytra(player);
		if (elytra.isEmpty())
			return false;
		final IElytraSpec spec = ElytraSpecCapability.getElytraSpecOrDefault(elytra);
		return (elytra.getDamageValue() < elytra.getMaxDamage() - 1 && spec.getAbility(Ability.FUEL) > 0
		        || player.isCreative())
		       && !player.isInLava() && (!player.isInWater() || spec.getAbility(Ability.AQUATIC) != 0);
	}
	
	/**
	 * Applies rotation acceleration.<br>
	 * Gets called more frequently than onAerobaticTravel, because
	 * camera angles must be interpolated per frame to avoid jittery
	 * visuals.
	 */
	public static void applyRotationAcceleration(Player player) {
		Optional<IAerobaticData> opt = getAerobaticData(player);
		if (opt.isEmpty())
			return;
		IAerobaticData data = opt.get();
		
		VectorBase rotationBase = data.getRotationBase();
		VectorBase cameraBase = data.getCameraBase();
		
		// Get time delta
		double time = currentTimeMillis() / 1000D; // Time in seconds
		double lastTime = data.getLastRotationTime();
		data.setLastRotationTime(time);
		float delta = (lastTime == 0D) ? 0F : (float) (time - lastTime) * 20F;
		if (delta == 0F) // Happens
			return;
		
		// Wind
		if (Config.weather.enabled && data.isAffectedByWeather()) {
			angularWindVec.set(WeatherData.getAngularWindVector(player));
		} else angularWindVec.set(ZERO);
		
		// Angular acceleration
		float tiltPitch = data.getTiltPitch();
		float tiltRoll = data.getTiltRoll();
		float tiltYaw = data.getTiltYaw();
		
		motionVec.set(player.getDeltaMovement());
		
		if (!rotationBase.valid) {
			rotationBase.init(data);
		}
		float strength = motionVec.dot(rotationBase.look);
		if (player.isInWater())
			strength = strength * strength / (abs(strength) + 2) + 0.5F;
		final float pitch = (-tiltPitch * strength - angularWindVec.x) * delta;
		float yaw = (tiltYaw * strength - angularWindVec.y) * delta;
		final float roll = (tiltRoll * strength + angularWindVec.z) * delta;
		if (player.isInWater())
			yaw *= 4F;
		
		rotationBase.rotate(pitch, yaw, roll);
		
		long bounceTime = System.currentTimeMillis();
		if (bounceTime - data.getLastBounceTime() <
		    Const.SLIME_BOUNCE_CAMERA_ANIMATION_LENGTH_MS
		) {
			//data.getBounceRotation().add(pitch, yaw, roll);
			float t = Interpolator.quadOut(
			  (bounceTime - data.getLastBounceTime()) /
			  (float) Const.SLIME_BOUNCE_CAMERA_ANIMATION_LENGTH_MS);
			cameraBase.interpolate(
			  t, data.getPreBounceBase(), data.getPosBounceBase(), rotationBase);
		} else {
			cameraBase.set(rotationBase);
		}
		float[] spherical = cameraBase.toSpherical(player.yRotO);
		
		data.setRotationYaw(spherical[0]);
		data.setRotationPitch(spherical[1]);
		data.setRotationRoll(spherical[2]);
	}
	
	/**
	 * Rotation vector base<br>
	 * Not thread safe
	 */
	public static class VectorBase {
		private static final Vec3f tempVec = Vec3f.ZERO.get();
		private static final VectorBase temp = new VectorBase();
		
		public final Vec3f look = Vec3f.ZERO.get();
		public final Vec3f roll = Vec3f.ZERO.get();
		public final Vec3f normal = Vec3f.ZERO.get();
		
		public boolean valid = true;
		
		public VectorBase() {}
		
		public void init(IAerobaticData data) {
			update(data.getRotationYaw(), data.getRotationPitch(), data.getRotationRoll());
			valid = true;
		}
		
		/**
		 * Set from the spherical coordinates of the look vector, in degrees
		 */
		public void update(float yawDeg, float pitchDeg, float rollDeg) {
			look.set(yawDeg, pitchDeg, true);
			roll.set(yawDeg + 90F, 0F, true);
			roll.rotateAlongOrtVecDegrees(look, rollDeg);
			normal.set(roll);
			normal.cross(look);
		}
		
		/**
		 * Translate to spherical coordinates
		 * @param prevYaw Previous yaw value, since Minecraft does not
		 *                restrict its domain
		 * @return [yaw, pitch, roll] of the look vector, in degrees
		 */
		public float[] toSpherical(float prevYaw) {
			float newPitch = look.getPitch();
			float newYaw;
			float newRoll;
			
			if (abs(newPitch) <= 89.9F) {
				newYaw = look.getYaw();
				tempVec.set(newYaw + 90F, 0F, true);
				newRoll = tempVec.angleUnitaryDegrees(roll, look);
			} else {
				newYaw = newPitch > 0? normal.getYaw() : (normal.getYaw() + 180F) % 360F;
				newRoll = 0F;
			}
			
			// Catch up;
			newYaw += Mth.floor(prevYaw / 360F) * 360F;
			if (newYaw - prevYaw > 180F)
				newYaw -= 360F;
			if (newYaw - prevYaw <= -180F)
				newYaw += 360F;
			
			if (Float.isNaN(newYaw) || Float.isNaN(newPitch) || Float.isNaN(newRoll)) {
 				LOGGER.error("Error translating spherical coordinates");
				return new float[] {0F, 0F, 0F};
			}
			
			return new float[] {newYaw, newPitch, newRoll};
		}
		
		/**
		 * Interpolate between bases {@code pre} and {@code pos}, and then rotate as
		 * would be necessary to carry {@code pos} to {@code target}.<br>
		 *
		 * The {@code pos} base can't be dropped, applying the same rotations applied to
		 * {@code target} also to {@code pre}, because 3D rotations are not
		 * commutative. All 3 bases are needed for the interpolation.
		 *
		 * @param t Interpolation progress ∈ [0, 1]
		 * @param pre Start base
		 * @param pos End base
		 * @param target Rotated end base
		 */
		public void interpolate(
		  float t, VectorBase pre, VectorBase pos, VectorBase target
		) {
			set(pre);
			// Lerp rotation
			Vec3f axis = look.copy();
			axis.cross(pos.look);
			if (axis.isZero()) {
				axis.set(normal);
			} else axis.unitary();
			float lookAngle = look.angleUnitary(pos.look, axis);
			tempVec.set(roll);
			tempVec.rotateAlongVec(axis, lookAngle);
			tempVec.unitary();
			float rollAngle = tempVec.angleUnitary(pos.roll, pos.look);
			if (rollAngle > PI)
				rollAngle = rollAngle - 2 * PI;
			look.rotateAlongOrtVec(axis, lookAngle * t);
			normal.rotateAlongVec(axis, lookAngle * t);
			roll.rotateAlongVec(axis, lookAngle * t);
			roll.rotateAlongOrtVec(look, rollAngle * t);
			normal.rotateAlongOrtVec(look, rollAngle * t);
			
			rotate(pos.angles(target));
			
			look.unitary();
			roll.unitary();
			normal.unitary();
		}
		
		/**
		 * Determine the rotation angles necessary to carry {@code this}
		 * to {@code other} in pitch, yaw, roll order.
		 * @param other Target base
		 * @return [pitch, yaw, roll];
		 */
		public float[] angles(VectorBase other) {
			temp.set(this);
			final float pitch = temp.look.angleProjectedDegrees(other.look, temp.roll);
			temp.look.rotateAlongOrtVecDegrees(temp.roll, pitch);
			temp.normal.rotateAlongOrtVecDegrees(temp.roll, pitch);
			final float yaw = temp.look.angleProjectedDegrees(other.look, temp.normal);
			temp.look.rotateAlongOrtVecDegrees(temp.normal, yaw);
			temp.roll.rotateAlongOrtVecDegrees(temp.normal, yaw);
			final float roll = temp.roll.angleProjectedDegrees(other.roll, temp.look);
			return new float[] {pitch, yaw, roll};
		}
		
		/**
		 * Rotate in degrees in pitch, yaw, roll order and normalize
		 * @param angles [pitch, yaw, roll]
		 */
		public void rotate(float[] angles) {
			rotate(angles[0], angles[1], angles[2]);
		}
		
		/**
		 * Rotate in degrees in pitch, yaw, roll order and normalize.
		 */
		public void rotate(float pitch, float yaw, float roll) {
			look.rotateAlongOrtVecDegrees(this.roll, pitch);
			normal.rotateAlongOrtVecDegrees(this.roll, pitch);
			look.rotateAlongOrtVecDegrees(normal, yaw);
			this.roll.rotateAlongOrtVecDegrees(normal, yaw);
			this.roll.rotateAlongOrtVecDegrees(look, roll);
			normal.rotateAlongOrtVecDegrees(look, roll);
			look.unitary();
			normal.unitary();
			this.roll.unitary();
		}
		
		/**
		 * Mirror across the plane defined by the given axis
		 * @param axis Normal vector to the plane of reflection
		 */
		public void mirror(Vec3f axis) {
			Vec3f ax = axis.copy();
			float angle = ax.angleUnitary(look);
			float mul = -2F;
			if (angle > PI_HALF) {
				angle = PI - angle;
				mul = 2F;
			}
			if (angle < 0.001F) {
				ax = normal;
			} else {
				ax.cross(look);
				ax.unitary();
			}
			angle = PI + mul * angle;
			look.rotateAlongVec(ax, angle);
			roll.rotateAlongVec(ax, angle);
			normal.rotateAlongVec(ax, angle);
		}
		
		/**
		 * Tilt a base in the same way as the player model is
		 * tilted before rendering.<br>
		 * That is, in degrees in yaw, -pitch, roll order<br>
		 * No normalization is applied
		 */
		public void tilt(float yaw, float pitch, float rollDeg) {
			look.rotateAlongOrtVecDegrees(normal, yaw);
			roll.rotateAlongOrtVecDegrees(normal, yaw);
			look.rotateAlongOrtVecDegrees(roll, -pitch);
			normal.rotateAlongOrtVecDegrees(roll, -pitch);
			roll.rotateAlongOrtVecDegrees(look, rollDeg);
			normal.rotateAlongOrtVecDegrees(look, rollDeg);
		}
		
		/**
		 * Offset the rocket vectors to position them approximately where the
		 * rockets should be
		 */
		public void offset(
		  Vec3d leftRocket, Vec3d rightRocket, Vec3d leftCenterRocket, Vec3d rightCenterRocket
		) {
			look.mul(1.6F);
			normal.mul(0.4F);
			roll.mul(0.7F);
			
			leftRocket.add(look);
			leftRocket.add(normal);
			rightRocket.set(leftRocket);
			leftCenterRocket.set(leftRocket);
			rightCenterRocket.set(rightRocket);
			leftRocket.sub(roll);
			rightRocket.add(roll);
			
			roll.mul(0.1F / 0.7F);
			leftCenterRocket.sub(roll);
			rightCenterRocket.add(roll);
		}
		
		/**
		 * Measure approximate distances to another base in each
		 * axis of rotation
		 * @param base Target base
		 * @return [yaw, pitch, roll] in degrees
		 */
		public float[] distance(VectorBase base) {
			Vec3f compare = base.look.copy();
			Vec3f axis = roll.copy();
			axis.mul(axis.dot(compare));
			compare.sub(axis);
			float pitch;
			if (compare.isZero()) {
				pitch = 0F;
			} else {
				compare.unitary();
				pitch = look.angleUnitaryDegrees(compare);
			}
			compare.set(base.look);
			axis.set(normal);
			axis.mul(axis.dot(compare));
			compare.sub(axis);
			float yaw;
			if (compare.isZero()) {
				yaw = 0F;
			} else {
				compare.unitary();
				yaw = look.angleUnitaryDegrees(compare);
			}
			compare.set(base.roll);
			axis.set(look);
			axis.mul(axis.dot(compare));
			compare.sub(axis);
			float roll;
			if (compare.isZero()) {
				roll = 0F;
			} else {
				compare.unitary();
				roll = this.roll.angleUnitaryDegrees(compare);
			}
			return new float[] {yaw, pitch, roll};
		}
		
		public void set(VectorBase base) {
			look.set(base.look);
			roll.set(base.roll);
			normal.set(base.normal);
		}
		
		public void write(FriendlyByteBuf buf) {
			look.write(buf);
			roll.write(buf);
			normal.write(buf);
		}
		
		public static VectorBase read(FriendlyByteBuf buf) {
			VectorBase base = new VectorBase();
			base.look.set(Vec3f.read(buf));
			base.roll.set(Vec3f.read(buf));
			base.normal.set(Vec3f.read(buf));
			return base;
		}
		
		public CompoundTag toNBT() {
			CompoundTag nbt = new CompoundTag();
			nbt.put("Look", look.toNBT());
			nbt.put("Roll", roll.toNBT());
			nbt.put("Normal", normal.toNBT());
			return nbt;
		}
		
		@SuppressWarnings("unused")
		public static VectorBase fromNBT(CompoundTag nbt) {
			VectorBase base = new VectorBase();
			base.readNBT(nbt);
			return base;
		}
		
		public void readNBT(CompoundTag nbt) {
			look.readNBT(nbt.getCompound("Look"));
			roll.readNBT(nbt.getCompound("Roll"));
			normal.readNBT(nbt.getCompound("Normal"));
		}
		
		@Override public String toString() {
			return format("[ %s\n  %s\n  %s ]", look, roll, normal);
		}
	}
}
