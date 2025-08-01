package com.groupitemtracker;

import com.groupitemtracker.events.ItemAdded;
import com.groupitemtracker.events.ItemRemoved;
import com.groupitemtracker.events.ItemUpdated;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

@Singleton
public class ItemTracker
{
	private final Client client;
	private final EventBus eventBus;
	private final ItemIdentifier itemIdentifier;
	private final Map<Integer, TrackedItem> itemLookup = new HashMap<>();
	private final Map<TrackedItem, TrackedItemSnapshot> itemSnapshotLookup = new HashMap<>();
	private boolean hasPendingChanges = false;

	@Inject
	public ItemTracker(Client client, EventBus eventBus, ItemIdentifier itemIdentifier)
	{
		this.client = client;
		this.eventBus = eventBus;
		this.itemIdentifier = itemIdentifier;
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!hasPendingChanges)
		{
			return;
		}

		hasPendingChanges = false;
		for (TrackedItem item : itemLookup.values())
		{
			if (!item.matchesSnapshot(itemSnapshotLookup.get(item)))
			{
				itemSnapshotLookup.put(item, item.createSnapshot());
				eventBus.post(new ItemUpdated(item));
			}
		}
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

	public void reset()
	{
		itemLookup.clear();
		itemSnapshotLookup.clear();
	}

	public void loadProfile(ProfileManager profileManager)
	{
		reset();

		for (int itemID : profileManager.readTrackedItemIDs())
		{
			createTrackedItem(itemID);
		}

		refreshAvailableContainers();

		for (TrackedItem item : itemLookup.values())
		{
			itemSnapshotLookup.put(item, item.createSnapshot());
		}
	}

	private TrackedItem createTrackedItem(int itemID)
	{
		final int baseID = itemIdentifier.getBaseID(itemID);
		final String name = itemIdentifier.getName(baseID);

		if (itemLookup.containsKey(baseID))
		{
			throw new IllegalArgumentException("Already tracking item: " + name);
		}

		final var trackedItem = new TrackedItem(baseID, name);
		itemLookup.put(baseID, trackedItem);
		return trackedItem;
	}

	public TrackedItem addItem(int itemID)
	{
		TrackedItem trackedItem = createTrackedItem(itemID);
		refreshAvailableContainers();
		itemSnapshotLookup.put(trackedItem, trackedItem.createSnapshot());
		eventBus.post(new ItemAdded(trackedItem));
		return trackedItem;
	}

	public TrackedItem removeItem(int itemID)
	{
		final int baseID = itemIdentifier.getBaseID(itemID);
		final TrackedItem removedItem = itemLookup.remove(baseID);
		if (removedItem == null)
		{
			final String itemName = itemIdentifier.getName(itemID);
			throw new IllegalArgumentException("Cannot remove untracked item: " + itemName);
		}

		itemSnapshotLookup.remove(removedItem);
		eventBus.post(new ItemRemoved(removedItem));
		return removedItem;
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		var trackedContainer = TrackedContainer.fromItemContainerID(event.getContainerId());
		if (trackedContainer != null)
		{
			refreshContainer(trackedContainer, event.getItemContainer());
			hasPendingChanges = true;
		}
	}

	private void refreshContainer(TrackedContainer trackedContainer, ItemContainer itemContainer)
	{
		final Item[] containerItems = itemContainer.getItems();

		for (TrackedItem trackedItem : itemLookup.values())
		{
			trackedItem.resetContainerCounter(trackedContainer);
		}

		for (Item item : containerItems)
		{
			final int itemId = item.getId();

			if (itemIdentifier.isPlaceholder(itemId))
			{
				continue;
			}

			final int baseID = itemIdentifier.getBaseID(itemId);
			final TrackedItem trackedItem = itemLookup.get(baseID);
			if (trackedItem != null)
			{
				trackedItem.increaseContainerCounter(trackedContainer, item.getQuantity());
			}
		}
	}

	private void refreshAvailableContainers()
	{
		for (var trackedContainer : TrackedContainer.values())
		{
			ItemContainer itemContainer = client.getItemContainer(trackedContainer.itemContainerID);
			if (itemContainer != null)
			{
				refreshContainer(trackedContainer, itemContainer);
			}
		}
	}
}