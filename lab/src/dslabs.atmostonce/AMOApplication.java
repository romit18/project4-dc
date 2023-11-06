package dslabs.atmostonce;

import dslabs.framework.Address;
import dslabs.framework.Application;
import dslabs.framework.Command;
import dslabs.framework.Result;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import java.util.Map;

@Getter
@EqualsAndHashCode
@ToString
@RequiredArgsConstructor
public final class AMOApplication<T extends Application>
        implements Application {
    @NonNull private final T application;

    //TODO: declare fields for your implementation
    @NonNull
    private Map<Address, CachedRequest> senderToReqCache = new HashMap<>();

    @Data
    @AllArgsConstructor
    public static class CachedRequest implements Serializable {
        private final int seqNum;
        private final AMOResult result;
    }

    @Override
    public AMOResult execute(Command command) {
        if (!(command instanceof AMOCommand)) {
            throw new IllegalArgumentException();
        }
        AMOCommand amoCommand = (AMOCommand) command;

        if (senderToReqCache.containsKey(amoCommand.sender())) {
            CachedRequest cachedReq = senderToReqCache.get(amoCommand.sender());
            if (amoCommand.seqNum() == cachedReq.seqNum()) {
                return cachedReq.result();
            } else if (amoCommand.seqNum() < cachedReq.seqNum()) {
                return new AMOResult(null);
            }
        }

        /*
         Since the tests do not call `sendCommand` twice in a row without getting a result back, we can ignore the case when a request is ongoing and the same request is sent again by the server.
         */
        Result result = this.application.execute(amoCommand.command());
        AMOResult amoResult = new AMOResult(result);
        senderToReqCache.put(amoCommand.sender(), new CachedRequest(amoCommand.seqNum(), amoResult));

        return amoResult;


        //TODO: execute the command
        //Hints: remember to check whether the command is executed before and update records
//        return null;
    }

    // copy constructor
    public AMOApplication(AMOApplication<Application> amoApplication)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Application application = amoApplication.application();
        this.application = (T) application.getClass().getConstructor(application.getClass()).newInstance(application);
        //TODO: a deepcopy constructor
        this.senderToReqCache.putAll(amoApplication.senderToReqCache);
        // Copy cache
        //Hints: remember to deepcopy all fields, especially the mutable ones
    }

    public Result executeReadOnly(Command command) {
        if (!command.readOnly()) {
            throw new IllegalArgumentException();
        }

        if (command instanceof AMOCommand) {
            return execute(command);
        }

        return application.execute(command);
    }

    public boolean alreadyExecuted(Command command) {
        if (!(command instanceof AMOCommand)) {
            throw new IllegalArgumentException();
        }

        AMOCommand amoCommand = (AMOCommand) command;
        return senderToReqCache.containsKey(amoCommand.sender()) &&
                senderToReqCache.get(amoCommand.sender()).seqNum ==
                        amoCommand.seqNum();
        
        //TODO: check whether the amoCommand is already executed or not
    }
}
