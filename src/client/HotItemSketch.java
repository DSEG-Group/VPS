package client;

public class HotItemSketch {

    /** 哈希函数数量 */
    private final int d;
    /** 每个哈希表的桶数量 */
    private final int w;

    /** 统计表 */
    private final long[][] countTable;
    private final double[][] valueSumTable;
    private long totalUpdates = 0;
    private long distinctEst = 0;

    /** 哈希种子 */
    private final int[] seeds;

    /**
     * @param d 哈希函数数量（推荐 3~5）
     * @param w 每行桶数（推荐 2^16）
     */
    public HotItemSketch(int d, int w) {
        this.d = d;
        this.w = w;
        this.countTable = new long[d][w];
        this.valueSumTable = new double[d][w];
        this.seeds = new int[d];

        for (int i = 0; i < d; i++) {
            seeds[i] = 31 * (i + 1) + 7;
        }
    }

    /** ---------------- 核心接口 ---------------- */

    /**
     * 在订单提交后调用，用于更新热门商品统计
     *
     * @param itemId    商品 ID
     * @param itemValue 商品价值（如价格）
     */
    public void update(int itemId, double itemValue) {
        for (int i = 0; i < d; i++) {
            int idx = hash(itemId, seeds[i]);
            if (countTable[i][idx] == 0) {
                distinctEst++;
            }
            countTable[i][idx]++;
            valueSumTable[i][idx] += itemValue;
        }
        totalUpdates++;
    }

    /**
     * 估计商品的热门程度（访问 / 购买次数）
     */
    public long estimateHotness(int itemId) {
        long min = Long.MAX_VALUE;
        for (int i = 0; i < d; i++) {
            int idx = hash(itemId, seeds[i]);
            min = Math.min(min, countTable[i][idx]);
        }
        return min == Long.MAX_VALUE ? 0 : min;
    }

    /**
     * 估计商品的平均价值
     */
    public double estimateAvgValue(int itemId) {
        double minAvg = Double.MAX_VALUE;
        for (int i = 0; i < d; i++) {
            int idx = hash(itemId, seeds[i]);
            long cnt = countTable[i][idx];
            if (cnt > 0) {
                minAvg = Math.min(minAvg, valueSumTable[i][idx] / cnt);
            }
        }
        return minAvg == Double.MAX_VALUE ? 0.0 : minAvg;
    }

    /**
     * 同时获取热门度和平均价值（减少哈希计算）
     */
    public HotItemFeature estimateFeature(int itemId) {
        long hot = Long.MAX_VALUE;
        double avg = Double.MAX_VALUE;

        for (int i = 0; i < d; i++) {
            int idx = hash(itemId, seeds[i]);
            long cnt = countTable[i][idx];
            if (cnt > 0) {
                hot = Math.min(hot, cnt);
                avg = Math.min(avg, valueSumTable[i][idx] / cnt);
            }
        }
        return new HotItemFeature(
                hot == Long.MAX_VALUE ? 0 : hot,
                avg == Double.MAX_VALUE ? 0.0 : avg
        );
    }

    /**
     * 判断某商品是否为热门商品
     *
     * @param itemId 商品 ID
     * @param alpha  热门阈值系数（推荐 2~5）
     */
    public boolean isHotItem(int itemId, double alpha) {
        long hotness = estimateHotness(itemId);

        if (distinctEst == 0) {
            return false;
        }

        double avgHotness = (double) totalUpdates / distinctEst;
        return hotness >= alpha * avgHotness;
    }

    /** ---------------- 工具方法 ---------------- */

    private int hash(int key, int seed) {
        int h = key ^ seed;
        h ^= (h >>> 16);
        return (h & 0x7fffffff) % w;
    }

    /** ---------------- 特征封装 ---------------- */

    public static class HotItemFeature {
        public final long hotness;
        public final double avgValue;

        public HotItemFeature(long hotness, double avgValue) {
            this.hotness = hotness;
            this.avgValue = avgValue;
        }
    }

    
}
