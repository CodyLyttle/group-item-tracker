package com.groupitemtracker.panels;

import com.groupitemtracker.TrackedItem;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.Comparator;
import java.util.TreeMap;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

public class ItemPanelContainer extends PluginPanel
{
	private static final Comparator<TrackedItem> itemComparer = Comparator.comparing(TrackedItem::getItemName);

	private final TreeMap<TrackedItem, ItemPanel> _items = new TreeMap<>(itemComparer);
	private final JPanel _itemContainer;
	private final JLabel _headerLabel;
	private final String header;
	private final CollapseExpandButton collapseExpandButton;

	public ItemPanelContainer(String header)
	{
		this.header = header;
		_itemContainer = new JPanel(new GridLayout(0, 1, 0, 4));

		_headerLabel = new JLabel();
		updateHeaderLabel();
		collapseExpandButton = new CollapseExpandButton(true, this::collapseItems, this::expandItems);
		final var headerPanel = new JPanel();
		headerPanel.setBorder(new EmptyBorder(0, 12, 0, 0));
		headerPanel.setLayout(new BorderLayout());
		headerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());
		headerPanel.add(_headerLabel);
		headerPanel.add(collapseExpandButton, BorderLayout.EAST);

		add(headerPanel, BorderLayout.NORTH);
		add(_itemContainer, BorderLayout.CENTER);
		setBorder(new EmptyBorder(6, 0, 0, 0));
	}

	private void collapseItems()
	{
		_itemContainer.removeAll();
		_itemContainer.revalidate();
	}

	private void expandItems()
	{
		for (ItemPanel itemPanel : _items.values())
		{
			_itemContainer.add(itemPanel);
		}
		_itemContainer.revalidate();
	}

	public int getItemCount()
	{
		return _items.values().size();
	}

	public void addItemPanel(ItemPanel itemPanel)
	{
		final TrackedItem item = itemPanel.getItem();
		if (_items.containsKey(item))
		{
			throw new IllegalArgumentException("An item panel already exists for item: " + item.toString());
		}

		_items.put(item, itemPanel);
		updateHeaderLabel();
		if (collapseExpandButton.isExpanded())
		{
			final int sortedIndex = _items.headMap(item).size();
			_itemContainer.add(itemPanel, sortedIndex);
		}
	}

	public void removeItemPanel(ItemPanel itemPanel)
	{
		final TrackedItem item = itemPanel.getItem();
		if (_items.remove(item) == null)
		{
			throw new IllegalArgumentException("An item panel doesn't exist for item: " + item.toString());
		}

		updateHeaderLabel();
		if (collapseExpandButton.isExpanded())
		{
			_itemContainer.remove(itemPanel);
		}
	}

	public void clearItems()
	{
		_items.clear();
		_itemContainer.removeAll();
	}

	public boolean containsItem(TrackedItem item)
	{
		return _items.containsKey(item);
	}

	public ItemPanel getItemPanel(TrackedItem item)
	{
		final ItemPanel itemPanel = _items.get(item);
		if (itemPanel == null)
		{
			throw new IllegalArgumentException("An item panel doesn't exist for item: " + item.toString());
		}

		return itemPanel;
	}

	private void updateHeaderLabel()
	{
		_headerLabel.setText(header + " (" + _items.size() + ")");
	}
}