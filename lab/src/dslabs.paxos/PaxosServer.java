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
    int slotDone = 0;
    Address clientAddress;
    Set <Address> waitfor = new HashSet <Address>();
    int slot = 1;
    boolean active = false;
    Ballot ballot;
    int countOfRequests = 0;
    Map<Integer, Integer> slotToDecisionMap = new HashMap<>();
    int slotExecuted = 1;
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
        leader = ballot.address();
        set(new HeartbeatCheckTimer(ballot), HeartbeatCheckTimer.PING_CHECK_MILLIS);
        set(new HeartbeatTimer(), HeartbeatTimer.HEARTBEAT_MILLIS);

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
        if(!decisions.containsKey(logSlotNum)){
            return PaxosLogSlotStatus.EMPTY;
        }
        if(decisions.containsKey(logSlotNum) && decisions.get(logSlotNum) != null){
            return PaxosLogSlotStatus.CHOSEN;
        }
        if(decisions.containsKey(logSlotNum) && decisions.get(logSlotNum) == null){
            return PaxosLogSlotStatus.CLEARED;
        }
        return PaxosLogSlotStatus.ACCEPTED;
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
        return decisions.get(logSlotNum).command();
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
        for(Map.Entry<Integer, PaxosRequest> prop: decisions.entrySet()){
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
        if(!decisions.isEmpty()){
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
//        if(!proposals.containsValue(m)){
//
//        }
        this.clientAddress = sender;
//        if(slot == m.sequenceNum() + 1 && decisions.containsValue(m)) {
//            AMOCommand amoCommand = new AMOCommand(m.command(), sender, m.sequenceNum());
//            AMOResult amoResult = (AMOResult) app.execute(amoCommand);
//            send(new PaxosReply(this.address().toString(), m.sequenceNum(),amoResult), sender);
//        }
    }

    private void propose(PaxosRequest m){
        if(!decisions.containsValue(m)){
            for(int i = 1;;i++){
                if(!proposals.containsKey(i) && !decisions.containsKey(i)){
                    proposals.put(i, m);
                    for(Address address: leaders){
                        if(!this.address().equals(address))
                            send(new ProposeMessage(i, m), address);
                    }
                    break;
                }
            }
        }
    }

    // TODO: your message handlers ...
    private void handleProposeMessage(ProposeMessage m, Address sender){
        waitfor.clear();
        P1aMessage m1 = new P1aMessage(this.address(), ballot);
        for(Address a : leaders){
            send(m1,a);
            waitfor.add(a);
        }
        if(!proposals.containsKey(m.slot_number())){
            proposals.put(m.slot_number(), m.paxosRequest());
            waitfor.clear();
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
        send(new P1bMessage(this.address(), ballot, new HashSet<>(accepted)), sender);
    }

    private void handleP1bMessage(P1bMessage m, Address sender){
        Set<Pvalue> pvalues = new HashSet<Pvalue>();
        int cmp = ballot.compareTo(m.ballot);
        if (cmp != 0) {
            send(new PreemptedMessage(this.address(), m.ballot), ballot.address());
            return;
        }
        if(2 * waitfor.size() >= leaders.length) {
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
            leader = ballot.address();
            P1aMessage m1 = new P1aMessage(this.address(), ballot);
            for(Address a : leaders){
                send(m1,a);
                waitfor.add(a);
            }
            active = false;
        }
    }

    private void handleP2aMessage(P2aMessage m, Address sender){
        if(ballot == null || ballot.compareTo(m.ballot()) == 0){
            ballot = m.ballot;
            accepted.add(new Pvalue(ballot, m.slot(), m.paxosRequest()));
        }
        send(new P2bMessage(this.address(), ballot, m.slot(), m.paxosRequest()), sender);
    }

    private void handleP2bMessage(P2bMessage m, Address sender){
        if (ballot.equals(m.ballot())) {
            waitfor.remove(m.address);
        }else{
            send(new PreemptedMessage(this.address(), m.ballot), ballot.address());
            return;
        }
        if(2*waitfor.size() >= leaders.length) {
            for(Address address: leaders){
                if(!decisions.containsValue(m.paxosRequest)){
                    send(new DecisionMessage(this.address(),slot,m.paxosRequest()),address);
                }
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
            if(slot > slotDone){
                AMOCommand amoCommand = new AMOCommand(c.command(), sender, c.sequenceNum());
                AMOResult amoResult = (AMOResult) app.execute(amoCommand);
                send(new PaxosReply(this.address().toString(), c.sequenceNum(), amoResult), clientAddress);
                slotDone = slot;
                slotExecuted = slot;
            }
        }
    }

    private void perform(PaxosRequest paxosRequest){
        for(int s = 1; s<=slot;s++){
            if(paxosRequest.equals(decisions.get(s))){
                slot++;
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
                if(ballot != null)
                    leader = ballot.address();
                if(ballot == null || ballot.compareTo(pv.ballot()) < 0){
                    max.put(pv.slot_num(), pv.ballot());
                    proposals.put(pv.slot_num(), pv.paxosRequest());
                }
            }
            for(int sn: proposals.keySet()){
                waitfor.clear();
                P2aMessage m2 = new P2aMessage(this.address(), ballot, slot, proposals.get(sn));
                for(Address a: leaders) {
                    send(m2, a);
                    waitfor.add(a);
                }
            }
            active = true;
        }
    }

    private void handleHeartbeat(Heartbeat m, Address sender) {
        for(int i = 1; i<=slotExecuted;i++){
            decisions.put(i, null);
        }
        send(new HeartbeatReply(slot, m.ballot()), sender);
    }

    private void handleHeartbeatReply(HeartbeatReply m, Address sender) {
        if(m.slot() > 1){
            slotToDecisionMap.put(m.slot()-1, slotToDecisionMap.getOrDefault(m.slot()-1, 0) + 1);
            if(m.slot() - 1 > slotExecuted){
                slotExecuted = m.slot() - 1;
            }
        }
    }

    /* -------------------------------------------------------------------------
        Timer Handlers
       -----------------------------------------------------------------------*/
    // TODO: your time handlers ...
    private synchronized void onHeartbeatCheckTimer(HeartbeatCheckTimer t) {
        // TODO: handle client request timeout ...
        if (!t.leader().address().equals(ballot.address())) {
            countOfRequests++;
            if(countOfRequests == 2){
                send(new P1aMessage(this.address(), new Ballot(t.leader().sequenceNum() + 1, this.address())), ballot.address());
                countOfRequests = 0;
                return;
            }
            set(new HeartbeatCheckTimer(t.leader()), HeartbeatCheckTimer.PING_CHECK_MILLIS);
        }
    }

    private void onHeartbeatTimer(HeartbeatTimer t) {
        // TODO: handle client request timeout ...
        if (this.address().equals(ballot.address())) {
            for(Address address: leaders){
                if(!address.equals(this.address())){
                    send(new Heartbeat(ballot, slotExecuted), address);
                }
            }
            set(new HeartbeatTimer(), HeartbeatTimer.HEARTBEAT_MILLIS);
        }
    }


    /* -------------------------------------------------------------------------
        Utils
       -----------------------------------------------------------------------*/
    // TODO: add utils here ...
}