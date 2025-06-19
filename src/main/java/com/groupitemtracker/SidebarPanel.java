package com.groupitemtracker;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.Comparator;
import java.util.TreeMap;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;

public class SidebarPanel extends PluginPanel
{
	private final ItemManager itemManager;
	private final JPanel itemContainer;
	private final TreeMap<TrackedItem, SidebarItemPanel> itemPanelLookup = new TreeMap<>(
		Comparator.comparing(TrackedItem::getItemName));

	public SidebarPanel(ItemManager itemManager)
	{
		this.itemManager = itemManager;

		final var titleLabel = new JLabel();
		titleLabel.setText("Group Item Tracker");
		titleLabel.setFont(FontManager.getRunescapeFont());
		final var titlePanel = new JPanel(new BorderLayout());
		titlePanel.setBorder(new EmptyBorder(8, 12, 8, 12));
		titlePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		titlePanel.add(titleLabel, BorderLayout.CENTER);

		itemContainer = new JPanel(new GridLayout(0, 1, 0, 4));

		setBorder(new EmptyBorder(6, 6, 6, 6));
		setLayout(new DynamicGridLayout(2, 1, 0, 4));
		add(titlePanel, BorderLayout.NORTH);
		add(itemContainer, BorderLayout.CENTER);
	}

	public void addItemPanel(TrackedItem item)
	{
		if (itemPanelLookup.containsKey(item))
		{
			throw new IllegalArgumentException("Panel already exists for item: " + item.getItemName());
		}

		final AsyncBufferedImage image = itemManager.getImage(item.getItemID(), Integer.MAX_VALUE, false);
		final var itemPanel = new SidebarItemPanel(item, item.getItemName(), image);

		itemPanelLookup.put(item, itemPanel);
		final int sortedIndex = itemPanelLookup.headMap(item).size();
		itemContainer.add(itemPanel, sortedIndex);
		itemContainer.revalidate();
		itemContainer.repaint();
	}

	public void removeItemPanel(TrackedItem item)
	{
		final SidebarItemPanel removed = itemPanelLookup.remove(item);
		if (removed == null)
		{
			throw new IllegalArgumentException("Panel doesn't exist for item: " + item.getItemName());
		}

		itemContainer.remove(removed);
		itemContainer.revalidate();
		itemContainer.repaint();
	}

	public void refreshItemPanel(TrackedItem item)
	{
		final SidebarItemPanel itemPanel = itemPanelLookup.get(item);
		if (itemPanel == null)
		{
			throw new IllegalArgumentException("Panel doesn't exist for item: " + item.getItemName());
		}

		itemPanel.refresh();
	}
}