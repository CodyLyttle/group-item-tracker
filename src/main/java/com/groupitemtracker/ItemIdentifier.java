package com.groupitemtracker;

import com.google.inject.Inject;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemVariationMapping;

// Adapter for simple item composition queries and test mocks.
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
			.getPlaceholderTemplateId() != -1;
	}

	public int getBaseID(int itemID)
	{
		return ItemVariationMapping.map(
			itemManager.canonicalize(itemID));
	}
}
