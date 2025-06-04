package com.groupitemtracker;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TrackedItemTests
{
	private final TrackedItem sut = new TrackedItem(123);

	@Test
	public void containerCountersStartAtZero()
	{
		Assert.assertEquals(0, sut.getTotalCount());
		for (var container : TrackedContainer.values())
		{
			Assert.assertEquals(0, sut.getContainerCount(container));
		}
	}

	@Test
	public void increaseContainerCounter()
	{
		sut.increaseContainerCounter(TrackedContainer.EQUIPMENT, 1);
		sut.increaseContainerCounter(TrackedContainer.INVENTORY, 2);
		sut.increaseContainerCounter(TrackedContainer.INVENTORY, 3);

		Assert.assertEquals(0, sut.getContainerCount(TrackedContainer.BANK));
		Assert.assertEquals(1, sut.getContainerCount(TrackedContainer.EQUIPMENT));
		Assert.assertEquals(5, sut.getContainerCount(TrackedContainer.INVENTORY));
		Assert.assertEquals(6, sut.getTotalCount());
	}

	@Test
	public void resetContainerCounter()
	{
		sut.increaseContainerCounter(TrackedContainer.BANK, 1);
		sut.increaseContainerCounter(TrackedContainer.EQUIPMENT, 2);

		sut.resetContainerCounter(TrackedContainer.BANK);

		Assert.assertEquals(0, sut.getContainerCount(TrackedContainer.BANK));
		Assert.assertEquals(2, sut.getContainerCount(TrackedContainer.EQUIPMENT));
		Assert.assertEquals(2, sut.getTotalCount());
	}
}
