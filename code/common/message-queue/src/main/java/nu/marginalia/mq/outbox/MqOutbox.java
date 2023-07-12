package nu.marginalia.mq.outbox;

import nu.marginalia.mq.MqMessage;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.persistence.MqPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class MqOutbox {
    private final Logger logger = LoggerFactory.getLogger(MqOutbox.class);
    private final MqPersistence persistence;
    private final String inboxName;
    private final String replyInboxName;
    private final String instanceUUID;

    private final ConcurrentHashMap<Long, Long> pendingRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, MqMessage> pendingResponses = new ConcurrentHashMap<>();

    private final int pollIntervalMs = Integer.getInteger("mq.outbox.poll-interval-ms", 100);
    private final int maxPollCount = Integer.getInteger("mq.outbox.max-poll-count", 10);
    private final Thread pollThread;

    private volatile boolean run = true;

    public MqOutbox(MqPersistence persistence,
                    String inboxName,
                    String outboxName,
                    UUID instanceUUID) {
        this.persistence = persistence;

        this.inboxName = inboxName;
        this.replyInboxName = outboxName + "//" + inboxName;
        this.instanceUUID = instanceUUID.toString();

        pollThread = new Thread(this::poll, "mq-outbox-poll-thread:" + inboxName);
        pollThread.setDaemon(true);
        pollThread.start();
    }

    public void stop() throws InterruptedException {
        if (!run)
            return;

        logger.info("Shutting down outbox {}", inboxName);

        pendingRequests.clear();

        run = false;
        pollThread.join();
    }

    private void poll() {
        try {
            for (long id = 1; run; id++) {
                pollDb(id);

                TimeUnit.MILLISECONDS.sleep(pollIntervalMs);
            }
        } catch (InterruptedException ex) {
            logger.error("Outbox poll thread interrupted", ex);
        }
    }

    private void pollDb(long tick) {
        if (pendingRequests.isEmpty())
            return;

        try {
            var updates = persistence.pollReplyInbox(replyInboxName, instanceUUID, tick, maxPollCount);

            for (var message : updates) {
                pendingResponses.put(message.relatedId(), message);
                pendingRequests.remove(message.relatedId());
            }

            if (updates.isEmpty() || pendingResponses.isEmpty())
                return;

            logger.info("Notifying {} pending responses", pendingResponses.size());

            synchronized (pendingResponses) {
                pendingResponses.notifyAll();
            }
        }
        catch (SQLException ex) {
            logger.error("Failed to poll inbox", ex);
        }

    }

    /** Send a message and wait for a response. */
    public MqMessage send(String function, String payload) throws Exception {
        final long id = sendAsync(function, payload);

        return waitResponse(id);
    }

    /** Send a message asynchronously, without waiting for a response.
     * <br>
     * Use waitResponse(id) or pollResponse(id) to fetch the response.  */
    public long sendAsync(String function, String payload) throws Exception {
        var id = persistence.sendNewMessage(inboxName, replyInboxName, function, payload, null);

        pendingRequests.put(id, id);

        return id;
    }

    /** Blocks until a response arrives for the given message id. */
    public MqMessage waitResponse(long id) throws Exception {
        synchronized (pendingResponses) {
            while (!pendingResponses.containsKey(id)) {
                pendingResponses.wait(100);
            }

            var msg = pendingResponses.remove(id);
            // Mark the response as OK so it can be cleaned up
            persistence.updateMessageState(msg.msgId(), MqMessageState.OK);

            return msg;
        }
    }

    /** Polls for a response for the given message id. */
    public Optional<MqMessage> pollResponse(long id) throws SQLException {
        // no need to sync here if we aren't going to wait()
        var response = pendingResponses.get(id);

        if (response != null) {
            // Mark the response as OK so it can be cleaned up
            persistence.updateMessageState(response.msgId(), MqMessageState.OK);
        }
        return Optional.ofNullable(response);
    }

    public long notify(String function, String payload) throws Exception {
        return persistence.sendNewMessage(inboxName, null, function, payload, null);
    }

}