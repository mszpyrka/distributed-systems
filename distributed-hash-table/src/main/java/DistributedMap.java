import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ReceiverAdapter;
import org.jgroups.View;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DistributedMap extends ReceiverAdapter implements SimpleStringMap {

    /**
     * Wrapper classes used for passing updates info between nodes in the cluster.
     */

    private static class InsertUpdate implements Serializable {
        String key;
        int value;

        private InsertUpdate(String key, int value) {
            this.key = key;
            this.value = value;
        }
    }

    private static class RemoveUpdate implements Serializable {
        String key;

        private RemoveUpdate(String key) {
            this.key = key;
        }
    }

    private final Map<String, Integer> map;
    private final JChannel channel;

    public DistributedMap(JChannel channel) {
        this.channel = channel;
        this.map = new ConcurrentHashMap<>();
    }

    /**
     * Packs given object into JGroups message and sends it to all nodes within the cluster.
     */
    private void broadcast(Object obj) {
        Message msg = new Message(null, obj);
        try {
            this.channel.send(msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean containsKey(String key) {

        return this.map.containsKey(key);
    }

    @Override
    public Integer get(String key) {

        return this.map.get(key);
    }

    @Override
    public void put(String key, Integer value) {

        broadcast(new InsertUpdate(key, value));
        this.map.put(key, value);
    }

    @Override
    public Integer remove(String key) {

        broadcast(new RemoveUpdate(key));
        return this.map.remove(key);
    }

    /**
     * Processes incoming update messages;
     */
    @Override
    public void receive(Message msg) {

        Object obj = msg.getObject();

        if (obj instanceof InsertUpdate) {
            InsertUpdate update = (InsertUpdate) obj;
            this.map.put(update.key, update.value);
        }

        else if (obj instanceof RemoveUpdate) {
            RemoveUpdate update = (RemoveUpdate) obj;
            this.map.remove(update.key);
        }
    }

    /**
     * Handles any changes in the cluster's state.
     * When this node initially connects to the partition that contains the cluster's coordinator,
     * then the state of the hash table is fetched.
     */
    public void viewAccepted(View new_view) {
        System.out.println("** view: " + new_view);
        // todo: fetching table state
    }
}
