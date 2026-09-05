package vn.midomax.helpdesk;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.midomax.helpdesk.storage.StorageService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Quản lý tài chính: theo dõi chi tiêu mua sắm công cụ / dụng cụ (CCDC) của team.
 * Route: /expenses (mục "Quản Lý Tài Chính" trên sidebar).
 */
@Controller
@RequestMapping("/expenses")
public class ExpenseController {

    private static final long DEFAULT_ALLOCATED = 10_000_000L; // 10 triệu mặc định

    @Autowired
    private ExpenseFundRepository fundRepository;

    @Autowired
    private ToolExpenseRepository expenseRepository;

    @Autowired
    private BudgetItemRepository budgetItemRepository;

    @Autowired
    private InvoiceEntryRepository invoiceRepository;

    @Autowired
    private ExcelService excelService;

    @Autowired
    private StorageService storageService;

    /** Trang tổng quan: liệt kê tất cả quỹ + tổng ngân sách toàn công ty. */
    @GetMapping
    public String overview(Model model) {
        List<ExpenseFund> funds = fundRepository.findAllByOrderByCreatedAtDesc();

        List<FundSummary> summaries = new java.util.ArrayList<>();
        long totalAllocated = 0, totalSpent = 0;
        for (ExpenseFund f : funds) {
            long spent = expenseRepository.sumAmountByFundId(f.getId());
            long count = expenseRepository.countByFundId(f.getId());
            summaries.add(new FundSummary(f, spent, (int) count));
            totalAllocated += f.getAllocatedAmount();
            totalSpent += spent;
        }
        long totalRemaining = totalAllocated - totalSpent;
        int totalPercent = 0;
        if (totalAllocated > 0) {
            totalPercent = (int) Math.round((totalSpent * 100.0) / totalAllocated);
            if (totalPercent > 100) totalPercent = 100;
            if (totalPercent < 0) totalPercent = 0;
        }

        model.addAttribute("summaries", summaries);
        model.addAttribute("fundCount", summaries.size());
        model.addAttribute("totalAllocated", totalAllocated);
        model.addAttribute("totalSpent", totalSpent);
        model.addAttribute("totalRemaining", totalRemaining);
        model.addAttribute("totalPercent", totalPercent);
        model.addAttribute("totalOverBudget", totalRemaining < 0);
        model.addAttribute("defaultAllocated", DEFAULT_ALLOCATED);
        model.addAttribute("activePage", "expenses");
        return "expense-overview";
    }

