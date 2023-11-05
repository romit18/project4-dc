package dslabs.paxos;

import dslabs.atmostonce.AMOCommand;
import dslabs.atmostonce.AMOResult;
import dslabs.framework.Address;
import dslabs.framework.Client;
import dslabs.framework.Command;
import dslabs.framework.Node;
import dslabs.framework.Result;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import static dslabs.paxos.ClientTimer.CLIENT_RETRY_MILLIS;


@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public final class PaxosClient extends Node implements Client {
    private final Address[] servers;

    //TODO: declare fields for your implementation ...
    private int currentSequenceNum = 0;
    private Result result = null;

    /* -------------------------------------------------------------------------
        Construction and Initialization
       -----------------------------------------------------------------------*/
    public PaxosClient(Address address, Address[] servers) {
        super(address);
        this.servers = servers;

        // TODO: initialize fields ...
    }

    @Override
    public synchronized void init() {
        // No need to initialize
    }

    /* -------------------------------------------------------------------------
        Public methods
       -----------------------------------------------------------------------*/
    @Override
    public synchronized void sendCommand(Command operation) {
        // TODO: send command ...
        result = null;
        PaxosRequest request = null;
        for(Address server: servers){
            request = new PaxosRequest(this.address().toString(),currentSequenceNum,operation);
            send(request, server);
        }
        set(new ClientTimer(request), ClientTimer.CLIENT_RETRY_MILLIS);
    }

    @Override
    public synchronized boolean hasResult() {
        // TODO: check result available ...
        return result != null;
    }

    @Override
    public synchronized Result getResult() throws InterruptedException {
        // TODO: get result ...
        while (result == null) {
            wait();
        }
        return result;
    }

    /* -------------------------------------------------------------------------
        Message Handlers
       -----------------------------------------------------------------------*/
    private synchronized void handlePaxosReply(PaxosReply m, Address sender) {
        // TODO: handle paxos server reply ...

        int sequenceNum = m.sequenceNum();
        AMOResult amoResult = (AMOResult) m.result();
        Result replyResult = amoResult.result();

        if (result == null && sequenceNum == currentSequenceNum) {
            result = replyResult;
            currentSequenceNum++;
            notify();
        }
    }

    /* -------------------------------------------------------------------------
        Timer Handlers
       -----------------------------------------------------------------------*/
    private synchronized void onClientTimer(ClientTimer t) {
        // TODO: handle client request timeout ...

        if (result == null && t.request().sequenceNum() == currentSequenceNum) {
            for(Address server: servers){
                send(t.request(), server);
            }
            set(new ClientTimer(t.request()), ClientTimer.CLIENT_RETRY_MILLIS);
        }
    }
}