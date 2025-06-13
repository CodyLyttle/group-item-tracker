package com.groupitemtracker;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import net.runelite.api.Item;

public class ItemTracker
{
	private final Map<Integer, TrackedItem> itemLookup = new HashMap<>();
	private final ItemIdentifier itemIdentifier;
	private final TrackedContainerReader containerReader;

	@Inject
	public ItemTracker(ItemIdentifier itemIdentifier, TrackedContainerReader containerReader)
	{
		this.itemIdentifier = itemIdentifier;
		this.containerReader = containerReader;
	}

	public Collection<TrackedItem> getItems()
	{
		return Collections.unmodifiableCollection(itemLookup.values());
	}
	
	public boolean containsItem(int itemID)
	{
		int baseID = itemIdentifier.getBaseID(itemID);
		return itemLookup.containsKey(baseID);
	}

	public TrackedItem addItem(int itemID)
	{
		final int trackedID = itemIdentifier.getBaseID(itemID);

		if (itemLookup.containsKey(trackedID))
		{
			throw new IllegalArgumentException("Cannot add an already tracked item");
		}

		final var trackedItem = new TrackedItem(trackedID);
		itemLookup.put(trackedID, trackedItem);

		for (var container : TrackedContainer.values())
		{
			Optional<Item[]> containerItems = containerReader.getItems(container);
			if (containerItems.isEmpty())
			{
				continue;
			}

			for (Item item : containerItems.get())
			{
				int baseItemID = itemIdentifier.getBaseID(item.getId());
				if (baseItemID == trackedID)
				{
					trackedItem.increaseContainerCounter(container, item.getQuantity());
				}
			}
		}

		return trackedItem;
	}

	public void removeItem(int itemID)
	{
		final int trackedID = itemIdentifier.getBaseID(itemID);
		if (itemLookup.remove(trackedID) == null)
		{
			throw new IllegalArgumentException("Cannot remove an untracked item");
		}
	}

	public void refreshContainer(TrackedContainer container)
	{
		final Optional<Item[]> itemsQuery = containerReader.getItems(container);

		// Can't update an unavailable container - e.g. bank isn't open.
		if (itemsQuery.isEmpty())
		{
			return;
		}

		for (TrackedItem item : itemLookup.values())
		{
			item.resetContainerCounter(container);
		}

		for (var item : itemsQuery.get())
		{
			int lookupKey = itemIdentifier.getBaseID(item.getId());
			TrackedItem match = itemLookup.get(lookupKey);
			if (match != null)
			{
				match.increaseContainerCounter(container, item.getQuantity());
			}
		}
	}
}