package com.groupitemtracker;

import java.util.Optional;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;

public enum TrackedLocation
{
	BANK(InventoryID.BANK, "Bank"),
	EQUIPMENT(InventoryID.WORN, "Equipment"),
	INVENTORY(InventoryID.INV, "Inventory");

	public final int itemContainerID;
	public final String description;

	TrackedLocation(int itemContainerID, String description)
	{
		this.itemContainerID = itemContainerID;
		this.description = description;
	}

	public static Optional<TrackedLocation> fromItemContainer(ItemContainer container)
	{
		int containerID = container.getId();

		for (var value : TrackedLocation.values())
		{
			if (value.itemContainerID == containerID)
			{
				return Optional.of(value);
			}
		}

		return Optional.empty();
	}
}
