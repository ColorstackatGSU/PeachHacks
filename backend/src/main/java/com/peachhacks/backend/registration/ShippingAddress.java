package com.peachhacks.backend.registration;

import com.peachhacks.backend.common.Texts;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Size;

/** Used both in the request body and as the stored columns. */
@Embeddable
public record ShippingAddress(
		@Column(name = "shipping_line1") @Size(max = 255, message = "Must be at most 255 characters") String line1,
		@Column(name = "shipping_line2") @Size(max = 255, message = "Must be at most 255 characters") String line2,
		@Column(name = "shipping_city") @Size(max = 255, message = "Must be at most 255 characters") String city,
		@Column(name = "shipping_state") @Size(max = 255, message = "Must be at most 255 characters") String state,
		@Column(name = "shipping_country") @Size(max = 255, message = "Must be at most 255 characters") String country,
		@Column(name = "shipping_postal_code") @Size(max = 255,
				message = "Must be at most 255 characters") String postalCode) {

	static ShippingAddress cleaned(ShippingAddress address) {
		if (address == null) {
			return null;
		}
		ShippingAddress cleaned = new ShippingAddress(Texts.clean(address.line1()), Texts.clean(address.line2()),
				Texts.clean(address.city()), Texts.clean(address.state()), Texts.clean(address.country()),
				Texts.clean(address.postalCode()));
		boolean empty = cleaned.line1() == null && cleaned.line2() == null && cleaned.city() == null
				&& cleaned.state() == null && cleaned.country() == null && cleaned.postalCode() == null;
		return empty ? null : cleaned;
	}

}
