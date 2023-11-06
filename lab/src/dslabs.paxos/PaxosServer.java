package dslabs.paxos;

import dslabs.atmostonce.AMOApplication;
import dslabs.atmostonce.AMOCommand;
import dslabs.atmostonce.AMOResult;
import dslabs.framework.Address;
import dslabs.framework.Application;
import dslabs.framework.Command;
import dslabs.framework.Node;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import static dslabs.paxos.HeartbeatTimer.HEARTBEAT_TIMER_MILLIS;
import static java.lang.Math.min;

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class PaxosServer extends Node {
    enum State {
        FOLLOWER, CANDIDATE, LEADER
    }

    static final int INITIAL_BALLOT_NUMBER = 0;
    private final Address me;
    private final Address[] peers;
    private int ballotNumber;
    private final AMOApplication amoApp;
    private TreeMap<Integer, LogEntry> logEntries;
    private HashMap<Address, RequestAndSlot> clientToRequestAndSlot;
    private int slotCounter;
    private int lastExecutedIndex;
    private State currentState;
    private final ElectionTimer electionTimer;
    private final HeartbeatTimer heartbeatTimer;
    private long electionResetEvent;
    private int timeOutDuration;
    private int heartbeatSequenceId;
    private int heartbeatLastLogIndex;
    private HashSet<Address> Votes;
    private HashSet<Address> HeartbeatVotes;
    private HashMap<Address, Integer> peersToLastExecutedIndex;
    private int prunedTill;
    private int pruneCounter;
    static final int PRUNE_EVERY = 350 / (HEARTBEAT_TIMER_MILLIS);


    /* -------------------------------------------------------------------------
        Construction and Initialization
       -----------------------------------------------------------------------*/
    public PaxosServer(Address address, Address[] servers, Application app) {
        super(address);

        // Addresses
        this.me = address;
        // Figure my peers
        peers = new Address[servers.length - 1];
        int count = 0;
        for (Address server : servers) {
            if (!server.equals(this.me)) {
                peers[count] = server;
                count++;
            }
        }

        // Long-lived States
        this.ballotNumber = INITIAL_BALLOT_NUMBER;
        this.amoApp = new AMOApplication<>(app);
        this.logEntries = new TreeMap<>();
        this.clientToRequestAndSlot = new HashMap<>();
        this.slotCounter = 0;
        this.lastExecutedIndex = -1;
        this.prunedTill = -1;
        this.currentState = State.FOLLOWER;
        this.peersToLastExecutedIndex = new HashMap<>();
        this.pruneCounter = 0;

        // Election Related vars
        electionTimer = new ElectionTimer();
        this.electionResetEvent = now();
        this.Votes = new HashSet<>();

        // Heartbeat Related vars
        this.heartbeatTimer = new HeartbeatTimer();
        this.heartbeatSequenceId = 0;
        this.heartbeatLastLogIndex = -1;
        this.HeartbeatVotes = new HashSet<>();
    }

    @Override
    public void init() {
        // Always running election timer
        int randomInterval = getRandom(this.electionTimer.ELECTION_TIMER_LOW,
                this.electionTimer.ELECTION_TIMER_HIGH);
        this.timeOutDuration = randomInterval;
        set(this.electionTimer, randomInterval);

        set(this.heartbeatTimer, HEARTBEAT_TIMER_MILLIS);

        if (peers.length == 0) {
            this.becomeLeader();
        } else {
            this.becomeCandidate();
        }
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
        int index = logSlotNum - 1;

        if (this.logEntries.containsKey(index)) {
            // Committed & thus can't be overwritten at all
            if (this.logEntries.get(index).isCommitted())
                return PaxosLogSlotStatus.CHOSEN;
                // Tentatively chosen
            else
                return PaxosLogSlotStatus.ACCEPTED;
        }

        // At this part of code; it is apparent that the index doesn't exist
        // It is considered empty, if it is beyond lastExecutedIndex
        if (index > this.lastExecutedIndex)
            return PaxosLogSlotStatus.EMPTY;
            // If the index is lesser than lastExecutedIndex; but still missing -> GC-ed
        else
            return PaxosLogSlotStatus.CLEARED;
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
        int index = logSlotNum - 1;
        if(this.logEntries.containsKey(index))
            return this.logEntries.get(index).request().command();
        return null;
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
        if (!this.logEntries.isEmpty()) {
            return this.logEntries.firstKey() + 1;
        } else {
            return this.lastExecutedIndex + 2;
        }
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
        if (!this.logEntries.isEmpty()) {
            return this.logEntries.lastKey() + 1;
        } else {
            if (this.lastExecutedIndex == -1) {
                return 0;
            }
            return this.lastExecutedIndex + 1;
        }
    }

    private void handlePaxosRequest(PaxosRequest request, Address client) {
        if (currentState != State.LEADER) {
            return;
        }

        addToLogEntries(request, client);
    }

    private void addToLogEntries(PaxosRequest request, Address client) {
        assert (!this.logEntries.containsKey(this.slotCounter)) :
                this.slotCounter + "is in logEntries=" + this.logEntries.get(this.slotCounter);

        if (this.clientToRequestAndSlot.containsKey(client)) {
            RequestAndSlot requestAndSlot = clientToRequestAndSlot.get(client);
            if (requestAndSlot.request().equals(request)) {
                LogEntry logEntry = logEntries.get(requestAndSlot.slot());
                if (logEntry.isCommitted() && logEntry.reply() != null) {
                    send(logEntry.reply(), client);
                }
                return;
            }
        }

        this.logEntries.put(this.slotCounter,
                new LogEntry(this.ballotNumber, client, request, null, false));
        this.clientToRequestAndSlot.put(client, new RequestAndSlot(request, this.slotCounter));

        if (this.peers.length == 0) {
            this.logEntries.get(this.slotCounter).isCommitted(true);
            runAppLogic("addToLogEntries");
        } else {
            sendHeartbeat();
        }
        this.slotCounter++;
    }

    private void becomeLeader() {
        // Update state
        this.currentState = State.LEADER;
        this.clientToRequestAndSlot.clear();
        this.heartbeatSequenceId = 0;

        // Fill Holes; Run App Logic; Reply to clients; Update slotCounter
        fillHoles();
        runAppLogic("becomeLeader");
        pruneTreeLeader();
        this.slotCounter = 0;

        if (!this.logEntries.isEmpty()) {
            this.slotCounter = this.logEntries.lastKey() + 1;

            // Make sure there are no holes (All are filled)
            for (int i = this.lastExecutedIndex + 1; i <= this.logEntries.lastKey(); i++) {
                assert (this.logEntries.containsKey(i)) :
                        "Leader with hole @ " + i;
            }
        }

    }

    public void handleHeartbeatReply(HeartbeatReply heartbeatReply, Address sender) {
        if (heartbeatReply.ballotNumber() > this.ballotNumber) {
            becomeFollower(heartbeatReply.ballotNumber(),
                    "handleHeartbeatReply: Got higher ballotNumber=" + heartbeatReply.ballotNumber());
        }

        // Solicit Phase2B replies only when in Leader state
        if (currentState != State.LEADER) {
            return;
        }

        if(this.heartbeatSequenceId == heartbeatReply.sequenceId() &&
                this.ballotNumber == heartbeatReply.ballotNumber() &&
                heartbeatReply.success()) {
            this.HeartbeatVotes.add(sender);
            this.peersToLastExecutedIndex.put(sender, heartbeatReply.lastExecutedIndex());
        }

        if(this.HeartbeatVotes.size() >= ((this.peers.length + 1) / 2) + 1) {
            commitTree();
            runAppLogic("handleHeartbeatReply");
        }
    }

    void sendHeartbeat() {
        if(this.currentState == State.LEADER) {
            // Set params of this hearbeat
            this.heartbeatSequenceId++;
            this.heartbeatLastLogIndex = -1;
            this.HeartbeatVotes.clear();
            this.HeartbeatVotes.add(this.address());

            // All peers would sync from their last sync-ed to maximum possible tree
            // So find the lastKey of the leader
            int lastKey = 1;
            if (!this.logEntries.isEmpty()) {
                lastKey = this.logEntries.lastKey() + 1;
                this.heartbeatLastLogIndex = this.logEntries.lastKey();
            }

            for (Address peer : this.peers) {
                // Create the submap
                SortedMap<Integer, LogEntry> subMap = null;
                if (this.logEntries.containsKey(this.peersToLastExecutedIndex.get(peer) + 1)) {
                    subMap = this.logEntries.subMap(this.peersToLastExecutedIndex.get(peer) + 1, lastKey);
                }
                // Send it to followers
                this.send(new Heartbeat(this.ballotNumber, this.heartbeatSequenceId, this.prunedTill, subMap), peer);
            }
        }
    }

    public void onHeartbeatTimer(HeartbeatTimer heartbeatTimer) {
        this.pruneCounter++;
        if (this.pruneCounter % PRUNE_EVERY == 0) {
            if (this.currentState == State.LEADER) {
                pruneTreeLeader();
            } else {
                pruneTreeFollower();
            }
        }
        sendHeartbeat();
        set(this.heartbeatTimer, HEARTBEAT_TIMER_MILLIS);
    }

    /* -------------------------------------------------------------------------
                          CANDIDATE MESSAGES & TIMERS
       -----------------------------------------------------------------------*/
    public void becomeCandidate() {
        assert (this.currentState == State.FOLLOWER);
        this.currentState = State.CANDIDATE;
    }

    private synchronized void onElectionTimer(ElectionTimer electionTimer) {
        if(this.currentState == State.FOLLOWER) {
            if(now() - this.electionResetEvent >= this.timeOutDuration) {
                becomeCandidate();
            }
        }

        // If you are a candidate, start election
        if (this.currentState == State.CANDIDATE) {
            this.ballotNumber++;
            this.Votes.clear();
            this.Votes.add(this.address());

            // Assume that no body has executed anything
            for (Address server: this.peers) {
                peersToLastExecutedIndex.put(server, -1);
            }

            this.broadcast(new Phase1A(this.ballotNumber), this.peers);
        }

        // Re-arm the timer
        int randomInterval = getRandom(this.electionTimer.ELECTION_TIMER_LOW,
                this.electionTimer.ELECTION_TIMER_HIGH);
        this.timeOutDuration = randomInterval;
        set(this.electionTimer, randomInterval);
    }

    public void handlePhase1B(Phase1B phase1B, Address sender) {

        if (phase1B.ballotNumber() > this.ballotNumber) {
            becomeFollower(phase1B.ballotNumber(),
                    "handlePhase1B: Got higherBallot="+phase1B.ballotNumber());
        }

        if (currentState != State.CANDIDATE) {
            return;
        }

        if (this.ballotNumber == phase1B.ballotNumber() && phase1B.voteGranted()) {
            this.Votes.add(sender);

            // Update logEntries
            phase1B.logEntries().forEach((slotNumber, logEntry) -> {
                if (!this.logEntries.containsKey(slotNumber)) {
                    this.logEntries.put(slotNumber, logEntry);
                } else if (logEntry.ballotNumber() >
                        this.logEntries.get(slotNumber).ballotNumber()) {
                    this.logEntries.put(slotNumber, logEntry);
                }
            });

            // Update this peer's lastExecutedIndex
            this.peersToLastExecutedIndex.put(sender, phase1B.lastExecutedIndex());

            if(this.Votes.size() >= ((this.peers.length + 1) / 2) + 1) {
                becomeLeader();
            }
        }
    }

    /* -------------------------------------------------------------------------
                          FOLLOWER MESSAGES & TIMERS
       -----------------------------------------------------------------------*/
    private void becomeFollower(int ballotNumber, String reason) {
        this.ballotNumber = ballotNumber;
        this.currentState = State.FOLLOWER;
        this.electionResetEvent = now();
        this.heartbeatSequenceId = 0;
        this.clientToRequestAndSlot.clear();

    }

    public void handlePhase1A(Phase1A phase1A, Address sender) {
        // All nodes will participate in voting
        boolean voteGranted = false;
        if (phase1A.ballotNumber() > this.ballotNumber) {
            becomeFollower(phase1A.ballotNumber(),
                    "handlePhase1A: Vote Granted to: " + sender + "; Ballot Update: " + this.ballotNumber + " -> " + phase1A.ballotNumber());
            voteGranted = true;
            this.electionResetEvent = now();
        }
        this.send(new Phase1B(voteGranted, this.ballotNumber, this.logEntries, this.lastExecutedIndex), sender);
    }

    public void handleHeartbeat(Heartbeat heartbeat, Address sender) {
        if(heartbeat.ballotNumber() > this.ballotNumber) {
            this.becomeFollower(heartbeat.ballotNumber(),
                    "handleHeartbeat: Ballot Update: " + this.ballotNumber + " -> " + heartbeat.ballotNumber());
        }

        boolean success = false;
        if(this.ballotNumber == heartbeat.ballotNumber() &&
                heartbeat.sequenceId() > this.heartbeatSequenceId) {

            if (heartbeat.subLogEntries() != null) {
                heartbeat.subLogEntries().forEach((slotNumber, logEntry) -> {
                    if (!this.logEntries.containsKey(slotNumber)) {
                        this.logEntries.put(slotNumber, logEntry);
                    } else {
                        LogEntry myLogEntry = this.logEntries.get(slotNumber);
                        if (myLogEntry.isCommitted()) {
                            assert (myLogEntry.request().equals(logEntry.request()));
                        }
                        this.logEntries.put(slotNumber, logEntry);
                    }

                });
            }

            this.prunedTill = heartbeat.pruneTill();

            this.electionResetEvent = now();
            this.heartbeatSequenceId = heartbeat.sequenceId();
            runAppLogic("handleHeartbeat");
            success = true;
        }

        send(new HeartbeatReply(success, this.ballotNumber, heartbeat.sequenceId(), this.lastExecutedIndex), sender);
    }

    private void fillHoles() {
        if (this.logEntries.isEmpty())
            return;

        int lastKey = this.logEntries.lastKey();
        for (int i=this.lastExecutedIndex+1; i<=lastKey; i++) {
            if (!this.logEntries.containsKey(i)) {
                this.logEntries.put(i, new LogEntry(
                        this.ballotNumber, null, null, null,
                        false
                ));
            }
        }
    }

    private void pruneTreeFollower() {
        if (!this.logEntries.isEmpty()) {
            this.prunedTill = pruneTree(this.prunedTill);
        }
    }

    private void pruneTreeLeader() {
        if (!this.logEntries.isEmpty()) {
            int minLastExecutedIndex = this.lastExecutedIndex;
            for (Map.Entry<Address, Integer> entry : this.peersToLastExecutedIndex.entrySet()) {
                minLastExecutedIndex = min(minLastExecutedIndex, entry.getValue());
            }
            this.prunedTill = pruneTree(minLastExecutedIndex);
        }
    }

    private int pruneTree(int pruneMax) {
        int firstKey = this.logEntries.firstKey();
        if (pruneMax < firstKey) {
            return pruneMax;
        }

        LogEntry logEntry;


        for (int i=firstKey; i<=pruneMax; i++) {
            logEntry = this.logEntries.get(i);
            if (logEntry.client() != null) {
                if (this.clientToRequestAndSlot.containsKey(logEntry.client())) {
                    if (this.clientToRequestAndSlot.get(logEntry.client()).request().requestID() >
                            logEntry.request().requestID()) {
                        this.logEntries.remove(i);
                    } else {
                        break;
                    }
                } else {
                    this.logEntries.remove(i);
                }
            } else {
                this.logEntries.remove(i);
            }
        }

        if (!this.logEntries.isEmpty()) {
            return this.logEntries.firstKey() - 1;
        }
        else {
            return -1;
        }
    }

    private void runAppLogic(String caller) {
        if (this.logEntries.isEmpty()) {
            return;
        }
        int newLastExecutedIndex = this.lastExecutedIndex;
        int lastKey = this.logEntries.lastKey();

        for (int i=this.lastExecutedIndex+1; i<=lastKey; i++) {
            if (!this.logEntries.containsKey(i)) {
                break;
            }

            if (!this.logEntries.get(i).isCommitted()) {
                break;
            }

            LogEntry logEntry = this.logEntries.get(i);
            if (logEntry.request() == null) {
                newLastExecutedIndex = i;
                continue;
            }

            AMOCommand amoCommand = new AMOCommand(
                    logEntry.request().command(), logEntry.client(), logEntry.request().requestID());
            AMOResult amoResult = (AMOResult) PaxosServer.this.amoApp.execute(amoCommand);


            if (this.currentState == State.LEADER) {
                PaxosReply reply = new PaxosReply(true, "default",
                        logEntry.request().requestID(), amoResult.result());
                this.logEntries.get(i).setReply(reply);
                this.send(reply, logEntry.client());
            }

            newLastExecutedIndex = i;
        }


        this.lastExecutedIndex = newLastExecutedIndex;
    }

    private int getRandom(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private long now() {
        return System.currentTimeMillis();
    }

    public void commitTree() {
        for(int i=this.lastExecutedIndex+1; i<=this.heartbeatLastLogIndex; i++) {
            this.logEntries.get(i).isCommitted(true);
        }
    }
}
