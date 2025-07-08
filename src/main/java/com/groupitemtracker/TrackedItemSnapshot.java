package com.groupitemtracker;

import java.util.EnumMap;

public final class TrackedItemSnapshot
{
	private final EnumMap<TrackedContainer, Integer> _immutableCopy;

	public TrackedItemSnapshot(EnumMap<TrackedContainer, Integer> source)
	{
		// Create a deep copy.
		_immutableCopy = new EnumMap<>(source);
	}

	public int getContainerCount(TrackedContainer container)
	{
		return _immutableCopy.getOrDefault(container, 0);
	}

	public boolean equals(TrackedItemSnapshot other)
	{
		return _immutableCopy.equals(other._immutableCopy);
	}

	public boolean equals(EnumMap<TrackedContainer, Integer> other)
	{
		return _immutableCopy.equals(other);
	}
}