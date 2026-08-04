package vn.midomax.helpdesk;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ExcelService {

    public ByteArrayInputStream exportEmployeesToExcel(List<Employee> employees) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Nhân Sự");

            Field[] fields = Employee.class.getDeclaredFields();
            List<Field> exportFields = new ArrayList<>();
            for (Field f : fields) {
                if (!f.getName().equals("id") && !f.getName().equals("children") && !f.getName().equals("emergencyContacts")) {
                    f.setAccessible(true);
                    exportFields.add(f);
                }
            }

            // Header Row
            Row headerRow = sheet.createRow(0);
            for (int col = 0; col < exportFields.size(); col++) {
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(exportFields.get(col).getName());
            }

            // Data Rows
            int rowIdx = 1;
            for (Employee emp : employees) {
                Row row = sheet.createRow(rowIdx++);
                for (int col = 0; col < exportFields.size(); col++) {
                    Cell cell = row.createCell(col);
                    try {
                        Object value = exportFields.get(col).get(emp);
                        if (value != null) {
                            if (value instanceof String) {
                                cell.setCellValue((String) value);
                            } else if (value instanceof LocalDate) {
                                cell.setCellValue(value.toString());
                            } else if (value instanceof Boolean) {
                                cell.setCellValue((Boolean) value ? "X" : "");
                            } else if (value instanceof Number) {
                                cell.setCellValue(((Number) value).doubleValue());
                            } else {
                                cell.setCellValue(value.toString());
                            }
                        }
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    /**
     * Xuất báo cáo chi tiêu CCDC của một quỹ ra Excel (kèm tổng ngân sách và danh sách khoản chi).
     */
    public ByteArrayInputStream exportExpensesToExcel(ExpenseFund fund, List<ToolExpense> expenses,
                                                      long allocated, long spent, String periodLabel) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Chi tiêu CCDC");

            // ===== Styles =====
            CellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);

            CellStyle labelStyle = workbook.createCellStyle();
            Font labelFont = workbook.createFont();
            labelFont.setBold(true);
            labelStyle.setFont(labelFont);

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            CellStyle moneyStyle = workbook.createCellStyle();
            DataFormat fmt = workbook.createDataFormat();
            moneyStyle.setDataFormat(fmt.getFormat("#,##0"));

            CellStyle moneyBoldStyle = workbook.createCellStyle();
            moneyBoldStyle.setDataFormat(fmt.getFormat("#,##0"));
            moneyBoldStyle.setFont(labelFont);

            long remaining = allocated - spent;

            // ===== Tiêu đề & tổng quan =====
            int r = 0;
            Row title = sheet.createRow(r++);
            Cell tc = title.createCell(0);
            tc.setCellValue("BÁO CÁO CHI TIÊU CÔNG CỤ DỤNG CỤ");
            tc.setCellStyle(titleStyle);

            createKeyValueRow(sheet, r++, "Quỹ:", fund != null ? fund.getName() : "", labelStyle);
            createKeyValueRow(sheet, r++, "Kỳ báo cáo:", periodLabel, labelStyle);
            createKeyValueMoney(sheet, r++, "Ngân sách được cấp:", allocated, labelStyle, moneyBoldStyle);
            createKeyValueMoney(sheet, r++, "Đã chi:", spent, labelStyle, moneyBoldStyle);
            createKeyValueMoney(sheet, r++, "Còn lại:", remaining, labelStyle, moneyBoldStyle);

            r++; // dòng trống

            // ===== Bảng chi tiết =====
            String[] headers = {"STT", "Ngày ghi", "Công cụ / Dụng cụ", "Loại", "Số tiền (đ)", "Số HĐ", "Ngày HĐ", "Người chi", "Ghi chú"};
            Row headerRow = sheet.createRow(r++);
            for (int c = 0; c < headers.length; c++) {
                Cell cell = headerRow.createCell(c);
                cell.setCellValue(headers[c]);
                cell.setCellStyle(headerStyle);
            }

            int idx = 1;
            long listedTotal = 0;
            for (ToolExpense e : expenses) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(idx++);
                row.createCell(1).setCellValue(e.getCreatedAtStr());
                row.createCell(2).setCellValue(e.getItemName() != null ? e.getItemName() : "");
                row.createCell(3).setCellValue(e.getCategory() != null ? e.getCategory() : "");
                Cell amountCell = row.createCell(4);
                amountCell.setCellValue(e.getAmount());
                amountCell.setCellStyle(moneyStyle);
                row.createCell(5).setCellValue(e.getInvoiceNumber() != null ? e.getInvoiceNumber() : "");
                row.createCell(6).setCellValue(e.getInvoiceDateStr());
                row.createCell(7).setCellValue(e.getCreatedBy() != null ? e.getCreatedBy() : "");
                row.createCell(8).setCellValue(e.getNote() != null ? e.getNote() : "");
                listedTotal += e.getAmount();
            }

            // Dòng tổng cộng (tổng của các khoản được liệt kê theo kỳ đã chọn)
            Row totalRow = sheet.createRow(r++);
            Cell totalLabel = totalRow.createCell(3);
            totalLabel.setCellValue("TỔNG CỘNG");
            totalLabel.setCellStyle(labelStyle);
            Cell totalValue = totalRow.createCell(4);
            totalValue.setCellValue(listedTotal);
            totalValue.setCellStyle(moneyBoldStyle);

            for (int c = 0; c < headers.length; c++) {
                sheet.autoSizeColumn(c);
            }

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    private void createKeyValueRow(Sheet sheet, int rowIdx, String key, String value, CellStyle labelStyle) {
        Row row = sheet.createRow(rowIdx);
        Cell k = row.createCell(0);
        k.setCellValue(key);
        k.setCellStyle(labelStyle);
        row.createCell(1).setCellValue(value != null ? value : "");
    }

    private void createKeyValueMoney(Sheet sheet, int rowIdx, String key, long value, CellStyle labelStyle, CellStyle moneyStyle) {
        Row row = sheet.createRow(rowIdx);
        Cell k = row.createCell(0);
        k.setCellValue(key);
        k.setCellStyle(labelStyle);
        Cell v = row.createCell(1);
        v.setCellValue(value);
        v.setCellStyle(moneyStyle);
    }

    /**
     * Xuất danh sách báo cáo công việc (WorkReport) ra Excel.
     * Sắp xếp theo dự án, việc cha trước rồi tới việc con (thụt đầu dòng bằng "↳").
     */
    public ByteArrayInputStream exportWorkReportsToExcel(List<WorkReport> reports) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Báo cáo công việc");

            CellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

            int r = 0;
            Row title = sheet.createRow(r++);
            Cell tc = title.createCell(0);
            tc.setCellValue("BÁO CÁO CÔNG VIỆC - IT PORTAL");
            tc.setCellStyle(titleStyle);
            r++; // dòng trống

            String[] headers = {"STT", "Dự án", "Loại", "Tên công việc / Báo cáo", "Người nhận nhiệm vụ",
                    "Người theo dõi", "Trạng thái", "Tiến độ (%)", "Ưu tiên", "Ngày tạo", "Hạn chót", "Diễn giải / Ghi chú", "Trạng thái SLA", "Giải trình trễ hạn"};
            Row headerRow = sheet.createRow(r++);
            for (int c = 0; c < headers.length; c++) {
                Cell cell = headerRow.createCell(c);
                cell.setCellValue(headers[c]);
                cell.setCellStyle(headerStyle);
            }

            // Sắp xếp: theo dự án -> việc cha (parentId null) trước -> theo id
            List<WorkReport> sorted = new ArrayList<>(reports);
            sorted.sort((a, b) -> {
                String pa = a.getProjectName() != null ? a.getProjectName() : "";
                String pb = b.getProjectName() != null ? b.getProjectName() : "";
                int cmp = pa.compareToIgnoreCase(pb);
                if (cmp != 0) return cmp;
                boolean aChild = a.getParentId() != null && a.getParentId() > 0;
                boolean bChild = b.getParentId() != null && b.getParentId() > 0;
                if (aChild != bChild) return aChild ? 1 : -1;
                return Long.compare(a.getId() != null ? a.getId() : 0, b.getId() != null ? b.getId() : 0);
            });

            int idx = 1;
            for (WorkReport w : sorted) {
                boolean isChild = w.getParentId() != null && w.getParentId() > 0;
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(idx++);
                row.createCell(1).setCellValue(w.getProjectName() != null ? w.getProjectName() : "");
                row.createCell(2).setCellValue(isChild ? "Việc con" : "Việc chính");
                row.createCell(3).setCellValue((isChild ? "↳ " : "") + (w.getTaskTitle() != null ? w.getTaskTitle() : ""));
                row.createCell(4).setCellValue(w.getAssignee() != null ? w.getAssignee() : "");
                row.createCell(5).setCellValue(w.getWatchers() != null ? w.getWatchers().replace(",", ", ") : "");
                row.createCell(6).setCellValue(w.getStatus() != null ? w.getStatus() : "");
                row.createCell(7).setCellValue(w.getProgressPercentage() != null ? w.getProgressPercentage() : 0);
                row.createCell(8).setCellValue(w.getPriority() != null ? w.getPriority() : "");
                row.createCell(9).setCellValue(w.getCreatedAt() != null ? w.getCreatedAt().format(dtf) : "");
                row.createCell(10).setCellValue(w.getDueDate() != null ? w.getDueDate().format(dtf) : "");
                row.createCell(11).setCellValue(w.getDailyReport() != null ? w.getDailyReport() : "");
                row.createCell(12).setCellValue(w.getSlaStatus());
                row.createCell(13).setCellValue(w.getDelayReason() != null ? w.getDelayReason() : "");
            }

            for (int c = 0; c < headers.length; c++) {
                sheet.autoSizeColumn(c);
            }

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    public List<Employee> importEmployeesFromExcel(MultipartFile file) {
        List<Employee> employees = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            Field[] fields = Employee.class.getDeclaredFields();
            List<Field> importFields = new ArrayList<>();
            for (Field f : fields) {
                if (!f.getName().equals("id") && !f.getName().equals("children") && !f.getName().equals("emergencyContacts")) {
                    f.setAccessible(true);
                    importFields.add(f);
                }
            }

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // Skip header

                Employee emp = new Employee();
                boolean isEmptyRow = true;

                for (int col = 0; col < importFields.size(); col++) {
                    Cell cell = row.getCell(col);
                    if (cell != null) {
                        isEmptyRow = false;
                        Field field = importFields.get(col);
                        try {
                            if (field.getType() == String.class) {
                                field.set(emp, cell.getCellType() == CellType.STRING ? cell.getStringCellValue() : String.valueOf(cell));
                            } else if (field.getType() == LocalDate.class) {
                                if (cell.getCellType() == CellType.STRING) {
                                    field.set(emp, LocalDate.parse(cell.getStringCellValue()));
                                } else if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                                    field.set(emp, cell.getLocalDateTimeCellValue().toLocalDate());
                                }
                            } else if (field.getType() == Boolean.class) {
                                field.set(emp, cell.getCellType() == CellType.STRING && "X".equalsIgnoreCase(cell.getStringCellValue().trim()));
                            } else if (field.getType() == Double.class) {
                                field.set(emp, cell.getNumericCellValue());
                            } else if (field.getType() == Integer.class) {
                                field.set(emp, (int) cell.getNumericCellValue());
                            }
                        } catch (Exception e) {
                            // Ignore casting errors for individual cells
                        }
                    }
                }
                
                if (!isEmptyRow) {
                    employees.add(emp);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return employees;
    }

    // ==================== SỔ HÓA ĐƠN ====================

    /** Kết quả một lần nhập sổ hóa đơn từ Excel. */
    public static class InvoiceImportResult {
        private final List<InvoiceEntry> entries = new ArrayList<>();
        private int skipped;
        private String error;

        public List<InvoiceEntry> getEntries() { return entries; }
        public int getSkipped() { return skipped; }
        public String getError() { return error; }
        public boolean hasError() { return error != null; }
    }

    /**
     * Nhập sổ hóa đơn từ file Excel đang dùng thủ công.
     *
     * Dò theo TÊN CỘT chứ không theo thứ tự cột, vì file thật có mấy dòng tổng và ghi
     * chú tiền tệ nằm phía trên, tiêu đề lại song ngữ hai dòng ("Ngày nhập / Entry
     * Date"). Dò tên nên chèn thêm cột hay đổi chỗ cột vẫn nhập được.
     *
     * Dòng tiêu đề được tìm bằng cách chấm điểm 30 dòng đầu, dòng nào khớp nhiều tên
     * cột nhất thì đó là tiêu đề — không giả định nó nằm ở dòng 0.
     */
    public InvoiceImportResult importInvoiceEntriesFromExcel(MultipartFile file) {
        InvoiceImportResult result = new InvoiceImportResult();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            int headerRowIdx = -1;
            java.util.Map<String, Integer> cols = null;
            int bestScore = 0;

            int scanTo = Math.min(sheet.getLastRowNum(), 30);
            for (int r = sheet.getFirstRowNum(); r <= scanTo; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                java.util.Map<String, Integer> map = new java.util.LinkedHashMap<>();
                for (Cell cell : row) {
                    String key = matchInvoiceColumn(headerText(cell));
                    if (key != null && !map.containsKey(key)) {
                        map.put(key, cell.getColumnIndex());
                    }
                }
                if (map.size() > bestScore) {
                    bestScore = map.size();
                    headerRowIdx = r;
                    cols = map;
                }
            }

            if (cols == null || bestScore < 4) {
                result.error = "Không nhận ra dòng tiêu đề. File cần có các cột như "
                        + "\"Ngày nhập\", \"Số tiền\", \"NCC/Người nhận\", \"Mô tả chi tiết\".";
                return result;
            }

            for (int r = headerRowIdx + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                long amount = cellAmount(row, cols.get("amount"));
                String description = cellString(row, cols.get("description"));
                String vendor = cellString(row, cols.get("vendor"));

                // Bỏ qua dòng trống và các dòng tổng cộng chen giữa bảng
                if (amount <= 0 && isBlank(description) && isBlank(vendor)) {
                    continue;
                }

                InvoiceEntry e = new InvoiceEntry();
                e.setEntryDate(cellDate(row, cols.get("entryDate")));
                e.setTransDate(cellDate(row, cols.get("transDate")));
                e.setPoRef(cellString(row, cols.get("poRef")));
                e.setCategory(cellString(row, cols.get("category")));
                e.setSubCategory(cellString(row, cols.get("subCategory")));
                e.setVendor(vendor);
                e.setDescription(description);
                e.setAmount(amount);
                e.setExpenseType(InvoiceEntry.normalizeExpenseType(cellString(row, cols.get("expenseType"))));
                e.setPaymentStatus(InvoiceEntry.normalizePaymentStatus(cellString(row, cols.get("paymentStatus"))));
                e.setEnteredBy(cellString(row, cols.get("enteredBy")));
                e.setNotes(cellString(row, cols.get("notes")));
                e.setBudgetItemName(cellString(row, cols.get("budgetItem")));
                e.setPeriodKey(InvoiceEntry.periodKeyOf(e.getTransDate(), e.getEntryDate()));

                if (amount <= 0) {
                    result.skipped++; // có mô tả nhưng không có tiền -> không phải khoản chi
                    continue;
                }
                result.entries.add(e);
            }

        } catch (Exception ex) {
            result.error = "Không đọc được file: " + ex.getMessage();
        }
        return result;
    }

    /** Gộp tiêu đề song ngữ nhiều dòng thành một chuỗi thường để so khớp. */
    private String headerText(Cell cell) {
        if (cell == null) return "";
        String raw;
        try {
            raw = cell.getCellType() == CellType.STRING ? cell.getStringCellValue() : String.valueOf(cell);
        } catch (Exception e) {
            return "";
        }
        if (raw == null) return "";
        return raw.replace('\n', ' ').replace('\r', ' ')
                .replaceAll("\\s+", " ").trim().toLowerCase();
    }

    /**
     * Ánh xạ tên cột -> khóa nội bộ. Thứ tự kiểm tra quan trọng: cột hẹp phải xét
     * trước cột rộng, vì "Phân loại nhỏ / Sub-Category" cũng chứa chữ "category",
     * còn "TT Thanh toán" phải xét sau "Loại chi".
     */
    private String matchInvoiceColumn(String h) {
        if (h == null || h.isEmpty()) return null;
        if (h.contains("hạng mục") || h.contains("budget item")) return "budgetItem";
        if (h.contains("phân loại") || h.contains("sub-category") || h.contains("sub category")) return "subCategory";
        if (h.contains("danh mục") || h.contains("category")) return "category";
        if (h.contains("ngày nhập") || h.contains("entry date")) return "entryDate";
        if (h.contains("ngày gd") || h.contains("trans")) return "transDate";
        if (h.contains("po/ref") || h.contains("po ref") || h.contains("số po")) return "poRef";
        if (h.contains("ncc") || h.contains("vendor") || h.contains("người nhận") || h.contains("payee")) return "vendor";
        if (h.contains("mô tả") || h.contains("description")) return "description";
        if (h.contains("số tiền") || h.contains("amount")) return "amount";
        if (h.contains("loại chi") || h.contains("expense type")) return "expenseType";
        if (h.contains("thanh toán") || h.contains("payment")) return "paymentStatus";
        if (h.contains("người nhập") || h.contains("entered")) return "enteredBy";
        if (h.contains("ghi chú") || h.contains("notes")) return "notes";
        return null;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String cellString(Row row, Integer idx) {
        if (idx == null) return null;
        Cell c = row.getCell(idx);
        if (c == null) return null;
        try {
            switch (c.getCellType()) {
                case STRING:
                    String s = c.getStringCellValue();
                    return s == null ? null : s.trim();
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(c)) {
                        return c.getLocalDateTimeCellValue().toLocalDate().toString();
                    }
                    double d = c.getNumericCellValue();
                    return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
                case BOOLEAN:
                    return String.valueOf(c.getBooleanCellValue());
                case FORMULA:
                    try {
                        return c.getStringCellValue().trim();
                    } catch (Exception e) {
                        return String.valueOf((long) c.getNumericCellValue());
                    }
                default:
                    return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private long cellAmount(Row row, Integer idx) {
        if (idx == null) return 0L;
        Cell c = row.getCell(idx);
        if (c == null) return 0L;
        try {
            if (c.getCellType() == CellType.NUMERIC || c.getCellType() == CellType.FORMULA) {
                return Math.round(c.getNumericCellValue());
            }
        } catch (Exception ignored) {
            // ô công thức trả về chuỗi -> rơi xuống nhánh bóc chữ số bên dưới
        }
        String s = cellString(row, idx);
        if (s == null) return 0L;
        String digits = s.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return 0L;
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private LocalDate cellDate(Row row, Integer idx) {
        if (idx == null) return null;
        Cell c = row.getCell(idx);
        if (c == null) return null;
        try {
            if (c.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(c)) {
                return c.getLocalDateTimeCellValue().toLocalDate();
            }
        } catch (Exception ignored) {
            // không phải ô ngày -> thử đọc dạng chuỗi
        }
        return parseFlexibleDate(cellString(row, idx));
    }

    /** Ô ngày lưu dạng chữ thì thử lần lượt các định dạng hay gặp. */
    private LocalDate parseFlexibleDate(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String v = raw.trim();
        String[] patterns = {"yyyy-MM-dd", "dd/MM/yyyy", "d/M/yyyy", "MM/dd/yyyy", "dd-MM-yyyy"};
        for (String p : patterns) {
            try {
                return LocalDate.parse(v, DateTimeFormatter.ofPattern(p));
            } catch (Exception ignored) {
                // thử định dạng kế tiếp
            }
        }
        return null;
    }

    /** Xuất sổ hóa đơn ra Excel, giữ nguyên bố cục quen thuộc: dòng tổng rồi tới bảng. */
    public ByteArrayInputStream exportInvoiceEntriesToExcel(List<InvoiceEntry> entries, String periodLabel) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("So Hoa Don");

            CellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 13);
            titleStyle.setFont(titleFont);

            CellStyle labelStyle = workbook.createCellStyle();
            Font labelFont = workbook.createFont();
            labelFont.setBold(true);
            labelStyle.setFont(labelFont);

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            CellStyle moneyStyle = workbook.createCellStyle();
            moneyStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));

            CellStyle moneyBoldStyle = workbook.createCellStyle();
            moneyBoldStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
            moneyBoldStyle.setFont(labelFont);

            long total = 0;
            for (InvoiceEntry e : entries) total += e.getAmount();

            Row r0 = sheet.createRow(0);
            createCell(r0, 0, "SỔ HÓA ĐƠN — MIDOMAX", titleStyle);

            Row r1 = sheet.createRow(1);
            createCell(r1, 0, "Kỳ:", labelStyle);
            createCell(r1, 1, periodLabel == null ? "Toàn bộ" : periodLabel, null);

            Row r2 = sheet.createRow(2);
            createCell(r2, 0, "TỔNG SỐ MỤC:", labelStyle);
            r2.createCell(1).setCellValue(entries.size());
            createCell(r2, 3, "TỔNG SỐ TIỀN (VND):", labelStyle);
            Cell totalCell = r2.createCell(4);
            totalCell.setCellValue(total);
            totalCell.setCellStyle(moneyBoldStyle);

            String[] headers = {"STT", "Ngày nhập", "Tháng/Kỳ", "Ngày GD", "Số PO/Ref", "Danh mục",
                    "Phân loại nhỏ", "NCC/Người nhận", "Mô tả chi tiết", "Số tiền (VND)",
                    "Loại chi", "TT Thanh toán", "Người nhập", "Ghi chú"};
            Row headerRow = sheet.createRow(4);
            for (int i = 0; i < headers.length; i++) {
                createCell(headerRow, i, headers[i], headerStyle);
            }

            int rowIdx = 5;
            int stt = 1;
            for (InvoiceEntry e : entries) {
                Row row = sheet.createRow(rowIdx++);
                createCell(row, 0, String.valueOf(stt++), null);
                createCell(row, 1, e.getEntryDateStr(), null);
                createCell(row, 2, e.getPeriodLabel(), null);
                createCell(row, 3, e.getTransDateStr(), null);
                createCell(row, 4, e.getPoRef(), null);
                createCell(row, 5, e.getCategory(), null);
                createCell(row, 6, e.getSubCategory(), null);
                createCell(row, 7, e.getVendor(), null);
                createCell(row, 8, e.getDescription(), null);
                Cell amountCell = row.createCell(9);
                amountCell.setCellValue(e.getAmount());
                amountCell.setCellStyle(moneyStyle);
                createCell(row, 10, e.getExpenseTypeLabel(), null);
                createCell(row, 11, e.getPaymentStatusLabel(), null);
                createCell(row, 12, e.getEnteredBy(), null);
                createCell(row, 13, e.getNotes(), null);
            }

            for (int c = 0; c < headers.length; c++) {
                sheet.autoSizeColumn(c);
            }

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    /**
     * Xuất danh sách Ticket ra file Excel (.xlsx) với header đẹp.
     */
    public ByteArrayInputStream exportTicketsToExcel(List<Ticket> tickets) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Danh sách Ticket");

            // ---- Styles ----
            // Header style: nền xanh đậm, chữ trắng, in đậm
            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.xssf.usermodel.XSSFColor headerColor =
                    new org.apache.poi.xssf.usermodel.XSSFColor(new byte[]{(byte) 30, (byte) 58, (byte) 138}, null);
            ((org.apache.poi.xssf.usermodel.XSSFCellStyle) headerStyle).setFillForegroundColor(headerColor);
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setFontHeightInPoints((short) 11);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            // Data style: border nhẹ
            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);
            dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            // Data style: nền xen kẽ nhạt
            CellStyle altStyle = workbook.createCellStyle();
            org.apache.poi.xssf.usermodel.XSSFColor altColor =
                    new org.apache.poi.xssf.usermodel.XSSFColor(new byte[]{(byte) 239, (byte) 246, (byte) 255}, null);
            ((org.apache.poi.xssf.usermodel.XSSFCellStyle) altStyle).setFillForegroundColor(altColor);
            altStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            altStyle.setBorderBottom(BorderStyle.THIN);
            altStyle.setBorderTop(BorderStyle.THIN);
            altStyle.setBorderLeft(BorderStyle.THIN);
            altStyle.setBorderRight(BorderStyle.THIN);
            altStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            // ---- Header Row ----
            String[] headers = {
                "Mã Ticket", "Tiêu đề / Nội dung", "Danh mục", "Người gửi",
                "Bộ phận", "Mức độ ưu tiên", "Trạng thái", "Người phụ trách",
                "Ghi chú khắc phục", "Ngày tạo", "SLA Deadline", "Dự kiến hoàn thành"
            };

            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(22);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // ---- Data Rows ----
            java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            int rowIdx = 1;
            for (Ticket t : tickets) {
                Row row = sheet.createRow(rowIdx);
                row.setHeightInPoints(18);
                CellStyle rowStyle = (rowIdx % 2 == 0) ? altStyle : dataStyle;

                createCell(row, 0, t.getTicketCode(), rowStyle);
                createCell(row, 1, t.getTitle(), rowStyle);
                createCell(row, 2, mapCategory(t.getCategory()), rowStyle);
                createCell(row, 3, t.getReporterName(), rowStyle);
                createCell(row, 4, t.getReporterDepartment(), rowStyle);
                createCell(row, 5, t.getPriority(), rowStyle);
                createCell(row, 6, mapStatus(t.getStatus()), rowStyle);
                createCell(row, 7, t.getAssignee() != null ? t.getAssignee() : "Chưa phân công", rowStyle);
                createCell(row, 8, t.getFixNote() != null ? t.getFixNote() : "", rowStyle);
                createCell(row, 9, t.getCreatedAt() != null ? t.getCreatedAt().format(dtf) : "", rowStyle);
                createCell(row, 10, t.getSlaDeadline() != null ? t.getSlaDeadline().format(dtf) : "", rowStyle);
                createCell(row, 11, t.getEstimatedCompletionTime() != null ? t.getEstimatedCompletionTime().format(dtf) : "", rowStyle);

                rowIdx++;
            }

            // ---- Auto-size columns ----
            int[] colWidths = {12, 40, 15, 20, 18, 15, 14, 18, 35, 18, 18, 20};
            for (int i = 0; i < headers.length; i++) {
                sheet.setColumnWidth(i, colWidths[i] * 256);
            }

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    private void createCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    private String mapCategory(String category) {
        if (category == null) return "";
        switch (category.toLowerCase()) {
            case "network": return "Mạng";
            case "hardware": return "Phần cứng";
            case "software": return "Phần mềm";
            case "server": return "Máy chủ";
            default: return category;
        }
    }

    private String mapStatus(String status) {
        if (status == null) return "";
        switch (status.toUpperCase()) {
            case "OPEN": return "Đang chờ";
            case "PROGRESS": return "Đang xử lý";
            case "RESOLVED": return "Đã hoàn thành";
            default: return status;
        }
    }

    // =====================================================================
    // BUDGET EXCEL IMPORT / EXPORT
    // =====================================================================

    /**
     * Đọc danh sách hạng mục ngân sách từ file Excel upload.
     * Cột kỳ vọng (dòng 1 là header):
     *   A: Chi phí nhóm 2  B: Chi phí nhóm 3  C: Nội dung  D: Mô tả
     *   E: Đơn giá         F: Đơn vị          G: Số lượng  H: Thành tiền  I: Ghi chú
     */
    public List<BudgetItem> parseBudgetItemsFromExcel(java.io.InputStream inputStream, Long fundId) throws IOException {
        List<BudgetItem> items = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            // Format ITD (file ngân sách năm thật): có các sheet IT_OPEX / IT_CAPEX
            // và sheet tổng hợp chứa các đầu mục ngoài OPEX/CAPEX chi tiết.
            List<Sheet> itdSheets = new ArrayList<>();
            Sheet summarySheet = null;
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                String name = workbook.getSheetName(s).toUpperCase();
                if (name.contains("TỔNG HỢP") || name.contains("TONG HOP")) {
                    summarySheet = workbook.getSheetAt(s);
                } else if (name.contains("OPEX") || name.contains("CAPEX")) {
                    itdSheets.add(workbook.getSheetAt(s));
                }
            }
            if (!itdSheets.isEmpty() || summarySheet != null) {
                for (Sheet sheet : itdSheets) {
                    items.addAll(parseItdBudgetSheet(sheet, fundId));
                }
                if (summarySheet != null) {
                    items.addAll(parseSummaryBudgetSheet(summarySheet, fundId));
                }
                return items;
            }

            // Format mẫu cũ (9 cột, sheet đầu tiên)
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) return items;
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String itemName = getCellString(row, 2);
                if (itemName == null || itemName.isBlank()) continue;

                BudgetItem item = new BudgetItem();
                item.setFundId(fundId);
                item.setGroupCategory(getCellString(row, 0));
                item.setSubCategory(getCellString(row, 1));
                item.setItemName(itemName);
                item.setDescription(getCellString(row, 3));
                item.setUnitPrice(getCellLong(row, 4));
                item.setUnit(getCellString(row, 5));
                item.setQuantity(getCellInt(row, 6));
                item.setAllocatedAmount(getCellLong(row, 7));
                item.setNotes(getCellString(row, 8));
                items.add(item);
            }
        }
        return items;
    }

    /**
     * Parse một sheet ngân sách theo format ITD (IT_OPEX / IT_CAPEX).
     * Cột: B=Hạng mục, C=Loại chi phí, D=Đơn giá, E=ĐVT, F=PBCM, G=Tổng SL,
     * V(21)=Tổng giá trị (VND), W..AH(22..33)=Giá trị theo tháng 1..12, AI(34)=Mô tả.
     */
    private List<BudgetItem> parseItdBudgetSheet(Sheet sheet, Long fundId) {
        List<BudgetItem> items = new ArrayList<>();
        String group = sheet.getSheetName().toUpperCase().contains("CAPEX") ? "CAPEX" : "OPEX";

        for (int i = 0; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            String itemName = getCellString(row, 1); // cột B
            if (itemName == null || itemName.isBlank()) continue;

            String lower = itemName.trim().toLowerCase();
            // Bỏ qua dòng tiêu đề / tổng / hướng dẫn
            if (lower.startsWith("ngân sách") || lower.equals("tổng") || lower.equals("hạng mục")
                    || lower.startsWith("thêm trên dòng")) continue;

            long total = getCellLong(row, 21); // cột V: Tổng giá trị (VND)
            long[] months = new long[12];
            long monthSum = 0;
            for (int m = 0; m < 12; m++) {
                months[m] = getCellLong(row, 22 + m);
                monthSum += months[m];
            }
            // Một số dòng cột tổng là công thức lỗi / trống -> lấy tổng các tháng
            if (total <= 0) total = monthSum;

            BudgetItem item = new BudgetItem();
            item.setFundId(fundId);
            item.setGroupCategory(group);
            item.setSubCategory(getCellString(row, 2)); // Loại chi phí: GIA HẠN, MUA MỚI...
            item.setCostType(getCellString(row, 2));
            item.setItemName(itemName.trim());
            item.setUnitPrice(getCellLong(row, 3));
            item.setUnit(getCellString(row, 4));
            item.setQuantity(getCellInt(row, 6));
            item.setAllocatedAmount(total);
            String desc = getCellString(row, 34);
            if (desc == null || desc.isBlank()) desc = getCellString(row, 19);
            item.setDescription(desc);
            if (total <= 0) item.setNotes("Chưa phân bổ tháng (tổng = 0 trong file Excel)");
            item.setMonthlyAmountsFromArray(months);
            items.add(item);
        }
        return items;
    }

    /**
     * Parse sheet "IT-TỔNG HỢP NGÂN SÁCH" — bảng chính, mỗi dòng là một đầu mục chi phí.
     * Cột: A=STT, B=Nội dung, C=Loại chi phí (OPEX/CAPEX/PROJECT/NHÂN SỰ/CÔNG TÁC PHÍ),
     * D=Ngân sách dự kiến, E=Tỷ trọng, F=Đầu mục chi phí, G=Ghi chú.
     *
     * Hai dòng "Chi phí gia hạn các dịch vụ" và "Chi phí đầu tư, mua sắm" có ngân sách là
     * công thức trỏ sang IT_OPEX!/IT_CAPEX! — đó chỉ là tổng của 2 sheet chi tiết đã nhập
     * ở trên nên phải bỏ qua, nếu không sẽ cộng trùng.
     */
    private List<BudgetItem> parseSummaryBudgetSheet(Sheet sheet, Long fundId) {
        List<BudgetItem> items = new ArrayList<>();

        for (int i = 0; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            String itemName = getCellString(row, 1); // cột B: Nội dung
            if (itemName == null || itemName.isBlank()) continue;

            String lower = itemName.trim().toLowerCase();
            if (lower.equals("nội dung") || lower.startsWith("tổng cộng")
                    || lower.startsWith("tổng chi phí")) continue;

            if (referencesDetailSheet(row, 3)) continue; // đã nhập chi tiết từ IT_OPEX / IT_CAPEX

            String costType = getCellString(row, 2); // cột C
            if (costType == null || costType.isBlank()) costType = "KHÁC";
            costType = costType.trim().toUpperCase();

            BudgetItem item = new BudgetItem();
            item.setFundId(fundId);
            item.setGroupCategory(costType);
            item.setCostType(costType);
            item.setSubCategory(getCellString(row, 5)); // Đầu mục chi phí
            item.setItemName(itemName.trim());
            item.setQuantity(1);
            long total = getCellLong(row, 3);
            item.setUnitPrice(total);
            item.setAllocatedAmount(total);
            item.setNotes(getCellString(row, 6));
            if (total <= 0) {
                String note = item.getNotes();
                item.setNotes((note == null || note.isBlank())
                        ? "Chưa có ngân sách trong file Excel" : note);
            }
            items.add(item);
        }
        return items;
    }

    /** Ô ngân sách là công thức trỏ sang sheet IT_OPEX / IT_CAPEX (tổng của sheet chi tiết). */
    private boolean referencesDetailSheet(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null || cell.getCellType() != CellType.FORMULA) return false;
        String formula = cell.getCellFormula().toUpperCase();
        return formula.contains("IT_OPEX") || formula.contains("IT_CAPEX");
    }

    /**
     * Tạo file Excel mẫu để nhập ngân sách — đúng cấu trúc mà
     * {@link #parseBudgetItemsFromExcel} đang đọc, để năm sau chỉ việc điền số.
     * Gồm 4 sheet: HƯỚNG DẪN, IT-TỔNG HỢP NGÂN SÁCH (bảng chính), IT_OPEX, IT_CAPEX.
     */
    public ByteArrayInputStream generateSampleBudgetExcel() throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle head = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            head.setFont(bold);
            CellStyle money = workbook.createCellStyle();
            money.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));

            buildGuideSheet(workbook, head);
            buildSummaryTemplateSheet(workbook, head, money);
            buildDetailTemplateSheet(workbook, "IT_OPEX", head, money, new String[][]{
                    {"Gia hạn Microsoft 365", "GIA HẠN", "3500000", "User/năm", "20"},
                    {"Gia hạn tên miền & hosting", "GIA HẠN", "5000000", "Gói/năm", "1"},
                    {"Chi phí dự trù", "DỰ TRÙ", "50000000", "Gói", "1"},
            });
            buildDetailTemplateSheet(workbook, "IT_CAPEX", head, money, new String[][]{
                    {"Laptop làm việc nhân viên", "MUA MỚI", "25000000", "Cái", "20"},
                    {"Máy in màu", "MUA MỚI", "30000000", "Cái", "1"},
                    {"Chi phí dự trù", "DỰ TRÙ", "50000000", "Gói", "1"},
            });

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    /** Sheet hướng dẫn — tên sheet không chứa OPEX/CAPEX/TỔNG HỢP nên hệ thống bỏ qua khi import. */
    private void buildGuideSheet(Workbook workbook, CellStyle head) {
        Sheet sheet = workbook.createSheet("HƯỚNG DẪN");
        sheet.setColumnWidth(0, 26000);
        String[] lines = {
            "HƯỚNG DẪN ĐIỀN FILE NGÂN SÁCH IT",
            "",
            "1. GIỮ NGUYÊN tên 3 sheet và THỨ TỰ CÁC CỘT. Chỉ điền thêm dòng, KHÔNG chèn/xoá cột —",
            "   hệ thống đọc theo vị trí cột, chèn cột sẽ làm lệch toàn bộ số liệu mà không báo lỗi.",
            "   Được phép đổi tên sheet miễn là vẫn chứa chữ OPEX / CAPEX / TỔNG HỢP (vd IT_OPEX 2027).",
            "",
            "2. Sheet IT-TỔNG HỢP NGÂN SÁCH là BẢNG CHÍNH. Mỗi dòng là một đầu mục chi phí.",
            "   - Cột LOẠI CHI PHÍ: OPEX / CAPEX / PROJECT / NHÂN SỰ / CÔNG TÁC PHÍ… (thêm loại mới thoải mái).",
            "   - Hai dòng đầu PHẢI để công thức =IT_OPEX!V6 và =IT_CAPEX!V6.",
            "     Gõ số cứng vào đây sẽ bị CỘNG TRÙNG với hai sheet chi tiết.",
            "",
            "3. Sheet IT_OPEX / IT_CAPEX là chi tiết. Điền từ dòng 7 trở xuống.",
            "   - Cột V (Tổng giá trị) = SUM(W:AH) tức tổng 12 tháng, cứ kéo công thức xuống.",
            "   - Ô TỔNG ở V6 phải phủ HẾT các dòng dữ liệu. Thêm dòng mới thì nhớ sửa lại vùng SUM,",
            "     nếu không tổng sẽ thiếu (file 2026 từng sót dòng Chi phí dự trù 50 triệu vì lý do này).",
            "",
            "4. Dòng tổng ở sheet chi tiết phải để đúng chữ Tổng ở cột B thì hệ thống mới bỏ qua,",
            "   viết khác đi sẽ bị nhập thành một hạng mục và làm ngân sách bị nhân đôi.",
            "",
            "5. Dòng nào chưa có số thì để trống, hệ thống vẫn nhập và ghi chú Chưa có ngân sách.",
        };
        for (int i = 0; i < lines.length; i++) {
            Cell cell = sheet.createRow(i).createCell(0);
            cell.setCellValue(lines[i]);
            if (i == 0 || (!lines[i].isEmpty() && Character.isDigit(lines[i].charAt(0)))) cell.setCellStyle(head);
        }
    }

    /** Bảng chính: A=STT, B=Nội dung, C=Loại chi phí, D=Ngân sách, E=Tỷ trọng, F=Đầu mục, G=Ghi chú. */
    private void buildSummaryTemplateSheet(Workbook workbook, CellStyle head, CellStyle money) {
        Sheet sheet = workbook.createSheet("IT-TỔNG HỢP NGÂN SÁCH");
        int[] widths = {2000, 12000, 6000, 6000, 4000, 12000, 10000};
        for (int i = 0; i < widths.length; i++) sheet.setColumnWidth(i, widths[i]);

        setText(sheet.createRow(0), 0, "TỔNG NGÂN SÁCH DỰ KIẾN CHO CNTT NĂM ....", head);

        Row r1 = sheet.createRow(1);
        setText(r1, 3, "Mục tiêu doanh thu", head);
        r1.createCell(4).setCellStyle(money);
        setText(r1, 5, "ĐẦU MỤC CHI PHÍ", head);
        setText(r1, 6, "GHI CHÚ", head);

        Row r2 = sheet.createRow(2);
        String[] cols = {"STT", "NỘI DUNG", "LOẠI CHI PHÍ DỰ KIẾN", "NGÂN SÁCH DỰ KIẾN", "TỶ TRỌNG"};
        for (int i = 0; i < cols.length; i++) setText(r2, i, cols[i], head);

        // Hai dòng đầu bắt buộc là công thức trỏ sang sheet chi tiết (hệ thống bỏ qua, tránh cộng trùng)
        String[][] rows = {
            {"1", "Chi phí gia hạn các dịch vụ", "OPEX", "=IT_OPEX!V6", "Chi phí Công cụ dụng cụ - Chung", ""},
            {"2", "Chi phí đầu tư, mua sắm", "CAPEX", "=IT_CAPEX!V6", "Chi phí Công cụ dụng cụ - Chung", ""},
            {"3", "Bản quyền Office", "OPEX", "0", "Chi phí Công cụ dụng cụ - Chung", "Ghi rõ số lượng license"},
            {"4", "Hạ tầng văn phòng mới", "CAPEX", "0", "Chi phí Công cụ dụng cụ - Chung", "Để trống nếu chưa khảo sát"},
            {"5", "Dự án CNTT", "PROJECT", "0", "Chi phí Công cụ dụng cụ - Chung", ""},
            {"6", "Chi phí nhân sự", "NHÂN SỰ", "0", "Tổng hợp từ bảng nhân sự", ""},
            {"7", "Chi phí công tác phí", "CÔNG TÁC PHÍ", "0", "Chi phí công tác phí - Chung", ""},
        };
        int first = 3;
        int totalRow = first + rows.length;
        for (int i = 0; i < rows.length; i++) {
            Row row = sheet.createRow(first + i);
            setText(row, 0, rows[i][0], null);
            setText(row, 1, rows[i][1], null);
            setText(row, 2, rows[i][2], null);
            Cell budget = row.createCell(3);
            budget.setCellStyle(money);
            if (rows[i][3].startsWith("=")) budget.setCellFormula(rows[i][3].substring(1));
            else budget.setCellValue(Double.parseDouble(rows[i][3]));
            int excelRow = first + i + 1;
            row.createCell(4).setCellFormula("D" + excelRow + "/$D$" + (totalRow + 1) + "*100");
            setText(row, 5, rows[i][4], null);
            setText(row, 6, rows[i][5], null);
        }

        Row total = sheet.createRow(totalRow);
        setText(total, 0, "TỔNG CỘNG", head);
        Cell totalCell = total.createCell(3);
        totalCell.setCellStyle(money);
        totalCell.setCellFormula("SUM(D" + (first + 1) + ":D" + totalRow + ")");

        Row ratio = sheet.createRow(totalRow + 1);
        setText(ratio, 0, "TỔNG CHI PHÍ/DOANH THU (%)", head);
        ratio.createCell(3).setCellFormula("D" + (totalRow + 1) + "/E2");
    }

    /** Sheet chi tiết: B=Hạng mục, C=Loại, D=Đơn giá, E=ĐVT, G=Tổng SL, V=Tổng tiền, W..AH=12 tháng, AI=Mô tả. */
    private void buildDetailTemplateSheet(Workbook workbook, String name, CellStyle head, CellStyle money, String[][] samples) {
        Sheet sheet = workbook.createSheet(name);
        sheet.setColumnWidth(1, 14000);
        sheet.setColumnWidth(2, 5000);
        sheet.setColumnWidth(3, 5000);
        sheet.setColumnWidth(21, 6000);
        sheet.setColumnWidth(34, 10000);

        setText(sheet.createRow(1), 1, "NGÂN SÁCH BỘ PHẬN CNTT — " + name, head);

        Row header = sheet.createRow(3);
        setText(header, 1, "Hạng mục", head);
        setText(header, 2, "Loại chi phí", head);
        setText(header, 3, "Đơn giá", head);
        setText(header, 4, "ĐVT", head);
        setText(header, 5, "PBCM", head);
        setText(header, 6, "Tổng SL", head);
        setText(header, 21, "Tổng Giá trị (VND)", head);
        for (int m = 0; m < 12; m++) setText(header, 22 + m, "Tháng " + (m + 1), head);
        setText(header, 34, "Mô tả", head);

        int first = 6;                      // dòng dữ liệu đầu tiên (Excel dòng 7)
        int last = first + samples.length;  // chừa sẵn 1 dòng trống để điền thêm

        Row totalRow = sheet.createRow(5);
        setText(totalRow, 1, "Tổng", head);
        Cell totalCell = totalRow.createCell(21);
        totalCell.setCellStyle(money);
        totalCell.setCellFormula("SUM(V" + (first + 1) + ":V" + (last + 1) + ")");

        for (int i = 0; i < samples.length; i++) {
            Row row = sheet.createRow(first + i);
            setText(row, 1, samples[i][0], null);
            setText(row, 2, samples[i][1], null);
            Cell price = row.createCell(3);
            price.setCellStyle(money);
            price.setCellValue(Double.parseDouble(samples[i][2]));
            setText(row, 4, samples[i][3], null);
            row.createCell(6).setCellValue(Double.parseDouble(samples[i][4]));
            int excelRow = first + i + 1;
            Cell sum = row.createCell(21);
            sum.setCellStyle(money);
            sum.setCellFormula("SUM(W" + excelRow + ":AH" + excelRow + ")");
            for (int m = 0; m < 12; m++) row.createCell(22 + m).setCellStyle(money);
        }
        setText(sheet.createRow(last + 1), 1, "Thêm trên dòng này …", null);
    }

    private void setText(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        if (style != null) cell.setCellStyle(style);
    }

    // --- Helper ---
    private String getCellString(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) type = cell.getCachedFormulaResultType();
        switch (type) {
            case STRING:  return cell.getStringCellValue().trim();
            case NUMERIC: return String.valueOf((long) cell.getNumericCellValue());
            default: return null;
        }
    }

    private Long getCellLong(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return 0L;
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) type = cell.getCachedFormulaResultType();
        if (type == CellType.NUMERIC) return Math.round(cell.getNumericCellValue());
        try { return Long.parseLong(cell.getStringCellValue().replaceAll("[^0-9]", "")); } catch (Exception e) { return 0L; }
    }

    private Integer getCellInt(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return 1;
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) type = cell.getCachedFormulaResultType();
        if (type == CellType.NUMERIC) return (int) cell.getNumericCellValue();
        try { return Integer.parseInt(cell.getStringCellValue().replaceAll("[^0-9]", "")); } catch (Exception e) { return 1; }
    }

    /** Cột của file nhập/mẫu công cụ dụng cụ — dùng chung cho cả sinh file mẫu lẫn đọc file. */
    private static final String[] ASSET_HEADERS = {
        "Mã kiểm kê (*)", "Danh mục", "Địa điểm văn phòng", "Số lượng",
        "Người sử dụng", "Chức vụ", "Bộ phận", "Địa điểm sử dụng",
        "Loại tài sản", "Nhà sản xuất", "Model", "Thông tin chi tiết",
        "Serial Number", "Ngày mua (dd/MM/yyyy)", "Tình trạng", "Ghi chú"
    };

    /** Tạo file Excel mẫu để nhập danh sách công cụ dụng cụ. */
    public ByteArrayInputStream generateSampleAssetExcel() throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle head = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            head.setFont(bold);

            Sheet guide = workbook.createSheet("HƯỚNG DẪN");
            guide.setColumnWidth(0, 26000);
            String[] notes = {
                "HƯỚNG DẪN NHẬP CÔNG CỤ DỤNG CỤ",
                "",
                "1. Điền dữ liệu vào sheet 'CÔNG CỤ DỤNG CỤ', mỗi dòng là một tài sản. Nhập bao nhiêu dòng cũng được.",
                "2. Chỉ 'Mã kiểm kê' là bắt buộc và phải KHÔNG TRÙNG. Mã đã có trong hệ thống sẽ được CẬP NHẬT chứ không tạo mới.",
                "3. 'Danh mục' điền đúng tên danh mục đã có (vd Laptop, Màn hình). Tên chưa có sẽ để trống danh mục.",
                "4. 'Ngày mua' định dạng dd/MM/yyyy, vd 15/07/2026. Để trống nếu không rõ.",
                "5. 'Tình trạng': Đang sử dụng / Trong kho / Hỏng / Thanh lý. Bỏ trống mặc định là Đang sử dụng.",
                "6. Giữ nguyên dòng tiêu đề. Được phép đổi thứ tự cột — hệ thống dò theo tên tiêu đề.",
            };
            for (int i = 0; i < notes.length; i++) {
                Cell cell = guide.createRow(i).createCell(0);
                cell.setCellValue(notes[i]);
                if (i == 0) cell.setCellStyle(head);
            }

            Sheet sheet = workbook.createSheet("CÔNG CỤ DỤNG CỤ");
            Row header = sheet.createRow(0);
            for (int i = 0; i < ASSET_HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(ASSET_HEADERS[i]);
                cell.setCellStyle(head);
                sheet.setColumnWidth(i, 5200);
            }

            String[][] samples = {
                {"IT-LAP-001", "Laptop", "VP HCM", "1", "Nguyễn Văn A", "Chuyên viên", "Kinh doanh", "Tầng 3",
                 "Laptop", "Dell", "Latitude 5440", "i7/16GB/512GB", "SN123456", "15/07/2026", "Đang sử dụng", ""},
                {"IT-MAN-002", "Màn hình", "VP HCM", "2", "", "", "", "Kho IT",
                 "Màn hình", "LG", "24MK600", "24 inch IPS", "", "", "Trong kho", "Dự phòng"},
            };
            for (int r = 0; r < samples.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < samples[r].length; c++) row.createCell(c).setCellValue(samples[r][c]);
            }

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    /** Kết quả đọc file công cụ dụng cụ. */
    public static class AssetImportRow {
        public String inventoryCode, categoryName, officeLocation, assignedToName, assignedToPosition,
                assignedToDepartment, assignedToLocation, assetType, manufacturer, model, details,
                serialNumber, status, note;
        public Integer quantity;
        public LocalDate purchaseDate;
    }

    /**
     * Đọc danh sách công cụ dụng cụ từ file Excel. Dò cột theo TÊN TIÊU ĐỀ nên đổi thứ tự
     * cột vẫn nhập được; dòng không có mã kiểm kê sẽ bỏ qua.
     */
    public List<AssetImportRow> parseAssetsFromExcel(java.io.InputStream inputStream) throws IOException {
        List<AssetImportRow> rows = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = null;
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                String name = workbook.getSheetName(s).toUpperCase();
                if (name.contains("HƯỚNG DẪN") || name.contains("HUONG DAN")) continue;
                sheet = workbook.getSheetAt(s);
                break;
            }
            if (sheet == null) return rows;

            int headerRowIdx = -1;
            java.util.Map<String, Integer> cols = null;
            int bestScore = 0;
            int scanTo = Math.min(sheet.getLastRowNum(), 20);
            for (int r = sheet.getFirstRowNum(); r <= scanTo; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                java.util.Map<String, Integer> map = new java.util.LinkedHashMap<>();
                for (Cell cell : row) {
                    String key = matchAssetColumn(headerText(cell));
                    if (key != null && !map.containsKey(key)) map.put(key, cell.getColumnIndex());
                }
                if (map.size() > bestScore) {
                    bestScore = map.size();
                    headerRowIdx = r;
                    cols = map;
                }
            }
            if (cols == null || !cols.containsKey("inventoryCode")) return rows;

            for (int r = headerRowIdx + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String code = cellString(row, cols.get("inventoryCode"));
                if (isBlank(code)) continue;

                AssetImportRow item = new AssetImportRow();
                item.inventoryCode = code.trim();
                item.categoryName = cellString(row, cols.get("category"));
                item.officeLocation = cellString(row, cols.get("officeLocation"));
                item.assignedToName = cellString(row, cols.get("assignedToName"));
                item.assignedToPosition = cellString(row, cols.get("assignedToPosition"));
                item.assignedToDepartment = cellString(row, cols.get("assignedToDepartment"));
                item.assignedToLocation = cellString(row, cols.get("assignedToLocation"));
                item.assetType = cellString(row, cols.get("assetType"));
                item.manufacturer = cellString(row, cols.get("manufacturer"));
                item.model = cellString(row, cols.get("model"));
                item.details = cellString(row, cols.get("details"));
                item.serialNumber = cellString(row, cols.get("serialNumber"));
                item.status = cellString(row, cols.get("status"));
                item.note = cellString(row, cols.get("note"));
                item.purchaseDate = cellDate(row, cols.get("purchaseDate"));
                String qty = cellString(row, cols.get("quantity"));
                try {
                    item.quantity = isBlank(qty) ? 1 : Integer.parseInt(qty.replaceAll("[^0-9]", ""));
                } catch (Exception e) {
                    item.quantity = 1;
                }
                rows.add(item);
            }
        }
        return rows;
    }

    /**
     * Xuất danh sách công cụ dụng cụ (tài sản) ra file Excel (.xlsx) trực quan, trình bày đẹp mắt.
     */
    public ByteArrayInputStream exportAssetsToExcel(List<Asset> assets,
                                                    String categoryNameFilter,
                                                    String statusFilter,
                                                    String keywordFilter,
                                                    Map<Long, String> categoryNames) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Danh Sách Tài Sản");

            // Bật lưới ô trong Excel
            sheet.setDisplayGridlines(true);

            // ---- STYLES ----
            // Title style: Nền xanh đen, chữ trắng in đậm, 14pt, căn giữa
            CellStyle titleStyle = workbook.createCellStyle();
            org.apache.poi.xssf.usermodel.XSSFColor navyColor =
                    new org.apache.poi.xssf.usermodel.XSSFColor(new byte[]{(byte) 30, (byte) 58, (byte) 138}, null);
            ((org.apache.poi.xssf.usermodel.XSSFCellStyle) titleStyle).setFillForegroundColor(navyColor);
            titleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setColor(IndexedColors.WHITE.getIndex());
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);
            titleStyle.setAlignment(HorizontalAlignment.CENTER);
            titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            // Subtitle / info style
            CellStyle subTitleStyle = workbook.createCellStyle();
            Font subTitleFont = workbook.createFont();
            subTitleFont.setItalic(true);
            subTitleFont.setFontHeightInPoints((short) 10);
            subTitleFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            subTitleStyle.setFont(subTitleFont);
            subTitleStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            // Header style: Nền xanh đậm, chữ trắng, in đậm, border mỏng
            CellStyle headerStyle = workbook.createCellStyle();
            ((org.apache.poi.xssf.usermodel.XSSFCellStyle) headerStyle).setFillForegroundColor(navyColor);
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setFontHeightInPoints((short) 10);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setWrapText(true);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            // Data cell styles (Left, Center, Right)
            CellStyle dataLeft = createAssetDataStyle(workbook, HorizontalAlignment.LEFT, false, null);
            CellStyle dataCenter = createAssetDataStyle(workbook, HorizontalAlignment.CENTER, false, null);
            CellStyle dataRight = createAssetDataStyle(workbook, HorizontalAlignment.RIGHT, false, null);
            CellStyle dataCenterBold = createAssetDataStyle(workbook, HorizontalAlignment.CENTER, true, null);

            // Alternating rows (Zebra striping - soft blue #F8FAFC)
            org.apache.poi.xssf.usermodel.XSSFColor altColor =
                    new org.apache.poi.xssf.usermodel.XSSFColor(new byte[]{(byte) 248, (byte) 250, (byte) 252}, null);
            CellStyle altLeft = createAssetDataStyle(workbook, HorizontalAlignment.LEFT, false, altColor);
            CellStyle altCenter = createAssetDataStyle(workbook, HorizontalAlignment.CENTER, false, altColor);
            CellStyle altRight = createAssetDataStyle(workbook, HorizontalAlignment.RIGHT, false, altColor);
            CellStyle altCenterBold = createAssetDataStyle(workbook, HorizontalAlignment.CENTER, true, altColor);

            // Status Badge Cell Styles
            CellStyle statusInUse = createAssetStatusStyle(workbook, new byte[]{(byte) 220, (byte) 252, (byte) 231}, new byte[]{(byte) 22, (byte) 101, (byte) 52}); // green
            CellStyle statusInStock = createAssetStatusStyle(workbook, new byte[]{(byte) 254, (byte) 243, (byte) 199}, new byte[]{(byte) 146, (byte) 64, (byte) 14}); // yellow/amber
            CellStyle statusBroken = createAssetStatusStyle(workbook, new byte[]{(byte) 254, (byte) 226, (byte) 226}, new byte[]{(byte) 153, (byte) 27, (byte) 27}); // red

            // Formatter
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy");

            // Header columns
            String[] headers = {
                "STT", "Mã kiểm kê", "Danh mục", "Loại tài sản", "Nhà sản xuất", "Model",
                "Số Serial", "Số lượng", "Người sử dụng / Bàn giao", "Chức vụ", "Bộ phận",
                "Địa điểm / Văn phòng", "Trạng thái", "Ngày mua", "Lần kiểm kê gần nhất",
                "Người kiểm kê", "Tình trạng kiểm kê", "Chi tiết / Cấu hình", "Ghi chú"
            };

            // ----- ROW 0: TITLE BANNER -----
            Row titleRow = sheet.createRow(0);
            titleRow.setHeightInPoints(34);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("BÁO CÁO QUẢN LÝ CÔNG CỤ DỤNG CỤ & TÀI SẢN — MIDOMAX IT");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, headers.length - 1));

            // ----- ROW 1: METADATA & FILTERS -----
            Row infoRow = sheet.createRow(1);
            infoRow.setHeightInPoints(20);
            String exportTimeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
            String catFilterStr = (categoryNameFilter != null && !categoryNameFilter.isBlank()) ? categoryNameFilter : "Tất cả";
            String statFilterStr = (statusFilter != null && !statusFilter.isBlank()) ? statusFilter : "Tất cả";
            String kwFilterStr = (keywordFilter != null && !keywordFilter.isBlank()) ? keywordFilter : "Không";

            Cell infoCell = infoRow.createCell(0);
            infoCell.setCellValue("Thời gian xuất: " + exportTimeStr + "  |  Danh mục: " + catFilterStr
                    + "  |  Trạng thái: " + statFilterStr + "  |  Từ khóa: " + kwFilterStr);
            infoCell.setCellStyle(subTitleStyle);

            // ----- ROW 2: STATS SUMMARY -----
            long totalQty = 0;
            long inUseCount = 0;
            long inStockCount = 0;
            long brokenCount = 0;
            for (Asset a : assets) {
                totalQty += (a.getQuantity() != null ? a.getQuantity() : 1);
                String st = a.getStatus() != null ? a.getStatus().trim() : "";
                if ("Đang sử dụng".equalsIgnoreCase(st)) inUseCount++;
                else if ("Trong kho".equalsIgnoreCase(st)) inStockCount++;
                else if ("Hỏng".equalsIgnoreCase(st) || "Đang sửa".equalsIgnoreCase(st)) brokenCount++;
            }

            Row statRow = sheet.createRow(2);
            statRow.setHeightInPoints(22);
            createCell(statRow, 0, "Tổng mã tài sản: " + assets.size() + "  |  Tổng số lượng: " + totalQty
                    + "  |  Đang sử dụng: " + inUseCount + "  |  Trong kho: " + inStockCount
                    + "  |  Hỏng / Đang sửa: " + brokenCount, subTitleStyle);

            // ----- ROW 4: TABLE HEADER -----
            Row headerRow = sheet.createRow(4);
            headerRow.setHeightInPoints(28);
            for (int i = 0; i < headers.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
            }

            // ----- DATA ROWS -----
            int rowIdx = 5;
            int stt = 1;
            for (Asset a : assets) {
                Row row = sheet.createRow(rowIdx++);
                row.setHeightInPoints(20);
                boolean isAlt = (rowIdx % 2 == 0);

                CellStyle styleL = isAlt ? altLeft : dataLeft;
                CellStyle styleC = isAlt ? altCenter : dataCenter;
                CellStyle styleR = isAlt ? altRight : dataRight;
                CellStyle styleCB = isAlt ? altCenterBold : dataCenterBold;

                createCell(row, 0, String.valueOf(stt++), styleC);
                createCell(row, 1, a.getInventoryCode(), styleCB);

                String catName = categoryNames != null && a.getCategoryId() != null ?
                        categoryNames.getOrDefault(a.getCategoryId(), "") : "";
                createCell(row, 2, catName, styleL);
                createCell(row, 3, a.getAssetType(), styleL);
                createCell(row, 4, a.getManufacturer(), styleL);
                createCell(row, 5, a.getModel(), styleL);
                createCell(row, 6, a.getSerialNumber(), styleC);

                Cell qtyCell = row.createCell(7);
                qtyCell.setCellValue(a.getQuantity() != null ? a.getQuantity() : 1);
                qtyCell.setCellStyle(styleR);

                createCell(row, 8, a.getAssignedToName(), styleL);
                createCell(row, 9, a.getAssignedToPosition(), styleL);
                createCell(row, 10, a.getAssignedToDepartment(), styleL);

                String loc = a.getAssignedToLocation();
                if (loc == null || loc.isBlank()) loc = a.getOfficeLocation();
                createCell(row, 11, loc, styleL);

                // Trạng thái badge
                String st = a.getStatus() != null ? a.getStatus().trim() : "";
                CellStyle statusStyle = styleC;
                if ("Đang sử dụng".equalsIgnoreCase(st)) statusStyle = statusInUse;
                else if ("Trong kho".equalsIgnoreCase(st)) statusStyle = statusInStock;
                else if ("Hỏng".equalsIgnoreCase(st) || "Đang sửa".equalsIgnoreCase(st)) statusStyle = statusBroken;
                createCell(row, 12, st, statusStyle);

                createCell(row, 13, a.getPurchaseDate() != null ? a.getPurchaseDate().format(dtf) : "", styleC);
                createCell(row, 14, a.getLastInventoryDate() != null ? a.getLastInventoryDate().format(dtf) : "", styleC);
                createCell(row, 15, a.getInventoryBy(), styleL);
                createCell(row, 16, a.getInventoryCondition(), styleL);
                createCell(row, 17, a.getDetails(), styleL);
                createCell(row, 18, a.getNote(), styleL);
            }

            // AUTO SIZE COLUMNS WITH MIN WIDTH
            for (int c = 0; c < headers.length; c++) {
                sheet.autoSizeColumn(c);
                int currentWidth = sheet.getColumnWidth(c);
                sheet.setColumnWidth(c, Math.min(15000, Math.max(currentWidth + 1200, 3200)));
            }

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    private CellStyle createAssetDataStyle(Workbook wb, HorizontalAlignment align, boolean bold, org.apache.poi.xssf.usermodel.XSSFColor bg) {
        CellStyle style = wb.createCellStyle();
        if (bg != null) {
            ((org.apache.poi.xssf.usermodel.XSSFCellStyle) style).setFillForegroundColor(bg);
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        Font font = wb.createFont();
        font.setFontHeightInPoints((short) 10);
        if (bold) font.setBold(true);
        style.setFont(font);
        style.setAlignment(align);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createAssetStatusStyle(Workbook wb, byte[] bgRgb, byte[] textRgb) {
        CellStyle style = wb.createCellStyle();
        org.apache.poi.xssf.usermodel.XSSFColor bgColor = new org.apache.poi.xssf.usermodel.XSSFColor(bgRgb, null);
        ((org.apache.poi.xssf.usermodel.XSSFCellStyle) style).setFillForegroundColor(bgColor);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 9.5);
        org.apache.poi.xssf.usermodel.XSSFColor textColor = new org.apache.poi.xssf.usermodel.XSSFColor(textRgb, null);
        ((org.apache.poi.xssf.usermodel.XSSFFont) font).setColor(textColor);

        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private String matchAssetColumn(String h) {
        if (h == null || h.isEmpty()) return null;
        if (h.contains("mã kiểm kê") || h.contains("ma kiem ke") || h.contains("inventory code")) return "inventoryCode";
        if (h.contains("danh mục") || h.contains("category")) return "category";
        if (h.contains("địa điểm văn phòng") || h.contains("office")) return "officeLocation";
        if (h.contains("số lượng") || h.contains("quantity")) return "quantity";
        if (h.contains("người sử dụng") || h.contains("họ và tên") || h.contains("người dùng")) return "assignedToName";
        if (h.contains("chức vụ") || h.contains("position")) return "assignedToPosition";
        if (h.contains("bộ phận") || h.contains("department")) return "assignedToDepartment";
        if (h.contains("địa điểm sử dụng") || h.contains("location")) return "assignedToLocation";
        if (h.contains("loại tài sản") || h.contains("asset type")) return "assetType";
        if (h.contains("nhà sản xuất") || h.contains("manufacturer")) return "manufacturer";
        if (h.contains("model")) return "model";
        if (h.contains("thông tin chi tiết") || h.contains("cấu hình") || h.contains("details")) return "details";
        if (h.contains("serial")) return "serialNumber";
        if (h.contains("ngày mua") || h.contains("purchase")) return "purchaseDate";
        if (h.contains("tình trạng") || h.contains("trạng thái") || h.contains("status")) return "status";
        if (h.contains("ghi chú") || h.contains("note")) return "note";
        return null;
    }
}
