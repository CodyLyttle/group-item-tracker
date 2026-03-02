package com.groupitemtracker;

import java.util.EnumMap;

public class TrackedItem
{
	private final EnumMap<TrackedContainer, Integer> containerCounters;

	private final int itemID;

	private final String itemName;

	public TrackedItem(int baseID, String itemName)
	{
		this.itemID = baseID;
		this.itemName = itemName;

		containerCounters = new EnumMap<>(TrackedContainer.class);
		for (var container : TrackedContainer.values())
		{
			containerCounters.put(container, 0);
		}
	}

	public int getItemID()
	{
		return itemID;
	}

	public String getItemName()
	{
		return itemName;
	}

	public int getContainerCount(TrackedContainer container)
	{
		return containerCounters.get(container);
	}

	public int getTotalCount()
	{
		int sum = 0;
		for (int quantity : containerCounters.values())
		{
			sum += quantity;
		}

		return sum;
	}

	public void increaseContainerCounter(TrackedContainer container, int value)
	{
		assert value > 0;
		assert containerCounters.containsKey(container);

		final int existingValue = containerCounters.get(container);
		containerCounters.put(container, existingValue + value);
	}

	public void setContainerCounter(TrackedContainer container, int value)
	{
		assert value >= 0;
		assert containerCounters.containsKey(container);

		containerCounters.put(container, value);
	}

	public void resetContainerCounter(TrackedContainer container)
	{
		assert containerCounters.containsKey(container);

		containerCounters.put(container, 0);
	}

	public EnumMap<TrackedContainer, Integer> createSnapshot()
	{
		return new EnumMap<>(containerCounters);
	}

	public boolean matchesSnapshot(EnumMap<TrackedContainer, Integer> snapshot)
	{
		return snapshot.equals(containerCounters);
	}
}
