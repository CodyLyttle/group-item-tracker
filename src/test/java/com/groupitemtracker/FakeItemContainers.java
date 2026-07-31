package com.groupitemtracker;

import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FakeItemContainers
{
	public final EnumMap<TrackedContainer, ItemContainer> containers = new EnumMap<>(TrackedContainer.class);
	public final EnumMap<TrackedContainer, Map<Integer, Item>> containerItems = new EnumMap<>(TrackedContainer.class);

	public FakeItemContainers()
	{
		for (var kind : TrackedContainer.values())
		{
			var fake = mock(ItemContainer.class);
			when(fake.getId()).thenAnswer(x -> kind.containerID);
			when(fake.getItems()).thenAnswer(x -> containerItems.get(kind).values().toArray(new Item[0]));

			containers.put(kind, fake);
			containerItems.put(kind, new HashMap<>());
		}
	}

	public ItemContainer getContainerByID(int containerID)
	{
		for (var kind : TrackedContainer.values())
		{
			if (kind.containerID == containerID)
			{
				return containers.get(kind);
			}
		}
		return null;
	}

	public int count(int itemID, TrackedContainer kind)
	{
		var item = containerItems.get(kind).get(itemID);
		return item == null ? 0 : item.getQuantity();
	}

	public void set(int itemID, TrackedContainer kind, int qty)
	{
		containerItems.get(kind).put(itemID, new Item(itemID, qty));
	}

	public void setAll(int itemID, int bankQty, int equipQty, int invQty)
	{
		set(itemID, TrackedContainer.BANK, bankQty);
		set(itemID, TrackedContainer.EQUIPMENT, equipQty);
		set(itemID, TrackedContainer.INVENTORY, invQty);
	}
}
