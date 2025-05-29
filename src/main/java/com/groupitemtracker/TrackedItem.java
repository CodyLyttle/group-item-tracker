package com.groupitemtracker;

import java.util.EnumMap;
import lombok.Getter;

public class TrackedItem
{
	private final EnumMap<TrackedLocation, Integer> locationCounters;

	@Getter
	private final int itemID;

	public TrackedItem(int itemID)
	{
		this.itemID = itemID;

		locationCounters = new EnumMap<>(TrackedLocation.class);
		for (var location : TrackedLocation.values())
		{
			locationCounters.put(location, 0);
		}
	}

	public void resetLocationCounter(TrackedLocation location)
	{
		assert locationCounters.containsKey(location);

		locationCounters.put(location, 0);
	}

	public void increaseLocationCounter(TrackedLocation location, int value)
	{
		assert value > 0;
		assert locationCounters.containsKey(location);

		Integer existingValue = locationCounters.get(location);
		locationCounters.put(location, existingValue + value);
	}

	public int getLocationCount(TrackedLocation location)
	{
		return locationCounters.get(location);
	}

	public int getTotalCount()
	{
		int sum = 0;
		for (int quantity : locationCounters.values())
		{
			sum += quantity;
		}

		return sum;
	}
}
