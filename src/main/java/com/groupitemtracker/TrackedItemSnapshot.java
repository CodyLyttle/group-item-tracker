package com.groupitemtracker;

public final class TrackedItemSnapshot
{
	public final String name;
	public final int itemID;
	public final int locationMask;
	public final int bankCount;
	public final int equipmentCount;
	public final int inventoryCount;

	public TrackedItemSnapshot(TrackedItem item)
	{
		this.name = item.name;
		this.itemID = item.realID;
		this.bankCount = item.counters.get(TrackedContainer.BANK);
		this.equipmentCount = item.counters.get(TrackedContainer.EQUIPMENT);
		this.inventoryCount = item.counters.get(TrackedContainer.INVENTORY);

		var mask = 0;
		mask |= bankCount > 0 ? TrackedContainer.BANK.mask : 0;
		mask |= equipmentCount > 0 ? TrackedContainer.EQUIPMENT.mask : 0;
		mask |= inventoryCount > 0 ? TrackedContainer.INVENTORY.mask : 0;
		this.locationMask = mask;
	}

	public int countAll()
	{
		return bankCount + equipmentCount + inventoryCount;
	}

	public boolean hasMatchingContainerCounts(TrackedItem item)
	{
		return bankCount == item.counters.get(TrackedContainer.BANK)
			&& equipmentCount == item.counters.get(TrackedContainer.EQUIPMENT)
			&& inventoryCount == item.counters.get(TrackedContainer.INVENTORY);
	}
}
