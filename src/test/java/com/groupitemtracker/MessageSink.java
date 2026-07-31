package com.groupitemtracker;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import net.runelite.client.eventbus.Subscribe;

public class MessageSink
{
	public final List<Object> messages = new ArrayList<>();

	public <T> boolean has(Class<T> type)
	{
		for (Object msg : messages)
		{
			if (type.isInstance(msg))
			{
				return true;
			}
		}

		return false;
	}

	public <T> T take(Class<T> type)
	{
		for (int i = 0; i < messages.size(); i++)
		{
			if (type.isInstance(messages.get(i)))
			{
				return type.cast(messages.remove(i));
			}
		}

		throw new NoSuchElementException();
	}

	@Subscribe
	private void onItemAdded(ItemTracker.ItemAdded event)
	{
		messages.add(event);
	}

	@Subscribe
	private void onItemRemoved(ItemTracker.ItemRemoved event)
	{
		messages.add(event);
	}

	@Subscribe
	private void onItemsUpdated(ItemTracker.ItemsUpdated event)
	{
		messages.add(event);
	}

	@Subscribe
	private void onInvalidated(ItemTracker.Invalidated event)
	{
		messages.add(event);
	}

	@Subscribe
	private void onSyncedWithBank(ItemTracker.SyncedWithBank event)
	{
		messages.add(event);
	}
}
