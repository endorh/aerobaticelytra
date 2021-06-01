package dnj.aerobatic_elytra.common.flight;

import dnj.aerobatic_elytra.AerobaticElytra;
import dnj.aerobatic_elytra.common.flight.mode.IFlightMode;
import dnj.aerobatic_elytra.common.registry.ModRegistries;
import dnj.endor8util.util.LogUtil;
import dnj.endor8util.util.ObfuscationReflectionUtil;
import dnj.flight_core.events.PlayerEntityTravelEvent;
import dnj.flight_core.events.PlayerEntityTravelEvent.RemotePlayerEntityTravelEvent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.ModifiableAttributeInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.play.ServerPlayNetHandler;
import net.minecraft.potion.Effects;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.util.ReflectionUtil;

import java.lang.reflect.Field;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static dnj.aerobatic_elytra.common.capability.FlightDataCapability.getFlightData;

@EventBusSubscriber(modid = AerobaticElytra.MOD_ID)
public class TravelHandler {
	private static final Logger LOGGER = LogManager.getLogger();
	
	/**
	 * {@code private static final AttributeModifier LivingEntity#SLOW_FALLING}
	 * <br>Accessed by reflection
	 */
	public static final AttributeModifier SLOW_FALLING;
	
	/**
	 * {@code private static final int ServerPlayNetHandler#floatingTickCount}<br>
	 * Accessed by reflection
	 */
	public static final Field ServerPlayNetHandler$floatingTickCount;
	
	// Perform reflection
	static {
		// Obtain the SLOW_FALLING modifier by reflection
		AttributeModifier slowFalling = null;
		try {
			Field slow_falling = ObfuscationReflectionHelper.findField(
			    LivingEntity.class, "SLOW_FALLING");
			slowFalling = (AttributeModifier) ReflectionUtil.getStaticFieldValue(slow_falling);
			LOGGER.debug("Got SLOW_FALLING attribute modifier: " + slowFalling);
		} catch (NullPointerException //| NoSuchFieldException
			| ObfuscationReflectionHelper.UnableToFindFieldException e) {
			LOGGER.warn("Could not access SLOW_FALLING attribute modifier");
		} finally {
			SLOW_FALLING = slowFalling;
		}
		
		ServerPlayNetHandler$floatingTickCount = ObfuscationReflectionUtil.getFieldOrLog(
		  ServerPlayNetHandler.class, "field_147365_f", "floatingTickCount",
		  LOGGER::error, "Some flight modes may kick the players for flying");
	}
	
	/**
	 * Event filter for the player travel tick<br>
	 * @see PlayerEntity#travel
	 * @see LivingEntity#travel
	 */
	@SubscribeEvent
	public static void onPlayerEntityTravelEvent(PlayerEntityTravelEvent event) {
		PlayerEntity player = event.player;
		getFlightData(player).ifPresent(fd -> {
			final IFlightMode mode = fd.getFlightMode();
			boolean cancel = mode.getFlightHandler().test(player, event.travelVector);
			for (IFlightMode m : ModRegistries.FLIGHT_MODE_REGISTRY) {
				if (mode != m) {
					final BiConsumer<PlayerEntity, Vector3d> handler = m.getNonFlightHandler();
					if (handler != null)
						handler.accept(player, event.travelVector);
				}
			}
			event.setCanceled(cancel);
		});
	}
	
	@SubscribeEvent
	public static void onRemotePlayerEntityTravelEvent(RemotePlayerEntityTravelEvent event) {
		PlayerEntity player = event.player;
		getFlightData(player).ifPresent(fd -> {
			final IFlightMode mode = fd.getFlightMode();
			final Consumer<PlayerEntity> flightHandler = mode.getRemoteFlightHandler();
			if (flightHandler != null)
				flightHandler.accept(player);
			for (IFlightMode m : ModRegistries.FLIGHT_MODE_REGISTRY) {
				if (mode != m) {
					final Consumer<PlayerEntity> handler = m.getRemoteNonFlightHandler();
					if (handler != null)
						handler.accept(player);
				}
			}
		});
	}
	
	/**
	 * Mimics the logic performed in {@link LivingEntity#travel},
	 * applying the SLOW_FALLING potion effect and returning the
	 * resulting gravity.
	 * @param player Player travelling
	 * @return The default gravity applied to the player on this tick
	 */
	public static double travelGravity(PlayerEntity player) {
		double grav = 0.08D;
		ModifiableAttributeInstance gravity = player.getAttribute(ForgeMod.ENTITY_GRAVITY.get());
		boolean flag = player.getMotion().y <= 0.0D;
		if (SLOW_FALLING != null) {
			assert gravity != null;
			// Directly extracted from LivingEntity#travel
			if (flag && player.isPotionActive(Effects.SLOW_FALLING)) {
				if (!gravity.hasModifier(SLOW_FALLING))
					gravity.applyNonPersistentModifier(SLOW_FALLING);
				player.fallDistance = 0.0F;
			} else if (gravity.hasModifier(SLOW_FALLING)) {
				gravity.removeModifier(SLOW_FALLING);
			}
			grav = gravity.getValue();
		} else if (flag && player.isPotionActive(Effects.SLOW_FALLING)) {
			// Reflection failed, defaulting to direct computation ignoring AttributeModifier
			grav = 0.01F;
			player.fallDistance = 0.0F;
		}
		return grav;
	}
	
	/**
	 * Resets the player's tick count through reflection upon its
	 * {@link ServerPlayNetHandler}<br>.
	 * Useful for flight modes which keep the player from falling
	 * without using elytra flight or creative flight.
	 * @param player Server player instance
	 * @return False if there was a reflection exception.
	 */
	public static boolean resetFloatingTickCount(ServerPlayerEntity player) {
		if (ServerPlayNetHandler$floatingTickCount == null) {
			LogUtil.errorOnce(
			  LOGGER, "A flight mode tried to prevent a player from being kicked " +
			          "for flying, but reflection had failed.");
			return false;
		}
		try {
			ServerPlayNetHandler$floatingTickCount.setInt(player.connection, 0);
			return true;
		} catch (IllegalAccessException e) {
			if (LogUtil.errorOnce(
			  LOGGER, "A flight mode tried to prevent a player from being kicked " +
			          "for flying, but reflective access has failed"))
				e.printStackTrace();
			return false;
		}
	}
}
