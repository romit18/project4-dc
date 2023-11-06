package dslabs.paxos;


import dslabs.framework.Address;
import dslabs.framework.Message;
import java.io.Serializable;
import java.util.HashSet;
import java.util.SortedMap;
import java.util.TreeMap;
import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
class LogEntry implements Serializable {
    /* FINAL */
    private final int ballotNumber;
    private final Address client;
    private final PaxosRequest request;

    /* NON-FINAL */
    private PaxosReply reply;
    private boolean isCommitted;

    public void setReply(PaxosReply replyParam) {
        this.reply = replyParam;
    }
}

@Data
@AllArgsConstructor
class RequestAndSlot implements Serializable {
    private final PaxosRequest request;
    private final int slot;
}

@Data
@AllArgsConstructor
class Phase1A implements Message {
    private final int ballotNumber;
}

@Data
@AllArgsConstructor
class Phase1B implements Message {
    private final boolean voteGranted;
    private final int ballotNumber;
    private final TreeMap<Integer, LogEntry> logEntries;
    private final int lastExecutedIndex;
}

@Data
@AllArgsConstructor
class Heartbeat implements Message {
    private final int ballotNumber;
    private final int sequenceId;
    private final int pruneTill;
    private final SortedMap<Integer, LogEntry> subLogEntries;
}

@Data
@AllArgsConstructor
class HeartbeatReply implements Message {
    private final boolean success;
    private final int ballotNumber;
    private final int sequenceId;
    private final int lastExecutedIndex;
}
