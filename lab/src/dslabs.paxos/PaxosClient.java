package dslabs.paxos;

import dslabs.framework.Address;
import dslabs.framework.Client;
import dslabs.framework.Command;
import dslabs.framework.Node;
import dslabs.framework.Result;
import java.util.HashMap;
import java.util.HashSet;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import static dslabs.paxos.ClientTimer.CLIENT_RETRY_MILLIS;


@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public final class PaxosClient extends Node implements Client {
    private static final int RETRY_MILLIS = 150;
    private final Address[] servers;
    private final Address Iam;
    private int requestID;
    private Result result;
    private final HashMap<Integer, Command> requestIDtoCommand;
    private final HashSet<Address> serverSet;

    enum CurrentStatus {
        SEND_REQUEST,
        GOT_REPLY,
    };

    //TODO: declare fields for your implementation ...

    /* -------------------------------------------------------------------------
        Construction and Initialization
       -----------------------------------------------------------------------*/
    public PaxosClient(Address address, Address[] servers) {
        super(address);
        this.Iam = address;
        this.servers = servers;
        requestID = 1;
        result = null;
        requestIDtoCommand = new HashMap<>();
        serverSet = new HashSet<>();
        populateServerSet(CurrentStatus.SEND_REQUEST, null);
    }

    @Override
    public synchronized void init() {
        this.set(new ClientTimer(), RETRY_MILLIS);
    }

    private void populateServerSet(CurrentStatus currentStatus, Address serverToAdd) {
        if (currentStatus == CurrentStatus.SEND_REQUEST) {
            assert (serverToAdd == null) : "SEND_REQUEST should NOT have server";
            for (Address server: servers) {
                serverSet.add(server);
            }
        } else if (currentStatus == CurrentStatus.GOT_REPLY) {
            serverSet.clear();
            serverSet.add(serverToAdd);
        }
    }

    /* -------------------------------------------------------------------------
        Public methods
       -----------------------------------------------------------------------*/
    @Override
    public synchronized void sendCommand(Command command) {
        result = null;
        requestIDtoCommand.put(requestID, command);
        for (Address server: serverSet) {
            this.send(new PaxosRequest("default", requestID, command), server);
        }
    }

    @Override
    public synchronized boolean hasResult() {
        return result != null;
    }

    @Override
    public synchronized Result getResult() throws InterruptedException {
        while (result == null)
            wait();
        return result;
    }

    /* -------------------------------------------------------------------------
        Message Handlers
       -----------------------------------------------------------------------*/
    private synchronized void handlePaxosReply(PaxosReply reply, Address sender) {
        if (reply.success() && reply.replyID() == requestID) {
            populateServerSet(CurrentStatus.GOT_REPLY, sender);
            requestID++;
            result = reply.result();
            requestIDtoCommand.remove(reply.replyID());
            notify();
        }
    }

    /* -------------------------------------------------------------------------
        Timer Handlers
       -----------------------------------------------------------------------*/
    private synchronized void onClientTimer(ClientTimer clientTimer) {
        populateServerSet(CurrentStatus.SEND_REQUEST, null);
        requestIDtoCommand.forEach((requestID, command) -> {
            for (Address server: serverSet) {
                this.send(new PaxosRequest("default", requestID, command), server);
            }
        });

        this.set(clientTimer, RETRY_MILLIS);
    }

}