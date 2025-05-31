package com.groupitemtracker;

import java.util.Optional;
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

	public static Optional<TrackedContainer> fromItemContainer(ItemContainer container)
	{
		int containerID = container.getId();

		for (var value : TrackedContainer.values())
		{
			if (value.itemContainerID == containerID)
			{
				return Optional.of(value);
			}
		}

		return Optional.empty();
	}
}
