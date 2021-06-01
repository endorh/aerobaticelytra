package dnj.aerobatic_elytra.common.recipe;

import com.google.gson.*;
import dnj.aerobatic_elytra.common.capability.ElytraSpecCapability;
import dnj.aerobatic_elytra.common.item.AerobaticElytraItem;
import dnj.aerobatic_elytra.common.item.ElytraDyementReader;
import dnj.aerobatic_elytra.common.item.ElytraDyementReader.WingSide;
import dnj.aerobatic_elytra.common.item.ModItems;
import dnj.endor8util.network.PacketBufferUtil;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipeSerializer;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapelessRecipe;
import net.minecraft.item.crafting.SpecialRecipeSerializer;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.JSONUtils;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.RecipeMatcher;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static dnj.aerobatic_elytra.AerobaticElytra.prefix;
import static dnj.endor8util.util.ForgeUtil.getSerializedCaps;

/**
 * Splits an Aerobatic Elytra in two wings, preserving their
 * colors/patterns/trails/abilities.
 */
public class SplitRecipe extends ShapelessRecipe {
	public static int MAX_WIDTH = 3;
	public static int MAX_HEIGHT = 3;
	
	public static final Serializer SERIALIZER = new Serializer();
	private static final ElytraDyementReader dyement = new ElytraDyementReader();
	
	public final NonNullList<Pair<Ingredient, LeaveData>> ingredients;
	
	/**
	 * Contains the leave behaviour for an ingredient
	 */
	public static class LeaveData {
		public final boolean leave;
		public final int damage;
		public static final LeaveData DO_NOT_LEAVE = new LeaveData();
		
		public LeaveData() {
			this(false);
		}
		
		public LeaveData(boolean leave) {
			this(leave, 0);
		}
		
		public LeaveData(boolean leave, int damage) {
			this.leave = leave;
			this.damage = damage;
		}
		
		public void write(PacketBuffer buf) {
			buf.writeBoolean(leave);
			if (leave)
				buf.writeVarInt(damage);
		}
		
		public static LeaveData read(PacketBuffer buf) {
			boolean leave = buf.readBoolean();
			int damage = leave? buf.readVarInt() : 0;
			return new LeaveData(leave, damage);
		}
		
		public static LeaveData from(JsonObject obj) {
			return new LeaveData(
			  JSONUtils.getBoolean(obj, "leave", false),
			  JSONUtils.getInt(obj, "damage", 0));
		}
	}
	
	public SplitRecipe(
	  ResourceLocation idIn, String group,
	  NonNullList<Pair<Ingredient, LeaveData>> recipeItems
	) {
		super(idIn, group, new ItemStack(ModItems.AEROBATIC_ELYTRA_WING),
		      NonNullList.from(Ingredient.EMPTY,
		      recipeItems.stream().map(Pair::getLeft).toArray(Ingredient[]::new)));
		ingredients = recipeItems;
		ItemStack elytra = new ItemStack(ModItems.AEROBATIC_ELYTRA, 1);
		int matches = 0;
		for (Pair<Ingredient, LeaveData> pair : recipeItems) {
			if (pair.getLeft().test(elytra))
				matches++;
		}
		if (matches != 1) {
			throw new JsonSyntaxException(
			  "An AerobaticElytraSplitRecipe must contain exactly one Aerobatic Elytra ingredient");
		}
	}
	
	@Override public boolean matches(
	  @NotNull CraftingInventory inv, @NotNull World world
	) {
		ItemStack elytra = ItemStack.EMPTY;
		for (int i = 0; i < inv.getSizeInventory(); i++) {
			ItemStack current = inv.getStackInSlot(i);
			if (current.getItem() instanceof AerobaticElytraItem) {
				if (!elytra.isEmpty())
					return false;
				elytra = current;
			}
		}
		if (elytra.isEmpty())
			return false;
		return super.matches(inv, world);
	}
	
	@Override
	public @NotNull ItemStack getCraftingResult(
	  @NotNull CraftingInventory inv
	) {
		ItemStack elytra = ItemStack.EMPTY;
		for (int i = 0; i < inv.getSizeInventory(); i++) {
			ItemStack current = inv.getStackInSlot(i);
			if (current.getItem() instanceof AerobaticElytraItem) {
				elytra = current;
				break;
			}
		}
		assert !elytra.isEmpty();
		return getWing(elytra, WingSide.RIGHT);
	}
	
	@Override
	public @NotNull NonNullList<ItemStack> getRemainingItems(@NotNull CraftingInventory inv) {
		NonNullList<ItemStack> rem = NonNullList.withSize(inv.getSizeInventory(), ItemStack.EMPTY);
		
		List<ItemStack> inputs = new java.util.ArrayList<>();
		List<Integer> inputMap = new ArrayList<>();
		for(int i = 0; i < inv.getSizeInventory(); i++) {
			ItemStack current = inv.getStackInSlot(i);
			if (!current.isEmpty()) {
				inputs.add(current);
				inputMap.add(i);
			}
		}
		
		int[] map = RecipeMatcher.findMatches(inputs, getIngredients());
		if (map == null)
			throw new IllegalStateException("Split recipe should not have matched");
		
		int j;
		for (int i = 0; i < inputMap.size(); i++) {
			j = inputMap.get(i);
			final ItemStack stack = inv.getStackInSlot(j);
			if (stack.getItem() instanceof AerobaticElytraItem) {
				rem.set(j, getWing(stack, WingSide.LEFT));
			} else {
				final LeaveData data = ingredients.get(map[i]).getRight();
				if (data.leave) {
					ItemStack left = stack.copy();
					left.setDamage(left.getDamage() + data.damage);
					rem.set(j, left);
				} else if (stack.hasContainerItem())
					rem.set(j, stack.getContainerItem());
			}
		}
		
		return rem;
	}
	
