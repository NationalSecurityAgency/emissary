package emissary.transaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;

import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Transaction implements Serializable {

    private final String id;
    private final Map<String, Transaction> transactions;
    private final Multimap<String, String> metadata;
    private final Long createTime;
    private Long startTime;
    private Long endTime;
    private Status status;
    private String message;

    public Transaction() {
        this(UUID.randomUUID().toString());
    }

    public Transaction(String id) {
        if (StringUtils.isBlank(id)) {
            throw new IllegalArgumentException("Id cannot be blank for a transaction");
        }
        this.id = id;
        this.createTime = System.currentTimeMillis();
        this.transactions = new HashMap<>();
        metadata = MultimapBuilder.hashKeys().hashSetValues().build();
        status = Status.CREATED;
    }

    public String getId() {
        return id;
    }

    public Long getCreateTime() {
        return createTime;
    }

    public void start() {
        setStatus(Status.STARTED);
        setMessage("Transaction started");
        setStartTime(System.currentTimeMillis());
    }

    public void success(String message) {
        complete(Status.SUCCESS, message);
    }

    public void success() {
        success("Transaction completed successfully");
    }

    public void fail(String message) {
        complete(Status.FAILED, message);
    }

    public void fail() {
        fail("Transaction failed to complete");
    }

    public void timeout(String message) {
        complete(Status.TIMEOUT, message);
    }

    public void timeout() {
        timeout("Transaction failed to complete in allotted time");
    }

    public Status getStatus() {
        return status;
    }

    private void setStatus(Status status) {
        if (this.status.ordinal() > status.ordinal()) {
            throw new IllegalStateException("Transaction is " + this.status + ", cannot update to " + status);
        }
        this.status = status;
    }

    private void complete(Status status, String message) {
        setStatus(status);
        setMessage(message);
        setEndTime(System.currentTimeMillis());
    }

    public boolean isComplete() {
        return endTime != null;
    }

    public boolean isRunning() {
        return !isComplete();
    }

    public Long getStartTime() {
        return startTime;
    }

    public void setStartTime(Long startTime) {
        this.startTime = startTime;
    }

    public Long getEndTime() {
        return endTime;
    }

    public void setEndTime(Long endTime) {
        this.endTime = endTime;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Collection<String> getMetadata(String key) {
        return Collections.unmodifiableCollection(metadata.get(key));
    }

    public void addMetadata(String key, String value) {
        metadata.put(key, value);
    }

    public void addMetadata(String key, Collection<String> values) {
        metadata.putAll(key, values);
    }

    public void addMetadata(Multimap<String, String> metadata) {
        this.metadata.putAll(metadata);
    }

    public void clearMetadata() {
        metadata.clear();
    }

    public void removeMetadata(String key) {
        metadata.removeAll(key);
    }

    public boolean hasTransactions() {
        return !transactions.isEmpty();
    }

    public Transaction getTransaction(String id) {
        return transactions.get(id);
    }

    public Transaction getTransaction(Transaction tx) {
        return getTransaction(tx.getId());
    }

    public void clearTransactions() {
        transactions.clear();
    }

    public void removeTransaction(String id) {
        transactions.remove(id);
    }

    public void addTransaction(Transaction tx) {
        addTransaction(tx.getId(), tx);
    }

    public void addTransaction(String id, Transaction tx) {
        transactions.put(id, tx);
    }

    public long getTransactionCount() {
        return transactions.size();
    }

    public long getTransactionCompleteCount() {
        return transactions.values().stream().filter(Transaction::isComplete).count();
    }

    public long getTransactionFailureCount() {
        return transactions.values().stream().filter(tx -> tx.status == Status.FAILED || tx.status == Status.TIMEOUT).count();
    }

    public static String generateTransactionId(String... parts) {
        return UUID.nameUUIDFromBytes(String.join("", parts).getBytes(StandardCharsets.UTF_8)).toString();
    }

    public Collection<Transaction> getTransactions() {
        return Collections.unmodifiableCollection(transactions.values());
    }

    public void log(Logger logger) {
        if (!logger.isInfoEnabled()) {
            return;
        }
        logger.info(toJson());
        transactions.forEach((id, tx) -> tx.log(logger));
    }

    @Override
    public String toString() {
        return MethodHandles.lookup().lookupClass().getSimpleName() +
                "{" +
                " id='" + id + '\'' +
                ", status=" + status +
                ", createTime=" + createTime +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", message='" + message + '\'' +
                ", metadata=" + metadata +
                ", transactionCount=" + transactions.size() +
                " }";
    }

    public String toJson() {
        ObjectMapper mapper = new ObjectMapper();

        ObjectNode node = mapper.createObjectNode();
        node.put("id", id);
        node.put("status", status.toString());
        node.put("createTime", createTime);
        node.put("startTime", startTime);
        node.put("endTime", endTime);
        node.put("message", message);
        node.put("transactionCount", transactions.size());

        ObjectNode meta = mapper.createObjectNode();
        metadata.asMap().forEach((key, value) -> {
            ArrayNode arrNode = mapper.createArrayNode();
            value.forEach(arrNode::add);
            meta.set(key, arrNode);
        });
        node.set("metadata", meta);

        return node.toString();
    }

    public enum Status {
        CREATED, STARTED, SUCCESS, TIMEOUT, FAILED
    }

}
