package com.groupitemtracker;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Group Item Tracker"
)
public class GroupItemTrackerPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private GroupItemTrackerConfig config;
	
	@Provides
	GroupItemTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GroupItemTrackerConfig.class);
	}
}
