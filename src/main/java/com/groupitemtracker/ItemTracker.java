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
		final int baseID = itemIdentifier.getBaseID(itemID);
		return itemLookup.containsKey(baseID);
	}
	
	public void clear()
	{
		itemLookup.clear();
	}
	
	// TODO: Optimize counter initialization when adding multiple items.
	public TrackedItem addItem(int itemID)
	{
		final int baseID = itemIdentifier.getBaseID(itemID);
		final String name = itemIdentifier.getName(itemID);

		if (itemLookup.containsKey(baseID))
		{
			throw new IllegalArgumentException("Already tracking item: " + name);
		}

		final var trackedItem = new TrackedItem(baseID, name);
		itemLookup.put(baseID, trackedItem);

		// Initialize location counters.
		for (var container : TrackedContainer.values())
		{
			final Optional<Item[]> containerItems = containerReader.getItems(container);
			if (containerItems.isEmpty())
			{
				continue;
			}

			for (Item containerItem : containerItems.get())
			{
				final int containerItemID = containerItem.getId();
				if (itemIdentifier.isPlaceholder(containerItemID))
				{
					continue;
				}

				final int containerItemBaseID = itemIdentifier.getBaseID(containerItemID);
				if (containerItemBaseID == baseID)
				{
					trackedItem.increaseContainerCounter(container, containerItem.getQuantity());
				}
			}
		}

		return trackedItem;
	}

	public TrackedItem removeItem(int itemID)
	{
		final int baseID = itemIdentifier.getBaseID(itemID);
		final TrackedItem removed = itemLookup.remove(baseID);
		if (removed == null)
		{
			final String itemName = itemIdentifier.getName(itemID);
			throw new IllegalArgumentException("Cannot remove untracked item: " + itemName);
		}

		return removed;
	}

	public void refreshContainer(TrackedContainer container)
	{
		final Optional<Item[]> containerItems = containerReader.getItems(container);

		// Can't update an unavailable container - e.g. bank isn't open.
		if (containerItems.isEmpty())
		{
			return;
		}

		for (TrackedItem trackedItem : itemLookup.values())
		{
			trackedItem.resetContainerCounter(container);
		}

		for (Item containerItem : containerItems.get())
		{
			final int containerItemID = containerItem.getId();
			if (itemIdentifier.isPlaceholder(containerItemID))
			{
				continue;
			}

			final int containerItemBaseID = itemIdentifier.getBaseID(containerItemID);
			final TrackedItem trackedItem = itemLookup.get(containerItemBaseID);
			if (trackedItem != null)
			{
				trackedItem.increaseContainerCounter(container, containerItem.getQuantity());
			}
		}
	}
}