package com.groupitemtracker.events;

import com.groupitemtracker.TrackedItem;
import lombok.Data;

@Data
public final class ItemAdded
{
	private final TrackedItem item;
}
