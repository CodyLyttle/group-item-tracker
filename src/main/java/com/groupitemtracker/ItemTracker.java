package com.groupitemtracker;

import com.groupitemtracker.events.ItemAdded;
import com.groupitemtracker.events.ItemRemoved;
import com.groupitemtracker.events.ItemUpdated;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Item;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

@Singleton
public class ItemTracker
{
	private final EventBus eventBus;
	private final ItemIdentifier itemIdentifier;
	private final TrackedContainerReader containerReader;
	private final Map<Integer, TrackedItem> itemLookup = new HashMap<>();
	private final Map<TrackedItem, TrackedItemSnapshot> itemSnapshotLookup = new HashMap<>();
	private final Set<TrackedItem> pendingItemUpdates = new HashSet<>();

	@Inject
	public ItemTracker(EventBus eventBus, ItemIdentifier itemIdentifier, TrackedContainerReader containerReader)
	{
		this.eventBus = eventBus;
		this.itemIdentifier = itemIdentifier;
		this.containerReader = containerReader;
	}

	@Subscribe
	private void onGameTick(GameTick event)
	{
		for (TrackedItem item : pendingItemUpdates)
		{
			TrackedItemSnapshot oldSnapshot = itemSnapshotLookup.get(item);
			if (item.matchesSnapshot(oldSnapshot))
			{
				continue;
			}

			itemSnapshotLookup.put(item, item.createSnapshot());
			eventBus.post(new ItemUpdated(item));
		}

		pendingItemUpdates.clear();
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

	public void clearProfile()
	{
		itemLookup.clear();
		itemSnapshotLookup.clear();
		pendingItemUpdates.clear();
	}

	public void loadProfile(ProfileManager profileManager)
	{
		clearProfile();

		for (int itemID : profileManager.readTrackedItemIDs())
		{
			createTrackedItem(itemID);
		}

		for (TrackedContainer container : TrackedContainer.values())
		{
			refreshContainer(container);
		}

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
				if (containerItemBaseID == trackedItem.getItemID())
				{
					trackedItem.increaseContainerCounter(container, containerItem.getQuantity());
					pendingItemUpdates.add(trackedItem);
				}
			}
		}

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
		TrackedContainer.fromItemContainer(event.getItemContainer())
			.ifPresent(this::refreshContainer);
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
				pendingItemUpdates.add(trackedItem);
			}
		}
	}
}