package dnj.aerobatic_elytra.network;

import dnj.aerobatic_elytra.common.capability.AerobaticDataCapability;
import dnj.aerobatic_elytra.common.capability.FlightDataCapability;
import dnj.aerobatic_elytra.common.capability.IAerobaticData;
import dnj.aerobatic_elytra.common.capability.IFlightData;
import dnj.aerobatic_elytra.common.config.Config;
import dnj.aerobatic_elytra.common.flight.AerobaticFlight.VectorBase;
import dnj.aerobatic_elytra.common.flight.mode.IFlightMode;
import dnj.aerobatic_elytra.server.KickHandler;
import dnj.endor8util.network.DistributedPlayerPacket;
import dnj.endor8util.network.ServerPlayerPacket;
import dnj.endor8util.network.ValidatedDistributedPlayerPacket;
import dnj.endor8util.math.Vec3f;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.network.NetworkEvent.Context;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;

import static dnj.aerobatic_elytra.common.capability.AerobaticDataCapability.getAerobaticDataOrDefault;
import static dnj.aerobatic_elytra.common.capability.FlightDataCapability.getFlightDataOrDefault;
import static dnj.aerobatic_elytra.network.NetworkHandler.ID_GEN;
import static java.lang.Math.abs;
import static java.lang.Math.max;

public class AerobaticPackets {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final String invalidPacketWarnSuffix =
	  "\nServer config might be out of sync, the server could be lagging, " +
	  "or the player could be trying to cheat.";
	
	public static void registerAll() {
		DistributedPlayerPacket.with(NetworkHandler.CHANNEL, ID_GEN)
		  .registerLocal(DFlightModePacket::new)
		  .registerLocal(DTiltPacket::new)
		  .registerLocal(DAccelerationPacket::new)
		  .registerLocal(DSneakingPacket::new)
		  .registerLocal(DJumpingPacket::new)
		  .registerLocal(DSprintingPacket::new)
		  .registerLocal(DRotationPacket::new);
		ServerPlayerPacket.with(NetworkHandler.CHANNEL, ID_GEN)
		  .register(SFlightDataPacket::new)
		  .register(SAerobaticDataPacket::new);
	}
	
	/**
	 * {@link IAerobaticData} update packet<br>
	 * Sent by the player when its flight mode changes
	 */
	public static class DFlightModePacket extends ValidatedDistributedPlayerPacket {
		IFlightMode mode;
		
		private DFlightModePacket() {}
		public DFlightModePacket(IFlightMode mode) {
			this.mode = mode;
		}
		
		@Override protected void onServer(PlayerEntity sender, Context ctx) {
			// TODO
			if (true) {
				getFlightDataOrDefault(sender).setFlightMode(mode);
			} else {
				mode = getFlightDataOrDefault(sender).getFlightMode();
				invalidate();
				KickHandler.incrementInvalidPacketCount((ServerPlayerEntity) sender);
				LOGGER.warn(
				  "Player " + sender.getScoreboardName() + " tried to use disabled flight mode: " +
				  mode.getRegistryName() + "\n" + invalidPacketWarnSuffix);
			}
		}
		
		@Override protected void onClient(PlayerEntity sender, Context ctx) {
			getFlightDataOrDefault(sender).setFlightMode(mode);
		}
		
		@Override protected void serialize(PacketBuffer buf) {
			mode.write(buf);
		}
		@Override protected void deserialize(PacketBuffer buf) {
			mode = IFlightMode.read(buf);
		}
	}
	
	public static class DRotationPacket extends ValidatedDistributedPlayerPacket {
		VectorBase rotation;
		
		public DRotationPacket() {}
		public DRotationPacket(IAerobaticData data) {
			rotation = data.getRotationBase();
		}
		
