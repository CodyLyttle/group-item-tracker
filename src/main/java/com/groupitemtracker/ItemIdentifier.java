package com.groupitemtracker;

import com.google.inject.Inject;
import java.util.Collection;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemVariationMapping;

public class ItemIdentifier
{
	private final ItemManager itemManager;

	@Inject
	public ItemIdentifier(ItemManager manager)
	{
		this.itemManager = manager;
	}

	public String getName(int itemID)
	{
		return itemManager.getItemComposition(itemID)
			.getMembersName();
	}

	public boolean isPlaceholder(int itemID)
	{
		return itemManager.getItemComposition(itemID)
			.getPlaceholderTemplateId() == 14401;
	}

	public int getBaseID(int itemID)
	{
		return ItemVariationMapping.map(
			itemManager.canonicalize(itemID));
	}

	public Collection<Integer> getVariationIDs(int itemID)
	{
		return ItemVariationMapping.getVariations(
			ItemVariationMapping.map(
				itemManager.canonicalize(itemID)));
	}
}
