package com.peachhacks.backend.badge;

import java.time.Instant;

public record BadgeView(String uid, Instant boundAt, String boundBy) {

	static BadgeView of(Badge badge) {
		return new BadgeView(badge.getUid(), badge.getBoundAt(), badge.getBoundBy());
	}

}
