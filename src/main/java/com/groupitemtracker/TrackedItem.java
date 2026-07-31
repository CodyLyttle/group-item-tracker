package com.groupitemtracker;

import java.util.EnumMap;

public class TrackedItem
{
	private static final EnumMap<TrackedContainer, Integer> EMPTY_COUNTERS;

	static
	{
		EMPTY_COUNTERS = new EnumMap<>(TrackedContainer.class);
		for (var kind : TrackedContainer.values())
		{
			EMPTY_COUNTERS.put(kind, 0);
		}
	}

	public final String name;
	public final int baseID;
	public final int realID;
	public final EnumMap<TrackedContainer, Integer> counters;

	public TrackedItem(int baseID, int realID, String name)
	{
		this.name = name;
		this.baseID = baseID;
		this.realID = realID;
		this.counters = new EnumMap<>(EMPTY_COUNTERS);
	}

	public int countAll()
	{
		int sum = 0;
		for (int count : counters.values())
		{
			sum += count;
		}

		return sum;
	}
}