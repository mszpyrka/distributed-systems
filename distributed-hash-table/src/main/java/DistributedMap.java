import org.jgroups.*;
import org.jgroups.util.Util;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DistributedMap extends ReceiverAdapter implements SimpleStringMap {

    private final Map<String, Integer> map;
    private final JChannel channel;

    public DistributedMap(JChannel channel) {
        this.channel = channel;
        this.channel.setReceiver(this);
        this.channel.setDiscardOwnMessages(true);
        this.map = new ConcurrentHashMap<>();

        // Fetches initial state from group's coordinator.
        try {
            this.channel.getState(null, 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Packs given object into JGroups message
     * and sends it to all nodes within the cluster.
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

    @Override
    public void getState(OutputStream output) throws Exception {
        synchronized(this.map) {
            Util.objectToStream(this.map, new DataOutputStream(output));
        }
    }

    @Override
    public void setState(InputStream input) throws Exception {
        Map<String, Integer> newMap = Util.objectFromStream(new DataInputStream(input));
        synchronized(this.map) {
            this.map.clear();
            this.map.putAll(newMap);
        }
    }

    /**
     * Handles any changes in the cluster's state.
     */
    public void viewAccepted(View view) {
        if (view instanceof MergeView) {
            MergeHandler handler = new MergeHandler(this.channel, (MergeView) view);
            handler.start();
        }
    }


    /**
     * Wrapper for update regarding new element insertion.
     */
    private static class InsertUpdate implements Serializable {
        String key;
        int value;

        private InsertUpdate(String key, int value) {
            this.key = key;
            this.value = value;
        }
    }

    /**
     * Wrapper for update regarding element removal.
     */
    private static class RemoveUpdate implements Serializable {
        String key;

        private RemoveUpdate(String key) {
            this.key = key;
        }
    }

    /**
     * Class used for managing partition merging.
     * Merging process is run in separate thread in order
     * not to block other JGroup functionality.
     */
    private static class MergeHandler extends Thread {
        JChannel channel;
        MergeView view;

        private MergeHandler(JChannel channel, MergeView view) {
            this.channel = channel;
            this.view = view;
        }

        /**
         * Fetches coordinator's hash table data in case of
         * either connecting to the group for the first time
         * or merging network partitions, if the network coordinator
         * is present in different partition than this node.
         */
        public void run() {
            List<View> subgroups = this.view.getSubgroups();
            View mainPartition = subgroups.get(0);
            Address localAddress = this.channel.getAddress();

            if (!mainPartition.getMembers().contains(localAddress)) {
                try {
                    this.channel.getState(null, 0);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
