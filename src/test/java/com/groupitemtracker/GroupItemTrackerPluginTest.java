package com.groupitemtracker;

import com.google.inject.testing.fieldbinder.Bind;
import java.util.Optional;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.ArgumentMatchers.anyInt;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class GroupItemTrackerPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(GroupItemTrackerPlugin.class);
		RuneLite.main(args);
	}

	@Mock
	@Bind
	private Client client;

	@Test
	public void testTrackedItem()
	{
		var sut = new TrackedItem(123);

		// counters start at zero. 
		Assert.assertEquals(0, sut.getLocationCount(TrackedLocation.BANK));
		Assert.assertEquals(0, sut.getTotalCount());

		// increase counters.
		sut.increaseLocationCounter(TrackedLocation.EQUIPMENT, 1);
		sut.increaseLocationCounter(TrackedLocation.INVENTORY, 2);
		sut.increaseLocationCounter(TrackedLocation.INVENTORY, 3);
		sut.increaseLocationCounter(TrackedLocation.INVENTORY, 4);
		Assert.assertEquals(0, sut.getLocationCount(TrackedLocation.BANK));
		Assert.assertEquals(1, sut.getLocationCount(TrackedLocation.EQUIPMENT));
		Assert.assertEquals(9, sut.getLocationCount(TrackedLocation.INVENTORY));
		Assert.assertEquals(10, sut.getTotalCount());

		// reset counters to zero.
		sut.resetLocationCounter(TrackedLocation.INVENTORY);
		Assert.assertEquals(0, sut.getLocationCount(TrackedLocation.INVENTORY));
		Assert.assertEquals(1, sut.getTotalCount());
	}

	@Test
	public void testTrackedLocationReader()
	{
		var sut = new TrackedLocationReader(client);

		// returns empty when null container.
		when(client.getItemContainer(anyInt())).thenReturn(null);
		Assert.assertTrue(sut.getItems(TrackedLocation.BANK).isEmpty());

		// returns container items.
		var container = mock(ItemContainer.class);
		var itemA = new Item(1, 1);
		var itemB = new Item(2, 2);
		when(client.getItemContainer(anyInt())).thenReturn(container);
		when(container.getItems()).thenReturn(new Item[]{itemA, itemB});
		
		Optional<Item[]> result = sut.getItems(TrackedLocation.BANK);
		Assert.assertTrue(result.isPresent());
		Assert.assertEquals(2, result.get().length);
		Assert.assertSame(itemA, result.get()[0]);
		Assert.assertSame(itemB, result.get()[1]);
	}
}