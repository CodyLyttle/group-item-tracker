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

// Highlights tracked items in the bank and shared bank interfaces.
// Item IDs are cached to avoid unnecessary calls to ItemManager.canonicalize.
public class BankItemOverlay extends WidgetItemOverlay
{
	private final ItemManager itemManager;
	private final ItemTracker itemTracker;
	private final Set<Integer> itemCache = new HashSet<>();
	private final GroupItemTrackerConfig config;
	private boolean isEnabled;
	private Color overlayColor;


	@Inject
	public BankItemOverlay(GroupItemTrackerConfig config, ItemManager itemManager, ItemTracker itemTracker)
	{
		this.config = config;
		this.itemManager = itemManager;
		this.itemTracker = itemTracker;
		isEnabled = config.useBankHighlights();
		overlayColor = config.bankHighlightColor();
		showOnBank();
	}

	public void refreshConfig()
	{
		isEnabled = config.useBankHighlights();
		overlayColor = config.bankHighlightColor();
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

	// Should be called whenever the item tracker is updated.
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
		if (!isEnabled || widgetItem.getQuantity() == 0)
		{
			return;
		}

		if (itemCache.contains(itemId))
		{
			final Rectangle bounds = widgetItem.getCanvasBounds();
			final BufferedImage outline = itemManager.getItemOutline(itemId, widgetItem.getQuantity(), overlayColor);
			graphics.drawImage(outline, (int) bounds.getX(), (int) bounds.getY(), null);
		}
	}
}