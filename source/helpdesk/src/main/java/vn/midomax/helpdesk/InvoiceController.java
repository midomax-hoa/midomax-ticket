package vn.midomax.helpdesk;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.midomax.helpdesk.storage.StorageService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Sổ Hóa Đơn: lưu trữ hóa đơn / phiếu chi của công ty.
 *
 * Nằm dưới /expenses nên dùng chung luật bảo mật "/expenses/**" (ADMIN, MANAGER)
 * đã khai trong SecurityConfig — không cần thêm rule mới.
 */
@Controller
@RequestMapping("/expenses/invoices")
public class InvoiceController {

    private static final int PAGE_SIZE = 5;

    @Autowired
    private InvoiceEntryRepository invoiceRepository;

    @Autowired
    private ExcelService excelService;

    @Autowired
    private StorageService storageService;

    @Autowired
    private BudgetItemRepository budgetItemRepository;

    @Autowired
    private ExpenseFundRepository fundRepository;

    /**
     * Bỏ dấu, bỏ hoa/thường và khoảng trắng thừa để so tên hạng mục giữa file Excel
     * và ngân sách đã import — người nhập hiếm khi gõ y hệt.
     */
    private static String normalizeName(String value) {
        if (value == null) return "";
        String s = java.text.Normalizer.normalize(value.trim().toLowerCase(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace('đ', 'd');
        return s.replaceAll("[^a-z0-9]+", " ").trim();
    }

    /** Dò hạng mục ngân sách của quỹ theo tên ghi trong file Excel. */
    private Long resolveBudgetItemId(String rawName, List<BudgetItem> items) {
        String key = normalizeName(rawName);
        if (key.isEmpty()) return null;
        for (BudgetItem item : items) {
            if (normalizeName(item.getItemName()).equals(key)) return item.getId();
        }
        // Không khớp tuyệt đối thì chấp nhận khớp chứa nhau, miễn là duy nhất
        Long found = null;
        for (BudgetItem item : items) {
            String name = normalizeName(item.getItemName());
            if (name.isEmpty()) continue;
            if (name.contains(key) || key.contains(name)) {
                if (found != null) return null; // mơ hồ, để người dùng tự chọn
                found = item.getId();
            }
        }
        return found;
    }

    /** Danh sách sổ hóa đơn kèm bộ lọc và các ô tổng. */
    @GetMapping
    public String list(@RequestParam(value = "period", required = false) String period,
                       @RequestParam(value = "category", required = false) String category,
                       @RequestParam(value = "vendor", required = false) String vendor,
                       @RequestParam(value = "expenseType", required = false) String expenseType,
                       @RequestParam(value = "paymentStatus", required = false) String paymentStatus,
                       @RequestParam(value = "search", required = false) String search,
                       @RequestParam(value = "page", required = false) Integer page,
                       Model model) {

        List<InvoiceEntry> entries = invoiceRepository.filter(
                blankToNull(period), blankToNull(category), blankToNull(vendor),
                blankToNull(expenseType), blankToNull(paymentStatus), blankToNull(search));

        long total = 0, paid = 0, unpaid = 0, recurring = 0;
        for (InvoiceEntry e : entries) {
            total += e.getAmount();
            if (InvoiceEntry.STATUS_PAID.equals(e.getPaymentStatus())) {
                paid += e.getAmount();
            } else {
                unpaid += e.getAmount();
            }
            if (InvoiceEntry.TYPE_RECURRING.equals(e.getExpenseType())) {
                recurring += e.getAmount();
            }
        }

        int totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) PAGE_SIZE));
        int currentPage = Math.min(Math.max(page == null ? 1 : page, 1), totalPages);
        int from = (currentPage - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, entries.size());
        List<InvoiceEntry> pagedEntries = entries.isEmpty() ? java.util.Collections.emptyList() : entries.subList(from, to);

