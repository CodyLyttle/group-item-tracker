package com.groupitemtracker;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.groupitemtracker.helpers.FakeTrackedContainerBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.util.OSXUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.ArgumentMatchers.anyInt;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ItemTrackerTests
{
	private static final int BASE_ID = ItemID.BLOOD_MOON_CHESTPLATE;
	private static final int VARIANT_ID = ItemID.BLOOD_MOON_CHESTPLATE_BROKEN;

	@Bind
	@Mock
	private TrackedContainerReader containerReader;

	@Mock
	@Bind
	private ItemIdentifier itemIdentifier;

	@Inject
	private ItemTracker sut;


	@Before
	public void before()
	{
		Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);

		// By default, treat all item IDs as base IDs.
		when(itemIdentifier.getBaseID(anyInt()))
			.thenAnswer(invocation -> invocation.getArguments()[0]);

		when(itemIdentifier.getBaseID(VARIANT_ID))
			.thenReturn(BASE_ID);
	}

	@Test(expected = IllegalArgumentException.class)
	public void throwsWhenItemAlreadyTracked()
	{
		sut.addItem(1);
		sut.addItem(1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void throwsWhenVariantAlreadyTracked()
	{
		sut.addItem(BASE_ID);
		sut.addItem(VARIANT_ID);
	}

	@Test(expected = IllegalArgumentException.class)
	public void throwsWhenRemovingUntrackedItem()
	{
		sut.addItem(1);
		sut.removeItem(2);
	}

	@Test
	public void getItems()
	{
		final TrackedItem firstItem = sut.addItem(1);
		final TrackedItem secondItem = sut.addItem(2);
		final TrackedItem thirdItem = sut.addItem(3);
		sut.removeItem(secondItem.getItemID());

		final Collection<TrackedItem> items = sut.getItems();

		Assert.assertEquals(2, items.size());
		Assert.assertTrue(items.contains(firstItem));
		Assert.assertTrue(items.contains(thirdItem));
	}

	@Test
	public void containsItem()
	{
		final TrackedItem firstItem = sut.addItem(1);
		final TrackedItem secondItem = sut.addItem(BASE_ID);

		// True for tracked items.
		Assert.assertTrue(sut.containsItem(firstItem.getItemID()));
		Assert.assertTrue(sut.containsItem(secondItem.getItemID()));

		// True when variant of tracked item. 
		Assert.assertTrue(sut.containsItem(VARIANT_ID));

		// False for removed items.
		sut.removeItem(firstItem.getItemID());
		Assert.assertFalse(sut.containsItem(firstItem.getItemID()));

		// False for untracked items.
		Assert.assertFalse(sut.containsItem(99));
	}

	@Test
	public void addItem()
	{
		TrackedItem firstItem = sut.addItem(VARIANT_ID);
		TrackedItem secondItem = sut.addItem(2);

		// Track by base ID.
		Assert.assertEquals(BASE_ID, firstItem.getItemID());
		Assert.assertEquals(2, secondItem.getItemID());

		// Initializes container counters.
		new FakeTrackedContainerBuilder(containerReader)
			.in(TrackedContainer.BANK).set(3, 5)
			.in(TrackedContainer.INVENTORY).set(3, 10);
		final TrackedItem thirdItem = sut.addItem(3);
		assertTrackedItemState(firstItem, 0, 0, 0);
		assertTrackedItemState(secondItem, 0, 0, 0);
		assertTrackedItemState(thirdItem, 5, 0, 10);
	}

	@Test
	public void removeItem()
	{
		final TrackedItem itemA = sut.addItem(1);
		final TrackedItem itemB = sut.addItem(2);
		sut.addItem(BASE_ID);

		sut.removeItem(itemA.getItemID()); // remove by ID
		sut.removeItem(VARIANT_ID); // remove by variant ID.
		List<TrackedItem> items = new ArrayList<>(sut.getItems());

		Assert.assertEquals(1, items.size());
		Assert.assertTrue(items.contains(itemB));
	}

	@Test
	public void refreshContainer()
	{
		final int firstID = 1;
		final int secondID = 2;
		TrackedItem firstItem = sut.addItem(1);
		TrackedItem secondItem = sut.addItem(2);

		final var builder = new FakeTrackedContainerBuilder(containerReader);
		builder.in(TrackedContainer.BANK).set(firstID, 1).set(secondID, 10)
			.in(TrackedContainer.EQUIPMENT).set(firstID, 4).set(secondID, 40)
			.in(TrackedContainer.INVENTORY).set(firstID, 5).set(secondID, 50);

		// Only refresh supplied container.
		sut.refreshContainer(TrackedContainer.BANK);
		assertTrackedItemState(firstItem, 1, 0, 0);
		assertTrackedItemState(secondItem, 10, 0, 0);

		// Maintains correct value after multiple updates.
		sut.refreshContainer(TrackedContainer.EQUIPMENT);
		sut.refreshContainer(TrackedContainer.INVENTORY);
		builder.in(TrackedContainer.BANK).set(firstID, 2).set(secondID, 20);
		sut.refreshContainer(TrackedContainer.BANK);
		assertTrackedItemState(firstItem, 2, 4, 5);
		assertTrackedItemState(secondItem, 20, 40, 50);

		// Do not modify counters for unavailable container.
		builder.in(TrackedContainer.INVENTORY).set(firstID, 99);
		builder.setContainerAvailability(TrackedContainer.INVENTORY, false);
		sut.refreshContainer(TrackedContainer.INVENTORY);
		assertTrackedItemState(firstItem, 2, 4, 5);

		// Missing items set counter to zero.
		builder.in(TrackedContainer.BANK).remove(secondID);
		sut.refreshContainer(TrackedContainer.BANK);
		assertTrackedItemState(secondItem, 0, 40, 50);
	}

	private void assertTrackedItemState(TrackedItem item, int bank, int equipment, int inventory)
	{
		Assert.assertEquals(bank, item.getContainerCount(TrackedContainer.BANK));
		Assert.assertEquals(equipment, item.getContainerCount(TrackedContainer.EQUIPMENT));
		Assert.assertEquals(inventory, item.getContainerCount(TrackedContainer.INVENTORY));
		Assert.assertEquals(bank + equipment + inventory, item.getTotalCount());
	}
}