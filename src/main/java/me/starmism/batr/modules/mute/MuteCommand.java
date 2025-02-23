package me.starmism.batr.modules.mute;

import com.google.common.base.Joiner;
import me.starmism.batr.BATR;
import me.starmism.batr.Configuration;
import me.starmism.batr.i18n.I18n;
import me.starmism.batr.modules.BATCommand;
import me.starmism.batr.modules.CommandHandler;
import me.starmism.batr.modules.IModule;
import me.starmism.batr.modules.core.Core;
import me.starmism.batr.modules.core.PermissionManager;
import me.starmism.batr.utils.FormatUtilsKt;
import me.starmism.batr.utils.UtilsKt;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import static com.google.common.base.Preconditions.checkArgument;

public class MuteCommand extends CommandHandler {
	private static Mute mute;
	private static I18n i18n;

	public MuteCommand(final Mute muteModule) {
		super(muteModule);
		mute = muteModule;
		i18n = BATR.getInstance().getI18n();
	}

	@BATCommand.RunAsync
	public static class MuteCmd extends BATCommand {
		public MuteCmd() {
			super("mute", "<player> [server] [reason]",
					"Mute the player on username basis on the specified server permanently or until unbanned.",
					PermissionManager.Action.MUTE.getPermission());
		}

		@Override
		public void onCommand(final CommandSender sender, final String[] args, final boolean confirmedCmd)
				throws IllegalArgumentException {
			if (args[0].equals("help")) {
				FormatUtilsKt.showFormattedHelp(BATR.getInstance().getModules().getModule("mute").getCommands(),
						sender, "MUTE");
				return;
			}
			handleMuteCommand(this, false, false, sender, args, confirmedCmd);
		}
	}

	@BATCommand.RunAsync
	public static class MuteIPCmd extends BATCommand {
		public MuteIPCmd() {
			super(
					"muteip",
					"<player/ip> [server] [reason]",
					"Mute player on an IP basis on the specified server permanently or until unbanned. No player logged in with that IP will be able to speak.",
					PermissionManager.Action.MUTEIP.getPermission());
		}

		@Override
		public void onCommand(final CommandSender sender, final String[] args, final boolean confirmedCmd)
				throws IllegalArgumentException {
			handleMuteCommand(this, false, true, sender, args, confirmedCmd);
		}
	}

	@BATCommand.RunAsync
	public static class GMuteCmd extends BATCommand {
		public GMuteCmd() {
			super(
					"gmute",
					"<name> [reason]",
					"Mute the player on username basis on all servers (the whole network) permanently or until unbanned.",
					PermissionManager.Action.MUTE.getPermission() + ".global");
		}

		@Override
		public void onCommand(final CommandSender sender, final String[] args, final boolean confirmedCmd)
				throws IllegalArgumentException {
			handleMuteCommand(this, true, false, sender, args, confirmedCmd);
		}
	}

	@BATCommand.RunAsync
	public static class GMuteIPCmd extends BATCommand {
		public GMuteIPCmd() {
			super(
					"gmuteip",
					"<player/ip> [reason]",
					"Mute player on an IP basis on all servers (the whole network) permanently or until unbanned. No player logged in with that IP will be able to speak.",
					PermissionManager.Action.MUTEIP.getPermission() + ".global");
		}

		@Override
		public void onCommand(final CommandSender sender, final String[] args, final boolean confirmedCmd)
				throws IllegalArgumentException {
			handleMuteCommand(this, true, true, sender, args, confirmedCmd);
		}
	}

