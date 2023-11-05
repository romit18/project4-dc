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

import java.util.*;


@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class PaxosServer extends Node {
    static final int INITIAL_BALLOT_NUMBER = 0;
    private final Address[] leaders;
    private AMOApplication<Application> app;
    Address leader;
    Map<Integer, PaxosRequest> proposals = new HashMap<Integer, PaxosRequest>();
    Map<Integer, PaxosRequest> decisions = new HashMap<Integer, PaxosRequest>();
    Set<Pvalue> accepted = new HashSet<>();

    Address clientAddress;

    Set <Address> waitfor = new HashSet <Address>();
    int slot = 1;
    boolean active = false;
    Ballot ballot;
    // TODO: declare fields for your implementation ...

    /* -------------------------------------------------------------------------
        Construction and Initialization
       -----------------------------------------------------------------------*/
    public PaxosServer(Address address, Address[] servers, Application app) {
        super(address);
        this.leaders = servers;
        this.app = new AMOApplication<>(app);

        // TODO: wrap app inside AMOApplication ...
    }


    @Override
    public void init() {
        // TODO: initialize fields ...
        ballot = new Ballot(INITIAL_BALLOT_NUMBER, this.address());

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
        if(!proposals.containsKey(logSlotNum)){
            return PaxosLogSlotStatus.EMPTY;
        }
        if(proposals.containsKey(logSlotNum) && proposals.get(logSlotNum) == null){
            return PaxosLogSlotStatus.CLEARED;
        }
        if(decisions.containsKey(logSlotNum) && decisions.get(logSlotNum) != null){
            return PaxosLogSlotStatus.ACCEPTED;
        }
        return PaxosLogSlotStatus.CHOSEN;
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
        PaxosLogSlotStatus status = status(logSlotNum);
        if(status == PaxosLogSlotStatus.CLEARED || status == PaxosLogSlotStatus.EMPTY){
            return null;
        }
        return proposals.get(logSlotNum).command();
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
        for(Map.Entry<Integer, PaxosRequest> prop: proposals.entrySet()){
            if(prop.getValue() != null){
                return prop.getKey();
            }
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
        // Your code here..
        if(!proposals.isEmpty()){
            return slot-1;
        }
        return 0;
    }

    /* -------------------------------------------------------------------------
        Message Handlers
       -----------------------------------------------------------------------*/
    private void handlePaxosRequest(PaxosRequest m, Address sender) {
        // TODO: handle paxos request ...
        propose(m);
        this.clientAddress = sender;
        if(slot == m.sequenceNum() + 1) {
            AMOCommand amoCommand = new AMOCommand(m.command(), sender, m.sequenceNum());
            AMOResult amoResult = (AMOResult) app.execute(amoCommand);
            send(new PaxosReply(this.address().toString(), m.sequenceNum(),amoResult), sender);
        }
    }

    private void propose(PaxosRequest m){
        if(!decisions.containsValue(m)){
            for(int i = 1;;i++){
                if(!proposals.containsKey(i) && !decisions.containsKey(i)){
                    proposals.put(i, m);
                    for(Address address: leaders){
                        send(new ProposeMessage(i, m), address);
                    }
                    break;
                }
            }
        }
    }

    // TODO: your message handlers ...
    private void handleProposeMessage(ProposeMessage m, Address sender){
        P1aMessage m1 = new P1aMessage(this.address(), ballot);
        for(Address a : leaders){
            send(m1,a);
            waitfor.add(a);
        }
        if(!proposals.containsKey(m.slot_number())){
            proposals.put(m.slot_number(), m.paxosRequest());
            if(active){
                P2aMessage m2 = new P2aMessage(this.address(), ballot, slot, m.paxosRequest());
                for(Address a: leaders) {
                    send(m2, a);
                    waitfor.add(a);
                }
            }
        }
    }

    private void handleP1aMessage(P1aMessage m, Address sender){
        if(ballot == null || ballot.compareTo(m.ballot()) < 0){
            ballot = m.ballot;
        }
        send(new P1bMessage(m.address, ballot, new HashSet<>(accepted)), m.address);
    }

    private void handleP1bMessage(P1bMessage m, Address sender){
        Set <Pvalue> pvalues = new HashSet <Pvalue>();
        if(2 * waitfor.size() >= leaders.length) {
            int cmp = ballot.compareTo(m.ballot);
            if (cmp != 0) {
                send(new PreemptedMessage(this.address(), m.ballot), ballot.address());
            }
            if (waitfor.contains(m.address)) {
                waitfor.remove(m.address);
                pvalues.addAll(m.accepted);
            }
            send(new AdoptedMessage(this.address(), ballot, pvalues), ballot.address());
        }
    }

    private void handlePreemptedMessage(PreemptedMessage m, Address sender){
        if(ballot.compareTo(m.ballot) < 0){
            ballot = new Ballot(m.ballot.sequenceNum() + 1, this.address());
            P1aMessage m1 = new P1aMessage(this.address(), ballot);
            for(Address a : leaders){
                send(m1,a);
                waitfor.add(a);
            }
            active = false;
        }
    }



    private void handleP2aMessage(P2aMessage m, Address sender){
        if(ballot == null || ballot.compareTo(m.ballot()) <= 0){
            ballot = m.ballot;
            accepted.add(new Pvalue(ballot, m.slot(), m.paxosRequest()));
        }
        send(new P2bMessage(this.address(), ballot, m.slot(), m.paxosRequest()), m.address);
    }

    private void handleP2bMessage(P2bMessage m, Address sender){
        if(2*waitfor.size() >= leaders.length) {
            if (ballot.equals(m.ballot())) {
                waitfor.remove(m.address);
            } else {
                send(new PreemptedMessage(this.address(), m.ballot), ballot.address());
            }
            for(Address address: leaders){
                send(new DecisionMessage(this.address(),slot,m.paxosRequest()),address);
            }
        }
    }

    private void handleDecisionMessage(DecisionMessage m, Address sender){
        decisions.put(m.slot, m.paxosRequest);
        for(;;){
            PaxosRequest c = decisions.get(slot);
            if(c == null){
                break;
            }
            PaxosRequest c2 = proposals.get(slot);
            if(c2 != null && !c2.equals(c)){
                propose(c2);
            }
            perform(c);
        }
    }

    private void perform(PaxosRequest paxosRequest){
        for(int s = 1; s<=slot;s++){
            if(paxosRequest.equals(decisions.get(s))){
                slot++;
//                AMOCommand amoCommand = new AMOCommand(paxosRequest.command(),clientAddress, paxosRequest.sequenceNum());
//                AMOResult amoResult = (AMOResult) app.execute(amoCommand);
//                send(new PaxosReply(this.address().toString(), paxosRequest.sequenceNum(), amoResult),clientAddress);
                return;
            }
        }
        slot++;
    }

    private void handleAdoptedMessage(AdoptedMessage m, Address sender){
        if(ballot.equals(m.ballot)){
            Map<Integer, Ballot> max = new HashMap<>();
            for(Pvalue pv: m.accepted){
                Ballot ballot = max.get(pv.slot_num());
                if(ballot == null || ballot.compareTo(pv.ballot()) < 0){
                    max.put(pv.slot_num(), pv.ballot());
                    proposals.put(pv.slot_num(), pv.paxosRequest());
                }
            }
            for(int sn: proposals.keySet()){
                P2aMessage m2 = new P2aMessage(this.address(), ballot, slot, proposals.get(sn));
                for(Address a: leaders) {
                    send(m2, a);
                    waitfor.add(a);
                }
            }
            active = true;
        }
    }

    private void handleAcceptor(PaxosRequest m, Address serverAddress) {
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