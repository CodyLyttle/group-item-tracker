package com.groupitemtracker.helpers;

import com.groupitemtracker.TrackedContainer;
import com.groupitemtracker.TrackedContainerReader;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.runelite.api.Item;
import static org.mockito.Mockito.when;

// Fluent API helper class simplifies the setup of fake tracked container contents.
public class FakeTrackedContainerBuilder
{
	private final Set<TrackedContainer> availableContainers = new HashSet<>();
	private final EnumMap<TrackedContainer, Map<Integer, Item>> containerLookup =
		new EnumMap<>(TrackedContainer.class);

	private TrackedContainer selectedContainer = null;

	public FakeTrackedContainerBuilder(TrackedContainerReader reader)
	{
		for (TrackedContainer container : TrackedContainer.values())
		{
			availableContainers.add(container);
			containerLookup.put(container, new HashMap<>());

			when(reader.getItems(container))
				.thenAnswer(x -> getFakeItems(container));
		}
	}

	private Optional<Item[]> getFakeItems(TrackedContainer container)
	{
		if (availableContainers.contains(container))
		{
			Collection<Item> items = containerLookup.get(container).values();
			return Optional.of(items.toArray(new Item[0]));
		}

		return Optional.empty();
	}

	// Selects the tracked container to be faked.
	public FakeTrackedContainerBuilder in(TrackedContainer container)
	{
		selectedContainer = container;
		return this;
	}

	// Available containers return their contents, unavailable containers return Optional.Empty().
	public FakeTrackedContainerBuilder setContainerAvailability(TrackedContainer container, boolean isAvailable)
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

	// Add or update the quantity of an item in the currently selected container.
	public FakeTrackedContainerBuilder set(int itemID, int quantity)
	{
		assert selectedContainer != null;
		assert quantity > 0;

		Map<Integer, Item> containerItems = containerLookup.get(selectedContainer);
		containerItems.put(itemID, new Item(itemID, quantity));
		return this;
	}

	// Remove an item from the currently selected container.
	public FakeTrackedContainerBuilder remove(int itemID)
	{
		assert (selectedContainer != null);

		Map<Integer, Item> containerItems = containerLookup.get(selectedContainer);
		containerItems.remove(itemID);
		return this;
	}
}
