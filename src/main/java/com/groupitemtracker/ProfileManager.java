package com.groupitemtracker;

import com.google.gson.Gson;
import com.groupitemtracker.events.ItemAdded;
import com.groupitemtracker.events.ItemRemoved;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;

public class ProfileManager
{
	public static final String CONFIG_KEY_TRACKED_ITEMS = "tracked-items";
	private final ConfigManager configManager;
	private final Gson gson;
	private final ItemTracker itemTracker;

	@Inject
	public ProfileManager(ConfigManager configManager, Gson gson, ItemTracker itemTracker)
	{
		this.configManager = configManager;
		this.gson = gson;
		this.itemTracker = itemTracker;
	}

	@Subscribe
	private void onItemAdded(ItemAdded event)
	{
		writeTrackedItems();
	}

	@Subscribe
	private void onItemRemoved(ItemRemoved event)
	{
		writeTrackedItems();
	}

	public int[] readTrackedItemIDs()
	{
		final String json = configManager.getRSProfileConfiguration(GroupItemTrackerConfig.GROUP, CONFIG_KEY_TRACKED_ITEMS);
		return json == null ? new int[0] : gson.fromJson(json, int[].class);
	}

	private void writeTrackedItems()
	{
		final List<Integer> itemIDs = itemTracker.getItems().stream()
			.map(TrackedItem::getItemID)
			.collect(Collectors.toList());

		final String json = gson.toJson(itemIDs);
		configManager.setRSProfileConfiguration(GroupItemTrackerConfig.GROUP, CONFIG_KEY_TRACKED_ITEMS, json);
	}
}
