package com.groupitemtracker;

import java.util.Optional;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;

public class TrackedContainerReader
{
	private final Client client;

	@Inject
	public TrackedContainerReader(Client client)
	{
		this.client = client;
	}

	public Optional<Item[]> getItems(TrackedContainer container)
	{
		ItemContainer itemContainer = client.getItemContainer(container.itemContainerID);
		return itemContainer == null
			? Optional.empty()
			: Optional.of(itemContainer.getItems());
	}
}

