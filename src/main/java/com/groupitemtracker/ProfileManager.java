package com.groupitemtracker;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import javax.inject.Inject;
import net.runelite.client.config.ConfigManager;

public class ProfileManager
{
	public static final String CONFIG_KEY_TRACKED_ITEMS = "tracked-items";
	private final ConfigManager configManager;
	private final Gson gson;

	@Inject
	public ProfileManager(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	public int[] readItemIDs()
	{
		String json = configManager.getRSProfileConfiguration(GroupItemTrackerConfig.GROUP, CONFIG_KEY_TRACKED_ITEMS);
		int[] ids = gson.fromJson(json, int[].class);
		return ids == null ? new int[0] : ids;
	}

	public String readItemIDsAsJson()
	{
		String json = configManager.getRSProfileConfiguration(GroupItemTrackerConfig.GROUP, CONFIG_KEY_TRACKED_ITEMS);
		return json == null ? "" : json;
	}

	public void writeItemIDs(int[] ids)
	{
		String json = gson.toJson(ids);
		configManager.setRSProfileConfiguration(GroupItemTrackerConfig.GROUP, CONFIG_KEY_TRACKED_ITEMS, json);
	}

	public boolean tryWriteItemIDsFromJson(String json)
	{
		try
		{
			var ids = gson.fromJson(json, int[].class);
			if (ids != null)
			{
				writeItemIDs(ids);
				return true;
			}
			// Failed.
		}
		catch (JsonSyntaxException ignored)
		{
			// Failed.
		}

		return false;
	}
}