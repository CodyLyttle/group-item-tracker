package com.groupitemtracker;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
import org.junit.Assert;
import org.junit.Test;

public class GroupItemTrackerPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(GroupItemTrackerPlugin.class);
		RuneLite.main(args);
	}

	@Test
	public void testTrackedItem()
	{
		var sut = new TrackedItem(123);

		// counters start at zero. 
		Assert.assertEquals(0, sut.getLocationCount(TrackedLocation.BANK));
		Assert.assertEquals(0, sut.getTotalCount());

		// increase counters.
		sut.increaseLocationCounter(TrackedLocation.EQUIPMENT, 1);
		sut.increaseLocationCounter(TrackedLocation.INVENTORY, 2);
		sut.increaseLocationCounter(TrackedLocation.INVENTORY, 3);
		sut.increaseLocationCounter(TrackedLocation.INVENTORY, 4);
		Assert.assertEquals(0, sut.getLocationCount(TrackedLocation.BANK));
		Assert.assertEquals(1, sut.getLocationCount(TrackedLocation.EQUIPMENT));
		Assert.assertEquals(9, sut.getLocationCount(TrackedLocation.INVENTORY));
		Assert.assertEquals(10, sut.getTotalCount());

		// reset counters to zero.
		sut.resetLocationCounter(TrackedLocation.INVENTORY);
		Assert.assertEquals(0, sut.getLocationCount(TrackedLocation.INVENTORY));
		Assert.assertEquals(1, sut.getTotalCount());
	}
}