package com.liabletech.platform.whatsapp;

/**
 * What an app does with a WhatsApp message once it knows the business it belongs to: read a POD,
 * record a delivery, save an appliance label, answer a part enquiry.
 */
public interface InboundHandler {

	void handle(InboundContext context);

	/**
	 * A message from a number no business recognises. The default reply tells the sender how to get
	 * registered; return null to stay silent.
	 */
	default String replyToUnknownSender(InboundMessage message) {
		return "This number is not registered. Please ask the business you work with to add your mobile number.";
	}

}
