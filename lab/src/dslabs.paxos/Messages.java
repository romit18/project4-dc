package dslabs.paxos;


// TODO: add messages for PAXOS here ...
import dslabs.framework.Command;
import dslabs.framework.Message;
import dslabs.framework.Result;
import lombok.Data;

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