        model.addAttribute("entries", pagedEntries);
        model.addAttribute("entryCount", entries.size());
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageFrom", entries.isEmpty() ? 0 : from + 1);
        model.addAttribute("pageTo", to);
        model.addAttribute("totalAmount", total);
        model.addAttribute("paidAmount", paid);
        model.addAttribute("unpaidAmount", unpaid);
        model.addAttribute("recurringAmount", recurring);

        model.addAttribute("periods", invoiceRepository.findDistinctPeriods());
        model.addAttribute("categories", invoiceRepository.findDistinctCategories());
        model.addAttribute("subCategories", invoiceRepository.findDistinctSubCategories());
        model.addAttribute("vendors", invoiceRepository.findDistinctVendors());

        model.addAttribute("fPeriod", period);
        model.addAttribute("fCategory", category);
        model.addAttribute("fVendor", vendor);
        model.addAttribute("fExpenseType", expenseType);
        model.addAttribute("fPaymentStatus", paymentStatus);
        model.addAttribute("fSearch", search);
        model.addAttribute("activePage", "invoices");

        // Đối chiếu ngân sách: danh sách quỹ để chọn khi import, và bản đồ id -> tên hạng mục
        List<ExpenseFund> funds = fundRepository.findAll();
        java.util.Map<Long, String> budgetItemNames = new java.util.LinkedHashMap<>();
        java.util.Map<Long, List<BudgetItem>> budgetItemsByFund = new java.util.LinkedHashMap<>();
        for (ExpenseFund fund : funds) {
            List<BudgetItem> items =
                    budgetItemRepository.findByFundIdOrderByGroupCategoryAscSubCategoryAscIdAsc(fund.getId());
            budgetItemsByFund.put(fund.getId(), items);
            for (BudgetItem item : items) {
                budgetItemNames.put(item.getId(), item.getGroupCategory() + " · " + item.getItemName());
            }
        }
        long unmatchedCount = entries.stream().filter(e -> e.getBudgetItemId() == null).count();
        model.addAttribute("funds", funds);
        model.addAttribute("budgetItemNames", budgetItemNames);
        model.addAttribute("budgetItemsByFund", budgetItemsByFund);
        model.addAttribute("unmatchedCount", unmatchedCount);

