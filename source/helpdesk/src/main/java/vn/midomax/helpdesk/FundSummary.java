package vn.midomax.helpdesk;

/**
 * Tổng hợp số liệu của một quỹ để hiển thị trên trang tổng quan (thẻ + thanh %).
 */
public class FundSummary {

    private final ExpenseFund fund;
    private final long allocated;
    private final long spent;
    private final long remaining;
    private final int spentPercent;
    private final boolean overBudget;
    private final int expenseCount;

    public FundSummary(ExpenseFund fund, long spent, int expenseCount) {
        this.fund = fund;
        this.allocated = fund.getAllocatedAmount();
        this.spent = spent;
        this.remaining = allocated - spent;
        this.expenseCount = expenseCount;

        int pct = 0;
        if (allocated > 0) {
            pct = (int) Math.round((spent * 100.0) / allocated);
            if (pct > 100) pct = 100;
            if (pct < 0) pct = 0;
        }
        this.spentPercent = pct;
        this.overBudget = remaining < 0;
    }

    public ExpenseFund getFund() { return fund; }
    public long getAllocated() { return allocated; }
    public long getSpent() { return spent; }
    public long getRemaining() { return remaining; }
    public int getSpentPercent() { return spentPercent; }
    public boolean isOverBudget() { return overBudget; }
    public int getExpenseCount() { return expenseCount; }
}
