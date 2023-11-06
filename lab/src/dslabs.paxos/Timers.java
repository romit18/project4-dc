package dslabs.paxos;

import dslabs.framework.Timer;
import lombok.Data;

@Data
final class ClientTimer implements Timer {
    static final int CLIENT_RETRY_MILLIS = 100;
}

@Data
final class ElectionTimer implements Timer {
    static final int ELECTION_TIMER_LOW = 150;
    static final int ELECTION_TIMER_HIGH = 300;
}
@Data
final class HeartbeatTimer implements Timer {
    static final int HEARTBEAT_TIMER_MILLIS = 50;
}
// TODO: add more timers here ...