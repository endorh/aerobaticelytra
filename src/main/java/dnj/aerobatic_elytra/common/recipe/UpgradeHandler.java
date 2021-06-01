package dnj.aerobatic_elytra.common.recipe;

import dnj.aerobatic_elytra.AerobaticElytra;
import dnj.aerobatic_elytra.common.AerobaticElytraLogic;
import dnj.aerobatic_elytra.common.item.AerobaticElytraItem;
import dnj.aerobatic_elytra.network.UpgradeRecipePacket;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResultType;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import java.util.List;

/**
 * React to using items on the Acrobatic Elytra
 */
@EventBusSubscriber(modid = AerobaticElytra.MOD_ID)
public class UpgradeHandler {
	/**
	 * Filter events
	 */
	@SubscribeEvent
	public static void onPlayerRightClickItem(PlayerInteractEvent.RightClickItem event) {
		PlayerEntity player = event.getPlayer();
		if (player.isOnGround()) {
			ItemStack elytra = player.getItemStackFromSlot(EquipmentSlotType.OFFHAND);
			if (elytra.getItem() instanceof AerobaticElytraItem) {
				ItemStack itemStack = event.getItemStack();
				if (onItemUse(player, elytra, itemStack)) {
					event.setCancellationResult(ActionResultType.func_233537_a_(player.world.isRemote));
					event.setCanceled(true);
				}
			}
		}
	}
	
	/**
	 * Main item use logic
	 */
	public static boolean onItemUse(PlayerEntity player, ItemStack elytra, ItemStack stack) {
		if (player.isCrouching()) {
			List<UpgradeRecipe> upgrades = UpgradeRecipe.getUpgradeRecipes(
			  player.world, elytra, stack);
			if (upgrades.size() == 0)
				return false;
			if (AerobaticElytraLogic.isAbstractClientPlayerEntity(player))
				new UpgradeRecipePacket(player, upgrades).send();
			return true;
		}
		return false;
	}
}
