package me.starmism.batr.modules.kick;

import java.sql.Timestamp;

public record KickEntry(String entity, String server, String reason,
						String staff, Timestamp date) {}