package com.clanbroadcasts;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.*;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import javax.inject.Inject;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;

@Slf4j
@PluginDescriptor(
		name = "Clan Broadcasts",
		description = "Sends clan broadcasts to Discord webhooks with role icons",
		tags = {"clan", "discord", "broadcast"}
)
public class ClanBroadcastsPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClanBroadcastsConfig config;

	private static final DateTimeFormatter TIME_FORMATTER =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

	private final Cache<String, Boolean> sentEvents = CacheBuilder.newBuilder()
			.expireAfterWrite(30, TimeUnit.SECONDS)
			.build();

	private final Map<String, String> memberRoleCache = new HashMap<>();

	private static final java.util.logging.Logger PLUGIN_LOGGER = java.util.logging.Logger.getLogger("ClanBroadcastsPlugin");
	private static final String DEFAULT_ICON_URL = "https://imgur.com/6VPdU9D";

	static
	{
		try
		{
			java.nio.file.Path logPath = java.nio.file.Paths.get(System.getProperty("user.home"), ".runelite", "logs", "ClanBroadcastsPlugin.log");
			FileHandler fh = new FileHandler(logPath.toString(), true);
			fh.setFormatter(new SimpleFormatter());
			PLUGIN_LOGGER.addHandler(fh);
		}
		catch (Exception e)
		{
			System.err.println("Failed to initialize plugin log: " + e.getMessage());
		}
	}

	@Provides
	ClanBroadcastsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ClanBroadcastsConfig.class);
	}

	// ------------------ Plugin Startup & Shutdown ------------------
	@Override
	protected void startUp()
	{
		PLUGIN_LOGGER.info("Clan Broadcasts plugin started.");
		fetchWiseOldManMembers();
	}

	@Override
	protected void shutDown()
	{
		PLUGIN_LOGGER.info("Clan Broadcasts plugin stopped.");
	}

	// ------------------ Fetch members ------------------
	private void fetchWiseOldManMembers()
	{
		try
		{
			int groupId = config.groupId();
			URL url = new URL("https://api.wiseoldman.net/v2/groups/" + groupId);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");

			try (InputStreamReader reader = new InputStreamReader(conn.getInputStream()))
			{
				JsonParser parser = new JsonParser();
				JsonElement rootElement = parser.parse(reader);
				JsonObject json = rootElement.getAsJsonObject();
				JsonArray memberships = json.getAsJsonArray("memberships");

				for (JsonElement memberElem : memberships)
				{
					JsonObject membership = memberElem.getAsJsonObject();
					JsonObject player = membership.getAsJsonObject("player");
					String displayName = player.get("displayName").getAsString();
					String role = membership.get("role").getAsString();
					String key = displayName.replace('\u00A0', ' ').trim().toLowerCase();
					memberRoleCache.put(key, role);
					PLUGIN_LOGGER.info("Loaded member: " + displayName + " with role: " + role);
				}
			}

			PLUGIN_LOGGER.info("Loaded " + memberRoleCache.size() + " members from WOM group " + groupId);
		}
		catch (Exception e)
		{
			PLUGIN_LOGGER.warning("Failed to fetch WOM members: " + e.getMessage());
		}
	}

	// ------------------ Icon Assignment ------------------
	private String getPlayerIconUrl(String displayName)
	{
		if (displayName == null || displayName.trim().isEmpty())
			return DEFAULT_ICON_URL;

		String normalizedName = displayName.replace('\u00A0', ' ').trim().toLowerCase();
		String role = memberRoleCache.getOrDefault(normalizedName, "general");
		String iconUrl = "https://raw.githubusercontent.com/2Lica/Clan-Broadcasts/master/src/main/resources/icons/" + role.toLowerCase() + ".png";

		if (isUrlAccessible(iconUrl))
		{
			PLUGIN_LOGGER.info("Assigned icon for " + displayName + " (role: " + role + "): " + iconUrl);
			return iconUrl;
		}
		else
		{
			PLUGIN_LOGGER.warning("Icon URL not accessible for role: " + role + " -> Using default icon");
			return DEFAULT_ICON_URL;
		}
	}

	private boolean isUrlAccessible(String urlString)
	{
		try
		{
			URL url = new URL(urlString);
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("HEAD");
			connection.setConnectTimeout(3000);
			connection.setReadTimeout(3000);
			return connection.getResponseCode() == 200;
		}
		catch (Exception e)
		{
			return false;
		}
	}

	// ------------------ Username Extraction ------------------
	private String extractUsername(String message, String defaultUsername)
	{
		if (message == null || message.isEmpty())
			return defaultUsername;

		message = message.replace('\u00A0', ' ').trim();

		if (message.contains("|"))
			message = message.substring(message.indexOf("|") + 1).trim();

		String username = null;

		if (message.contains(" has "))
			username = message.substring(0, message.indexOf(" has ")).trim();
		else if (message.contains(" received "))
			username = message.substring(0, message.indexOf(" received ")).trim();
		else if (message.contains(" completed "))
			username = message.substring(0, message.indexOf(" completed ")).trim();
		else
			username = message.split(" ")[0].trim();

		if (username.isEmpty())
			username = defaultUsername;

		return username;
	}

	// ------------------ Chat Message Listener ------------------
	@Subscribe
	public void onChatMessage(ChatMessage chatMessage)
	{
		String message = chatMessage.getMessage();
		if (message == null) return;

		message = message.replace('\u00A0', ' ').trim();

		// Extract username from chatMessage.getName(), fallback to message parsing
		String username = chatMessage.getName();
		if (username != null && username.contains("|"))
		{
			username = username.substring(username.indexOf("|") + 1).trim();
		}

		if (username == null || username.isEmpty())
		{
			username = extractUsername(message, "Clan Member");
		}

		// ------------------ !testicon command ------------------
		if (message.equalsIgnoreCase("!testicon"))
		{
			if (sentEvents.getIfPresent("!testicon_" + username) != null)
			{
				PLUGIN_LOGGER.info("Duplicate !testicon skipped for " + username);
				return;
			}

			sendBroadcastToRelay(
					"Test Icon",
					username,
					"Test icon for " + username,
					config.generalWebhook(),
					0x3498db,
					getPlayerIconUrl(username)
			);
			sentEvents.put("!testicon_" + username, true);
			PLUGIN_LOGGER.info("Triggered !testicon for " + username);
			return;
		}

		// Only process clan messages
		if (chatMessage.getType() != ChatMessageType.CLAN_MESSAGE) return;
		if (message.contains("To talk in your clan's channel") ||
				message.startsWith("[WELCOME]") ||
				message.startsWith("[DIDYOUKNOW]") ||
				message.startsWith("[FRIENDSCHAT") ||
				message.startsWith("[SPAM]")) return;

		String description = message;

		// ------------------ Determine Event Type ------------------
		String lc = description.toLowerCase();
		String eventType;
		String webhookUrl;
		int color;

		if (lc.contains("received a drop") || (lc.contains("received a clue item") && lc.contains("coins")))
		{
			eventType = "Valuable Drops";
			webhookUrl = config.webhookValuableDrops();
			color = 0x00FF00;
		}
		else if (lc.contains("collection log"))
		{
			eventType = "Collection Log";
			webhookUrl = config.webhookCollectionLog();
			color = 0xFFA500;
		}
		else if (lc.contains("a funny feeling"))
		{
			eventType = "Pets";
			webhookUrl = config.webhookPets();
			color = 0xFF69B4;
		}
		else if (lc.contains("combat task") || lc.contains("personal best") || lc.contains("combat achievement"))
		{
			eventType = "PVM";
			webhookUrl = config.webhookPVM();
			color = 0x808080;
		}
		else if (lc.contains("has been defeated") || lc.contains("has defeated") || lc.contains("loot key"))
		{
			eventType = "PVP";
			webhookUrl = config.webhookPVP();
			color = 0x808080;
		}
		else if ((lc.contains("has reached") && lc.contains("level")) || (lc.contains("reached") && lc.contains("xp")) || lc.contains("reached a total level"))
		{
			eventType = "Level";
			webhookUrl = config.webhookLevel();
			color = 0xFFFF00;
		}
		else if (lc.contains("completed a quest") || lc.contains("achievement diary"))
		{
			eventType = "Quests and Achievement Diary";
			webhookUrl = config.webhookQuests();
			color = 0x3498DB;
		}
		else if (lc.contains("level 99") || lc.contains("combat level 126") || lc.contains("fire cape") || lc.contains("infernal cape") || lc.contains("dizana's quiever") || lc.contains("champion's cape") || lc.contains("total level of 2277!"))
		{
			eventType = "99s and Capes";
			webhookUrl = config.webhook99sAndCapes();
			color = 0x800080;
		}
		else if ((lc.contains("has deposited") && lc.contains("into the coffer")))
		{
			eventType = "Clan's Coffer";
			webhookUrl = config.webhookClansCoffer();
			color = 0x00FF00;
		}
		else if ((lc.contains("has withdrawn") && lc.contains("from the coffer")))
		{
			eventType = "Clan's Coffer";
			webhookUrl = config.webhookClansCoffer();
			color = 0xFF0000;
		}
		else
		{
			eventType = "General";
			webhookUrl = config.generalWebhook();
			color = 0x3498DB;
		}

		// ------------------ Send Embed ------------------
		sendBroadcastToRelay(eventType, username, description, webhookUrl, color, getPlayerIconUrl(username));
	}

	// ------------------ Send to Discord ------------------
	private void sendBroadcastToRelay(String title, String authorName, String description, String webhookUrl, int color, String iconUrl)
	{
		try
		{
			JsonObject embed = new JsonObject();
			embed.addProperty("title", title);
			embed.addProperty("description", description);
			embed.addProperty("color", color);

			JsonObject author = new JsonObject();
			author.addProperty("name", authorName);
			author.addProperty("icon_url", iconUrl);
			embed.add("author", author);

			JsonObject footer = new JsonObject();
			footer.addProperty("text", "Event Time: " + TIME_FORMATTER.format(Instant.now()));
			embed.add("footer", footer);

			JsonArray embeds = new JsonArray();
			embeds.add(embed);

			JsonObject payload = new JsonObject();
			payload.add("embeds", embeds);

			postToWebhook(webhookUrl, payload.toString());
			PLUGIN_LOGGER.info("Sent embed to relay: " + payload.toString());
		}
		catch (Exception e)
		{
			PLUGIN_LOGGER.warning("Failed to send embed: " + e.getMessage());
		}
	}

	private void postToWebhook(String webhookUrl, String payload)
	{
		try
		{
			URL url = new URL(webhookUrl);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/json");
			conn.setDoOutput(true);
			conn.getOutputStream().write(payload.getBytes());
			int responseCode = conn.getResponseCode();
			PLUGIN_LOGGER.info("Successfully sent to relay, response code: " + responseCode);
		}
		catch (Exception e)
		{
			PLUGIN_LOGGER.warning("Failed to post webhook: " + e.getMessage());
		}
	}
}