	public static void handleMuteCommand(final BATCommand command, final boolean global, final boolean ipMute,
			final CommandSender sender, final String[] args, final boolean confirmedCmd) {
		String target = args[0];
		String server = IModule.GLOBAL_SERVER;
		final String staff = sender.getName();
		String reason = IModule.NO_REASON;

		final ProxiedPlayer player = ProxyServer.getInstance().getPlayer(target);

		String ip = null;

		String returnedMsg;

		if (global) {
			if (args.length > 1) {
				reason = UtilsKt.getFinalArg(args, 1);
			}
		} else {
			if (args.length == 1) {
				checkArgument(sender instanceof ProxiedPlayer, i18n.format("specifyServer"));
				server = ((ProxiedPlayer) sender).getServer().getInfo().getName();
			} else {
				checkArgument(UtilsKt.isServer(args[1]), i18n.format("invalidServer"));
				server = args[1];
				reason = (args.length > 2) ? UtilsKt.getFinalArg(args, 2) : IModule.NO_REASON;
			}
		}
                
        checkArgument(
                    !reason.equalsIgnoreCase(IModule.NO_REASON) || !BATR.getInstance().getConfiguration().get(Configuration.MUST_GIVE_REASON),
                    i18n.format("noReasonInCommand"));

		// Check if the target isn't an ip and the player is offline
		if (!UtilsKt.validIP(target) && player == null) {
			ip = Core.getPlayerIP(target);
			if (ipMute) {
				checkArgument(!"0.0.0.0".equals(ip), i18n.format("ipUnknownPlayer"));
			} else {
				// If ip = 0.0.0.0, it means the player never connects
				if ("0.0.0.0".equals(ip) && !confirmedCmd) {
					command.mustConfirmCommand(sender, command.getName() + " " + Joiner.on(' ').join(args),
							i18n.format("operationUnknownPlayer", new String[] { target }));
					return;
				}
				// Set the ip to null to avoid checking if the ip is banned
				ip = null;
			}
		}

		if (!global) {
			checkArgument(PermissionManager.canExecuteAction((ipMute) ? PermissionManager.Action.MUTEIP : PermissionManager.Action.MUTE, sender, server),
					i18n.format("noPerm"));
		}
		target = (ip == null) ? target : ip;

		checkArgument(!PermissionManager.isExemptFrom(PermissionManager.Action.MUTE, target), i18n.format("isExempt"));

		checkArgument(!mute.isMute((ip == null) ? target : ip, server, false), i18n.format("alreadyMute"));

		if (ipMute && !BATR.getInstance().getRedis().isRedisEnabled() && player != null) {
			returnedMsg = mute.muteIP(player, server, staff, 0, reason);
		} else {
			returnedMsg = mute.mute(target, server, staff, 0, reason);
		}

		BATR.broadcast(returnedMsg, PermissionManager.Action.MUTE_BROADCAST.getPermission());
	}

	@BATCommand.RunAsync
	public static class TempMuteCmd extends BATCommand {
		public TempMuteCmd() {
			super("tempmute", "<player/ip> <duration> [server] [reason]",
					"Temporarily mute the player on username basis on from the specified server for duration.",
					PermissionManager.Action.TEMPMUTE.getPermission());
		}

		@Override
		public void onCommand(final CommandSender sender, final String[] args, final boolean confirmedCmd)
				throws IllegalArgumentException {
			handleTempMuteCommand(this, false, false, sender, args, confirmedCmd);
		}
	}

	@BATCommand.RunAsync
	public static class TempMuteIPCmd extends BATCommand {
		public TempMuteIPCmd() {
			super(
					"tempmuteip",
					"<player> <duration> [server] [reason]",
					"Temporarily mute the player on IP basis on the specified server for duration. No player logged in with that IP will be able to speak.",
					PermissionManager.Action.TEMPMUTEIP.getPermission());
		}

		@Override
		public void onCommand(final CommandSender sender, final String[] args, final boolean confirmedCmd)
				throws IllegalArgumentException {
			handleTempMuteCommand(this, false, true, sender, args, confirmedCmd);
		}
	}

	@BATCommand.RunAsync
	public static class GTempMuteCmd extends BATCommand {
		public GTempMuteCmd() {
			super("gtempmute", "<player> <duration> [reason]",
					"Temporarily mute the player on username basis on all servers (the whole network) for duration.",
					PermissionManager.Action.TEMPMUTE.getPermission() + ".global");
		}

		@Override
		public void onCommand(final CommandSender sender, final String[] args, final boolean confirmedCmd)
				throws IllegalArgumentException {
			handleTempMuteCommand(this, true, false, sender, args, confirmedCmd);
		}
	}

