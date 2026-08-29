package vn.midomax.helpdesk;

import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Test import file ngân sách ITD thật (nếu file tồn tại trên máy). */
class ItdBudgetImportTest {

    private static final Path ITD_FILE =
            Path.of("D:\\Midomax\\MIDOMAX PROJECT\\ITD-2026-NGÂN SÁCH 6 THÁNG CUỐI NĂM 2026 (1).xlsx");

    @Test
    void parseItdBudgetFile() throws Exception {
        if (!Files.exists(ITD_FILE)) return; // bỏ qua nếu không có file

        ExcelService service = new ExcelService();
        List<BudgetItem> items;
        try (FileInputStream in = new FileInputStream(ITD_FILE.toFile())) {
            items = service.parseBudgetItemsFromExcel(in, 1L);
        }

        assertFalse(items.isEmpty(), "Phải đọc được hạng mục từ file ITD");

        long totalOpex = items.stream().filter(i -> "OPEX".equals(i.getGroupCategory()))
                .mapToLong(BudgetItem::getAllocatedAmount).sum();
        long totalCapex = items.stream().filter(i -> "CAPEX".equals(i.getGroupCategory()))
                .mapToLong(BudgetItem::getAllocatedAmount).sum();

        System.out.println("Số hạng mục: " + items.size());
        System.out.println("Tổng OPEX:  " + totalOpex);
        System.out.println("Tổng CAPEX: " + totalCapex);
        for (BudgetItem i : items) {
            System.out.printf("[%s/%s] %s = %,d (T1..12: %s)%n",
                    i.getGroupCategory(), i.getCostType(), i.getItemName(),
                    i.getAllocatedAmount(), i.getMonthlyAmounts());
        }

        // Theo file: tổng OPEX = 346.378.000.
        // CAPEX: dòng "Tổng" trong file ghi 1.657.080.000 nhưng công thức SUM
        // không cover dòng "Chi phí dự trù" (50tr) thêm sau — tổng thực = 1.707.080.000.
        assertEquals(346_378_000L, totalOpex, "Tổng OPEX phải khớp sheet IT_OPEX");
        assertEquals(1_707_080_000L, totalCapex, "Tổng CAPEX phải khớp sheet IT_CAPEX");

        // Kế hoạch theo tháng phải cộng lại đúng tổng của hạng mục
        for (BudgetItem i : items) {
            long monthSum = 0;
            for (long m : i.getMonthlyAmountsArray()) monthSum += m;
            if (i.getAllocatedAmount() > 0) {
                assertEquals(i.getAllocatedAmount(), monthSum,
                        "Tổng 12 tháng phải khớp tổng năm: " + i.getItemName());
            }
        }
    }
}
