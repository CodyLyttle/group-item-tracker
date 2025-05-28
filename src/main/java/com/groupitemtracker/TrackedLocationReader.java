package com.groupitemtracker;

import java.util.Optional;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;

public class TrackedLocationReader
{
	private final Client client;

	@Inject
	public TrackedLocationReader(Client client)
	{
		this.client = client;
	}

	public Optional<Item[]> getItems(TrackedLocation location)
	{
		ItemContainer container = client.getItemContainer(location.itemContainerID);
		return container == null
			? Optional.empty()
			: Optional.of(container.getItems());
	}
}

