package com.groupitemtracker.events;

import com.groupitemtracker.TrackedItem;
import lombok.Data;

@Data
public final class ItemRemoved
{
	private final TrackedItem item;
}