    /** Trang chi tiết một quỹ: ngân sách + báo cáo tháng + lịch sử chi. */
    @GetMapping("/fund/{fundId}")
    public String fundDetail(@PathVariable("fundId") Long fundId,
                             @RequestParam(value = "month", required = false) String month,
                             Model model,
                             RedirectAttributes redirectAttributes) {

        ExpenseFund current = fundRepository.findById(fundId).orElse(null);
        if (current == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy quỹ.");
            return "redirect:/expenses";
        }

        List<ExpenseFund> funds = fundRepository.findAllByOrderByCreatedAtDesc();

        // Hóa đơn trong Sổ Hóa Đơn đã đối chiếu về quỹ này cũng ăn vào ngân sách,
        // nhưng chỉ tính dòng ĐÃ THANH TOÁN (xem InvoiceEntry.isDeductible).
        // Tổng quỹ trừ MỌI hóa đơn PAID của quỹ, kể cả dòng chưa đối chiếu tới hạng mục —
        // tiền đã chi thật thì phải ăn vào quỹ ngay; đối chiếu hạng mục chỉ để xem chi tiết.
        List<InvoiceEntry> fundInvoices = invoiceRepository.findByFundId(current.getId());
        long invoiceSpent = fundInvoices.stream()
                .filter(i -> InvoiceEntry.STATUS_PAID.equalsIgnoreCase(i.getPaymentStatus()))
                .mapToLong(InvoiceEntry::getAmount)
                .sum();

        long allocated = current.getAllocatedAmount();
        long spent = expenseRepository.sumAmountByFundId(current.getId()) + invoiceSpent;
        long remaining = allocated - spent;

        // Phần trăm đã chi (0..100), làm tròn để hiển thị thanh tiến trình
        int spentPercent = 0;
        if (allocated > 0) {
            spentPercent = (int) Math.round((spent * 100.0) / allocated);
            if (spentPercent > 100) spentPercent = 100;
            if (spentPercent < 0) spentPercent = 0;
        }
        int remainingPercent = 100 - spentPercent;

        // Toàn bộ khoản chi của quỹ (đã sắp xếp mới nhất trước)
        List<ToolExpense> allExpenses = expenseRepository.findByFundIdOrderByCreatedAtDesc(current.getId());

        // Lấy danh sách Hạng mục ngân sách (từ file Excel đã import)
        List<BudgetItem> budgetItems = budgetItemRepository.findByFundIdOrderByGroupCategoryAscSubCategoryAscIdAsc(current.getId());

        // "Đã chi" của một hạng mục = khoản chi lẻ (tool_expenses) + HÓA ĐƠN đã gán ở Sổ Hóa Đơn.
        // Không cộng hóa đơn vào đây thì phần trăm ngân sách luôn bằng 0 dù tiền đã chi thật.
        java.util.Map<Long, Long> invoiceSpentByItem = new java.util.HashMap<>();
        java.util.Map<Long, Integer> invoiceCountByItem = new java.util.HashMap<>();
        // Thực chi TỪNG THÁNG của mỗi hạng mục, để popup kế hoạch tháng đối chiếu ngay
        java.util.Map<Long, long[]> invoiceMonthlyByItem = new java.util.HashMap<>();
        List<Long> itemIds = budgetItems.stream().map(BudgetItem::getId).toList();
        int outOfPeriod = 0;
        if (!itemIds.isEmpty()) {
            for (InvoiceEntry inv : invoiceRepository.findByBudgetItemIdIn(itemIds)) {
                // Hóa đơn đã hủy / hoàn tiền hoặc chưa thanh toán: tiền chưa chi ra thật nên bỏ qua
                // (cùng luật với tổng quỹ ở trên: chỉ tính hóa đơn PAID, xem InvoiceEntry.isDeductible).
                if (inv.isCancelled() || !inv.isDeductible()) continue;
                // Chỉ tính hóa đơn nằm trong KỲ NGÂN SÁCH của quỹ.
                // Quỹ "6 tháng cuối năm" đặt T6–T12 thì hóa đơn tháng 1–5 không bị trừ vào đây.
                if (!current.covers(inv.getYearOfEntry(), inv.getMonthOfYear())) {
                    outOfPeriod++;
                    continue;
                }
                invoiceSpentByItem.merge(inv.getBudgetItemId(), inv.getAmount(), Long::sum);
                invoiceCountByItem.merge(inv.getBudgetItemId(), 1, Integer::sum);
                int mIdx = inv.getMonthOfYear() - 1;
                if (mIdx >= 0 && mIdx < 12) {
                    invoiceMonthlyByItem.computeIfAbsent(inv.getBudgetItemId(), k -> new long[12])[mIdx] += inv.getAmount();
                }
            }
        }
        model.addAttribute("outOfPeriodCount", outOfPeriod);

        java.util.Map<Long, String> budgetItemNameMap = new java.util.HashMap<>();
        long invoiceSpentTotal = 0;
        for (BudgetItem item : budgetItems) {
            long fromExpenses = allExpenses.stream()
                    .filter(e -> item.getId().equals(e.getBudgetItemId()))
                    .mapToLong(ToolExpense::getAmount)
                    .sum();
            long fromInvoices = invoiceSpentByItem.getOrDefault(item.getId(), 0L);
            invoiceSpentTotal += fromInvoices;
            item.setSpentAmount(fromExpenses + fromInvoices);
            budgetItemNameMap.put(item.getId(), item.getItemName() + " (" + item.getSubCategory() + ")");
        }
        // Chuỗi CSV "0,0,3000000,..." cho từng hạng mục — template gắn vào nút mở popup
        java.util.Map<Long, String> invoiceMonthlyCsv = new java.util.HashMap<>();
        for (var e : invoiceMonthlyByItem.entrySet()) {
            StringBuilder sb = new StringBuilder();
            long[] arr = e.getValue();
            for (int i = 0; i < 12; i++) {
                if (i > 0) sb.append(",");
                sb.append(arr[i]);
            }
            invoiceMonthlyCsv.put(e.getKey(), sb.toString());
        }
        model.addAttribute("invoiceMonthlyCsv", invoiceMonthlyCsv);
        model.addAttribute("invoiceSpentByItem", invoiceSpentByItem);
        model.addAttribute("invoiceCountByItem", invoiceCountByItem);
        model.addAttribute("invoiceSpentTotal", invoiceSpentTotal);

        // Khoản chi lẻ của quỹ (tool_expenses). Tổng "spent" của quỹ đã tính ở đầu hàm:
        // khoản chi lẻ + MỌI hóa đơn PAID đã đối chiếu về quỹ (kể cả dòng chưa gán hạng mục).
        long expenseSpent = expenseRepository.sumAmountByFundId(current.getId());
        model.addAttribute("expenseSpent", expenseSpent);

        // Nhóm Hạng mục ngân sách theo "Chi phí nhóm 2"
        java.util.LinkedHashMap<String, List<BudgetItem>> budgetGroupMap = new java.util.LinkedHashMap<>();
        for (BudgetItem item : budgetItems) {
            budgetGroupMap.computeIfAbsent(item.getGroupCategory(), k -> new java.util.ArrayList<>()).add(item);
        }

        // Tổng hợp theo nhóm: [0]=cấp, [1]=đã chi, [2]=% đã chi (cho header OPEX/CAPEX)
        java.util.Map<String, long[]> groupSummaryMap = new java.util.HashMap<>();
        for (var entry : budgetGroupMap.entrySet()) {
            long gAlloc = entry.getValue().stream().mapToLong(BudgetItem::getAllocatedAmount).sum();
            long gSpent = entry.getValue().stream().mapToLong(BudgetItem::getSpentAmount).sum();
            long gPct = gAlloc > 0 ? Math.round(gSpent * 100.0 / gAlloc) : 0;
            groupSummaryMap.put(entry.getKey(), new long[]{gAlloc, gSpent, gPct});
        }

        // Báo cáo theo tháng (gộp toàn bộ khoản chi của quỹ)
        List<MonthlyExpenseSummary> monthlyReport = buildMonthlyReport(allExpenses);

        // Lọc danh sách hiển thị theo tháng đã chọn (nếu có)
        List<ToolExpense> expenses = filterByMonth(allExpenses, month);
        long periodSpent = expenses.stream().mapToLong(ToolExpense::getAmount).sum();

        model.addAttribute("funds", funds);
        model.addAttribute("fund", current);
        model.addAttribute("allocated", allocated);
        model.addAttribute("spent", spent);
        model.addAttribute("remaining", remaining);
        model.addAttribute("spentPercent", spentPercent);
        model.addAttribute("remainingPercent", remainingPercent);
        model.addAttribute("overBudget", remaining < 0);
        model.addAttribute("expenses", expenses);
        model.addAttribute("expenseCount", expenses.size());
        model.addAttribute("budgetItems", budgetItems);
        model.addAttribute("budgetGroupMap", budgetGroupMap);
        model.addAttribute("groupSummaryMap", groupSummaryMap);
        model.addAttribute("budgetItemNameMap", budgetItemNameMap);
        model.addAttribute("monthlyReport", monthlyReport);
        model.addAttribute("selectedMonth", month);
        model.addAttribute("selectedMonthLabel",
                (month != null && !month.isBlank()) ? monthLabel(month) : null);
        model.addAttribute("periodSpent", periodSpent);
        model.addAttribute("activePage", "expenses");

        return "expense-management";
    }

