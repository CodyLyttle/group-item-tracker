package com.groupitemtracker.sidebar;

import com.groupitemtracker.TrackedItem;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.Comparator;
import java.util.TreeMap;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

public class ItemPanelContainer extends PluginPanel
{
	private static final Comparator<TrackedItem> itemComparer = Comparator.comparing(TrackedItem::getItemName);
	private final TreeMap<TrackedItem, ItemPanel> items = new TreeMap<>(itemComparer);

	private final String header;
	private final JLabel headerLabel;
	private final JPanel itemContainer;
	private final CollapseExpandButton collapseExpandButton;

	public ItemPanelContainer(String header)
	{
		this.header = header;

		final var headerPanel = new JPanel();
		headerPanel.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));
		headerPanel.setLayout(new BorderLayout());
		headerPanel.setBackground(ColorScheme.BORDER_COLOR);
		headerLabel = new JLabel(createHeaderString());
		collapseExpandButton = new CollapseExpandButton(true, this::collapseItems, this::expandItems);
		headerPanel.add(headerLabel);
		headerPanel.add(collapseExpandButton, BorderLayout.EAST);

		itemContainer = new JPanel(new GridLayout(0, 1, 0, 1));
		itemContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

		setBorder(BorderFactory.createEmptyBorder());
		setLayout(new BorderLayout());
		add(headerPanel, BorderLayout.NORTH);
		add(itemContainer, BorderLayout.CENTER);
	}

	private void collapseItems()
	{
		itemContainer.removeAll();
		itemContainer.revalidate();
		itemContainer.repaint();
	}

	private void expandItems()
	{
		for (ItemPanel itemPanel : items.values())
		{
			itemContainer.add(itemPanel);
		}

		itemContainer.revalidate();
		itemContainer.repaint();
	}

	public void addItemPanel(ItemPanel itemPanel)
	{
		final TrackedItem item = itemPanel.getItem();
		if (items.containsKey(item))
		{
			throw new IllegalArgumentException("An item panel already exists for item: " + item.toString());
		}

		items.put(item, itemPanel);
		headerLabel.setText(createHeaderString());
		if (collapseExpandButton.isExpanded())
		{
			final int sortedIndex = items.headMap(item).size();
			itemContainer.add(itemPanel, sortedIndex);
		}
	}

	public void removeItemPanel(ItemPanel itemPanel)
	{
		final TrackedItem item = itemPanel.getItem();
		if (items.remove(item) == null)
		{
			throw new IllegalArgumentException("An item panel doesn't exist for item: " + item.toString());
		}

		headerLabel.setText(createHeaderString());
		if (collapseExpandButton.isExpanded())
		{
			itemContainer.remove(itemPanel);
		}
	}

	public void clearItems()
	{
		items.clear();
		itemContainer.removeAll();
	}

	public void refresh()
	{
		setVisible(items.size() > 0);
		revalidate();
		repaint();
	}

	public boolean containsItem(TrackedItem item)
	{
		return items.containsKey(item);
	}

	public ItemPanel getItemPanel(TrackedItem item)
	{
		final ItemPanel itemPanel = items.get(item);
		if (itemPanel == null)
		{
			throw new IllegalArgumentException("An item panel doesn't exist for item: " + item.toString());
		}

		return itemPanel;
	}

	private String createHeaderString()
	{
		return header + " (" + items.size() + ")";
	}
}