package dslabs.paxos;


// TODO: add messages for PAXOS here ...
import dslabs.framework.Command;
import dslabs.framework.Message;
import dslabs.framework.Result;
import lombok.Data;

import java.util.Queue;

@Data
class Ping implements Message {
    private final int viewNum;
}
/* -------------------------------------------------------------------------
    Primary-Backup Messages
   -----------------------------------------------------------------------*/
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