package com.clanbroadcasts;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("clanBroadcasts")
public interface ClanBroadcastsConfig extends Config
{
	@ConfigItem(
			keyName = "groupId",
			name = "WOM Group ID",
			description = "The Wise Old Man group ID for your clan"
	)
	default int groupId()
	{
		return 0;
	}

	// ------------------ Webhooks ------------------
	@ConfigItem(
			keyName = "webhookGeneral",
			name = "General Webhook",
			description = "Webhook for General broadcasts"
	)
	default String generalWebhook()
	{
		return "";
	}

	@ConfigItem(
			keyName = "webhookValuableDrops",
			name = "Valuable Drops Webhook",
			description = "Webhook for Valuable Drops category"
	)
	default String webhookValuableDrops()
	{
		return "";
	}

	@ConfigItem(
			keyName = "webhookCollectionLog",
			name = "Collection Log Webhook",
			description = "Webhook for Collection Log category"
	)
	default String webhookCollectionLog()
	{
		return "";
	}

	@ConfigItem(
			keyName = "webhookPets",
			name = "Pets Webhook",
			description = "Webhook for Pets category"
	)
	default String webhookPets()
	{
		return "";
	}

	@ConfigItem(
			keyName = "webhookPVM",
			name = "PVM Webhook",
			description = "Webhook for PVM category"
	)
	default String webhookPVM()
	{
		return "";
	}

	@ConfigItem(
			keyName = "webhookPVP",
			name = "PVP Webhook",
			description = "Webhook for PVP category"
	)
	default String webhookPVP()
	{
		return "";
	}

	@ConfigItem(
			keyName = "webhookLevel",
			name = "Level Webhook",
			description = "Webhook for Level-ups and XP broadcasts"
	)
	default String webhookLevel()
	{
		return "";
	}

	@ConfigItem(
			keyName = "webhookQuests",
			name = "Quests and Achievement Diary Webhook",
			description = "Webhook for Quests and Achievement Diary category"
	)
	default String webhookQuests()
	{
		return "";
	}

	@ConfigItem(
			keyName = "webhook99sAndCapes",
			name = "99s and Capes Webhook",
			description = "Webhook for 99s and Capes category"
	)
	default String webhook99sAndCapes()
	{
		return "";
	}

	@ConfigItem(
			keyName = "webhookClansCoffer",
			name = "Clan's Coffer Webhook",
			description = "Webhook for Clan's Coffer deposits and withdrawals"
	)
	default String webhookClansCoffer()
	{
		return "";
	}
}
