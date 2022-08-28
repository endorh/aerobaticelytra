package endorh.aerobaticelytra.integration.jei.category;

import com.mojang.datafixers.util.Pair;
import endorh.aerobaticelytra.AerobaticElytra;
import endorh.aerobaticelytra.client.ModResources;
import endorh.aerobaticelytra.common.item.ModItems;
import endorh.aerobaticelytra.common.recipe.JoinRecipe;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Stream;

import static endorh.aerobaticelytra.integration.jei.AerobaticElytraJeiHelper.getAerobaticElytrasMatchingFocus;
import static endorh.aerobaticelytra.integration.jei.AerobaticElytraJeiHelper.split;
import static endorh.util.text.TextUtil.optSplitTtc;

public class JoinRecipeCategory extends BaseCategory<JoinRecipe> {
	public static final RecipeType<JoinRecipe> TYPE = RecipeType.create(AerobaticElytra.MOD_ID, "join", JoinRecipe.class);
	
	public JoinRecipeCategory() {
		super(
		  TYPE, ModResources::regular3x3RecipeBg,
		  ModItems.AEROBATIC_ELYTRA_WING, ModItems.AEROBATIC_ELYTRA_WING, false);
	}
	
	@Override
	public void setRecipe(@NotNull IRecipeLayoutBuilder builder, @NotNull JoinRecipe recipe, @NotNull IFocusGroup focuses) {
		Stream<IFocus<ItemStack>> focus = focuses.getFocuses(VanillaTypes.ITEM_STACK);
		
		List<ItemStack> elytras = getAerobaticElytrasMatchingFocus(focus);
		Pair<List<ItemStack>, List<ItemStack>> wings = split(elytras);
		
		builder.addSlot(RecipeIngredientRole.INPUT, 0, 0).addItemStacks(wings.getFirst());
		builder.addSlot(RecipeIngredientRole.INPUT, 18, 0).addItemStacks(wings.getSecond());
		builder.addSlot(RecipeIngredientRole.OUTPUT, 94, 18).addItemStacks(elytras);
	}
	
	@Override public @NotNull List<Component> getTooltipStrings(
	  @NotNull JoinRecipe recipe, @NotNull IRecipeSlotsView view, double mouseX, double mouseY
	) {
		final List<Component> tt = super.getTooltipStrings(recipe, view, mouseX, mouseY);
		if (inRect(mouseX, mouseY, 61, 19, 22, 15))
			tt.addAll(optSplitTtc("aerobaticelytra.jei.help.category.join"));
		return tt;
	}
}