		@Override protected void onServer(PlayerEntity sender, Context ctx) {
			IAerobaticData data = getAerobaticDataOrDefault(sender);
			if (!Config.disable_aerobatic_elytra_rotation_check) {
				MinecraftServer server = sender.getServer();
				assert server != null;
				long[] times = server.getTickTime(sender.world.getDimensionKey());
				long mspt = times != null ? max(Arrays.stream(times).sum() / times.length, 50L) : 50L;
				float lag = max(50F, ((ServerPlayerEntity) sender).ping);
				
				// Take lag into account when validating packets, plus extra tolerance
				float overlook =
				  (mspt / 50F) * (lag / 50F) * Config.aerobatic_elytra_rotation_check_overlook;
				// Multiply by motion strength, plus extra tolerance
				float mul =
				  overlook * max(0.2F, abs(new Vec3f(sender.getMotion()).dot(rotation.look)));
				
				float[] distance = data.getRotationBase().distance(rotation);
				float tiltYaw = validateClose(distance[0], 0F, Config.tilt_range_yaw * mul);
				float tiltPitch = validateClose(distance[1], 0F, Config.tilt_range_pitch * mul);
				float tiltRoll = validateClose(distance[2], 0F, Config.tilt_range_roll * mul);
				
				if (isInvalid()) {
					data.getRotationBase().rotate(tiltPitch, tiltYaw, tiltRoll);
					KickHandler.incrementInvalidPacketCount((ServerPlayerEntity) sender);
					LOGGER.warn("Player '" + sender.getScoreboardName() + "' rotated too fast!"
					            + invalidPacketWarnSuffix);
				} else {
					data.getRotationBase().set(rotation);
				}
			} else {
				data.getRotationBase().set(rotation);
			}
		}
		@Override public void onCommon(PlayerEntity sender, Context ctx) {
			IAerobaticData data = getAerobaticDataOrDefault(sender);
			data.getRotationBase().set(rotation);
		}
		
		@Override protected void serialize(PacketBuffer buf) {
			rotation.write(buf);
		}
		@Override protected void deserialize(PacketBuffer buf) {
			rotation = VectorBase.read(buf);
		}
	}
	
	/**
	 * {@link IAerobaticData} update packet<br>
	 * Sent by the player when its tilt changes
	 */
	public static class DTiltPacket extends ValidatedDistributedPlayerPacket {
		float tiltPitch;
		float tiltRoll;
		float tiltYaw;
		public DTiltPacket() {}
		public DTiltPacket(IAerobaticData data) {
			tiltPitch = data.getTiltPitch();
			tiltRoll = data.getTiltRoll();
			tiltYaw = data.getTiltYaw();
		}
		@Override public void onCommon(PlayerEntity sender, Context ctx) {
			IAerobaticData data = getAerobaticDataOrDefault(sender);
			data.setTiltPitch(validateClamp(
			  tiltPitch, -Config.tilt_range_pitch, Config.tilt_range_pitch));
			data.setTiltPitch(validateClamp(
			  tiltRoll, -Config.tilt_range_roll, Config.tilt_range_roll));
			data.setTiltYaw(validateClamp(
			  tiltYaw, -Config.tilt_range_yaw, Config.tilt_range_yaw));
			
			if (isInvalid()) {
				KickHandler.incrementInvalidPacketCount((ServerPlayerEntity) sender);
				LOGGER.warn("Player '" + sender.getScoreboardName() + "' tilted too much!"
				            + invalidPacketWarnSuffix);
			}
		}
		@Override protected void serialize(PacketBuffer buf) {
			buf.writeFloat(tiltPitch);
			buf.writeFloat(tiltRoll);
			buf.writeFloat(tiltYaw);
		}
		@Override protected void deserialize(PacketBuffer buf) {
			tiltPitch = buf.readFloat();
			tiltRoll = buf.readFloat();
			tiltYaw = buf.readFloat();
		}
	}
	
	/**
	 * {@link IAerobaticData} update packet<br>
	 * Sent from the player when its acceleration changes
	 */
	public static class DAccelerationPacket extends ValidatedDistributedPlayerPacket {
		float propStrength;
		float brakeStrength;
		public DAccelerationPacket() {}
		public DAccelerationPacket(IAerobaticData data) {
			this.propStrength = data.getPropulsionStrength();
			this.brakeStrength = data.getBrakeStrength();
		}
		
		@Override public void onCommon(PlayerEntity sender, Context ctx) {
			IAerobaticData data = getAerobaticDataOrDefault(sender);
			data.setPropulsionStrength(
			  validateClamp(propStrength, Config.propulsion_min, Config.propulsion_max));
			data.setBrakeStrength(validateClamp(brakeStrength, 0F, 1F));
		}
		@Override protected void serialize(PacketBuffer buf) {
			buf.writeFloat(propStrength);
			buf.writeFloat(brakeStrength);
		}
		@Override protected void deserialize(PacketBuffer buf) {
			propStrength = buf.readFloat();
			brakeStrength = buf.readFloat();
		}
	}
	
