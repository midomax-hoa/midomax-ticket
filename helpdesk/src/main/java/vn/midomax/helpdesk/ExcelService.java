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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * Chuẩn hóa chuỗi đọc từ Excel: đổi mọi loại khoảng trắng lạ (non-breaking space,
     * tab, xuống dòng...) thành dấu cách thường, gộp nhiều dấu cách liền nhau thành một,
     * rồi cắt hai đầu. Không làm vậy thì "License Office 365" và "License  Office 365"
     * (hoặc bản có NBSP từ Excel) bị coi là hai giá trị khác nhau -> danh sách phân loại
     * nhỏ bị nhân đôi mỗi lần import thêm file.
     */
    public static String normalizeText(String s) {
        if (s == null) return null;
        String out = s.replace('\u00A0', ' ')   // non-breaking space
                      .replace('\u200B', ' ')   // zero-width space
                      .replace('\uFEFF', ' ')   // BOM
                      .replaceAll("\s+", " ")
                      .trim();
        return out.isEmpty() ? null : out;
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
                    return s == null ? null : normalizeText(s);
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
                        return normalizeText(c.getStringCellValue());
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
            List<Sheet> itdSheets = new ArrayList<>();
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                String name = workbook.getSheetName(s).toUpperCase();
                if (name.contains("OPEX") || name.contains("CAPEX")) {
                    itdSheets.add(workbook.getSheetAt(s));
                }
            }
            if (!itdSheets.isEmpty()) {
                for (Sheet sheet : itdSheets) {
                    items.addAll(parseItdBudgetSheet(sheet, fundId));
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

    /** Tạo file Excel mẫu để nhập ngân sách. */
    public ByteArrayInputStream generateSampleBudgetExcel() throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Ngân sách IT");
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            String[] headers = {
                "Chi phí nhóm 2", "Chi phí nhóm 3", "Nội dung (*)", "Mô tả",
                "Đơn giá", "Đơn vị", "Số lượng", "Thành tiền", "Ghi chú"
            };
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 5000);
            }

            // Dòng dữ liệu mẫu
            Object[][] sample = {
                {"Phần cứng", "Laptop/PC", "Laptop Dell i7 16GB", "Dành cho kỹ sư", 25000000L, "Cái", 2, 50000000L, ""},
                {"Bản quyền", "Phần mềm", "Microsoft 365 Business", "Gói 1 năm/user", 3500000L, "User", 5, 17500000L, "Gia hạn hàng năm"},
            };
            for (int r = 0; r < sample.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < sample[r].length; c++) {
                    Cell cell = row.createCell(c);
                    Object val = sample[r][c];
                    if (val instanceof String)  cell.setCellValue((String) val);
                    else if (val instanceof Long)    cell.setCellValue((double)(Long) val);
                    else if (val instanceof Integer) cell.setCellValue((Integer) val);
                }
            }

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
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
}

