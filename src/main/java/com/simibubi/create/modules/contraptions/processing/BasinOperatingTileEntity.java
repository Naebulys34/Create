package com.simibubi.create.modules.contraptions.processing;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.simibubi.create.foundation.utility.recipe.RecipeFinder;
import com.simibubi.create.modules.contraptions.base.KineticTileEntity;
import com.simibubi.create.modules.contraptions.processing.BasinTileEntity.BasinInventory;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.BucketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemHandlerHelper;

public abstract class BasinOperatingTileEntity extends KineticTileEntity {

	public boolean checkBasin;
	public boolean basinRemoved;
	protected IRecipe<?> lastRecipe;
	protected LazyOptional<IItemHandler> basinInv = LazyOptional.empty();
	protected List<ItemStack> inputs;

	public BasinOperatingTileEntity(TileEntityType<?> typeIn) {
		super(typeIn);
		checkBasin = true;
	}

	@Override
	public void onSpeedChanged(float prevSpeed) {
		super.onSpeedChanged(prevSpeed);
		if (getSpeed() == 0)
			basinRemoved = true;
		checkBasin = true;
	}

	public void gatherInputs() {
		BasinInventory inv = (BasinInventory) basinInv.orElse(null);
		inputs = new ArrayList<>();
		IItemHandlerModifiable inputHandler = inv.getInputHandler();
		for (int slot = 0; slot < inputHandler.getSlots(); ++slot) {
			ItemStack itemstack = inputHandler.extractItem(slot, inputHandler.getSlotLimit(slot), true);
			if (!itemstack.isEmpty()) {
				inputs.add(itemstack);
			}
		}
	}

	@Override
	public void tick() {
		super.tick();

		if (basinRemoved) {
			basinRemoved = false;
			basinRemoved();
			sendData();
			return;
		}

		if (!isSpeedRequirementFulfilled())
			return;
		if (getSpeed() == 0)
			return;
		if (!isCheckingBasin())
			return;
		if (!checkBasin)
			return;
		checkBasin = false;
		TileEntity basinTE = world.getTileEntity(pos.down(2));
		if (basinTE == null || !(basinTE instanceof BasinTileEntity))
			return;
		if (!basinInv.isPresent())
			basinInv = basinTE.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY);
		if (!basinInv.isPresent())
			return;

		if (world.isRemote)
			return;

		gatherInputs();
		List<IRecipe<?>> recipes = getMatchingRecipes();
		if (recipes.isEmpty())
			return;

		lastRecipe = recipes.get(0);
		startProcessingBasin();
		sendData();
	}

	protected boolean isCheckingBasin() {
		return true;
	}

	public void startProcessingBasin() {
	}

	public boolean continueWithPreviousRecipe() {
		return true;
	}

	public void applyBasinRecipe() {
		if (lastRecipe == null)
			return;
		if (!basinInv.isPresent())
			return;

		BasinInventory inv = (BasinInventory) basinInv.orElse(null);
		if (inv == null)
			return;

		IItemHandlerModifiable inputs = inv.getInputHandler();
		IItemHandlerModifiable outputs = inv.getOutputHandler();
		List<ItemStack> catalysts = new ArrayList<>();

		int buckets = 0;
		Ingredients: for (Ingredient ingredient : lastRecipe.getIngredients()) {
			for (int slot = 0; slot < inputs.getSlots(); slot++) {
				if (!ingredient.test(inputs.extractItem(slot, 1, true)))
					continue;
				ItemStack extracted = inputs.extractItem(slot, 1, false);
				if (extracted.getItem() instanceof BucketItem)
					buckets++;

				if ((lastRecipe instanceof ProcessingRecipe)) {
					ProcessingRecipe<?> pr = (ProcessingRecipe<?>) lastRecipe;
					if (pr.getRollableIngredients().get(slot).remains())
						catalysts.add(extracted.copy());
				}
				continue Ingredients;
			}
			// something wasn't found
			return;
		}

		ItemHandlerHelper.insertItemStacked(outputs, lastRecipe.getRecipeOutput().copy(), false);
		if (buckets > 0)
			ItemHandlerHelper.insertItemStacked(outputs, new ItemStack(Items.BUCKET, buckets), false);
		catalysts.forEach(c -> ItemHandlerHelper.insertItemStacked(outputs, c, false));

		// Continue mixing
		gatherInputs();
		if (matchBasinRecipe(lastRecipe)) {
			continueWithPreviousRecipe();
			sendData();
		}
	}

	protected List<IRecipe<?>> getMatchingRecipes() {
		List<IRecipe<?>> list = RecipeFinder.get(getRecipeCacheKey(), world, this::matchStaticFilters);
		return list.stream().filter(this::matchBasinRecipe)
				.sorted((r1, r2) -> -r1.getIngredients().size() + r2.getIngredients().size())
				.collect(Collectors.toList());
	}

	protected void basinRemoved() {

	}

	protected abstract <C extends IInventory> boolean matchStaticFilters(IRecipe<C> recipe);

	protected abstract <C extends IInventory> boolean matchBasinRecipe(IRecipe<C> recipe);

	protected abstract Object getRecipeCacheKey();

}
