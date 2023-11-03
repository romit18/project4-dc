package dslabs.paxos;

import dslabs.atmostonce.AMOApplication;
import dslabs.atmostonce.AMOCommand;
import dslabs.atmostonce.AMOResult;
import dslabs.framework.Address;
import dslabs.framework.Application;
import dslabs.framework.Command;
import dslabs.framework.Node;
import dslabs.framework.Result;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;


@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class PaxosServer extends Node {
    static final int INITIAL_BALLOT_NUMBER = 0;
    private final Address[] servers;
    private AMOApplication<Application> app;
    //for log slots/can be switched to different data structure i feel (a custom class)
    List<AMOCommand> listOfCommands;
    int latestValue = 0;
    // TODO: declare fields for your implementation ...

    /* -------------------------------------------------------------------------
        Construction and Initialization
       -----------------------------------------------------------------------*/
    public PaxosServer(Address address, Address[] servers, Application app) {
        super(address);
        this.servers = servers;
        this.app = new AMOApplication<>(app);

        // TODO: wrap app inside AMOApplication ...
    }


    @Override
    public void init() {
        // TODO: initialize fields ...
        listOfCommands = new ArrayList<>();
    }


    /* -------------------------------------------------------------------------
        Interface Methods

        Be sure to implement the following methods correctly. The test code uses
        them to check correctness more efficiently.
       -----------------------------------------------------------------------*/

    /**
     * Return the status of a given slot in the servers's local log.
     *
     * Log slots are numbered starting with 1.
     *
     * @param logSlotNum
     *         the index of the log slot
     * @return the slot's status
     */
    public PaxosLogSlotStatus status(int logSlotNum) {
        // Your code here...
        if(logSlotNum > listOfCommands.size()){
            return PaxosLogSlotStatus.ACCEPTED;
        }else if(listOfCommands.contains(logSlotNum) && logSlotNum == listOfCommands.size()){
            return PaxosLogSlotStatus.CHOSEN;
        }else if(listOfCommands.contains(logSlotNum) && logSlotNum < listOfCommands.size()){
            return PaxosLogSlotStatus.CLEARED;
        }
        return PaxosLogSlotStatus.EMPTY;
    }

    /**
     * Return the command associated with a given slot in the server's local
     * log. If the slot has status {@link PaxosLogSlotStatus#CLEARED} or {@link
     * PaxosLogSlotStatus#EMPTY}, this method should return {@code null}. If
     * clients wrapped commands in {@link dslabs.atmostonce.AMOCommand}, this
     * method should unwrap them before returning.
     *
     * Log slots are numbered starting with 1.
     *
     * @param logSlotNum
     *         the index of the log slot
     * @return the slot's contents or {@code null}
     */
    public Command command(int logSlotNum) {
        // Your code here...
        Command slotCommand = listOfCommands.get(logSlotNum).command();
        return slotCommand;
    }

    /**
     * Return the index of the first non-cleared slot in the server's local
     * log.
     *
     * Log slots are numbered starting with 1.
     *
     * @return the index in the log
     */
    public int firstNonCleared() {
        // Your code here...
        for(int i = 0; i < listOfCommands.size(); i++){
            if(listOfCommands.get(i) != null)
                return i;
        }
        return 1;
    }

    /**
     * Return the index of the last non-empty slot in the server's local log. If
     * there are no non-empty slots in the log, this method should return 0.
     *
     * Log slots are numbered starting with 1.
     *
     * @return the index in the log
     */
    public int lastNonEmpty() {
        // Your code here...
        for(int i = 0; i < listOfCommands.size(); i++){
            if(listOfCommands.get(i) == null)
                return i - 1;
        }
        return 0;
    }

    /* -------------------------------------------------------------------------
        Message Handlers
       -----------------------------------------------------------------------*/
    private void handlePaxosRequest(PaxosRequest m, Address sender) {
        // TODO: handle paxos request ...
        AMOCommand amoCommand = new AMOCommand(m.command(), sender, m.sequenceNum());
        AMOResult amoResult = (AMOResult) app.execute(amoCommand);
        PaxosReply reply = new PaxosReply(this.address().toString(), m.sequenceNum(), amoResult);
        listOfCommands.add(amoCommand);
        this.send(reply, sender);
    }

    // TODO: your message handlers ...
    private void handleProposer(PaxosRequest m){
        int count = 0;
        for(int i = 0; i < servers.length; i++) {
            this.send(m, servers[i]);
        }
    }

    private void handleAcceptor(PaxosRequest m, Address serverAddress) {
        Promise acceptorPromise = null;
        if (m.sequenceNum() > latestValue) {
            latestValue = m.sequenceNum();
            acceptorPromise = new Promise(latestValue);
        }
        this.send(acceptorPromise, serverAddress);
    }

    /* -------------------------------------------------------------------------
        Timer Handlers
       -----------------------------------------------------------------------*/
    // TODO: your time handlers ...


    /* -------------------------------------------------------------------------
        Utils
       -----------------------------------------------------------------------*/
    // TODO: add utils here ...
}