package costco;

public class Customer implements Runnable {
    private final int id;
    private final long shoppingTimeMillis;
    private final CheckoutQueue queue;
    private final long[] arrivalTimes;
    private final long[] queueEntryTimes;

    public Customer(int id, long shoppingTimeMillis, CheckoutQueue queue,
                    long[] arrivalTimes, long[] queueEntryTimes) {
        this.id = id;
        this.shoppingTimeMillis = shoppingTimeMillis;
        this.queue = queue;
        this.arrivalTimes = arrivalTimes;
        this.queueEntryTimes = queueEntryTimes;
    }

    @Override
    public void run() {
        try {
            Thread.sleep(shoppingTimeMillis);

            long arrivalTime = System.nanoTime();
            arrivalTimes[id] = arrivalTime;
            queue.blockingPut(id);
            queueEntryTimes[id] = System.nanoTime() - arrivalTime;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}