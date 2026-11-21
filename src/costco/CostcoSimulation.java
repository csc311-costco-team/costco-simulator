package costco;

import java.security.SecureRandom;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Costco checkout simulator (main class).
 *
 * Prompts for the number of customers, cashiers and the line capacity, runs
 * Customer threads (producers) and Cashier threads (consumers) against one
 * shared CheckoutQueue, then prints wait-time and labor-cost results.
 *
 * Time scale: 1 simulated minute = MS_PER_SIM_MINUTE real milliseconds,
 * so a simulated hour of checkout runs in a few seconds.
 */
public class CostcoSimulation {

    /** Real milliseconds per simulated minute. */
    public static final int MS_PER_SIM_MINUTE = 100;

    /** Hourly wage per cashier, in dollars. */
    public static final double CASHIER_WAGE = 18.00;

    /** Each customer shops for a random 0 to MAX_SHOPPING_MINUTES simulated minutes. */
    public static final int MAX_SHOPPING_MINUTES = 30;

    /** Simulated minutes a cashier takes to ring up one customer. */
    public static final int SERVICE_MINUTES = 3;

    private static final SecureRandom generator = new SecureRandom();

    public static void main(String[] args) throws InterruptedException {
        Scanner input = new Scanner(System.in);

        System.out.println("====== Costco Checkout Simulator ======");
        int numCustomers = promptInt(input, "Number of customers: ", 1, 1000);
        int numCashiers  = promptInt(input, "Number of cashiers: ", 1, 100);
        int capacity     = promptInt(input, "Checkout line capacity: ", 1, 1000);
        System.out.println();

        CheckoutQueue queue = new CheckoutQueue(capacity);

        // Shared timing arrays, indexed by customer ID. IDs run 1..numCustomers,
        // so each array needs numCustomers + 1 slots (slot 0 is unused).
        // Each slot is written by only one thread, so no extra locking is needed.
        long[] arrivalTimes    = new long[numCustomers + 1]; // Customer: when they reached the line (nanoTime)
        long[] queueEntryTimes = new long[numCustomers + 1]; // Customer: time spent getting INTO the line
        long[] waitTimes       = new long[numCustomers + 1]; // Cashier: time from arrival until served

        // Name cashier threads "Cashier 1", "Cashier 2", ... so the output is readable.
        AtomicInteger cashierNumber = new AtomicInteger(1);
        ExecutorService cashierPool = Executors.newFixedThreadPool(numCashiers,
                task -> new Thread(task, "Cashier " + cashierNumber.getAndIncrement()));
        ExecutorService customerPool = Executors.newCachedThreadPool();

        long serviceTimeMillis = (long) SERVICE_MINUTES * MS_PER_SIM_MINUTE;
        long start = System.nanoTime();

        // Open the registers first, then let customers in.
        for (int i = 1; i <= numCashiers; i++) {
            cashierPool.execute(new Cashier(queue, serviceTimeMillis, arrivalTimes, waitTimes));
        }
        for (int id = 1; id <= numCustomers; id++) {
            long shoppingTimeMillis =
                    (long) generator.nextInt(MAX_SHOPPING_MINUTES + 1) * MS_PER_SIM_MINUTE;
            customerPool.execute(new Customer(id, shoppingTimeMillis, queue,
                    arrivalTimes, queueEntryTimes));
        }

        // 1. Wait until every customer has finished shopping and is in line.
        customerPool.shutdown();
        if (!customerPool.awaitTermination(10, TimeUnit.MINUTES)) {
            System.out.println("WARNING: some customers never reached the line.");
        }

        // 2. Store closes: tell each cashier to stop once the line is empty.
        queue.closeLine(numCashiers);

        // 3. Wait for the cashiers to finish serving everyone.
        cashierPool.shutdown();
        if (!cashierPool.awaitTermination(10, TimeUnit.MINUTES)) {
            System.out.println("WARNING: some cashiers did not finish.");
        }

        long elapsedNanos = System.nanoTime() - start;
        printReport(queue, numCustomers, numCashiers, elapsedNanos, queueEntryTimes, waitTimes);
    }

    /** Prints the results of one run. */
    private static void printReport(CheckoutQueue queue, int numCustomers, int numCashiers,
                                    long elapsedNanos, long[] queueEntryTimes, long[] waitTimes) {
        // Wait-time stats from the Customers' and Cashiers' own timing.
        long totalWait = 0, maxWait = 0, totalEntry = 0;
        for (int id = 1; id <= numCustomers; id++) {
            totalWait += waitTimes[id];
            maxWait = Math.max(maxWait, waitTimes[id]);
            totalEntry += queueEntryTimes[id];
        }
        double avgWait  = CheckoutQueue.toMinutes(totalWait) / numCustomers;
        double avgEntry = CheckoutQueue.toMinutes(totalEntry) / numCustomers;

        double simMinutes  = CheckoutQueue.toMinutes(elapsedNanos);
        double cashierTime = numCashiers * simMinutes;              // total cashier-minutes paid for
        double utilization = 100.0 * (cashierTime - queue.getTotalIdleMinutes()) / cashierTime;
        double laborCost   = numCashiers * CASHIER_WAGE * (simMinutes / 60.0);

        System.out.println();
        System.out.println("============ Simulation Results ============");
        System.out.printf("Customers / cashiers / line capacity : %d / %d / %d%n",
                numCustomers, numCashiers, queue.getCapacity());
        System.out.printf("Service time per customer            : %d min%n", SERVICE_MINUTES);
        System.out.printf("Simulated duration                   : %.1f min%n", simMinutes);
        System.out.printf("Average wait in line                 : %.1f min%n", avgWait);
        System.out.printf("Longest wait in line                 : %.1f min%n", CheckoutQueue.toMinutes(maxWait));
        System.out.printf("Longest line                         : %d customers%n", queue.getLongestLine());
        System.out.printf("Times a customer found the line full : %d%n", queue.getTimesFull());
        System.out.printf("Avg time to get into the line        : %.1f min%n", avgEntry);
        System.out.printf("Cashier utilization (busy time)      : %.0f%%%n", utilization);
        System.out.printf("Labor cost ($%.2f/hr per cashier)   : $%.2f%n", CASHIER_WAGE, laborCost);
        System.out.printf("Labor cost per customer              : $%.2f%n", laborCost / numCustomers);
        System.out.println("============================================");
    }

    /** Reads an int in [min, max], re-prompting on bad input. */
    private static int promptInt(Scanner input, String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);
            if (input.hasNextInt()) {
                int value = input.nextInt();
                if (value >= min && value <= max) {
                    return value;
                }
            } else if (!input.hasNext()) {
                throw new IllegalStateException("No more input.");
            } else {
                input.next(); // discard non-number
            }
            System.out.printf("Please enter a whole number from %d to %d.%n", min, max);
        }
    }
}