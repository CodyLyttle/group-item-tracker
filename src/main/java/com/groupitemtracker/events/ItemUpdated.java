package com.groupitemtracker.events;

import com.groupitemtracker.TrackedItem;
import lombok.Data;

@Data
public final class ItemUpdated
{
	private final TrackedItem item;
}