	public ItemStack getWing(ItemStack elytra, WingSide side) {
		ItemStack wing = new ItemStack(ModItems.AEROBATIC_ELYTRA_WING, 1, getSerializedCaps(elytra));
		dyement.read(elytra);
		dyement.getWing(side).write(wing, null);
		wing.setDamage(elytra.getDamage());
		CompoundNBT tag = elytra.getTag();
		if (tag != null && tag.contains("HideFlags", 99)) {
			wing.getOrCreateTag().putInt("HideFlags", tag.getInt("HideFlags"));
		}
		ElytraSpecCapability.getElytraSpecOrDefault(wing).getTrailData().keep(side);
		return wing;
	}
	
	@Override
	public boolean canFit(int width, int height) {
		return width * height >= 2;
	}
	
	public static class Serializer extends SpecialRecipeSerializer<SplitRecipe> {
		public static final ResourceLocation NAME = prefix("split_recipe");
		public Serializer() {
			super(id -> null);
			setRegistryName(NAME);
		}
		
		@Override
		public @NotNull SplitRecipe read(
		  @NotNull ResourceLocation recipeId, @NotNull JsonObject json
		) {
			String s = JSONUtils.getString(json, "group", "");
			NonNullList<Pair<Ingredient, LeaveData>> list =
			  readIngredients(JSONUtils.getJsonArray(json, "ingredients"));
			if (list.isEmpty()) {
				throw new JsonParseException("No ingredients for split recipe recipe");
			} else if (list.size() > MAX_WIDTH * MAX_HEIGHT) {
				throw new JsonParseException("Too many ingredients for split recipe, the max is " +
				                             (SplitRecipe.MAX_WIDTH * SplitRecipe.MAX_HEIGHT));
			} else {
				int matches = 0;
				ItemStack elytra = new ItemStack(ModItems.AEROBATIC_ELYTRA);
				for (Pair<Ingredient, LeaveData> pair : list) {
					if (pair.getLeft().test(elytra))
						matches++;
				}
				if (matches != 1)
					throw new JsonParseException(
					  "In an AerobaticElytraSplitRecipe there must be exactly one Aerobatic Elytra ingredient");
				return new SplitRecipe(recipeId, s, list);
			}
		}
		
		public static NonNullList<Pair<Ingredient, LeaveData>> readIngredients(JsonArray arr) {
			NonNullList<Pair<Ingredient, LeaveData>> list = NonNullList.create();
			for (JsonElement elem : arr) {
				if (elem.isJsonObject()) {
					JsonObject obj = elem.getAsJsonObject();
					if (!obj.has("ingredient")) {
						list.add(Pair.of(
						  Ingredient.fromItemListStream(Stream.of(
						    Ingredient.deserializeItemList(obj))),
						  LeaveData.from(obj)));
					} else {
						JsonElement ing = obj.get("ingredient");
						LeaveData data = LeaveData.from(obj);
						if (ing.isJsonObject()) {
							list.add(Pair.of(
							  Ingredient.fromItemListStream(Stream.of(
							    Ingredient.deserializeItemList(ing.getAsJsonObject())
							  )), data));
						} else if (ing.isJsonArray()) {
							list.add(Pair.of(ingredientFromJsonArray(ing.getAsJsonArray()), data));
						} else {
							throw new JsonSyntaxException(
							  "Expected item to be object or array of objects");
						}
					}
				} else if (elem.isJsonArray()) {
					list.add(Pair.of(ingredientFromJsonArray(elem.getAsJsonArray()),
					                 LeaveData.DO_NOT_LEAVE));
				} else {
					throw new JsonSyntaxException(
					  "Expected item to be object or array of objects");
				}
			}
			return list;
		}
		
		public static Ingredient ingredientFromJsonArray(JsonArray arr) {
			if (arr.size() == 0) {
				throw new JsonSyntaxException("Item array cannot be empty, at least one item must be defined");
			} else {
				return Ingredient.fromItemListStream(
				  StreamSupport.stream(arr.spliterator(), false).map(
				    (element) -> Ingredient.deserializeItemList(JSONUtils.getJsonObject(element, "item"))));
			}
		}
		
		@Override
		public SplitRecipe read(@NotNull ResourceLocation recipeId, @NotNull PacketBuffer buf) {
			String group = PacketBufferUtil.readString(buf);
			NonNullList<Pair<Ingredient, LeaveData>> ingredients =
			  PacketBufferUtil.readNonNullList(
			    buf, b -> Pair.of(Ingredient.read(b), LeaveData.read(b)),
			    Pair.of(Ingredient.EMPTY, LeaveData.DO_NOT_LEAVE));
			return new SplitRecipe(recipeId, group, ingredients);
		}
		
		@Override
		public void write(@NotNull PacketBuffer buf, @NotNull SplitRecipe recipe) {
			buf.writeString(recipe.getGroup());
			PacketBufferUtil.writeList(
			  recipe.ingredients, buf, (p, b) -> {
			  	   p.getLeft().write(b);
			  	   p.getRight().write(b);
			  });
		}
	}
	
	@Override public @NotNull IRecipeSerializer<?> getSerializer() {
		return SERIALIZER;
	}
}
