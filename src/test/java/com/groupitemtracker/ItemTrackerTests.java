package com.groupitemtracker;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.groupitemtracker.events.ItemAdded;
import com.groupitemtracker.events.ItemRemoved;
import com.groupitemtracker.events.ItemUpdated;
import com.groupitemtracker.helpers.TrackedContainerTestBuilder;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.eventbus.EventBus;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import org.mockito.junit.MockitoJUnitRunner;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

// In depth test suite for core logic.
@RunWith(MockitoJUnitRunner.class)
public class ItemTrackerTests
{
	private static final int BASE_ID = ItemID.BLOOD_MOON_CHESTPLATE;
	private static final int VARIANT_ID = ItemID.BLOOD_MOON_CHESTPLATE_BROKEN;

	@Bind
	private final Client client = mock(Client.class);

	@Bind
	@Mock
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

	@Test
	public void addItem_sendsItemAddedEvent()
	{
		var itemID = 1;

		sut.addItem(itemID);

		Mockito.verify(eventBus, times(1)).post(
			argThat((ItemAdded event) -> event.getItem().getItemID() == itemID));
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
	public void removeItem_sendsItemRemovedEvent()
	{
		var itemID = 1;
		sut.addItem(itemID);

		sut.removeItem(itemID);

		Mockito.verify(eventBus, times(1)).post(
			argThat(event -> event instanceof ItemRemoved &&
				((ItemRemoved) event).getItem().getItemID() == itemID));
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

	@Test
	public void onGameTick_sendsEventForEachUpdatedItem()
	{
		TrackedItem firstItem = sut.addItem(1);
		TrackedItem secondItem = sut.addItem(2);
		TrackedItem thirdItem = sut.addItem(3);
		testBuilder.selectItem(firstItem).inBank(1).invokeContainerChangedEvent(TrackedContainer.BANK)
			.selectItem(secondItem).inEquipment(1).invokeContainerChangedEvent(TrackedContainer.EQUIPMENT)
			.selectItem(thirdItem).inInventory(1).invokeContainerChangedEvent(TrackedContainer.INVENTORY)
			// Revert changes before game tick to prevent an ItemUpdated event for thirdItem.
			.removeItem(TrackedContainer.INVENTORY).invokeContainerChangedEvent(TrackedContainer.INVENTORY);
		reset(eventBus);

		sut.onGameTick(new GameTick());

		verify(eventBus, times(1)).post(argThat(event ->
			event instanceof ItemUpdated && ((ItemUpdated) event).getItem() == firstItem));
		verify(eventBus, times(1)).post(argThat(event ->
			event instanceof ItemUpdated && ((ItemUpdated) event).getItem() == secondItem));
		verifyNoMoreInteractions(eventBus);
	}

	@Test
	public void onGameTick_sendsEventWhenItemRemovedFromContainer()
	{
		TrackedItem item = sut.addItem(1);
		testBuilder.selectItem(item).inBank(1).invokeContainerChangedEvent(TrackedContainer.BANK);
		sut.onGameTick(new GameTick());
		reset(eventBus);
		testBuilder.removeItem(TrackedContainer.BANK).invokeContainerChangedEvent(TrackedContainer.BANK);

		sut.onGameTick(new GameTick());

		verify(eventBus, times(1)).post(argThat(event ->
			event instanceof ItemUpdated && ((ItemUpdated) event).getItem() == item));
	}

	@Test
	public void addItems_initializesContainerCounters()
	{
		final int firstID = 1;
		final int secondID = 2;
		testBuilder.selectItemByID(firstID).inBank(1).inEquipment(2).inInventory(3)
			.selectItemByID(secondID).inEquipment(2)
			.invokeContainerChangedEventAllContainers();

		sut.addItems(new int[]{firstID, secondID});

		testBuilder.assertStateOfTrackedItems(sut.getItems().toArray(new TrackedItem[]{}));
	}

	// EDGE-CASES.
	@Test
	public void closeBankWithItemDepositPending()
	{
		// Begin with items in inventory.
		testBuilder.selectItemByID(1).inInventory(4).invokeContainerChangedEvent(TrackedContainer.INVENTORY);
		TrackedItem item = sut.addItem(1);
		reset(eventBus);

		// Transfer from inventory to bank & close bank same tick.
		sut.onWidgetClosed(new WidgetClosed(InterfaceID.BANKMAIN, 0, true));
		testBuilder.inInventory(1).invokeContainerChangedEvent(TrackedContainer.INVENTORY);
		sut.onGameTick(new GameTick());

		Assert.assertEquals(4, item.getTotalCount());
		Assert.assertEquals(3, item.getContainerCount(TrackedContainer.BANK));
		Assert.assertEquals(1, item.getContainerCount(TrackedContainer.INVENTORY));
		verifyItemUpdated(item, 1);
	}

	@Test
	public void closeBankWithItemWithdrawPending()
	{
		// Begin with items in bank.
		testBuilder.selectItemByID(1).inBank(4).invokeContainerChangedEvent(TrackedContainer.BANK);
		TrackedItem item = sut.addItem(1);
		reset(eventBus);

		// Transfer from bank to inventory & close bank same tick.
		sut.onWidgetClosed(new WidgetClosed(InterfaceID.BANKMAIN, 0, true));
		testBuilder.inInventory(3).invokeContainerChangedEvent(TrackedContainer.INVENTORY);
		sut.onGameTick(new GameTick());

		Assert.assertEquals(4, item.getTotalCount());
		Assert.assertEquals(1, item.getContainerCount(TrackedContainer.BANK));
		Assert.assertEquals(3, item.getContainerCount(TrackedContainer.INVENTORY));
		verifyItemUpdated(item, 1);
	}

	@Test
	public void closeBankWithItemEquipPending()
	{
		// Begin with items in bank and inventory
		testBuilder.selectItemByID(1).inBank(2).inInventory(2).invokeContainerChangedEventAllContainers();
		TrackedItem item = sut.addItem(1);
		reset(eventBus);

		// Equip from inventory & close bank same tick.
		sut.onWidgetClosed(new WidgetClosed(InterfaceID.BANKMAIN, 0, true));
		testBuilder.inEquipment(1).inInventory(1).invokeContainerChangedEventAllContainers();
		sut.onGameTick(new GameTick());

		Assert.assertEquals(4, item.getTotalCount());
		Assert.assertEquals(2, item.getContainerCount(TrackedContainer.BANK));
		Assert.assertEquals(1, item.getContainerCount(TrackedContainer.EQUIPMENT));
		Assert.assertEquals(1, item.getContainerCount(TrackedContainer.INVENTORY));
		verifyItemUpdated(item, 1);
	}

	@Test
	public void closeBankWithDepositEquipmentPending()
	{
		// Begin with items equipped. 
		testBuilder.selectItemByID(1).inEquipment(1)
			.selectItemByID(2).inEquipment(1)
			.invokeContainerChangedEventAllContainers();
		TrackedItem firstItem = sut.addItem(1);
		TrackedItem secondItem = sut.addItem(2);
		reset(eventBus);

		// Deposit equipment & close bank same tick.
		sut.onWidgetClosed(new WidgetClosed(InterfaceID.BANKMAIN, 0, true));
		testBuilder.selectItem(firstItem).removeItem(TrackedContainer.EQUIPMENT)
			.selectItem(secondItem).removeItem(TrackedContainer.EQUIPMENT)
			.invokeContainerChangedEvent(TrackedContainer.EQUIPMENT);
		sut.onGameTick(new GameTick());

		Assert.assertEquals(1, firstItem.getTotalCount());
		Assert.assertEquals(1, secondItem.getTotalCount());
		Assert.assertEquals(1, firstItem.getContainerCount(TrackedContainer.BANK));
		Assert.assertEquals(1, secondItem.getContainerCount(TrackedContainer.BANK));
		verifyItemUpdated(firstItem, 1);
		verifyItemUpdated(secondItem, 1);
	}

	private void verifyItemUpdated(TrackedItem expectedItem, int expectedTimes)
	{
		verify(eventBus, times(expectedTimes)).post(argThat(event ->
			event instanceof ItemUpdated && ((ItemUpdated) event).getItem() == expectedItem));
	}
}