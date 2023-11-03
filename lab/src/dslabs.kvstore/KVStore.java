package dslabs.kvstore;

import dslabs.framework.Application;
import dslabs.framework.Command;
import dslabs.framework.Result;
import java.util.ArrayList;
import java.util.HashMap;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;


@ToString
@EqualsAndHashCode
public class KVStore implements Application {

    public interface KVStoreCommand extends Command {
    }

    public interface SingleKeyCommand extends KVStoreCommand {
        String key();
    }

    @Data
    public static final class Get implements SingleKeyCommand {
        @NonNull private final String key;

        @Override
        public boolean readOnly() {
            return true;
        }
    }

    @Data
    public static final class Put implements SingleKeyCommand {
        @NonNull private final String key, value;
    }

    @Data
    public static final class Append implements SingleKeyCommand {
        @NonNull private final String key, value;
    }

    public interface KVStoreResult extends Result {
    }

    @Data
    public static final class GetResult implements KVStoreResult {
        @NonNull private final String value;
    }

    @Data
    public static final class KeyNotFound implements KVStoreResult {
    }

    @Data
    public static final class PutOk implements KVStoreResult {
    }

    @Data
    public static final class AppendResult implements KVStoreResult {
        @NonNull private final String value;
    }

    //TODO: declare fields for your implementation

    private HashMap<String,String> keyValue;

    public KVStore() {
        keyValue = new HashMap<>();
    }

    // copy constructor
    public KVStore(KVStore application){
        this.keyValue = new HashMap<>();
        this.keyValue.putAll(application.keyValue);
        //TODO: a deepcopy constructor
        //Hints: remember to deepcopy all fields, especially the mutable ones
    }

    @Override
    public KVStoreResult execute(Command command) {
        if (command instanceof Get) {
            Get g = (Get) command;
            //TODO: get the value
            if(keyValue.containsKey(g.key))
                return new KVStore.GetResult(keyValue.get(g.key));
            return new KeyNotFound();
        }

        if (command instanceof Put) {
            Put p = (Put) command;
            keyValue.put(p.key,p.value);
            return new PutOk();
            //TODO: put the value
        }

        if (command instanceof Append) {
            Append a = (Append) command;
            keyValue.put(a.key, keyValue.getOrDefault(a.key,"") + a.value);
            return new AppendResult(keyValue.get(a.key));
            //TODO: append after previous
        }

        throw new IllegalArgumentException();
    }
}
