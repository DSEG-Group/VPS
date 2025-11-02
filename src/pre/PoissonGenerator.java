package pre;

import java.util.Random;

public class PoissonGenerator {
    private Random random = new Random();
    private double lambda;

    public PoissonGenerator(double lambda) {
        this.lambda = lambda;
    }

    public int sample() {
        double L = Math.exp(-lambda);
        int k = 0;
        double p = 1.0;
        do {
            k++;
            p *= random.nextDouble();
        } while (p > L);
        return k - 1;
    }
}

