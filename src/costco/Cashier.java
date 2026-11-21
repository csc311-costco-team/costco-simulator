package costco;

public class Cashier implements Runnable {
    private final CheckoutQueue sharedLocation;
    private final long serviceTimeMillis;
    private final long[] arrivalTimes;
    private final long[] waitTimes;

    public Cashier(CheckoutQueue sharedLocation, long serviceTimeMillis,
                   long[] arrivalTimes, long[] waitTimes) {
        this.sharedLocation = sharedLocation;
        this.serviceTimeMillis = serviceTimeMillis;
        this.arrivalTimes = arrivalTimes;
        this.waitTimes = waitTimes;
    }

    @Override
    public void run() {
        try {
            while (true) {
                // Take the next customer ID from the shared buffer.
                int customerId = sharedLocation.blockingGet();

                if (customerId == CheckoutQueue.STOP) {
                    return;
                }

                long waitTime = System.nanoTime() - arrivalTimes[customerId];

                // Simulate serving the customer.
                Thread.sleep(serviceTimeMillis);
                waitTimes[customerId] = waitTime;
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}