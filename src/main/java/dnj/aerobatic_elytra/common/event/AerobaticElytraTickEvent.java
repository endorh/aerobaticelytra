package dnj.aerobatic_elytra.common.event;

import dnj.aerobatic_elytra.common.capability.IAerobaticData;
import dnj.aerobatic_elytra.common.capability.IElytraSpec;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/**
 * @see Pre
 * @see Post
 */
public abstract class AerobaticElytraTickEvent extends Event {
	public final PlayerEntity player;
	public final IElytraSpec elytraSpec;
	public final IAerobaticData aerobaticData;
	
	protected AerobaticElytraTickEvent(
	  PlayerEntity player, IElytraSpec elytraSpec, IAerobaticData aerobaticData
	) {
		this.player = player;
		this.elytraSpec = elytraSpec;
		this.aerobaticData = aerobaticData;
	}
	
	/**
	 * Posted before every aerobatic flight tick on both sides<br>
	 * Can be cancelled, preventing the mod's logic. If cancelled, the
	 * {@link Pre#preventDefault} can be set to {@code true} to also
	 * prevent the vanilla movement logic<br>
	 *
	 * If you're replacing the mod's logic, you may be responsible
	 * of updating certain data, such as calling
	 * {@link IAerobaticData#updateFlying} by yourself<br>
	 *
	 * This event does not fire for remote client player entities
	 *
	 * @see Post
	 * @see Remote
	 */
	@Cancelable @HasResult
	public static class Pre extends AerobaticElytraTickEvent {
		/**
		 * If true and the event is cancelled, will prevent the default movement logic<br>
		 * Otherwise, the vanilla movement logic will run
		 */
		public boolean preventDefault = false;
		
		public Pre(
		  PlayerEntity player, IElytraSpec elytraSpec, IAerobaticData aerobaticData
		) { super(player, elytraSpec, aerobaticData); }
		
		/**
		 * Posted before every aerobatic flight tick on clients for remote client player entities<br>
		 * Can be cancelled, preventing the mod's logic.
		 * Unlike the non-remote event, there's no vanilla logic to prevent on remote players,
		 * so the value of {@link Pre#preventDefault} is ignored
		 * @see Pre
		 */
		public static class Remote extends Pre {
			public Remote(
			  PlayerEntity player, IElytraSpec elytraSpec, IAerobaticData aerobaticData
			) { super(player, elytraSpec, aerobaticData); }
		}
	}
	
	/**
	 * Posted after every aerobatic flight tick on both sides<br>
	 * This event does not fire for remote client player entities
	 *
	 * @see Pre
	 * @see Remote
	 */
	public static class Post extends AerobaticElytraTickEvent {
		public Post(
		  PlayerEntity player, IElytraSpec elytraSpec, IAerobaticData aerobaticData
		) { super(player, elytraSpec, aerobaticData); }
		
		/**
		 * Posted after every aerobatic flight tick for remote client player entities
		 *
		 * @see Post
		 */
		public static class Remote extends Post {
			public Remote(
			  PlayerEntity player, IElytraSpec elytraSpec, IAerobaticData aerobaticData
			) { super(player, elytraSpec, aerobaticData); }
		}
	}
}