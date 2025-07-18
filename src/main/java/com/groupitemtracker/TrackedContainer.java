package com.groupitemtracker;

import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;

public enum TrackedContainer
{
	BANK(InventoryID.BANK, "Bank"),
	EQUIPMENT(InventoryID.WORN, "Equipment"),
	INVENTORY(InventoryID.INV, "Inventory");

	public final int itemContainerID;
	public final String description;

	TrackedContainer(int itemContainerID, String description)
	{
		this.itemContainerID = itemContainerID;
		this.description = description;
	}

	public static TrackedContainer fromItemContainerID(int itemContainerID)
	{
		for (var value : TrackedContainer.values())
		{
			if (value.itemContainerID == itemContainerID)
			{
				return value;
			}
		}

		return null;
	}
}