	/**
	 * Input update packet<br>
	 * Sent when the player sneaking input changes
	 */
	public static class DSneakingPacket extends DistributedPlayerPacket {
		boolean sneaking;
		public DSneakingPacket() {}
		public DSneakingPacket(IAerobaticData data) {
			sneaking = data.isSneaking();
		}
		
		@Override public void onCommon(PlayerEntity sender, Context ctx) {
			IAerobaticData target = getAerobaticDataOrDefault(sender);
			target.setSneaking(sneaking);
		}
		@Override protected void serialize(PacketBuffer buf) {
			buf.writeBoolean(sneaking);
		}
		@Override protected void deserialize(PacketBuffer buf) {
			sneaking = buf.readBoolean();
		}
	}
	
	/**
	 * Input update packet<br>
	 * Sent when the player jumping input changes
	 */
	public static class DJumpingPacket extends DistributedPlayerPacket {
		boolean jumping;
		public DJumpingPacket() {}
		public DJumpingPacket(IAerobaticData data) {
			jumping = data.isJumping();
		}
		
		@Override protected void onCommon(PlayerEntity sender, Context ctx) {
			IAerobaticData target = getAerobaticDataOrDefault(sender);
			target.setJumping(jumping);
		}
		@Override protected void serialize(PacketBuffer buf) {
			buf.writeBoolean(jumping);
		}
		@Override protected void deserialize(PacketBuffer buf) {
			jumping = buf.readBoolean();
		}
	}
	
	public static class DSprintingPacket extends DistributedPlayerPacket {
		boolean sprinting;
		public DSprintingPacket() {}
		public DSprintingPacket(IAerobaticData data) {
			sprinting = data.isSprinting();
		}
		
		@Override protected void onCommon(PlayerEntity sender, Context ctx) {
			IAerobaticData target = getAerobaticDataOrDefault(sender);
			if (sprinting)
				sender.setSprinting(false);
			target.setSprinting(sprinting);
		}
		
		@Override protected void serialize(PacketBuffer buf) {
			buf.writeBoolean(sprinting);
		}
		@Override protected void deserialize(PacketBuffer buf) {
			sprinting = buf.readBoolean();
		}
	}
	
	
	/**
	 * {@link IAerobaticData} initialization packet<br>
	 * Sent by the server when a client starts tracking another or self
	 */
	public static class SAerobaticDataPacket extends ServerPlayerPacket {
		IAerobaticData data;
		
		protected SAerobaticDataPacket() {}
		public SAerobaticDataPacket(PlayerEntity player) {
			super(player);
			data = getAerobaticDataOrDefault(player);
		}
		@Override protected void onClient(PlayerEntity player, Context ctx) {
			IAerobaticData targetData = getAerobaticDataOrDefault(player);
			targetData.copy(data);
		}
		@Override protected void serialize(PacketBuffer buf) {
			buf.writeCompoundTag(AerobaticDataCapability.asNBT(data));
		}
		@Override protected void deserialize(PacketBuffer buf) {
			data = AerobaticDataCapability.fromNBT(buf.readCompoundTag());
		}
	}
	
	
	/**
	 * {@link IFlightData} initialization packet<br>
	 * Sent by the server when a client starts tracking another or self
	 */
	public static class SFlightDataPacket extends ServerPlayerPacket {
		IFlightData data;
		
		protected SFlightDataPacket() {}
		public SFlightDataPacket(PlayerEntity player) {
			super(player);
			data = getFlightDataOrDefault(player);
		}
		@Override protected void onClient(PlayerEntity player, Context ctx) {
			IFlightData targetData = getFlightDataOrDefault(player);
			targetData.copy(data);
		}
		@Override protected void serialize(PacketBuffer buf) {
			buf.writeCompoundTag(FlightDataCapability.asNBT(data));
		}
		@Override protected void deserialize(PacketBuffer buf) {
			data = FlightDataCapability.fromNBT(buf.readCompoundTag());
		}
	}
}