        return "invoice-ledger";
    }

    /** Thêm một hóa đơn mới. */
    @PostMapping("/add")
    public String add(@RequestParam("transDate") String transDateRaw,
                      @RequestParam(value = "entryDate", required = false) String entryDateRaw,
                      @RequestParam(value = "poRef", required = false) String poRef,
                      @RequestParam(value = "category", required = false) String category,
                      @RequestParam(value = "subCategory", required = false) String subCategory,
                      @RequestParam(value = "vendor", required = false) String vendor,
                      @RequestParam("description") String description,
                      @RequestParam("amount") String amountRaw,
                      @RequestParam(value = "expenseType", required = false) String expenseType,
                      @RequestParam(value = "paymentStatus", required = false) String paymentStatus,
                      @RequestParam(value = "notes", required = false) String notes,
                      @RequestParam(value = "attachmentFile", required = false) MultipartFile attachmentFile,
                      Authentication authentication,
                      RedirectAttributes ra) {

        long amount = parseMoney(amountRaw);
        if (description == null || description.isBlank() || amount <= 0) {
            ra.addFlashAttribute("errorMessage", "Cần nhập mô tả và số tiền hợp lệ.");
            return "redirect:/expenses/invoices";
        }

        InvoiceEntry e = new InvoiceEntry();
        e.setTransDate(parseDate(transDateRaw));
        e.setEntryDate(entryDateRaw != null && !entryDateRaw.isBlank() ? parseDate(entryDateRaw) : LocalDate.now());
        e.setPoRef(trim(poRef));
        e.setCategory(trim(category));
        e.setSubCategory(trim(subCategory));
        e.setVendor(trim(vendor));
        e.setDescription(description.trim());
        e.setAmount(amount);
        e.setExpenseType(InvoiceEntry.normalizeExpenseType(expenseType));
        e.setPaymentStatus(InvoiceEntry.normalizePaymentStatus(paymentStatus));
        e.setEnteredBy(ReporterIdentity.of(authentication));
        e.setNotes(trim(notes));
        e.setPeriodKey(InvoiceEntry.periodKeyOf(e.getTransDate(), e.getEntryDate()));
        e.setCreatedAt(LocalDateTime.now());
        e.setAttachmentPath(storeAttachment(attachmentFile));

        // Cảnh báo trùng chứng từ nhưng vẫn lưu: có thể là thanh toán nhiều đợt thật.
        List<InvoiceEntry> dup = invoiceRepository.findByVendorAndAmountAndTransDate(
                e.getVendor(), e.getAmount(), e.getTransDate());

        invoiceRepository.save(e);

        if (!dup.isEmpty()) {
            ra.addFlashAttribute("warningMessage",
                    "Đã lưu, nhưng lưu ý: đã có " + dup.size() + " chứng từ khác cùng nhà cung cấp, "
                            + "cùng số tiền và cùng ngày giao dịch. Kiểm tra lại xem có bị nhập trùng không.");
        } else {
            ra.addFlashAttribute("successMessage", "Đã lưu hóa đơn.");
        }
        return "redirect:/expenses/invoices";
    }

    /** Sửa một hóa đơn đã lưu. */
    @PostMapping("/update")
    public String update(@RequestParam("id") Long id,
                         @RequestParam("transDate") String transDateRaw,
                         @RequestParam(value = "entryDate", required = false) String entryDateRaw,
                         @RequestParam(value = "poRef", required = false) String poRef,
                         @RequestParam(value = "category", required = false) String category,
                         @RequestParam(value = "subCategory", required = false) String subCategory,
                         @RequestParam(value = "vendor", required = false) String vendor,
                         @RequestParam("description") String description,
                         @RequestParam("amount") String amountRaw,
                         @RequestParam(value = "expenseType", required = false) String expenseType,
                         @RequestParam(value = "paymentStatus", required = false) String paymentStatus,
                         @RequestParam(value = "notes", required = false) String notes,
                         @RequestParam(value = "attachmentFile", required = false) MultipartFile attachmentFile,
                         RedirectAttributes ra) {

        InvoiceEntry e = invoiceRepository.findById(id).orElse(null);
        if (e == null) {
            ra.addFlashAttribute("errorMessage", "Không tìm thấy hóa đơn.");
            return "redirect:/expenses/invoices";
        }

        long amount = parseMoney(amountRaw);
        if (amount > 0) e.setAmount(amount);
        e.setTransDate(parseDate(transDateRaw));
        if (entryDateRaw != null && !entryDateRaw.isBlank()) e.setEntryDate(parseDate(entryDateRaw));
        e.setPoRef(trim(poRef));
        e.setCategory(trim(category));
        e.setSubCategory(trim(subCategory));
        e.setVendor(trim(vendor));
        if (description != null && !description.isBlank()) e.setDescription(description.trim());
        e.setExpenseType(InvoiceEntry.normalizeExpenseType(expenseType));
        e.setPaymentStatus(InvoiceEntry.normalizePaymentStatus(paymentStatus));
        e.setNotes(trim(notes));
        e.setPeriodKey(InvoiceEntry.periodKeyOf(e.getTransDate(), e.getEntryDate()));

        String newPath = storeAttachment(attachmentFile);
        if (newPath != null) e.setAttachmentPath(newPath); // không gửi file mới thì giữ file cũ

        invoiceRepository.save(e);
        ra.addFlashAttribute("successMessage", "Đã cập nhật hóa đơn.");
        return "redirect:/expenses/invoices";
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable("id") Long id, RedirectAttributes ra) {
        invoiceRepository.deleteById(id);
        ra.addFlashAttribute("successMessage", "Đã xóa hóa đơn.");
        return "redirect:/expenses/invoices";
    }

    /** Xóa hàng loạt theo danh sách ID chọn từ Checkbox. */
    @PostMapping("/delete-batch")
    public String deleteBatch(@RequestParam(value = "ids", required = false) List<Long> ids, RedirectAttributes ra) {
        if (ids == null || ids.isEmpty()) {
            ra.addFlashAttribute("errorMessage", "Chưa chọn hóa đơn nào để xóa.");
            return "redirect:/expenses/invoices";
        }
        invoiceRepository.deleteAllById(ids);
        ra.addFlashAttribute("successMessage", "Đã xóa thành công " + ids.size() + " hóa đơn đã chọn.");
        return "redirect:/expenses/invoices";
    }

    /** Xóa toàn bộ hóa đơn đang được hiển thị theo bộ lọc tìm kiếm hiện tại. */
    @PostMapping("/delete-filtered")
    public String deleteFiltered(@RequestParam(value = "period", required = false) String period,
                                 @RequestParam(value = "category", required = false) String category,
                                 @RequestParam(value = "vendor", required = false) String vendor,
                                 @RequestParam(value = "expenseType", required = false) String expenseType,
                                 @RequestParam(value = "paymentStatus", required = false) String paymentStatus,
                                 @RequestParam(value = "search", required = false) String search,
                                 RedirectAttributes ra) {

        List<InvoiceEntry> entries = invoiceRepository.filter(
                blankToNull(period), blankToNull(category), blankToNull(vendor),
                blankToNull(expenseType), blankToNull(paymentStatus), blankToNull(search));

        if (entries.isEmpty()) {
            ra.addFlashAttribute("errorMessage", "Không có hóa đơn nào khớp với bộ lọc hiện tại.");
            return "redirect:/expenses/invoices";
        }

        int count = entries.size();
        invoiceRepository.deleteAll(entries);
        ra.addFlashAttribute("successMessage", "Đã xóa thành công " + count + " hóa đơn khớp với bộ lọc.");
        return "redirect:/expenses/invoices";
    }

    /**
     * Nhập sổ hóa đơn từ file Excel đang dùng thủ công.
     *
     * Bỏ qua dòng đã có trong sổ (trùng cả nhà cung cấp, số tiền, ngày GD và mô tả)
     * để lỡ bấm nhập hai lần cũng không nhân đôi dữ liệu.
     */
    @PostMapping("/import")
    public String importExcel(@RequestParam("file") MultipartFile file,
                              @RequestParam(value = "fundId", required = false) Long fundId,
                              Authentication authentication,
                              RedirectAttributes ra) {

        if (file == null || file.isEmpty()) {
            ra.addFlashAttribute("errorMessage", "Chưa chọn file Excel.");
            return "redirect:/expenses/invoices";
        }

        ExcelService.InvoiceImportResult result = excelService.importInvoiceEntriesFromExcel(file);
        if (result.hasError()) {
            ra.addFlashAttribute("errorMessage", result.getError());
            return "redirect:/expenses/invoices";
        }

        String importer = ReporterIdentity.of(authentication);
        int saved = 0, duplicated = 0, unmatched = 0;

        // Danh sách hạng mục của quỹ được chọn — dùng để đối chiếu cột "Hạng mục ngân sách"
        List<BudgetItem> budgetItems = fundId != null
                ? budgetItemRepository.findByFundIdOrderByGroupCategoryAscSubCategoryAscIdAsc(fundId)
                : java.util.Collections.emptyList();

        for (InvoiceEntry e : result.getEntries()) {
            List<InvoiceEntry> existing = invoiceRepository.findByVendorAndAmountAndTransDate(
                    e.getVendor(), e.getAmount(), e.getTransDate());
            boolean already = existing.stream()
                    .anyMatch(x -> java.util.Objects.equals(x.getDescription(), e.getDescription()));
            if (already) {
                duplicated++;
                continue;
            }
            if (e.getEnteredBy() == null || e.getEnteredBy().isBlank()) {
                e.setEnteredBy(importer);
            }
            if (e.getEntryDate() == null) {
                e.setEntryDate(LocalDate.now());
            }
            e.setCreatedAt(LocalDateTime.now());

            // Đối chiếu về hạng mục ngân sách của quỹ để khấu trừ
            e.setFundId(fundId);
            if (fundId != null) {
                e.setBudgetItemId(resolveBudgetItemId(e.getBudgetItemName(), budgetItems));
                if (e.getBudgetItemId() == null) unmatched++;
            }

            invoiceRepository.save(e);
            saved++;
        }

        StringBuilder msg = new StringBuilder("Đã nhập ").append(saved).append(" hóa đơn từ Excel.");
        if (unmatched > 0) {
            msg.append(" Có ").append(unmatched)
               .append(" dòng chưa đối chiếu được hạng mục ngân sách — lọc \"Chưa đối chiếu\" để chọn tay.");
        }
        if (duplicated > 0) msg.append(" Bỏ qua ").append(duplicated).append(" dòng đã có trong sổ.");
        if (result.getSkipped() > 0) msg.append(" Bỏ qua ").append(result.getSkipped()).append(" dòng không có số tiền.");
        ra.addFlashAttribute("successMessage", msg.toString());

        return "redirect:/expenses/invoices";
    }

    /** Đối chiếu tay: gán một hóa đơn về hạng mục ngân sách (hoặc bỏ đối chiếu khi để trống). */
    @PostMapping("/match")
    public String matchBudgetItem(@RequestParam("id") Long id,
                                  @RequestParam(value = "budgetItemId", required = false) Long budgetItemId,
                                  RedirectAttributes ra) {
        InvoiceEntry entry = invoiceRepository.findById(id).orElse(null);
        if (entry == null) {
            ra.addFlashAttribute("errorMessage", "Không tìm thấy hóa đơn #" + id);
            return "redirect:/expenses/invoices";
        }
        if (budgetItemId == null) {
            entry.setBudgetItemId(null);
            entry.setFundId(null);
        } else {
            BudgetItem item = budgetItemRepository.findById(budgetItemId).orElse(null);
            if (item == null) {
                ra.addFlashAttribute("errorMessage", "Hạng mục ngân sách không còn tồn tại.");
                return "redirect:/expenses/invoices";
            }
            entry.setBudgetItemId(item.getId());
            entry.setFundId(item.getFundId());
        }
        invoiceRepository.save(entry);
        ra.addFlashAttribute("successMessage", "Đã cập nhật đối chiếu ngân sách cho hóa đơn.");
        return "redirect:/expenses/invoices";
    }

    /** Xuất sổ hóa đơn (theo đúng bộ lọc đang xem) ra Excel. */
    @GetMapping("/export")
    public ResponseEntity<InputStreamResource> export(
            @RequestParam(value = "period", required = false) String period,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "vendor", required = false) String vendor,
            @RequestParam(value = "expenseType", required = false) String expenseType,
            @RequestParam(value = "paymentStatus", required = false) String paymentStatus,
            @RequestParam(value = "search", required = false) String search) throws IOException {

        List<InvoiceEntry> entries = invoiceRepository.filter(
                blankToNull(period), blankToNull(category), blankToNull(vendor),
                blankToNull(expenseType), blankToNull(paymentStatus), blankToNull(search));

        String label = (period != null && !period.isBlank()) ? period : "Toàn bộ";
        ByteArrayInputStream in = excelService.exportInvoiceEntriesToExcel(entries, label);

        String suffix = (period != null && !period.isBlank()) ? period : "toanbo";
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=SoHoaDon_" + suffix + ".xlsx");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(in));
    }

    // ----- helper -----

    /** Lưu file chứng từ đính kèm, trả null nếu không có file. */
    private String storeAttachment(MultipartFile file) {
        return storageService.store(file);
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private String trim(String s) {
        return s == null ? null : s.trim();
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw.trim());
        } catch (Exception e) {
            return null;
        }
    }

    /** "2.000.000", "2,000,000", "2000000 đ" -> 2000000 */
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