    /** Xuất báo cáo chi tiêu ra Excel (theo quỹ, có thể lọc theo tháng). */
    @GetMapping("/export")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.InputStreamResource> exportExcel(
            @RequestParam(value = "fundId", required = false) Long fundId,
            @RequestParam(value = "month", required = false) String month) throws IOException {

        List<ExpenseFund> funds = fundRepository.findAllByOrderByCreatedAtDesc();
        if (funds.isEmpty()) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }
        ExpenseFund current = resolveFund(fundId, funds);

        long allocated = current.getAllocatedAmount();
        long spent = expenseRepository.sumAmountByFundId(current.getId());

        List<ToolExpense> allExpenses = expenseRepository.findByFundIdOrderByCreatedAtDesc(current.getId());
        List<ToolExpense> expenses = filterByMonth(allExpenses, month);

        String periodLabel = (month != null && !month.isBlank()) ? monthLabel(month) : "Toàn bộ";
        java.io.ByteArrayInputStream in = excelService.exportExpensesToExcel(current, expenses, allocated, spent, periodLabel);

        String suffix = (month != null && !month.isBlank()) ? month : "toanbo";
        String filename = "BaoCaoChiTieu_" + suffix + ".xlsx";

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=" + filename);

        return org.springframework.http.ResponseEntity
                .ok()
                .headers(headers)
                .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new org.springframework.core.io.InputStreamResource(in));
    }

    /** Chọn quỹ đang xem: theo id -> quỹ active -> quỹ mới nhất. */
    private ExpenseFund resolveFund(Long fundId, List<ExpenseFund> funds) {
        ExpenseFund current = null;
        if (fundId != null) {
            current = fundRepository.findById(fundId).orElse(null);
        }
        if (current == null) {
            current = fundRepository.findFirstByActiveTrueOrderByCreatedAtDesc();
        }
        if (current == null && !funds.isEmpty()) {
            current = funds.get(0);
        }
        return current;
    }

    /** Gộp các khoản chi thành báo cáo theo tháng (giữ thứ tự mới -> cũ). */
    private List<MonthlyExpenseSummary> buildMonthlyReport(List<ToolExpense> expenses) {
        java.util.LinkedHashMap<String, MonthlyExpenseSummary> map = new java.util.LinkedHashMap<>();
        for (ToolExpense e : expenses) {
            if (e.getCreatedAt() == null) continue;
            String key = monthKey(e.getCreatedAt());
            MonthlyExpenseSummary s = map.computeIfAbsent(key, k -> new MonthlyExpenseSummary(k, monthLabel(k)));
            s.add(e.getAmount());
        }
        return new java.util.ArrayList<>(map.values());
    }

    /** Lọc danh sách theo tháng dạng "yyyy-MM"; rỗng -> giữ nguyên. */
    private List<ToolExpense> filterByMonth(List<ToolExpense> expenses, String month) {
        if (month == null || month.isBlank()) return expenses;
        List<ToolExpense> result = new java.util.ArrayList<>();
        for (ToolExpense e : expenses) {
            if (e.getCreatedAt() != null && month.equals(monthKey(e.getCreatedAt()))) {
                result.add(e);
            }
        }
        return result;
    }

    private String monthKey(LocalDateTime dt) {
        return String.format("%04d-%02d", dt.getYear(), dt.getMonthValue());
    }

    private String monthLabel(String key) {
        // key = "yyyy-MM"
        try {
            String[] p = key.split("-");
            return "Tháng " + p[1] + "/" + p[0];
        } catch (Exception e) {
            return key;
        }
    }

    /** Thêm một khoản chi mới (kèm ảnh hóa đơn) vào quỹ và tự động khấu trừ hạng mục. */
    @PostMapping("/add")
    public String addExpense(@RequestParam("fundId") Long fundId,
                             @RequestParam("itemName") String itemName,
                             @RequestParam("amount") String amountRaw,
                             @RequestParam(value = "budgetItemId", required = false) Long budgetItemId,
                             @RequestParam(value = "category", required = false) String category,
                             @RequestParam(value = "invoiceNumber", required = false) String invoiceNumber,
                             @RequestParam(value = "invoiceDate", required = false) String invoiceDateRaw,
                             @RequestParam(value = "note", required = false) String note,
                             @RequestParam(value = "invoiceFile", required = false) MultipartFile invoiceFile,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {

        long amount = parseMoney(amountRaw);
        if (itemName == null || itemName.isBlank() || amount <= 0) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vui lòng nhập tên công cụ và số tiền hợp lệ.");
            return "redirect:/expenses/fund/" + fundId;
        }

        ToolExpense expense = new ToolExpense();
        expense.setFundId(fundId);
        expense.setItemName(itemName.trim());
        expense.setAmount(amount);
        expense.setBudgetItemId(budgetItemId);
        expense.setCategory(category);
        expense.setInvoiceNumber(invoiceNumber);
        expense.setInvoiceDate(parseDate(invoiceDateRaw));
        expense.setNote(note);
        expense.setCreatedBy(authentication != null ? authentication.getName() : "unknown");
        expense.setCreatedAt(LocalDateTime.now());

        // Upload ảnh hóa đơn (theo đúng pattern của TicketController)
        String invoicePath = storageService.store(invoiceFile);
        if (invoicePath != null) {
            expense.setInvoicePath(invoicePath);
        }

        expenseRepository.save(expense);

        // Cập nhật số tiền đã khấu trừ cho BudgetItem
        if (budgetItemId != null) {
            budgetItemRepository.findById(budgetItemId).ifPresent(item -> {
                long totalSpent = expenseRepository.findByFundIdOrderByCreatedAtDesc(fundId).stream()
                        .filter(e -> item.getId().equals(e.getBudgetItemId()))
                        .mapToLong(ToolExpense::getAmount)
                        .sum();
                item.setSpentAmount(totalSpent);
                budgetItemRepository.save(item);
            });
        }

        redirectAttributes.addFlashAttribute("successMessage",
                "Đã ghi nhận khoản chi \"" + expense.getItemName() + "\" & khấu trừ vào ngân sách.");
        return "redirect:/expenses/fund/" + fundId;
    }

    /**
     * Import file Excel phân bổ ngân sách.
     * - Chọn quỹ có sẵn (fundId) -> nạp vào quỹ đó.
     * - Không chọn quỹ -> tự tạo quỹ mới (mục cha) từ file, các hạng mục con ăn theo.
     */
    /**
     * Lưu kế hoạch ngân sách 12 tháng cho một hạng mục.
     *
     * Dùng cho các khoản trả theo tháng (ChatGPT, cước Internet, thuê máy photo...):
     * nhập thẳng SỐ TIỀN của từng tháng, ngân sách cấp của hạng mục = tổng các tháng
     * đã nhập — thay vì phải quy về đơn giá × số lượng.
     */
    @PostMapping("/budget-item/{id}/monthly")
    public String saveMonthlyPlan(@PathVariable("id") Long id,
                                  @RequestParam(value = "amounts", required = false) List<String> amounts,
                                  RedirectAttributes redirectAttributes) {
        BudgetItem item = budgetItemRepository.findById(id).orElse(null);
        if (item == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy hạng mục ngân sách.");
            return "redirect:/expenses";
        }

        long[] months = new long[12];
        long total = 0;
        if (amounts != null) {
            for (int i = 0; i < 12 && i < amounts.size(); i++) {
                months[i] = parseMoney(amounts.get(i));
                total += months[i];
            }
        }
        item.setMonthlyAmountsFromArray(months);
        // Tổng các tháng chính là ngân sách cấp của hạng mục
        item.setAllocatedAmount(total);
        budgetItemRepository.save(item);

        // Cập nhật lại tổng ngân sách của quỹ theo các hạng mục
        Long fundId = item.getFundId();
        long fundTotal = budgetItemRepository.findByFundIdOrderByGroupCategoryAscSubCategoryAscIdAsc(fundId)
                .stream().mapToLong(BudgetItem::getAllocatedAmount).sum();
        fundRepository.findById(fundId).ifPresent(f -> {
            f.setAllocatedAmount(fundTotal);
            fundRepository.save(f);
        });

        redirectAttributes.addFlashAttribute("successMessage",
                "Đã lưu kế hoạch 12 tháng cho \"" + item.getItemName() + "\". Ngân sách cấp: "
                        + String.format("%,d", total).replace(',', '.') + " đ.");
        return "redirect:/expenses/fund/" + fundId;
    }

    /** Một dòng đối chiếu: hạng mục ngân sách + kế hoạch/thực chi 12 tháng. */
    public static class ReconcileRow {
        private BudgetItem item;
        private long[] planned = new long[12];   // kế hoạch theo tháng
        private long[] actual = new long[12];    // thực chi từ hóa đơn đã gán
        private long plannedTotal;
        private long actualTotal;
        private int invoiceCount;

        public BudgetItem getItem() { return item; }
        public long[] getPlanned() { return planned; }
        public long[] getActual() { return actual; }
        public long getPlannedTotal() { return plannedTotal; }
        public long getActualTotal() { return actualTotal; }
        public int getInvoiceCount() { return invoiceCount; }
        /** Dương = còn dư ngân sách, âm = đã vượt. */
        public long getVariance() { return plannedTotal - actualTotal; }
        public boolean isOverBudget() { return actualTotal > plannedTotal; }
        public int getUsedPercent() {
            if (plannedTotal <= 0) return actualTotal > 0 ? 100 : 0;
            return (int) Math.min(999, Math.round(actualTotal * 100.0 / plannedTotal));
        }
    }

    /**
     * Đối chiếu KẾ HOẠCH NGÂN SÁCH với THỰC CHI trong Sổ Hóa Đơn.
     *
     * Hóa đơn được tính vào một hạng mục khi đã gán budgetItemId; hóa đơn chưa gán
     * liệt kê riêng để nhân sự tài chính gán tiếp — nhờ vậy con số đối chiếu luôn
     * kiểm chứng được, không đoán mò theo tên.
     */
    @GetMapping("/reconcile")
    public String reconcile(@RequestParam(value = "fundId", required = false) Long fundId,
                            @RequestParam(value = "year", required = false) Integer year,
                            Model model) {

        List<ExpenseFund> funds = fundRepository.findAllByOrderByCreatedAtDesc();
        ExpenseFund current = null;
        if (fundId != null) {
            current = fundRepository.findById(fundId).orElse(null);
        }
        if (current == null && !funds.isEmpty()) {
            current = funds.get(0);
        }
        int targetYear = year != null ? year : java.time.LocalDate.now().getYear();

        List<ReconcileRow> rows = new ArrayList<>();
        List<InvoiceEntry> unlinked = new ArrayList<>();
        long[] plannedByMonth = new long[12];
        long[] actualByMonth = new long[12];

        if (current != null) {
            List<BudgetItem> items = budgetItemRepository
                    .findByFundIdOrderByGroupCategoryAscSubCategoryAscIdAsc(current.getId());

            // Gom hóa đơn của năm đang xem theo hạng mục ngân sách
            java.util.Map<Long, List<InvoiceEntry>> invoicesByItem = new java.util.HashMap<>();
            for (InvoiceEntry inv : invoiceRepository.findAll()) {
                if (inv.getYearOfEntry() != targetYear) continue;
                if (inv.isCancelled()) continue; // đã hủy / hoàn tiền: không đối chiếu
                // Ngoài kỳ ngân sách của quỹ (VD quỹ 6 tháng cuối năm: bỏ qua T1–T5)
                if (!current.covers(inv.getYearOfEntry(), inv.getMonthOfYear())) continue;
                if (inv.getBudgetItemId() == null) {
                    unlinked.add(inv);
                } else {
                    invoicesByItem.computeIfAbsent(inv.getBudgetItemId(), k -> new ArrayList<>()).add(inv);
                }
            }

            for (BudgetItem item : items) {
                ReconcileRow row = new ReconcileRow();
                row.item = item;
                row.planned = item.getMonthlyAmountsArray();
                for (int m = 0; m < 12; m++) {
                    row.plannedTotal += row.planned[m];
                    plannedByMonth[m] += row.planned[m];
                }
                // Kế hoạch chưa nhập theo tháng thì lấy ngân sách cấp làm tổng
                if (row.plannedTotal == 0) {
                    row.plannedTotal = item.getAllocatedAmount();
                }

                for (InvoiceEntry inv : invoicesByItem.getOrDefault(item.getId(), Collections.emptyList())) {
                    int m = inv.getMonthOfYear();
                    if (m >= 1 && m <= 12) {
                        row.actual[m - 1] += inv.getAmount();
                        actualByMonth[m - 1] += inv.getAmount();
                    }
                    row.actualTotal += inv.getAmount();
                    row.invoiceCount++;
                }
                rows.add(row);
            }
        }

        long plannedGrand = 0, actualGrand = 0;
        for (ReconcileRow r : rows) {
            plannedGrand += r.getPlannedTotal();
            actualGrand += r.getActualTotal();
        }
        long unlinkedTotal = unlinked.stream().mapToLong(InvoiceEntry::getAmount).sum();

        model.addAttribute("funds", funds);
        model.addAttribute("fund", current);
        model.addAttribute("rows", rows);
        model.addAttribute("unlinked", unlinked);
        model.addAttribute("unlinkedTotal", unlinkedTotal);
        model.addAttribute("plannedByMonth", plannedByMonth);
        model.addAttribute("actualByMonth", actualByMonth);
        model.addAttribute("plannedGrand", plannedGrand);
        model.addAttribute("actualGrand", actualGrand);
        model.addAttribute("year", targetYear);
        model.addAttribute("activePage", "reconcile");
        return "expense-reconcile";
    }

    /** Gán một hóa đơn vào hạng mục ngân sách (dùng ở trang đối chiếu). */
    @PostMapping("/reconcile/link")
    public String linkInvoice(@RequestParam Long invoiceId,
                              @RequestParam(required = false) Long budgetItemId,
                              @RequestParam(required = false) Long fundId,
                              @RequestParam(required = false) Integer year,
                              RedirectAttributes redirectAttributes) {
        invoiceRepository.findById(invoiceId).ifPresent(inv -> {
            inv.setBudgetItemId(budgetItemId);
            invoiceRepository.save(inv);
        });
        redirectAttributes.addFlashAttribute("successMessage",
                budgetItemId == null ? "Đã bỏ gán hạng mục cho hóa đơn." : "Đã gán hóa đơn vào hạng mục ngân sách.");
        return "redirect:/expenses/reconcile?fundId=" + (fundId == null ? "" : fundId)
                + (year == null ? "" : "&year=" + year);
    }

    @PostMapping("/import-budget")
    public String importBudget(@RequestParam(value = "fundId", required = false) Long fundId,
                               @RequestParam(value = "newFundName", required = false) String newFundName,
                               @RequestParam("excelFile") MultipartFile file,
                               Authentication authentication,
                               RedirectAttributes redirectAttributes) {

        if (file == null || file.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vui lòng chọn file Excel để upload.");
            return "redirect:/expenses";
        }

        boolean autoCreated = false;
        ExpenseFund fund = null;
        if (fundId != null) {
            fund = fundRepository.findById(fundId).orElse(null);
            if (fund == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy quỹ ngân sách.");
                return "redirect:/expenses";
            }
        } else {
            // Tự tạo quỹ mới (mục cha) từ file Excel
            String fundName = (newFundName != null && !newFundName.isBlank())
                    ? newFundName.trim()
                    : fundNameFromFile(file.getOriginalFilename());
            String creator = authentication != null ? authentication.getName() : "system";
            fund = new ExpenseFund(fundName, 0L, creator);
            fund = fundRepository.save(fund);
            fundId = fund.getId();
            autoCreated = true;
        }

        try {
            List<BudgetItem> items = excelService.parseBudgetItemsFromExcel(file.getInputStream(), fundId);
            if (items.isEmpty()) {
                if (autoCreated) fundRepository.delete(fund); // không giữ lại quỹ rỗng vừa tự tạo
                redirectAttributes.addFlashAttribute("errorMessage", "Không đọc được dữ liệu hạng mục nào từ file Excel. Vui lòng kiểm tra mẫu file.");
                return autoCreated ? "redirect:/expenses" : "redirect:/expenses/fund/" + fundId;
            }

            // Xóa ngân sách cũ của quỹ và lưu ngân sách mới
            budgetItemRepository.deleteByFundId(fundId);
            budgetItemRepository.saveAll(items);

            // Cập nhật lại tổng ngân sách được cấp cho Quỹ
            long totalAllocated = items.stream().mapToLong(BudgetItem::getAllocatedAmount).sum();
            if (totalAllocated > 0) {
                fund.setAllocatedAmount(totalAllocated);
                fundRepository.save(fund);
            }

            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã import thành công " + items.size() + " hạng mục ngân sách vào quỹ \"" + fund.getName() + "\"!");

        } catch (Exception e) {
            e.printStackTrace();
            if (autoCreated) {
                fundRepository.delete(fund); // không giữ lại quỹ rỗng vừa tự tạo
                redirectAttributes.addFlashAttribute("errorMessage", "Lỗi đọc file Excel: " + e.getMessage());
                return "redirect:/expenses";
            }
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi đọc file Excel: " + e.getMessage());
        }

        return "redirect:/expenses/fund/" + fundId;
    }

    /** Sinh tên quỹ từ tên file Excel (bỏ đuôi .xlsx và các ký tự thừa). */
    private String fundNameFromFile(String filename) {
        if (filename == null || filename.isBlank()) return "Ngân sách import " + LocalDateTime.now().getYear();
        String name = filename;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) name = name.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        name = name.replaceAll("\\s*\\(\\d+\\)\\s*$", "").trim(); // bỏ " (1)" cuối tên
        return name.isBlank() ? "Ngân sách import " + LocalDateTime.now().getYear() : name;
    }

    /** Tải file Excel mẫu phân bổ ngân sách. */
    @GetMapping("/sample-budget-template")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.InputStreamResource> downloadSampleBudgetTemplate() throws IOException {
        java.io.ByteArrayInputStream in = excelService.generateSampleBudgetExcel();

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=Mau_Nhap_Ngan_Sach_Team_IT.xlsx");

        return org.springframework.http.ResponseEntity
                .ok()
                .headers(headers)
                .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new org.springframework.core.io.InputStreamResource(in));
    }

    /** Xóa một khoản chi (cộng lại vào quỹ). */
    @PostMapping("/delete/{id}")
    public String deleteExpense(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        Long fundId = expenseRepository.findById(id).map(ToolExpense::getFundId).orElse(null);
        expenseRepository.deleteById(id);
        redirectAttributes.addFlashAttribute("successMessage", "Đã xóa khoản chi.");
        return "redirect:/expenses" + (fundId != null ? "/fund/" + fundId : "");
    }

    /** Cập nhật số tiền được cấp / tên của quỹ. */
    @PostMapping("/fund/update")
    public String updateFund(@RequestParam("fundId") Long fundId,
                             @RequestParam("name") String name,
                             @RequestParam("allocatedAmount") String allocatedRaw,
                             @RequestParam(value = "budgetYear", required = false) Integer budgetYear,
                             @RequestParam(value = "fromMonth", required = false) Integer fromMonth,
                             @RequestParam(value = "toMonth", required = false) Integer toMonth,
                             RedirectAttributes redirectAttributes) {
        ExpenseFund fund = fundRepository.findById(fundId).orElse(null);
        if (fund != null) {
            if (name != null && !name.isBlank()) fund.setName(name.trim());
            fund.setAllocatedAmount(parseMoney(allocatedRaw));
            // Kỳ ngân sách: quỹ "6 tháng cuối năm" đặt T6–T12 thì chỉ hóa đơn trong
            // khoảng đó mới bị trừ vào quỹ này.
            fund.setBudgetYear(budgetYear);
            fund.setFromMonth(fromMonth);
            fund.setToMonth(toMonth);
            fundRepository.save(fund);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã cập nhật quỹ. Kỳ ngân sách: " + fund.getPeriodLabel() + ".");
        }
        return "redirect:/expenses/fund/" + fundId;
    }

    /** Tạo một quỹ ngân sách mới (song song với các quỹ hiện có). */
    @PostMapping("/fund/create")
    public String createFund(@RequestParam("name") String name,
                             @RequestParam("allocatedAmount") String allocatedRaw,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        String creator = authentication != null ? authentication.getName() : "system";
        String fundName = (name != null && !name.isBlank()) ? name.trim() : "Quỹ mới";
        ExpenseFund fund = new ExpenseFund(fundName, parseMoney(allocatedRaw), creator);
        fundRepository.save(fund);
        redirectAttributes.addFlashAttribute("successMessage", "Đã tạo quỹ mới: " + fundName);
        return "redirect:/expenses/fund/" + fund.getId();
    }

    /** Xóa một quỹ và toàn bộ khoản chi thuộc quỹ đó. */
    @PostMapping("/fund/delete/{fundId}")
    public String deleteFund(@PathVariable("fundId") Long fundId, RedirectAttributes redirectAttributes) {
        ExpenseFund fund = fundRepository.findById(fundId).orElse(null);
        if (fund != null) {
            List<ToolExpense> expenses = expenseRepository.findByFundIdOrderByCreatedAtDesc(fundId);
            expenseRepository.deleteAll(expenses);
            fundRepository.delete(fund);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã xóa quỹ \"" + fund.getName() + "\" cùng " + expenses.size() + " khoản chi.");
        }
        return "redirect:/expenses";
    }

    /** Parse ngày từ input HTML type=date ("yyyy-MM-dd"); rỗng/không hợp lệ -> null. */
    private java.time.LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return java.time.LocalDate.parse(raw.trim());
        } catch (Exception e) {
            return null;
        }
    }

    /** Chuyển tiền tệ dạng chuỗi ("2.000.000", "2,000,000", "2000000 đ") sang long. */
    private long parseMoney(String raw) {
        if (raw == null) return 0L;
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return 0L;
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
