package com.groupitemtracker;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.util.Optional;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.ArgumentMatchers.anyInt;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TrackedContainerReaderTests
{
	@Mock
	@Bind
	private Client client;

	@Inject
	private TrackedContainerReader sut;

	@Before
	public void before()
	{
		Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
	}

	@Test
	public void nullContainerReturnsEmpty()
	{
		when(client.getItemContainer(TrackedContainer.BANK.itemContainerID)).thenReturn(null);

		Assert.assertTrue(sut.getItems(TrackedContainer.BANK).isEmpty());
	}

	@Test
	public void getItemContainerReturnsAllContainerItems()
	{
		final var expectedItems = new Item[]{new Item(1, 1), new Item(2, 2)};
		final var container = mock(ItemContainer.class);
		when(container.getItems()).thenReturn(expectedItems);
		when(client.getItemContainer(anyInt())).thenReturn(container);

		Optional<Item[]> result = sut.getItems(TrackedContainer.BANK);

		Assert.assertTrue(result.isPresent());
		Assert.assertEquals(expectedItems.length, result.get().length);
		for (int i = 0; i < expectedItems.length; i++)
		{
			Assert.assertSame(expectedItems[i], result.get()[i]);
		}
	}
}