	@BATCommand.RunAsync
	public static class GTempMuteIPCmd extends BATCommand {
		public GTempMuteIPCmd() {
			super(
					"gtempmuteip",
					"<player/ip> <duration> [reason]",
					"Temporarily mute the player on IP basis on all servers (the whole network) for duration. No player logged in with that IP will be able to speak.",
					PermissionManager.Action.TEMPMUTEIP.getPermission() + ".global");
		}

		@Override
		public void onCommand(final CommandSender sender, final String[] args, final boolean confirmedCmd)
				throws IllegalArgumentException {
			handleTempMuteCommand(this, true, true, sender, args, confirmedCmd);
		}
	}

	public static void handleTempMuteCommand(final BATCommand command, final boolean global, final boolean ipMute,
			final CommandSender sender, final String[] args, final boolean confirmedCmd) {
		String target = args[0];
		String server = IModule.GLOBAL_SERVER;
		final String staff = sender.getName();
		String reason = IModule.NO_REASON;
		final long expirationTimestamp = UtilsKt.parseDuration(args[1]);

		final ProxiedPlayer player = UtilsKt.getPlayer(target);

		String ip = null;

		String returnedMsg;

		if (global) {
			if (args.length > 2) {
				reason = UtilsKt.getFinalArg(args, 2);
			}
		} else {
			if (args.length == 2) {
				checkArgument(sender instanceof ProxiedPlayer, i18n.format("specifyServer"));
				server = ((ProxiedPlayer) sender).getServer().getInfo().getName();
			} else {
				checkArgument(UtilsKt.isServer(args[2]), i18n.format("invalidServer"));
				server = args[2];
				reason = (args.length > 3) ? UtilsKt.getFinalArg(args, 3) : IModule.NO_REASON;
			}
		}

        checkArgument(
                    !reason.equalsIgnoreCase(IModule.NO_REASON) || !BATR.getInstance().getConfiguration().get(Configuration.MUST_GIVE_REASON),
                    i18n.format("noReasonInCommand"));
                
		// Check if the target isn't an ip and the player is offline
		if (!UtilsKt.validIP(target) && player == null) {
			ip = Core.getPlayerIP(target);
			if (ipMute) {
				checkArgument(!"0.0.0.0".equals(ip), i18n.format("ipUnknownPlayer"));
			} else {
				// If ip = 0.0.0.0, it means the player never connects
				if ("0.0.0.0".equals(ip) && !confirmedCmd) {
					command.mustConfirmCommand(sender, command.getName() + " " + Joiner.on(' ').join(args),
							i18n.format("operationUnknownPlayer", new String[] { target }));
					return;
				}
				// Set the ip to null to avoid checking if the ip is banned
				ip = null;
			}
		}

		if (!global) {
			checkArgument(
					PermissionManager.canExecuteAction((ipMute) ? PermissionManager.Action.TEMPMUTEIP : PermissionManager.Action.TEMPMUTE, sender, server),
					i18n.format("noPerm"));
		}
		target = (ip == null) ? target : ip;

		checkArgument(!PermissionManager.isExemptFrom(PermissionManager.Action.MUTE, target), i18n.format("isExempt"));

		checkArgument(!mute.isMute((ip == null) ? target : ip, server, false), i18n.format("alreadyMute"));

		if (ipMute && !BATR.getInstance().getRedis().isRedisEnabled() && player != null) {
			returnedMsg = mute.muteIP(player, server, staff, expirationTimestamp, reason);
		} else {
			returnedMsg = mute.mute(target, server, staff, expirationTimestamp, reason);
		}

		BATR.broadcast(returnedMsg, PermissionManager.Action.MUTE_BROADCAST.getPermission());
	}

	@BATCommand.RunAsync
	public static class UnmuteCmd extends BATCommand {
		public UnmuteCmd() {
			super("unmute", "<player> [server] [reason]",
					"Unmute the player on a username basis from the specified server.", PermissionManager.Action.UNMUTE.getPermission());
		}

		@Override
		public void onCommand(final CommandSender sender, final String[] args, final boolean confirmedCmd)
				throws IllegalArgumentException {
			handleUnmuteCommand(this, false, false, sender, args, confirmedCmd);
		}
	}

