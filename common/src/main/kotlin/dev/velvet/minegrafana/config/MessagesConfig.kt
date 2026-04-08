package dev.velvet.minegrafana.config

data class MessagesConfig(
    val prefix: String = "<gradient:#7c3aed:#a78bfa>mineGrafana</gradient> <gray>»</gray>",
    val player: PlayerMessages = PlayerMessages(),
    val punishment: PunishmentMessages = PunishmentMessages(),
    val monitor: MonitorMessages = MonitorMessages(),
    val permission: PermissionMessages = PermissionMessages()
)

data class PlayerMessages(
    val notFound: String = "<red>Player {player} not found.",
    val infoHeader: String = "<gold>--- Player Info: {player} ---",
    val altDetected: String = "<yellow>Alt accounts detected for {player}: {alts}",
    val memoAdded: String = "<green>Memo added for {player}."
)

data class PunishmentMessages(
    val banMessage: String = "<red>You have been banned.\n<gray>Reason: {reason}\n<gray>Expires: {expiry}",
    val muteMessage: String = "<red>You are muted. Reason: {reason}",
    val kickMessage: String = "<red>You have been kicked.\n<gray>Reason: {reason}",
    val warnMessage: String = "<yellow>You have been warned!\n<gray>Reason: {reason}\n<gray>Warnings: {count}/{threshold}",
    val broadcastBan: String = "<red>{player} has been banned by {issuer}. Reason: {reason}",
    val broadcastMute: String = "<yellow>{player} has been muted by {issuer}.",
    val broadcastWarn: String = "<yellow>{player} has been warned by {issuer}. ({count}/{threshold})",
    val unbanned: String = "<green>{player} has been unbanned.",
    val unmuted: String = "<green>{player} has been unmuted."
)

data class MonitorMessages(
    val healthReport: String = """
        <gold>--- Server Health Report ---
        <gray>TPS: <{tps_color}>{tps}
        <gray>MSPT: <{mspt_color}>{mspt}ms (avg) / {mspt_95}ms (95th)
        <gray>CPU: {cpu_process}% process / {cpu_system}% system
        <gray>Memory: {memory_used}MB / {memory_max}MB ({memory_percent}%)
        <gray>Entities: {entity_count} | Chunks: {chunk_count}
        <gray>Players: {player_count} (avg ping: {avg_ping}ms)
    """.trimIndent(),
    val profilerStarted: String = "<green>Profiler started. Duration: {duration}s, Event: {event}",
    val profilerStopped: String = "<green>Profiler stopped. View results: {url}",
    val profilerNotRunning: String = "<red>No profiler session is currently running."
)

data class PermissionMessages(
    val noPermission: String = "<red>You do not have permission to do that.",
    val luckpermsNotFound: String = "<red>LuckPerms is not installed on this server.",
    val groupCreated: String = "<green>Group {group} created.",
    val groupDeleted: String = "<green>Group {group} deleted.",
    val permissionAdded: String = "<green>Permission {permission} added to {target}.",
    val permissionRemoved: String = "<green>Permission {permission} removed from {target}.",
    val promoted: String = "<green>{player} promoted to {group} on track {track}.",
    val demoted: String = "<green>{player} demoted to {group} on track {track}."
)
