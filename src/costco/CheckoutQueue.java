package costco;

import java.util.concurrent.ArrayBlockingQueue;


public class CheckoutQueue {

    /** Placed in the line when the store closes; a Cashier that takes it stops working. */
    public static final int STOP = -1;

    private final ArrayBlockingQueue<Integer> line;
    private final int capacity;

    // Statistics: shared by every thread, so only touched inside synchronized methods.
    private int timesFull;
    private int longestLine;
    private long totalIdleNanos;

    public CheckoutQueue(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Line capacity must be at least 1");
        }
        this.capacity = capacity;
        this.line = new ArrayBlockingQueue<>(capacity);
    }

    /**
     * Called by a Customer when they finish shopping (the producer side).
     * Blocks if the line is already full until a Cashier frees a spot.
     */
    public void blockingPut(int customerId) throws InterruptedException {
        if (!line.offer(customerId)) {           // no room right now
            recordFull();
            System.out.printf("Customer %3d finds the line FULL and waits to get in%n", customerId);
            line.put(customerId);                // block until a spot opens
        }

        int length = line.size();
        recordLineLength(length);
        System.out.printf("Customer %3d joins the line          Line length: %d%n", customerId, length);
    }

    /**
     * Called by a Cashier to get the next customer (the consumer side).
     * Blocks while the line is empty.
     *
     * @return the ID of the customer to serve, or STOP when the Cashier should go home
     */
    public int blockingGet() throws InterruptedException {
        long idleStart = System.nanoTime();
        int customerId = line.take();            // block until someone is in line
        recordIdle(System.nanoTime() - idleStart);

        String cashier = Thread.currentThread().getName();   // e.g. "Cashier 2", named in CostcoSimulation
        if (customerId == STOP) {
            System.out.printf("        %s closes their register%n", cashier);
        } else {
            System.out.printf("        %s serves Customer %3d     Line length: %d%n",
                    cashier, customerId, line.size());
        }
        return customerId;
    }

    /**
     * Called by main after every Customer has joined the line.
     * Puts one STOP marker in the line per Cashier, so each
     * Cashier finishes the real customers ahead of it and then stops.
     */
    public void closeLine(int numCashiers) throws InterruptedException {
        for (int i = 0; i < numCashiers; i++) {
            line.put(STOP);
        }
    }

    // ---------- synchronized statistics updates ----------

    private synchronized void recordFull() {
        timesFull++;
    }

    private synchronized void recordLineLength(int length) {
        longestLine = Math.max(longestLine, length);
    }

    private synchronized void recordIdle(long idleNanos) {
        totalIdleNanos += idleNanos;
    }

    // ---------- getters for the final report ----------

    public int getCapacity() { return capacity; }

    public synchronized int getTimesFull() { return timesFull; }

    public synchronized int getLongestLine() { return longestLine; }

    /** Total time all cashiers spent waiting for customers, in simulated minutes. */
    public synchronized double getTotalIdleMinutes() { return toMinutes(totalIdleNanos); }

    /** Converts real elapsed nanoseconds into simulated minutes. */
    public static double toMinutes(long nanos) {
        return nanos / 1_000_000.0 / CostcoSimulation.MS_PER_SIM_MINUTE;
    }
}