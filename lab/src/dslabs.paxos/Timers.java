package dslabs.paxos;

import dslabs.framework.Address;
import dslabs.framework.Timer;
import lombok.Data;

@Data
final class ClientTimer implements Timer {
    static final int CLIENT_RETRY_MILLIS = 100;
    private final PaxosRequest request;
    // TODO: add fields for client timer ...
}

@Data
final class HeartbeatCheckTimer implements Timer {
    static final int PING_CHECK_MILLIS = 100;
    private final Ballot leader;
}

@Data
final class HeartbeatTimer implements Timer {
    static final int HEARTBEAT_MILLIS = 25;
}

// TODO: add more timers here ...