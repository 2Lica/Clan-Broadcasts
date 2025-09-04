package com.clanbroadcasts;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanRank;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import javax.inject.Inject;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.Graphics;
import java.awt.Canvas;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Slf4j
@PluginDescriptor(
		name = "Clan Broadcast",
		description = "Sends clan events (loot, kills, diaries, levels, quests) to Discord with embeds and optional screenshots",
		tags = {"clan", "discord", "broadcast", "chat", "embed", "loot", "kills", "diary"}
)
public class ClanBroadcastsPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClanBroadcastsConfig config;

	@Inject
	private ClientThread clientThread;

	private final Cache<String, Boolean> sentEvents = CacheBuilder.newBuilder()
			.maximumSize(200)
			.expireAfterWrite(10, TimeUnit.MINUTES)
			.build();

	@Provides
	ClanBroadcastsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ClanBroadcastsConfig.class);
	}

	@Override
	protected void startUp()
	{
		log.info("Clan Broadcast Discord Plugin started");
	}

	@Override
	protected void shutDown()
	{
		log.info("Clan Broadcast Discord Plugin stopped");
		sentEvents.invalidateAll();
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		ChatMessageType type = event.getType();
		String message = event.getMessage();
		String username = event.getName() != null ? event.getName() : "Clan System";

		if (type == ChatMessageType.CLAN_MESSAGE)
		{
			try
			{
				String eventType;
				String webhookUrl;
				int color;

				if (message.matches(".*has reached level \\d+ in .*"))
				{
					eventType = "Level-Up";
					webhookUrl = config.levelWebhook();
					color = 0xFFD700;
				}
				else if (message.contains("has deposited") || message.contains("has withdrawn"))
				{
					eventType = "Clan Coffer";
					webhookUrl = config.cofferWebhook();
					color = 0x1E90FF;
				}
				else if (message.contains("has completed a quest"))
				{
					eventType = "Quest Completed";
					webhookUrl = config.questWebhook();
					color = 0xFF4500;
				}
				else if (message.contains("has defeated") || message.contains("has looted"))
				{
					eventType = "Loot/Kill";
					webhookUrl = config.lootWebhook();
					color = 0x00FF00;
				}
				else if (message.contains("has completed the") && message.contains("Achievement Diary"))
				{
					eventType = "Diary Completed";
					webhookUrl = config.diaryWebhook();
					color = 0x800080;
				}
				else
				{
					eventType = "General Broadcast";
					webhookUrl = config.generalWebhook();
					color = 0x808080;
				}

				sendGenericEmbed(eventType, username, message, webhookUrl, color);
			}
			catch (Exception e)
			{
				log.warn("Error processing clan message", e);
			}
		}
	}

	private void sendGenericEmbed(String eventType, String username, String message, String webhookUrl, int color)
	{
		if (webhookUrl == null || webhookUrl.isEmpty()) return;

		String hash = generateHash(username + message + eventType);
		if (sentEvents.getIfPresent(hash) != null) return;
		sentEvents.put(hash, true);

		try
		{
			String iconPath = getPlayerIcon(username);
			String iconDataUri = iconPath != null ? getIconBase64(iconPath) : null;

			String imageJson = "";
			if (config.sendScreenshot())
			{
				BufferedImage screenshot = captureScreenshot(config.fullClientScreenshot());
				if (screenshot != null)
				{
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					ImageIO.write(screenshot, "png", baos);
					String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());
					imageJson = ", \"image\":{\"url\":\"data:image/png;base64," + base64 + "\"}";
				}
			}

			String payload = "{"
					+ "\"embeds\":[{"
					+ "\"title\":\"" + eventType + "\","
					+ "\"description\":\"" + message + "\","
					+ "\"color\":" + color + ","
					+ "\"author\":{"
					+ "\"name\":\"" + username + "\""
					+ (iconDataUri != null ? ",\"icon_url\":\"" + iconDataUri + "\"" : "")
					+ "},"
					+ "\"footer\":{\"text\":\"Event Time: " + Instant.now() + "\"}"
					+ imageJson
					+ "}]"
					+ "}";

			postToWebhook(webhookUrl, payload);
		}
		catch (Exception e)
		{
			log.warn("Failed to send embed", e);
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
			try (OutputStream os = conn.getOutputStream())
			{
				os.write(payload.getBytes());
			}
			conn.getResponseCode();
		}
		catch (Exception e)
		{
			log.warn("Failed to post to webhook", e);
		}
	}

	private BufferedImage captureScreenshot(boolean fullClient)
	{
		try
		{
			Canvas canvas = client.getCanvas();
			BufferedImage screenshot = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_ARGB);
			Graphics g = screenshot.getGraphics();
			canvas.paint(g);
			g.dispose();

			if (!fullClient)
			{
				java.awt.Rectangle bounds = client.getWidget(WidgetInfo.CHATBOX).getBounds();
				return screenshot.getSubimage(bounds.x, bounds.y, bounds.width, bounds.height);
			}
			return screenshot;
		}
		catch (Exception e)
		{
			log.warn("Failed to capture screenshot", e);
			return null;
		}
	}

	private String generateHash(String input)
	{
		try
		{
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] hashBytes = md.digest(input.getBytes());
			StringBuilder sb = new StringBuilder();
			for (byte b : hashBytes)
			{
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		}
		catch (Exception e)
		{
			log.warn("Failed to generate hash", e);
			return String.valueOf(input.hashCode());
		}
	}

	private String getIconBase64(String resourcePath)
	{
		try
		{
			BufferedImage image = ImageIO.read(getClass().getResourceAsStream(resourcePath));
			if (image == null) return null;

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ImageIO.write(image, "png", baos);
			return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
		}
		catch (Exception e)
		{
			log.warn("Failed to load icon {}", resourcePath, e);
			return null;
		}
	}

	private String getPlayerIcon(String username)
	{
		try
		{
			// Account type icons
			switch (client.getAccountType())
			{
				case IRONMAN:
					return "/icons/ironman.png";
				case HARDCORE_IRONMAN:
					return "/icons/hardcore_ironman.png";
				case ULTIMATE_IRONMAN:
					return "/icons/ultimate_ironman.png";
				case GROUP_IRONMAN:
					return "/icons/Group_ironman_chat_badge.png";
				default:
					break;
			}

			// Clan rank icons
			ClanMember member = client.getClanSettings().findMember(username);
			if (member != null)
			{
				int rank = member.getRank().getRank(); // use getRank() for integer comparison

				if (rank == ClanRank.JMOD.getRank())
					return "/icons/Clan_icon_-_Moderator.png";
				else if (rank == ClanRank.OWNER.getRank())
					return "/icons/Clan_icon_-_Owner.png";
				else if (rank == ClanRank.DEPUTY_OWNER.getRank())
					return "/icons/Clan_icon_-_Deputy_owner.png";
				else if (rank == ClanRank.ADMINISTRATOR.getRank())
					return "/icons/Clan_icon_-_Administrator.png";
				else if (rank == ClanRank.GUEST.getRank())
					return null; // guest has no icon
				else
					return "/icons/Clan_icon_-_Pawn.png"; // default member icon
			}
		}
		catch (Exception e)
		{
			log.debug("No icon found for {}", username, e);
		}
		return null;
	}
}
