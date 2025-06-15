package com.groupitemtracker;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

// Handles the highlighting of tracked items in the bank and group ironman bank interfaces.
// Item IDs are cached to avoid unnecessary calls to ItemManager.canonicalize.
public class BankItemOverlay extends WidgetItemOverlay
{
	private static final Color OVERLAY_COLOR = Color.GREEN;

	private final ItemManager itemManager;
	private final ItemTracker itemTracker;
	private final Set<Integer> itemCache = new HashSet<>();

	@Inject
	public BankItemOverlay(ItemManager itemManager, ItemTracker itemTracker)
	{
		this.itemManager = itemManager;
		this.itemTracker = itemTracker;
		showOnBank();
	}

	@Subscribe
	private void onItemContainerChanged(ItemContainerChanged event)
	{
		final int containerID = event.getContainerId();
		if (containerID == InventoryID.BANK || containerID == InventoryID.INV_GROUP_TEMP)
		{
			refreshItemCache(event.getItemContainer());
		}
	}

	// Should be called whenever the item tracked is updated.
	public void refreshItemCache(ItemContainer bankContainer)
	{
		assert bankContainer.getId() == InventoryID.BANK || bankContainer.getId() == InventoryID.INV_GROUP_TEMP;

		itemCache.clear();
		for (Item item : bankContainer.getItems())
		{
			final int itemID = item.getId();
			if (itemTracker.containsItem(itemID))
			{
				itemCache.add(itemID);
			}
		}
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		// Skip placeholders.
		if (widgetItem.getQuantity() == 0)
		{
			return;
		}

		if (itemCache.contains(itemId))
		{
			final Rectangle bounds = widgetItem.getCanvasBounds();
			final BufferedImage outline = itemManager.getItemOutline(itemId, widgetItem.getQuantity(), OVERLAY_COLOR);
			graphics.drawImage(outline, (int) bounds.getX(), (int) bounds.getY(), null);
		}
	}
}