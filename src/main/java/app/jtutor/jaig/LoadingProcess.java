package app.jtutor.jaig;


public class LoadingProcess {
    private Thread animationThread;
    private volatile boolean running;

    public void start() {
        running = true;

        animationThread = new Thread(() -> {
            int frame = 0;
            while (running) {
                String loadingText = "Generating response" + getLoadingDots(frame);
                System.out.print("\r" + loadingText);
                frame = (frame + 1) % 4;
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        animationThread.start();
    }

    public void stop() {
        running = false;
        if (animationThread != null) {
            animationThread.interrupt();
            animationThread = null;
            System.out.println("\n");
        }
    }

    private String getLoadingDots(int count) {
        StringBuilder dots = new StringBuilder();
        for (int i = 0; i < count; i++) {
            dots.append(".");
        }
        return dots.toString();
    }
}

