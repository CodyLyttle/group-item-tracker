package com.groupitemtracker.panels;

import java.awt.GridLayout;
import java.util.Comparator;
import java.util.TreeMap;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.PluginPanel;

public class SortedItemsPanel<TKey, TItemPanel extends PluginPanel> extends PluginPanel
{
	private final TreeMap<TKey, TItemPanel> _itemPanels;

	public SortedItemsPanel(Comparator<TKey> keySorter)
	{
		_itemPanels = new TreeMap<>(keySorter);
		setLayout(new GridLayout(0, 1, 0, 4));
		setBorder(new EmptyBorder(0,0,0,0));
	}

	public void addItem(TKey key, TItemPanel itemPanel)
	{
		if (_itemPanels.containsKey(key))
		{
			throw new IllegalArgumentException("An item panel already exists for key: " + key.toString());
		}

		_itemPanels.put(key, itemPanel);
		final int sortedIndex = _itemPanels.headMap(key).size();
		add(itemPanel, sortedIndex);
	}

	public void removeItem(TKey key)
	{
		final TItemPanel itemPanel = _itemPanels.remove(key);
		if (itemPanel == null)
		{
			throw new IllegalArgumentException("An item panel doesn't exist for key: " + key.toString());
		}

		remove(itemPanel);
	}

	public void clearItems()
	{
		_itemPanels.clear();
		removeAll();
	}

	public boolean containsKey(TKey key)
	{
		return _itemPanels.containsKey(key);
	}

	public TItemPanel getItemPanel(TKey key)
	{
		final TItemPanel itemPanel = _itemPanels.get(key);
		if (itemPanel == null)
		{
			throw new IllegalArgumentException("An item panel doesn't exist for key: " + key.toString());
		}

		return itemPanel;
	}
}
