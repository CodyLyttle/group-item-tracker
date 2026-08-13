package com.groupitemtracker;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.util.ArrayList;
import java.util.Collection;
import java.util.NoSuchElementException;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.RuneLite;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.externalplugins.ExternalPluginManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static com.groupitemtracker.TrackedContainer.*;
import static com.groupitemtracker.ItemTracker.ItemAdded;
import static com.groupitemtracker.ItemTracker.ItemRemoved;
import static com.groupitemtracker.ItemTracker.ItemsUpdated;
import static com.groupitemtracker.ItemTracker.Invalidated;
import static com.groupitemtracker.ItemTracker.SyncedWithBank;

@RunWith(MockitoJUnitRunner.class)
public class GroupItemTrackerPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(GroupItemTrackerPlugin.class);
		RuneLite.main(args);
	}

	private static final class ItemWithVariant
	{
		public final int baseID;
		public final int variantID;

		public ItemWithVariant(int baseID, int variantID)
		{
			this.baseID = baseID;
			this.variantID = variantID;
		}
	}

	private static final ItemWithVariant AHRIM = new ItemWithVariant(ItemID.BARROWS_AHRIM_HEAD, ItemID.BARROWS_AHRIM_HEAD_100);
	private static final ItemWithVariant DHAROK = new ItemWithVariant(ItemID.BARROWS_DHAROK_BODY, ItemID.BARROWS_DHAROK_BODY_50);
	private static final ItemWithVariant TORAG = new ItemWithVariant(ItemID.BARROWS_TORAG_LEGS, ItemID.BARROWS_TORAG_LEGS_BROKEN);

	@Bind
	private final Client client = mock(Client.class);

	@Bind
	private final EventBus eventBus = new EventBus();

	@Mock
	@Bind
	private ItemIdentifier itemIdentifier;

	@Inject
	private ItemTracker sut;

	private MessageSink msgSink;

	private FakeItemContainers containers;

	@Before
	public void before()
	{
		Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);

		msgSink = new MessageSink();
		eventBus.register(msgSink);
		eventBus.register(sut);

		// By default, items are treated as non-variant and are passed without processing.
		when(itemIdentifier.getBaseID(anyInt())).thenAnswer(x -> x.getArgument(0, Integer.class));
		when(itemIdentifier.getBaseID(AHRIM.variantID)).thenReturn(AHRIM.baseID);
		when(itemIdentifier.getBaseID(DHAROK.variantID)).thenReturn(DHAROK.baseID);
		when(itemIdentifier.getBaseID(TORAG.variantID)).thenReturn(TORAG.baseID);

		containers = new FakeItemContainers();
		when(client.getItemContainer(anyInt())).thenAnswer(x -> containers.getContainerByID(x.getArgument(0, Integer.class)));
	}

	@After
	public void after()
	{
		eventBus.unregister(msgSink);
		eventBus.unregister(sut);
	}

	@Test
	public void loadItems()
	{
		containers.setAll(AHRIM.baseID, 1, 2, 3);
		containers.setAll(AHRIM.variantID, 4, 5, 6);
		containers.setAll(DHAROK.baseID, 10, 20, 30);
		containers.setAll(DHAROK.variantID, 40, 50, 60);

		// By base ID, by variant ID, ignores duplicates.
		sut.loadItems(new int[]{AHRIM.baseID, AHRIM.variantID, DHAROK.variantID, DHAROK.baseID, AHRIM.baseID});
		var items = listOf(msgSink.take(Invalidated.class).getItems());
		Assert.assertEquals(2, items.size());
		assertItemCounts(AHRIM, takeFromList(items, AHRIM));
		assertItemCounts(DHAROK, takeFromList(items, DHAROK));
		assertIsTracking(AHRIM);
		assertIsTracking(DHAROK);

		// Clears existing items.
		sut.loadItems(new int[]{});
		msgSink.take(Invalidated.class);
		Assert.assertEquals(0, sut.getItems().size());
		assertIsNotTracking(AHRIM);
		assertIsNotTracking(DHAROK);
	}

	@Test
	public void startTracking()
	{
		containers.setAll(AHRIM.baseID, 1, 2, 3);
		containers.setAll(AHRIM.variantID, 4, 5, 6);
		containers.setAll(DHAROK.baseID, 10, 20, 30);
		containers.setAll(DHAROK.variantID, 40, 50, 60);

		// By base ID.
		sut.startTracking(AHRIM.baseID);
		assertItemCounts(AHRIM, msgSink.take(ItemAdded.class).getItem());
		assertIsTracking(AHRIM);

		// By variant ID.
		sut.startTracking(DHAROK.variantID);
		assertItemCounts(DHAROK, msgSink.take(ItemAdded.class).getItem());
		assertIsTracking(DHAROK);

		// Ignore items that are already being tracked.
		sut.startTracking(AHRIM.variantID);
		sut.startTracking(DHAROK.baseID);
		assertNoMessage(ItemAdded.class);
	}

	@Test
	public void stopTracking()
	{
		sut.loadItems(new int[]{AHRIM.baseID, DHAROK.baseID, TORAG.baseID});

		// By base ID.
		sut.stopTracking(AHRIM.baseID);
		assertItemCounts(AHRIM, msgSink.take(ItemRemoved.class).getItem());
		assertIsNotTracking(AHRIM);

		// By variant ID.
		sut.stopTracking(DHAROK.variantID);
		assertItemCounts(DHAROK, msgSink.take(ItemRemoved.class).getItem());
		assertIsNotTracking(DHAROK);

		// Ignores untracked items.
		sut.stopTracking(AHRIM.baseID);
		sut.stopTracking(AHRIM.variantID);
		sut.stopTracking(DHAROK.baseID);
		sut.stopTracking(DHAROK.variantID);
		assertNoMessage(ItemRemoved.class);

		// Doesn't remove other items.
		assertIsTracking(TORAG);
	}

	@Test
	public void reset()
	{
		sut.loadItems(new int[]{AHRIM.baseID, DHAROK.baseID});
		sut.startTracking(TORAG.baseID);
		msgSink.messages.clear();

		sut.reset();
		Assert.assertEquals(0, msgSink.take(Invalidated.class).getItems().size());
		assertIsNotTracking(AHRIM);
		assertIsNotTracking(DHAROK);
		assertIsNotTracking(TORAG);
	}

	@Test
	public void sendsMessageOnInitialBankSync()
	{
		// Initial sync.
		postItemContainerChanged(BANK);
		msgSink.take(SyncedWithBank.class);
		Assert.assertTrue(sut.isSyncedWithBank());

		// Already synced.
		postItemContainerChanged(BANK);
		assertNoMessage(SyncedWithBank.class);
		Assert.assertTrue(sut.isSyncedWithBank());

		// After reset.
		sut.reset();
		Assert.assertFalse(sut.isSyncedWithBank());
		postItemContainerChanged(BANK);
		msgSink.take(SyncedWithBank.class);
		Assert.assertTrue(sut.isSyncedWithBank());
	}

	@Test
	public void updatesItemsOnGameTick()
	{
		sut.loadItems(new int[]{AHRIM.baseID, DHAROK.baseID, TORAG.baseID});

		// Individual container & item updates.
		for (var kind : TrackedContainer.values())
		{
			// By base ID.
			containers.set(AHRIM.baseID, kind, 1);
			postItemContainerChanged(kind).postGameTick();
			var items = listOf(msgSink.take(ItemsUpdated.class).getItems());
			Assert.assertEquals(1, items.size());
			assertItemCounts(AHRIM, items.get(0));

			// By variantID.
			containers.set(AHRIM.variantID, kind, 10);
			postItemContainerChanged(kind).postGameTick();
			items = listOf(msgSink.take(ItemsUpdated.class).getItems());
			Assert.assertEquals(1, items.size());
			assertItemCounts(AHRIM, items.get(0));
		}

		// Multiple container and item updates.
		containers.setAll(AHRIM.baseID, 0, 0, 0);
		containers.setAll(AHRIM.variantID, 0, 0, 0);
		containers.setAll(DHAROK.baseID, 10, 20, 30);
		containers.setAll(DHAROK.variantID, 40, 50, 60);
		containers.setAll(TORAG.baseID, 100, 200, 300);
		containers.setAll(TORAG.variantID, 400, 500, 600);
		postAllItemContainersChanged().postGameTick();
		var items = listOf(msgSink.take(ItemsUpdated.class).getItems());
		Assert.assertEquals(3, items.size());
		assertItemCounts(AHRIM, takeFromList(items, AHRIM));
		assertItemCounts(DHAROK, takeFromList(items, DHAROK));
		assertItemCounts(TORAG, takeFromList(items, TORAG));
	}

	@Test
	public void ignoresPlaceholderItems()
	{
		when(itemIdentifier.isPlaceholder(AHRIM.baseID)).thenReturn(true);
		when(itemIdentifier.isPlaceholder(DHAROK.variantID)).thenReturn(true);

		// Ignores pre-existing placeholders.
		containers.setAll(AHRIM.baseID, 999, 999, 999);
		containers.setAll(DHAROK.variantID, 999, 999, 999);
		sut.loadItems(new int[]{AHRIM.baseID});
		sut.startTracking(DHAROK.variantID);
		for (var item : sut.getItems())
		{
			Assert.assertEquals(0, item.countAll());
		}

		// Ignores changes to placeholders.
		containers.set(AHRIM.baseID, BANK, 1);
		containers.set(DHAROK.variantID, BANK, 1);
		postItemContainerChanged(BANK).postGameTick();
		assertNoMessage(ItemsUpdated.class);

		// Doesn't ignore changes to real items.
		containers.set(AHRIM.variantID, BANK, 2);
		containers.set(DHAROK.baseID, BANK, 5);
		postItemContainerChanged(BANK).postGameTick();
		var items = listOf(msgSink.take(ItemsUpdated.class).getItems());
		Assert.assertEquals(2, takeFromList(items, AHRIM).bankCount);
		Assert.assertEquals(5, takeFromList(items, DHAROK).bankCount);
	}

	@Test
	public void fixupBankWhenClosedDuringDeposit()
	{
		containers.set(AHRIM.baseID, BANK, 1);
		containers.set(AHRIM.baseID, EQUIPMENT, 4);
		containers.set(DHAROK.variantID, INVENTORY, 10);
		sut.loadItems(new int[]{AHRIM.baseID, DHAROK.baseID});

		// Notify ItemTracker that the bank has closed and items have "disappeared" from their respective containers.
		containers.set(AHRIM.baseID, EQUIPMENT, 0);
		containers.set(DHAROK.variantID, INVENTORY, 5);
		postBankClosed().postAllItemContainersChanged().postGameTick();

		// Update containers to reflect the expected fixed-up values.
		containers.set(AHRIM.baseID, BANK, 5);
		containers.set(DHAROK.variantID, BANK, 5);
		var items = listOf(msgSink.take(ItemsUpdated.class).getItems());
		assertItemCounts(AHRIM, takeFromList(items, AHRIM));
		assertItemCounts(DHAROK, takeFromList(items, DHAROK));
	}

	@Test
	public void fixupBankWhenClosedDuringWithdraw()
	{
		containers.set(AHRIM.baseID, BANK, 1);
		containers.set(AHRIM.baseID, EQUIPMENT, 9);
		containers.set(DHAROK.baseID, INVENTORY, 5);
		containers.set(DHAROK.variantID, BANK, 10);
		sut.loadItems(new int[]{AHRIM.baseID, DHAROK.baseID});

		// Notify ItemTracker that the bank has closed and items have "appeared" in their respective containers.
		containers.set(AHRIM.baseID, EQUIPMENT, 10);
		containers.set(DHAROK.variantID, INVENTORY, 5);
		postBankClosed().postAllItemContainersChanged().postGameTick();

		// Update containers to reflect the expected fixed-up values.
		containers.set(AHRIM.baseID, BANK, 0);
		containers.set(DHAROK.variantID, BANK, 5);
		var items = listOf(msgSink.take(ItemsUpdated.class).getItems());
		assertItemCounts(AHRIM, takeFromList(items, AHRIM));
		assertItemCounts(DHAROK, takeFromList(items, DHAROK));
	}

	private void assertItemCounts(ItemWithVariant item, TrackedItemSnapshot actual)
	{
		int bank = containers.count(item.baseID, BANK) + containers.count(item.variantID, BANK);
		int equipment = containers.count(item.baseID, EQUIPMENT) + containers.count(item.variantID, EQUIPMENT);
		int inventory = containers.count(item.baseID, INVENTORY) + containers.count(item.variantID, INVENTORY);

		Assert.assertEquals(bank, actual.bankCount);
		Assert.assertEquals(equipment, actual.equipmentCount);
		Assert.assertEquals(inventory, actual.inventoryCount);
	}

	private void assertIsTracking(ItemWithVariant item)
	{
		Assert.assertTrue(sut.isTracking(item.baseID));
		Assert.assertTrue(sut.isTracking(item.variantID));
	}

	private void assertIsNotTracking(ItemWithVariant item)
	{
		Assert.assertFalse(sut.isTracking(item.baseID));
		Assert.assertFalse(sut.isTracking(item.variantID));
	}

	private <T> void assertNoMessage(Class<T> type)
	{
		Assert.assertFalse(msgSink.has(type));
	}

	private ArrayList<TrackedItemSnapshot> listOf(Collection<TrackedItemSnapshot> items)
	{
		return new ArrayList<>(items);
	}

	private TrackedItemSnapshot takeFromList(ArrayList<TrackedItemSnapshot> items, ItemWithVariant item)
	{
		for (var i = 0; i < items.size(); i++)
		{
			int itemID = items.get(i).itemID;
			if (itemID == item.baseID || itemID == item.variantID)
			{
				return items.remove(i);
			}
		}

		throw new NoSuchElementException();
	}

	private GroupItemTrackerPluginTest postAllItemContainersChanged()
	{
		for (var kind : TrackedContainer.values())
		{
			postItemContainerChanged(kind);
		}

		return this;
	}

	private GroupItemTrackerPluginTest postItemContainerChanged(TrackedContainer kind)
	{
		eventBus.post(new ItemContainerChanged(kind.containerID, containers.containers.get(kind)));

		return this;
	}

	private void postGameTick()
	{
		eventBus.post(new GameTick());
	}

	private GroupItemTrackerPluginTest postBankClosed()
	{
		eventBus.post(new WidgetClosed(InterfaceID.BANKMAIN, 0, true));
		return this;
	}
}