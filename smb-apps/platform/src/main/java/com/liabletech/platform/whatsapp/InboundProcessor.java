package com.liabletech.platform.whatsapp;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.liabletech.platform.account.Account;
import com.liabletech.platform.account.Accounts;
import com.liabletech.platform.files.FileStore;
import com.liabletech.platform.files.StoredFile;
import com.liabletech.platform.messaging.Messenger;
import com.liabletech.platform.messaging.Outgoing;

/**
 * Works through received WhatsApp messages off the webhook thread: finds the business, downloads
 * any photo, then hands over to the app's {@link InboundHandler}.
 *
 * <p>
 * Messages from the same phone are processed one at a time and in order (a technician sends a
 * customer's number, then the label photo), while different phones run in parallel lanes. Anything
 * interrupted by a restart, or that failed, is picked up again by {@link #retryStuck()}.
 */
@Service
public class InboundProcessor {

	private static final Logger log = LoggerFactory.getLogger(InboundProcessor.class);

	private final InboundMessages messages;

	private final Accounts accounts;

	private final ObjectProvider<InboundRouter> router;

	private final ObjectProvider<InboundHandler> handler;

	private final FileStore files;

	private final CloudApi cloudApi;

	private final Messenger messenger;

	private final WhatsAppProperties properties;

	private final Clock clock;

	private final List<ExecutorService> lanes = new ArrayList<>();

	public InboundProcessor(InboundMessages messages, Accounts accounts, ObjectProvider<InboundRouter> router,
			ObjectProvider<InboundHandler> handler, FileStore files, CloudApi cloudApi, Messenger messenger,
			WhatsAppProperties properties, Clock clock, @Value("${app.inbound.lanes:4}") int laneCount) {
		this.messages = messages;
		this.accounts = accounts;
		this.router = router;
		this.handler = handler;
		this.files = files;
		this.cloudApi = cloudApi;
		this.messenger = messenger;
		this.properties = properties;
		this.clock = clock;
		for (int i = 0; i < laneCount; i++) {
			lanes.add(Executors.newSingleThreadExecutor(Thread.ofPlatform().name("wa-inbound-" + i).factory()));
		}
	}

	/** Queues a stored message. With no lanes configured (tests), it is processed right away. */
	public void dispatch(long messageId, String fromPhone) {
		if (lanes.isEmpty()) {
			process(messageId);
			return;
		}
		int lane = Math.floorMod(fromPhone.hashCode(), lanes.size());
		lanes.get(lane).execute(() -> process(messageId));
	}

	void process(long messageId) {
		Optional<InboundMessage> found = messages.find(messageId);
		if (found.isEmpty() || InboundMessage.PROCESSED.equals(found.get().status())
				|| InboundMessage.UNROUTED.equals(found.get().status())) {
			return;
		}
		InboundMessage message = found.get();
		try {
			Optional<Account> account = resolveAccount(message);
			if (account.isEmpty()) {
				answerUnknownSender(message);
				return;
			}
			long accountId = account.get().id();
			if (message.accountId() == null) {
				messages.assignAccount(message.id(), accountId);
			}
			if (message.mediaId() != null && message.fileId() == null && !properties.simulator()) {
				CloudApi.Media media = cloudApi.download(message.mediaId());
				StoredFile file = files.save(accountId, media.bytes(),
						media.mimeType() != null ? media.mimeType() : message.mediaMimeType(), message.mediaFilename());
				messages.attachFile(message.id(), file.id());
			}
			else if (message.fileId() != null) {
				files.assignAccount(message.fileId(), accountId);
			}
			InboundMessage ready = messages.find(message.id()).orElseThrow();
			InboundHandler appHandler = handler.getIfAvailable();
			if (appHandler != null) {
				appHandler.handle(new InboundContext(ready, account.get(), files, messenger));
			}
			messages.finish(message.id(), InboundMessage.PROCESSED, null, OffsetDateTime.now(clock));
		}
		catch (FileStore.UnsupportedFileException ex) {
			messages.finish(message.id(), InboundMessage.PROCESSED, ex.getMessage(), OffsetDateTime.now(clock));
		}
		catch (RuntimeException ex) {
			log.warn("Processing WhatsApp message {} failed", message.waMessageId(), ex);
			messages.finish(message.id(), InboundMessage.FAILED, ex.toString(), OffsetDateTime.now(clock));
		}
	}

	private Optional<Account> resolveAccount(InboundMessage message) {
		if (message.accountId() != null) {
			return accounts.find(message.accountId());
		}
		Optional<Account> byNumber = accounts.findByWhatsAppNumber(message.toPhoneNumberId());
		if (byNumber.isPresent()) {
			return byNumber;
		}
		InboundRouter appRouter = router.getIfAvailable();
		return appRouter == null ? Optional.empty()
				: appRouter.accountFor(message.fromPhone()).flatMap(accounts::find);
	}

	private void answerUnknownSender(InboundMessage message) {
		messages.finish(message.id(), InboundMessage.UNROUTED, null, OffsetDateTime.now(clock));
		InboundHandler appHandler = handler.getIfAvailable();
		String reply = appHandler == null ? null : appHandler.replyToUnknownSender(message);
		if (reply != null) {
			// Recorded in the outbox without an account: no business owns this conversation.
			messenger.send(Outgoing.whatsApp(null, message.fromPhone(), reply).about("wa_inbound", message.id()));
		}
	}

	/** Picks up messages a restart interrupted, and retries failures (up to three attempts). */
	@Scheduled(fixedDelayString = "${app.inbound.retry-every:PT1M}", initialDelayString = "PT30S")
	public void retryStuck() {
		for (Long id : messages.needingRetry(OffsetDateTime.now(clock).minusMinutes(2))) {
			messages.find(id).ifPresent((message) -> dispatch(id, message.fromPhone()));
		}
	}

	@PreDestroy
	void shutdown() {
		lanes.forEach(ExecutorService::shutdown);
	}

}
