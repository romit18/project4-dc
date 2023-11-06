package dslabs.paxos;

import dslabs.framework.Message;
import dslabs.framework.Result;
import lombok.Data;

/**
 * Please see {@link PaxosRequest} for illustration.
 */

@Data
public final class PaxosReply implements Message {
    private final boolean success;
    private final String id;
    private final int replyID;
    private final Result result;
}
