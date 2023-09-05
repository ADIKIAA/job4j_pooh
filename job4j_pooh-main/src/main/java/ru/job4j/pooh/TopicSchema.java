package ru.job4j.pooh;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

public class TopicSchema implements Schema {

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Receiver>> receivers = new ConcurrentHashMap();

    private final ConcurrentHashMap<
            String,
            ConcurrentHashMap<String, ConcurrentLinkedQueue<String>>> topics = new ConcurrentHashMap<>();

    private final Condition condition = new Condition();

    @Override
    public void addReceiver(Receiver receiver) {
        receivers.putIfAbsent(receiver.name(), new CopyOnWriteArrayList<>());
        receivers.get(receiver.name()).add(receiver);
        condition.on();
    }

    @Override
    public void publish(Message message) {
        topics.putIfAbsent(message.name(), new ConcurrentHashMap<>());
        topics.get(message.name()).putIfAbsent(message.name(), new ConcurrentLinkedQueue<>());
        topics.get(message.name()).get(message.name()).add(message.text());
        condition.on();
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            for (var queueKey : receivers.keySet()) {
                var queue = topics.getOrDefault(queueKey, new ConcurrentHashMap<>());
                var topic = queue.get(queueKey);
                var receiversByQueue = receivers.get(queueKey);
                var it = receiversByQueue.iterator();
                while (it.hasNext()) {
                    var data = topic.poll();
                    if (data != null) {
                        it.next().receive(data);
                    }
                    if (data == null) {
                        break;
                    }
                    if (!it.hasNext()) {
                        it = receiversByQueue.iterator();
                    }
                }
            }
            condition.off();
            try {
                condition.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
