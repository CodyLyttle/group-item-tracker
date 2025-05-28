package com.groupitemtracker;

import com.google.inject.testing.fieldbinder.Bind;
import java.util.Collection;
import java.util.Optional;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.game.ItemManager;
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

	@Mock
	@Bind
	private ItemManager itemManager;

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

	@Test
	public void testItemIdentifier()
	{
		var sut = new ItemIdentifier(itemManager);
		when(itemManager.canonicalize(anyInt()))
			.thenAnswer(invocation -> invocation.getArguments()[0]);

		// returns canonicalized base ID.
		when(itemManager.canonicalize(ItemID.SHRIMP + 1)).thenReturn(ItemID.SHRIMP);
		int fromNoted = sut.getBaseID(ItemID.SHRIMP + 1);
		Assert.assertEquals(ItemID.SHRIMP, fromNoted);

		// returns variant base ID.
		Assert.assertEquals(ItemID.SERPENTINE_HELM, sut.getBaseID(ItemID.SERPENTINE_HELM_CHARGED_CYAN));

		// returns base + variant IDs for noted item.
		when(itemManager.canonicalize(ItemID._2DOSEGOADING + 1)).thenReturn(ItemID._2DOSEGOADING);
		Collection<Integer> actualIDs = sut.getVariationIDs(ItemID._2DOSEGOADING);
		Assert.assertTrue(actualIDs.contains(ItemID._1DOSEGOADING));
		Assert.assertTrue(actualIDs.contains(ItemID._2DOSEGOADING));
		Assert.assertTrue(actualIDs.contains(ItemID._3DOSEGOADING));
		Assert.assertTrue(actualIDs.contains(ItemID._4DOSEGOADING));
		Assert.assertEquals(4, actualIDs.size());
	}
}