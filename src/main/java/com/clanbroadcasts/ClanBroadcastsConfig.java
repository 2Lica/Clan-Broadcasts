package com.clanbroadcasts;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("clanbroadcasts")
public interface ClanBroadcastsConfig extends Config
{
	@ConfigItem(
			keyName = "levelWebhook",
			name = "Level-up Webhook",
			description = "Discord webhook URL for level-up messages"
	)
	default String levelWebhook()
	{
		return "";
	}

	@ConfigItem(
			keyName = "cofferWebhook",
			name = "Clan Coffer Webhook",
			description = "Discord webhook URL for coffer messages"
	)
	default String cofferWebhook()
	{
		return "";
	}

	@ConfigItem(
			keyName = "questWebhook",
			name = "Quest Webhook",
			description = "Discord webhook URL for quest completions"
	)
	default String questWebhook()
	{
		return "";
	}

	@ConfigItem(
			keyName = "lootWebhook",
			name = "Loot/Kill Webhook",
			description = "Discord webhook URL for loot or kill events"
	)
	default String lootWebhook()
	{
		return "";
	}

	@ConfigItem(
			keyName = "diaryWebhook",
			name = "Achievement Diary Webhook",
			description = "Discord webhook URL for diary completions"
	)
	default String diaryWebhook()
	{
		return "";
	}

	@ConfigItem(
			keyName = "generalWebhook",
			name = "General Webhook",
			description = "Discord webhook URL for miscellaneous clan events"
	)
	default String generalWebhook()
	{
		return "";
	}

	@ConfigItem(
			keyName = "sendScreenshot",
			name = "Send Screenshot",
			description = "Include a screenshot with each event"
	)
	default boolean sendScreenshot()
	{
		return true;
	}

	@ConfigItem(
			keyName = "fullClientScreenshot",
			name = "Full Client Screenshot",
			description = "Capture the full game client window instead of just the chatbox"
	)
	default boolean fullClientScreenshot()
	{
		return true;
	}
}
