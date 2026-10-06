package com.peachhacks.backend.acceptance;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * How much of a group comes from the host school. share is null for an empty group, which
 * counts as meeting the target because nothing has to change. moreHostNeeded is how many
 * host-school people would have to be added, with nobody removed, to reach the target;
 * it is null when no number is enough (a target of 100% with anyone else in the group).
 * fewerOthersNeeded is how many others would have to be removed instead.
 */
public record HostSchoolShare(long total, long host, long other, Double share, boolean met, Long moreHostNeeded,
		long fewerOthersNeeded) {

	public static HostSchoolShare of(long host, long total, BigDecimal target) {
		long other = total - host;
		BigDecimal required = target.multiply(BigDecimal.valueOf(total));
		boolean met = BigDecimal.valueOf(host).compareTo(required) >= 0;
		Double share = (total > 0) ? (double) host / total : null;
		if (met) {
			return new HostSchoolShare(total, host, other, share, true, 0L, 0);
		}
		// (host + x) / (total + x) >= target  <=>  x >= (target * total - host) / (1 - target)
		BigDecimal headroom = BigDecimal.ONE.subtract(target);
		Long moreHost = (headroom.signum() == 0) ? null
				: required.subtract(BigDecimal.valueOf(host)).divide(headroom, 0, RoundingMode.CEILING).longValueExact();
		// host / (total - y) >= target  <=>  y >= total - host / target; not met implies target > 0
		long fewerOthers = total
				- BigDecimal.valueOf(host).divide(target, 0, RoundingMode.FLOOR).longValueExact();
		return new HostSchoolShare(total, host, other, share, false, moreHost, fewerOthers);
	}

}
