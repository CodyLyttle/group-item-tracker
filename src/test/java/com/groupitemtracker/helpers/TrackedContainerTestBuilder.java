package com.groupitemtracker.helpers;

import com.groupitemtracker.ItemTracker;
import com.groupitemtracker.TrackedContainer;
import com.groupitemtracker.TrackedItem;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
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
	private final EnumMap<TrackedContainer, Map<Integer, Item>> fakeContainers = new EnumMap<>(TrackedContainer.class);

	public TrackedContainerTestBuilder(ItemTracker sut)
	{
		this.sut = sut;
		for (var container : TrackedContainer.values())
		{
			fakeContainers.put(container, new HashMap<>());
		}
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

	private void setSelectedItemCount(TrackedContainer trackedContainer, int quantity)
	{
		assert selectedItemID >= 0;

		final var fakeItem = new Item(selectedItemID, quantity);
		fakeContainers.get(trackedContainer).put(selectedItemID, fakeItem);
	}

	public TrackedContainerTestBuilder removeItem(TrackedContainer trackedContainer)
	{
		assert selectedItemID >= 0;

		final Map<Integer, Item> fakeItems = fakeContainers.get(trackedContainer);
		fakeItems.remove(selectedItemID);
		return this;
	}

	public ItemContainer getItemContainer(int containerID)
	{
		var trackedContainer = TrackedContainer.fromContainerID(containerID);
		if (trackedContainer == null)
		{
			return null;
		}

		final var fakeContainer = mock(ItemContainer.class);
		when(fakeContainer.getId()).thenReturn(containerID);
		when(fakeContainer.getItems())
			.thenAnswer(invocation -> fakeContainers.get(trackedContainer).values()
				.toArray(new Item[0]));

		return fakeContainer;
	}

	public TrackedContainerTestBuilder invokeContainerChangedEvent(TrackedContainer trackedContainer)
	{
		ItemContainer fakeContainer = getItemContainer(trackedContainer.containerID);
		final var event = new ItemContainerChanged(fakeContainer.getId(), fakeContainer);
		sut.onItemContainerChanged(event);

		return this;
	}

	public TrackedContainerTestBuilder invokeContainerChangedEventAllContainers()
	{
		for (var trackedContainer : TrackedContainer.values())
		{
			invokeContainerChangedEvent(trackedContainer);
		}

		return this;
	}

	public TrackedContainerTestBuilder assertStateOfTrackedItems(TrackedItem... trackedItems)
	{
		for (TrackedItem item : trackedItems)
		{
			int fakeSum = 0;
			int realSum = item.getTotalCount();
			int itemID = item.getItemID();

			for (var entry : fakeContainers.entrySet())
			{
				final TrackedContainer container = entry.getKey();
				final Map<Integer, Item> fakeContainer = entry.getValue();
				final Item fakeItem = fakeContainer.get(itemID);

				final int fakeCount = fakeItem != null ? fakeItem.getQuantity() : 0;
				final int realCount = item.getContainerCount(container);
				Assert.assertEquals(fakeCount, realCount);
				fakeSum += fakeCount;
			}

			Assert.assertEquals(fakeSum, realSum);
		}

		return this;
	}
}
