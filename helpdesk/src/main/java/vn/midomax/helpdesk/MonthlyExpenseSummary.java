package vn.midomax.helpdesk;

/**
 * Dòng tổng hợp chi tiêu theo tháng (dùng cho báo cáo tháng).
 */
public class MonthlyExpenseSummary {

    private final String monthKey; // "2026-07" dùng để lọc
    private final String label;    // "Tháng 07/2026" hiển thị
    private int count;
    private long total;

    public MonthlyExpenseSummary(String monthKey, String label) {
        this.monthKey = monthKey;
        this.label = label;
    }

    public void add(long amount) {
        this.count++;
        this.total += amount;
    }

    public String getMonthKey() { return monthKey; }
    public String getLabel() { return label; }
    public int getCount() { return count; }
    public long getTotal() { return total; }
}
