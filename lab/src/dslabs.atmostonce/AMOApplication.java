package dslabs.atmostonce;

import dslabs.framework.Address;
import dslabs.framework.Application;
import dslabs.framework.Command;
import dslabs.framework.Result;
import lombok.*;
import org.apache.commons.lang3.tuple.Pair;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

@EqualsAndHashCode
@ToString
@RequiredArgsConstructor
public final class AMOApplication<T extends Application>
        implements Application {
    @Getter @NonNull private final T application;

    Map<Address, Pair<Integer, AMOResult>> sequenceNums = new HashMap<>();

    @Override
    public AMOResult execute(Command command) {
        if (!(command instanceof AMOCommand)) {
            throw new IllegalArgumentException();
        }

        AMOCommand amoCommand = (AMOCommand) command;
        Address clientAddress = amoCommand.clientAddress();
        int sequenceNum = amoCommand.sequenceNum();
        if (alreadyExecuted(amoCommand)) {
            Pair<Integer, AMOResult> pair = sequenceNums.get(clientAddress);
            return pair.getRight();
        }

        Result result = application.execute(amoCommand.command());
        AMOResult amoResult = new AMOResult(result);
        sequenceNums.put(clientAddress, Pair.of(sequenceNum, amoResult));

        return new AMOResult(result);
    }

    // copy constructor
    public AMOApplication(AMOApplication<Application> amoApplication)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Application application = amoApplication.application();
        this.application = (T) application.getClass().getConstructor(application.getClass()).newInstance(application);
        
        for (Map.Entry<Address, Pair<Integer, AMOResult>> entry : amoApplication.sequenceNums.entrySet()) {
            Address address = entry.getKey();
            Pair<Integer, AMOResult> pair = entry.getValue();

            Integer cSequenceNum = new Integer(pair.getLeft());
            AMOResult cAmoResult = new AMOResult(pair.getRight().result().getClass().getConstructor(pair.getRight().result().getClass()).newInstance(pair.getRight().result()));
            sequenceNums.put(address, Pair.of(cSequenceNum, cAmoResult));
        }
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
        Address clientAddress = amoCommand.clientAddress();
        int sequenceNum = amoCommand.sequenceNum();

        if (!sequenceNums.containsKey(clientAddress)) {
            return false;
        }
        Pair<Integer, AMOResult> pair = sequenceNums.get(clientAddress);
        int lastSequenceNum = pair.getLeft();

        return sequenceNum <= lastSequenceNum;
    }
}
