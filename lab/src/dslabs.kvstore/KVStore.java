package dslabs.kvstore;

import dslabs.framework.Application;
import dslabs.framework.Command;
import dslabs.framework.Result;
import lombok.*;

import java.util.HashMap;
import java.util.Map;


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

    @Getter private Map<String, String> map;

    public KVStore() {
        map = new HashMap<>();
    }

    // copy constructor
    public KVStore(KVStore application){
        map = new HashMap<>();
        
        for (Map.Entry<String, String> entry : application.map.entrySet()) {
            map.put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public KVStoreResult execute(Command command) {
        if (command instanceof Get) {
            Get g = (Get) command;
            String key = g.key();

            if (!map.containsKey(key)) {
                return new KeyNotFound();
            }
            
            return new GetResult(map.get(key));
        }

        if (command instanceof Put) {
            Put p = (Put) command;
            String key = p.key();
            String value = p.value();

            map.put(key, value);

            return new PutOk();
        }

        if (command instanceof Append) {
            Append a = (Append) command;
            String key = a.key();
            String value = a.value();

            if (!map.containsKey(key)) {
                map.put(key, value);
            } else {
                String oldValue = map.get(key);
                map.put(key, oldValue + value);
            }

            return new AppendResult(map.get(key));
        }

        throw new IllegalArgumentException();
    }
}
