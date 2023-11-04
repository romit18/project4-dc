package dslabs.paxos;


// TODO: add messages for PAXOS here ...
import dslabs.framework.Address;
import dslabs.framework.Command;
import dslabs.framework.Message;
import dslabs.framework.Result;
import lombok.Data;

import java.util.Set;

@Data
class Request implements Message {
    private final Command command;
    private final int requestNum;
}

@Data
class Reply implements Message {
    private final Result result;
    private final int replyNum;
}

@Data
class Promise implements Message {
    private final int promiseNum;
}

@Data
class PaxosMessage implements Message {
    final Address address;
}
@Data
class P1aMessage implements Message {
    final Address address;
    final Ballot ballot;
}

@Data
class P1bMessage implements Message {
    final Address address;
    final Ballot ballot;
    final Set<Pvalue> accepted;
}

@Data
class P2aMessage implements Message {
    final Address address;
    final Ballot ballot;
    private final int slot;
    private final PaxosRequest paxosRequest;
}

@Data
class P2bMessage implements Message {
    final Address address;
    final Ballot ballot;
    final int slot;
    final PaxosRequest paxosRequest;
}

@Data
class PreemptedMessage implements Message {
    final Address address;
    final Ballot ballot;
}

@Data
class AdoptedMessage implements Message {
    final Address address;
    final Ballot ballot;
    final Set <Pvalue> accepted;
}

@Data
class DecisionMessage implements Message {
    final Address address;
    final int slot;
    final Command command;
}

@Data
class RequestMessage implements Message {
    final Address address;
    private final Command command;
}

@Data
class ProposeMessage implements Message {
    private final int slot_number;
    private final Command command ;
}