	@BATCommand.RunAsync
	public static class UnmuteIPCmd extends BATCommand {
		public UnmuteIPCmd() {
			super("unmuteip", "<player/ip> [server] [reason]",
					"Unmute the player on a username basis from all servers (the whole network).", PermissionManager.Action.UNMUTEIP
							.getPermission());
		}

		@Override
		public void onCommand(final CommandSender sender, final String[] args, final boolean confirmedCmd)
				throws IllegalArgumentException {
			handleUnmuteCommand(this, false, true, sender, args, confirmedCmd);
		}
	}

	@BATCommand.RunAsync
	public static class GUnmuteCmd extends BATCommand {
		public GUnmuteCmd() {
			super("gunmute", "<player> [reason]", "Unmute the player on an IP basis from the specified server.",
					PermissionManager.Action.UNMUTE.getPermission() + ".global");
		}

		@Override
		public void onCommand(final CommandSender sender, final String[] args, final boolean confirmedCmd)
				throws IllegalArgumentException {
			handleUnmuteCommand(this, true, false, sender, args, confirmedCmd);
		}
	}

	@BATCommand.RunAsync
	public static class GUnmuteIPCmd extends BATCommand {
		public GUnmuteIPCmd() {
			super("gunmuteip", "<player/ip> [reason]",
					"Unmute the player on an IP basis from all servers (the whole network).", PermissionManager.Action.UNMUTEIP
							.getPermission() + ".global");
		}

		@Override
		public void onCommand(final CommandSender sender, final String[] args, final boolean confirmedCmd)
				throws IllegalArgumentException {
			handleUnmuteCommand(this, true, true, sender, args, confirmedCmd);
		}
	}

	public static void handleUnmuteCommand(final BATCommand command, final boolean global, final boolean ipUnmute,
			final CommandSender sender, final String[] args, final boolean confirmedCmd) {
		String target = args[0];
		String server = IModule.ANY_SERVER;
		final String staff = sender.getName();
		String reason = IModule.NO_REASON;

		String ip = null;

		String returnedMsg;

		if (global) {
			if (args.length > 1) {
				reason = UtilsKt.getFinalArg(args, 1);
			}
		} else {
			if (args.length == 1) {
				checkArgument(sender instanceof ProxiedPlayer, i18n.format("specifyServer"));
				server = ((ProxiedPlayer) sender).getServer().getInfo().getName();
			} else {
				checkArgument(UtilsKt.isServer(args[1]), i18n.format("invalidServer"));
				server = args[1];
				reason = (args.length > 2) ? UtilsKt.getFinalArg(args, 2) : IModule.NO_REASON;
			}
		}
                
        checkArgument(
                    !reason.equalsIgnoreCase(IModule.NO_REASON) || !BATR.getInstance().getConfiguration().get(Configuration.MUST_GIVE_REASON),
                    i18n.format("noReasonInCommand"));

		// Check if the target isn't an ip and the player is offline
		if (!UtilsKt.validIP(target) && ipUnmute) {
			ip = Core.getPlayerIP(target);
			checkArgument(!"0.0.0.0".equals(ip), i18n.format("ipUnknownPlayer"));
		}

		if (!global) {
			checkArgument(
					PermissionManager.canExecuteAction((ipUnmute) ? PermissionManager.Action.UNMUTEIP : PermissionManager.Action.UNMUTE, sender, server),
					i18n.format("noPerm"));
		}
		target = (ip == null) ? target : ip;

		final String[] formatArgs = { args[0] };

		checkArgument(
				mute.isMute((ip == null) ? target : ip, server, true),
				(IModule.ANY_SERVER.equals(server) ? i18n.format("notMutedAny", formatArgs) : ((ipUnmute) ? i18n.format("notMutedIP",
						formatArgs) : i18n.format("notMuted", formatArgs))));

		if (ipUnmute) {
			returnedMsg = mute.unMuteIP(target, server, staff, reason);
		} else {
			returnedMsg = mute.unMute(target, server, staff, reason);
		}

		BATR.broadcast(returnedMsg, PermissionManager.Action.MUTE_BROADCAST.getPermission());
	}
}