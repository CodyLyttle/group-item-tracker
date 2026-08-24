package com.groupitemtracker;

import java.util.*;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Data;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

@Singleton
public class ItemTracker
{
	@Data
	public static final class ItemAdded
	{
		private final TrackedItemSnapshot item;
	}

	@Data
	public static final class ItemRemoved
	{
		private final TrackedItemSnapshot item;
	}

	@Data
	public static final class ItemsUpdated
	{
		private final Collection<TrackedItemSnapshot> items;
	}

	@Data
	public static final class Invalidated
	{
		private final Collection<TrackedItemSnapshot> items;
	}

	public static final class SyncedWithBank
	{
		// Signal message, no data.
	}

	private final Client client;
	private final EventBus eventBus;
	private final ItemIdentifier identifier;
	private Map<Integer, TrackedItem> items = new HashMap<>();
	private Map<Integer, TrackedItemSnapshot> snapshots = new HashMap<>();
	private boolean bankClosedLastTick = false;
	private boolean sharedBankClosedLastTick = false;
	private boolean containerHasChanged = false;

	@Getter
	private boolean syncedWithBank = false;

	@Inject
	public ItemTracker(Client client, EventBus eventBus, ItemIdentifier identifier)
	{
		this.client = client;
		this.eventBus = eventBus;
		this.identifier = identifier;
	}

	public Collection<TrackedItemSnapshot> getItems()
	{
		return Collections.unmodifiableCollection(snapshots.values());
	}

	public int[] exportItemIDs()
	{
		var ids = new int[items.size()];
		int i = 0;

		for (var item : items.values())
		{
			ids[i] = item.realID;
			i++;
		}

		return ids;
	}

	public boolean isTracking(int id)
	{
		int baseID = identifier.getBaseID(id);
		return items.containsKey(baseID);
	}

	public void loadItems(int[] itemIDs)
	{
		resetState();

		for (int itemID : itemIDs)
		{
			int baseID = identifier.getBaseID(itemID);
			if (!items.containsKey(baseID))
			{
				items.put(baseID, new TrackedItem(baseID, itemID, identifier.getName(itemID)));
			}
		}

		for (var kind : TrackedContainer.values())
		{
			ItemContainer container = client.getItemContainer(kind.containerID);
			if (container != null)
			{
				refreshContainer(kind, container);
				if (!syncedWithBank && kind == TrackedContainer.BANK)
				{
					syncedWithBank = true;
					eventBus.post(new SyncedWithBank());
				}
			}
		}

		var outgoingSnapshots = new ArrayList<TrackedItemSnapshot>(items.size());
		for (TrackedItem item : items.values())
		{
			var snapshot = new TrackedItemSnapshot(item);
			outgoingSnapshots.add(snapshot);
			snapshots.put(item.baseID, snapshot);
		}

		eventBus.post(new Invalidated(Collections.unmodifiableCollection(outgoingSnapshots)));
	}

	public void startTracking(int itemID)
	{
		int baseID = identifier.getBaseID(itemID);
		if (items.containsKey(baseID))
		{
			return;
		}

		// Set initial item count for all available containers.
		var item = new TrackedItem(baseID, itemID, identifier.getName(itemID));
		for (var kind : TrackedContainer.values())
		{
			var container = client.getItemContainer(kind.containerID);
			if (container == null)
			{
				continue;
			}

			int count = 0;
			for (var containerItem : container.getItems())
			{
				int id = containerItem.getId();
				if (identifier.getBaseID(id) == baseID && !identifier.isPlaceholder(id))
				{
					count += containerItem.getQuantity();
				}
			}
			item.counters.put(kind, count);
		}

		items.put(baseID, item);
		var snapshot = new TrackedItemSnapshot(item);
		snapshots.put(baseID, snapshot);
		eventBus.post(new ItemAdded(snapshot));
	}

	public void stopTracking(int itemID)
	{
		int baseID = identifier.getBaseID(itemID);
		if (items.remove(baseID) != null)
		{
			TrackedItemSnapshot removedSnapshots = snapshots.remove(baseID);
			eventBus.post(new ItemRemoved(removedSnapshots));
		}
	}

	public void reset()
	{
		resetState();
		eventBus.post(new Invalidated(new ArrayList<>()));
	}

	public void freeExcessMemory()
	{
		items = new HashMap<>(0);
		snapshots = new HashMap<>(0);
	}

	@Subscribe
	private void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			bankClosedLastTick = true;
		}
		else if (event.getGroupId() == InterfaceID.SHARED_BANK)
		{
			sharedBankClosedLastTick = true;
		}
	}

	@Subscribe
	private void onItemContainerChanged(ItemContainerChanged event)
	{
		var kind = TrackedContainer.fromContainerID(event.getContainerId());
		if (kind != null)
		{
			refreshContainer(kind, event.getItemContainer());
			containerHasChanged = true;
			if (!syncedWithBank && kind == TrackedContainer.BANK)
			{
				syncedWithBank = true;
				eventBus.post(new SyncedWithBank());
			}
		}
	}

	@Subscribe
	private void onGameTick(GameTick event)
	{
		if (containerHasChanged)
		{
			// Don't allocate until we match a changed item.
			ArrayList<TrackedItemSnapshot> updatedSnapshots = null;

			for (TrackedItem item : items.values())
			{
				var snapshot = snapshots.get(item.baseID);
				if (snapshot.hasMatchingContainerCounts(item))
				{
					continue;
				}

				// Handle edge-case where transferring an item and closing the bank on the same tick caused counter desync.
				// e.g. Deposited item is decremented from inventory but not incremented in bank.
				// Note: Immediately closing the interface with ESC still incurs desync, can this be improved further?
				// Note: The adjustment isn't made if the shared bank was closed, as this results in further desync.
				if (bankClosedLastTick && !sharedBankClosedLastTick)
				{
					int prevTotal = snapshot.countAll();
					int currentTotal = item.countAll();
					int error = currentTotal - prevTotal;
					int corrected = item.counters.get(TrackedContainer.BANK) - error;
					item.counters.put(TrackedContainer.BANK, corrected);
				}

				snapshot = new TrackedItemSnapshot(item);
				snapshots.put(item.baseID, snapshot);

				if (updatedSnapshots == null)
				{
					// Big enough to avoid resize in most cases.
					updatedSnapshots = new ArrayList<>(8);
				}
				updatedSnapshots.add(snapshot);
			}

			if (updatedSnapshots != null)
			{
				eventBus.post(new ItemsUpdated(Collections.unmodifiableCollection(updatedSnapshots)));
			}
		}

		bankClosedLastTick = false;
		sharedBankClosedLastTick = false;
		containerHasChanged = false;
	}

	private void resetState()
	{
		bankClosedLastTick = false;
		sharedBankClosedLastTick = false;
		containerHasChanged = false;
		syncedWithBank = false;
		items.clear();
		snapshots.clear();
	}

	private void refreshContainer(TrackedContainer kind, ItemContainer container)
	{
		assert kind.containerID == container.getId();

		for (TrackedItem item : items.values())
		{
			item.counters.put(kind, 0);
		}

		for (Item containerItem : container.getItems())
		{
			int id = containerItem.getId();
			TrackedItem item = items.get(identifier.getBaseID(id));
			if (item != null && !identifier.isPlaceholder(id))
			{
				int count = item.counters.get(kind) + containerItem.getQuantity();
				item.counters.put(kind, count);
			}
		}
	}
}