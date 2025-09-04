package com.clanbroadcasts;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ClanBroadcastsPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(ClanBroadcastsPlugin.class);
		RuneLite.main(args);
	}
}