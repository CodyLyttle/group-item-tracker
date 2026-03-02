package com.groupitemtracker;

import net.runelite.api.gameval.InventoryID;

public enum TrackedContainer
{
	BANK(InventoryID.BANK, "Bank"),
	EQUIPMENT(InventoryID.WORN, "Equipment"),
	INVENTORY(InventoryID.INV, "Inventory");

	public final int containerID;
	public final String description;

	TrackedContainer(int containerID, String description)
	{
		this.containerID = containerID;
		this.description = description;
	}

	public static TrackedContainer fromContainerID(int containerID)
	{
		for (var value : TrackedContainer.values())
		{
			if (value.containerID == containerID)
			{
				return value;
			}
		}

		return null;
	}
}
