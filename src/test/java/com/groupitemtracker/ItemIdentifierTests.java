package com.groupitemtracker;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.util.Collection;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ItemIdentifierTests
{
	private static final int BASE_ID = ItemID._4DOSEGOADING;
	private static final int VARIANT_ID = ItemID._1DOSEGOADING;
	private static final int NOTED_BASE_ID = BASE_ID + 1;
	private static final int NOTED_VARIANT_ID = VARIANT_ID + 1;

	@Mock
	@Bind
	private ItemManager itemManager;

	@Inject
	private ItemIdentifier sut;

	@Before
	public void before()
	{
		Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);

		when(itemManager.canonicalize(anyInt())).thenAnswer(invocation -> invocation.getArguments()[0]);
		when(itemManager.canonicalize(NOTED_BASE_ID)).thenReturn(BASE_ID);
		when(itemManager.canonicalize(NOTED_VARIANT_ID)).thenReturn(VARIANT_ID);
	}

	@Test
	public void getBaseID()
	{
		final var params = new int[]{BASE_ID, NOTED_BASE_ID, VARIANT_ID, NOTED_VARIANT_ID};

		for (var id : params)
		{
			int actual = sut.getBaseID(id);
			Assert.assertEquals(BASE_ID, actual);
			verify(itemManager, times(1)).canonicalize(id);
		}
	}

	@Test
	public void getVariantIDs()
	{
		final var params = new int[]{BASE_ID, NOTED_BASE_ID, VARIANT_ID, NOTED_VARIANT_ID};
		final var expectedIds = new int[]{
			ItemID._1DOSEGOADING,
			ItemID._2DOSEGOADING,
			ItemID._3DOSEGOADING,
			ItemID._4DOSEGOADING};

		for (int id : params)
		{
			Collection<Integer> actualIDs = sut.getVariationIDs(id);
			Assert.assertEquals(expectedIds.length, actualIDs.size());
			verify(itemManager, times(1)).canonicalize(id);

			for (var expectedID : expectedIds)
			{
				Assert.assertTrue(actualIDs.contains(expectedID));
			}
		}
	}

	@Test
	public void getItemName()
	{
		final String expectedName = "Climbing boots";
		final var composition = mock(ItemComposition.class);
		when(composition.getMembersName()).thenReturn(expectedName);
		when(itemManager.getItemComposition(any())).thenReturn(composition);

		Assert.assertEquals(expectedName, sut.getName(123));
	}

	@Test
	public void isPlaceholder()
	{
		final int itemID = 10;
		final var itemComposition = mock(ItemComposition.class);
		when(itemComposition.getPlaceholderTemplateId()).thenReturn(-1);
		when(itemManager.getItemComposition(itemID)).thenReturn(itemComposition);
		Assert.assertFalse(sut.isPlaceholder(itemID));

		final int placeholderID = 20;
		final var placeholderComposition = mock(ItemComposition.class);
		when(placeholderComposition.getPlaceholderTemplateId()).thenReturn(14401);
		when(itemManager.getItemComposition(placeholderID)).thenReturn(placeholderComposition);
		Assert.assertTrue(sut.isPlaceholder(placeholderID));
	}
}
