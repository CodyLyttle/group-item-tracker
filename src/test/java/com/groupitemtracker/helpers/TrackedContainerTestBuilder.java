package com.groupitemtracker.helpers;

import com.groupitemtracker.ItemTracker;
import com.groupitemtracker.TrackedContainer;
import com.groupitemtracker.TrackedItem;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ItemContainerChanged;
import org.junit.Assert;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TrackedContainerTestBuilder
{
	private final ItemTracker sut;
	private int selectedItemID = -1;
	private final Set<TrackedContainer> availableContainers = new HashSet<>();
	private final EnumMap<TrackedContainer, Map<Integer, Item>> containerLookup =
		new EnumMap<>(TrackedContainer.class);

	public TrackedContainerTestBuilder(ItemTracker sut)
	{
		this.sut = sut;
		for (TrackedContainer container : TrackedContainer.values())
		{
			availableContainers.add(container);
			containerLookup.put(container, new HashMap<>());
		}
	}

	// Available containers return their contents, unavailable containers return Optional.Empty().
	public TrackedContainerTestBuilder setContainerAvailability(TrackedContainer container, boolean isAvailable)
	{
		if (isAvailable)
		{
			availableContainers.add(container);
		}
		else
		{
			availableContainers.remove(container);
		}

		return this;
	}

	public TrackedContainerTestBuilder selectItemByID(int itemId)
	{
		assert itemId >= 0;
		selectedItemID = itemId;
		return this;
	}

	public TrackedContainerTestBuilder selectItem(TrackedItem item)
	{
		selectedItemID = item.getItemID();
		return this;
	}

	public TrackedContainerTestBuilder inBank(int quantity)
	{
		setSelectedItemCount(TrackedContainer.BANK, quantity);
		return this;
	}

	public TrackedContainerTestBuilder inEquipment(int quantity)
	{
		setSelectedItemCount(TrackedContainer.EQUIPMENT, quantity);
		return this;
	}

	public TrackedContainerTestBuilder inInventory(int quantity)
	{
		setSelectedItemCount(TrackedContainer.INVENTORY, quantity);
		return this;
	}

	private void setSelectedItemCount(TrackedContainer container, int quantity)
	{
		assert selectedItemID >= 0;

		final var item = new Item(selectedItemID, quantity);
		containerLookup.get(container).put(selectedItemID, item);
	}

	public TrackedContainerTestBuilder removeItem(TrackedContainer container)
	{
		assert selectedItemID >= 0;

		Map<Integer, Item> containerItems = containerLookup.get(container);
		containerItems.remove(selectedItemID);
		return this;
	}

	public ItemContainer getItemContainer(int itemContainerID)
	{
		var trackedContainer = TrackedContainer.fromItemContainerID(itemContainerID);
		if (trackedContainer == null || !availableContainers.contains(trackedContainer))
		{
			return null;
		}

		final var itemContainer = mock(ItemContainer.class);
		when(itemContainer.getId()).thenReturn(itemContainerID);
		when(itemContainer.getItems())
			.thenAnswer(invocation -> containerLookup.get(trackedContainer).values()
				.toArray(new Item[0]));

		return itemContainer;
	}

	public TrackedContainerTestBuilder invokeContainerChangedEvent(TrackedContainer container)
	{
		ItemContainer itemContainer = getItemContainer(container.itemContainerID);
		final var event = new ItemContainerChanged(itemContainer.getId(), itemContainer);
		sut.onItemContainerChanged(event);

		return this;
	}

	public TrackedContainerTestBuilder invokeContainerChangedEventAllContainers()
	{
		for (var container : TrackedContainer.values())
		{
			invokeContainerChangedEvent(container);
		}

		return this;
	}

	public TrackedContainerTestBuilder assertStateOfTrackedItems(TrackedItem... trackedItems)
	{
		for (TrackedItem trackedItem : trackedItems)
		{
			int expectedSum = 0;
			for (var entry : containerLookup.entrySet())
			{
				TrackedContainer container = entry.getKey();
				Map<Integer, Item> containerItemLookup = entry.getValue();

				Item item = containerItemLookup.get(trackedItem.getItemID());
				int expectedCount = item != null ? item.getQuantity() : 0;
				int actualCount = trackedItem.getContainerCount(container);
				Assert.assertEquals(expectedCount, trackedItem.getContainerCount(container));
				expectedSum += actualCount;
			}

			Assert.assertEquals(expectedSum, trackedItem.getTotalCount());
		}

		return this;
	}
}
