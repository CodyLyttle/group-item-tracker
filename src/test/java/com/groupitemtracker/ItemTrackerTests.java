package com.groupitemtracker;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.groupitemtracker.helpers.TrackedContainerTestBuilder;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.eventbus.EventBus;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.ArgumentMatchers.anyInt;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ItemTrackerTests
{
	private static final int BASE_ID = ItemID.BLOOD_MOON_CHESTPLATE;
	private static final int VARIANT_ID = ItemID.BLOOD_MOON_CHESTPLATE_BROKEN;

	@Bind
	private final Client client = mock(Client.class);

	@Bind
	private final EventBus eventBus = new EventBus();

	@Mock
	@Bind
	private ItemIdentifier itemIdentifier;

	@Inject
	private ItemTracker sut;

	private TrackedContainerTestBuilder testBuilder;

	@Before
	public void before()
	{
		Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
		eventBus.register(sut);

		testBuilder = new TrackedContainerTestBuilder(sut);
		when(client.getItemContainer(anyInt()))
			.thenAnswer(invocation -> testBuilder.getItemContainer(invocation.getArgument(0)));

		// By default, treat all item IDs as base IDs.
		when(itemIdentifier.getBaseID(anyInt()))
			.thenAnswer(invocation -> invocation.getArguments()[0]);

		when(itemIdentifier.getBaseID(VARIANT_ID))
			.thenReturn(BASE_ID);
	}

	@Test
	public void containsItem()
	{
		sut.addItem(1);
		Assert.assertTrue(sut.containsItem(1));
		Assert.assertFalse(sut.containsItem(2));
	}

	@Test
	public void containsItem_falseForRemovedItems()
	{
		sut.addItem(1);
		sut.removeItem(1);
		Assert.assertFalse(sut.containsItem(1));
	}

	@Test
	public void containsItem_trueForVariantItems()
	{
		sut.addItem(BASE_ID);
		Assert.assertTrue(sut.containsItem(VARIANT_ID));
	}

	@Test(expected = IllegalArgumentException.class)
	public void addItem_throwsWhenItemAlreadyTracked()
	{
		sut.addItem(1);
		sut.addItem(1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void addItem_throwsWhenVariantAlreadyTracked()
	{
		sut.addItem(BASE_ID);
		sut.addItem(VARIANT_ID);
	}

	@Test
	public void addItem_usesBaseItemID()
	{
		TrackedItem item = sut.addItem(VARIANT_ID);
		Assert.assertEquals(BASE_ID, item.getItemID());
	}

	@Test
	public void addItem_initializesContainerCounters()
	{
		var itemID = 1;
		testBuilder.selectItemByID(itemID).inBank(1).inEquipment(10).inInventory(100);

		TrackedItem item = sut.addItem(itemID);

		testBuilder.assertStateOfTrackedItems(item);
	}

	@Test(expected = IllegalArgumentException.class)
	public void removeItem_throwsWhenRemovingUntrackedItem()
	{
		sut.addItem(1);
		sut.removeItem(2);
	}

	@Test
	public void removeItem_onlyRemovesExpectedItem()
	{
		TrackedItem firstItem = sut.addItem(1);
		TrackedItem secondItem = sut.addItem(2);

		sut.removeItem(firstItem.getItemID());

		List<TrackedItem> items = new ArrayList<>(sut.getItems());
		Assert.assertEquals(1, items.size());
		Assert.assertTrue(items.contains(secondItem));
	}

	@Test
	public void removeItem_byVariantID()
	{
		sut.addItem(BASE_ID);

		sut.removeItem(VARIANT_ID);

		Assert.assertTrue(sut.getItems().isEmpty());
	}

	@Test
	public void reset_clearsTrackedItems()
	{
		sut.addItem(1);

		sut.reset();

		Assert.assertTrue(sut.getItems().isEmpty());
		Assert.assertFalse(sut.containsItem(1));
	}

	@Test
	public void reset_canReAddItems()
	{
		sut.addItem(1);
		sut.reset();

		// Doesn't throw item already exists exception.
		sut.addItem(1);
	}

	@Test
	public void onItemContainerChanged_onlyUpdatesTargetContainer()
	{
		TrackedItem item = sut.addItem(1);
		testBuilder.selectItem(item).inBank(1).inEquipment(1).inInventory(1);

		testBuilder.invokeContainerChangedEvent(TrackedContainer.BANK);

		Assert.assertEquals(1, item.getTotalCount());
		Assert.assertEquals(1, item.getContainerCount(TrackedContainer.BANK));
		Assert.assertEquals(0, item.getContainerCount(TrackedContainer.EQUIPMENT));
		Assert.assertEquals(0, item.getContainerCount(TrackedContainer.INVENTORY));
	}

	@Test
	public void onItemContainerChanged_resetsCounterForMissingItems()
	{
		TrackedItem item = sut.addItem(1);
		testBuilder.selectItem(item).inBank(10).invokeContainerChangedEvent(TrackedContainer.BANK);

		testBuilder.removeItem(TrackedContainer.BANK).invokeContainerChangedEvent(TrackedContainer.BANK);

		Assert.assertEquals(0, item.getContainerCount(TrackedContainer.BANK));
	}

	@Test
	public void onItemContainerChanged_ignoresPlaceholderItems()
	{
		TrackedItem item = sut.addItem(1);
		when(itemIdentifier.isPlaceholder(item.getItemID())).thenReturn(true);

		testBuilder.selectItem(item).inBank(10).invokeContainerChangedEvent(TrackedContainer.BANK);

		Assert.assertEquals(0, item.getContainerCount(TrackedContainer.BANK));
	}

	@Test
	public void onItemContainerChanged_usesSumOfVariants()
	{
		TrackedItem item = sut.addItem(BASE_ID);

		testBuilder.selectItemByID(BASE_ID).inBank(1)
			.selectItemByID(VARIANT_ID).inBank(2)
			.invokeContainerChangedEvent(TrackedContainer.BANK);

		Assert.assertEquals(3, item.getContainerCount(TrackedContainer.BANK));
	}

	@Test
	public void onItemContainerChanged_maintainsCorrectStateAcrossMultipleUpdates()
	{
		TrackedItem firstItem = sut.addItem(1);
		TrackedItem secondItem = sut.addItem(2);

		// Assert that items have individual quantities.
		testBuilder.selectItem(firstItem).inBank(1).inEquipment(10).inInventory(100)
			.selectItem(secondItem).inBank(2).inEquipment(20).inInventory(200)
			.invokeContainerChangedEventAllContainers()
			.assertStateOfTrackedItems(firstItem, secondItem);

		// Assert that a change to one item doesn't  affect another.
		testBuilder.selectItem(firstItem).inEquipment(100)
			.invokeContainerChangedEvent(TrackedContainer.EQUIPMENT)
			.assertStateOfTrackedItems(firstItem, secondItem);

		// Assert that adding an item doesn't affect existing item states.
		TrackedItem thirdItem = sut.addItem(3);
		testBuilder.selectItem(thirdItem).inBank(10).inEquipment(5)
			.invokeContainerChangedEventAllContainers()
			.assertStateOfTrackedItems(firstItem, secondItem, thirdItem);
	}
}