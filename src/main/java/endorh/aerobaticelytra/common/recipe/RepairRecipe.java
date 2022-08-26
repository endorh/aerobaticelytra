package endorh.aerobaticelytra.common.recipe;

import com.google.gson.JsonObject;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipeSerializer;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.SpecialRecipe;
import net.minecraft.network.PacketBuffer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.JSONUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import net.minecraftforge.registries.ForgeRegistryEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static endorh.aerobaticelytra.AerobaticElytra.prefix;

public class RepairRecipe extends SpecialRecipe {
	public static final Serializer SERIALIZER = new Serializer();
	
	public static List<RepairRecipe> getRepairRecipes() {
		final MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		if (server != null) {
			return server.getRecipeManager().getRecipes().stream().map(
			  r -> r instanceof RepairRecipe? (RepairRecipe) r : null
			).filter(Objects::nonNull).collect(Collectors.toList());
		} else return Collections.emptyList();
	}
	
	public final Ingredient ingredient;
	public final int amount; // Currently unused
	
	public RepairRecipe(ResourceLocation idIn, Ingredient ingredient, int amount) {
		super(idIn);
		this.ingredient = ingredient;
		this.amount = amount;
	}
	
	public boolean matches(ItemStack stack) {
		return ingredient.test(stack);
	}
	
	@Override public boolean matches(@NotNull CraftingInventory inv, @NotNull World world) {
		return false;
	}
	@Override public @NotNull ItemStack assemble(@NotNull CraftingInventory inv) {
		return ItemStack.EMPTY;
	}
	@Override public boolean canCraftInDimensions(int width, int height) {
		return false;
	}
	
	@Override public @NotNull IRecipeSerializer<?> getSerializer() {
		return SERIALIZER;
	}
	
	public static class Serializer extends ForgeRegistryEntry<IRecipeSerializer<?>>
	  implements IRecipeSerializer<RepairRecipe> {
		public static final ResourceLocation NAME = prefix("repair_recipe");
		
		Serializer() {
			setRegistryName(NAME);
		}
		
		@Override public @NotNull RepairRecipe fromJson(
		  @NotNull ResourceLocation recipeId, @NotNull JsonObject json
		) {
			final Ingredient ingredient = Ingredient.fromJson(
			  JSONUtils.getAsJsonObject(json, "ingredient"));
			final int amount = JSONUtils.getAsInt(json, "amount", 0); // Unused
			return new RepairRecipe(recipeId, ingredient, amount);
		}
		
		@Nullable @Override public RepairRecipe fromNetwork(
		  @NotNull ResourceLocation recipeId, @NotNull PacketBuffer buf
		) {
			return new RepairRecipe(recipeId, Ingredient.fromNetwork(buf), buf.readVarInt());
		}
		
		@Override public void toNetwork(
		  @NotNull PacketBuffer buf, @NotNull RepairRecipe recipe
		) {
			recipe.ingredient.toNetwork(buf);
			buf.writeVarInt(recipe.amount);
		}
	}
}