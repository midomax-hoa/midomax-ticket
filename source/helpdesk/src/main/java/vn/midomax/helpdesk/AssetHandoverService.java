package vn.midomax.helpdesk;

import org.apache.poi.util.Units;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Sinh "BIÊN BẢN BÀN GIAO TÀI SẢN" (.docx) cho một hoặc nhiều {@link Asset}
 * bàn giao cùng lúc cho một người, dựng theo đúng mẫu Word hiện hành của công ty.
 *
 * Thông tin bên giao để trống (dấu chấm) cho người dùng điền tay khi in.
 */
@Service
public class AssetHandoverService {

    /**
     * Một dòng phụ kiện kèm theo (chuột, túi, sạc...) — không có mã kiểm kê,
     * chỉ nhập lúc xuất biên bản nên không lưu xuống DB.
     */
    public record Accessory(String name, String spec, String serial, String quantity, String condition) {
    }

    private static final String FONT = "Times New Roman";

    /** Chỗ trống để điền tay sau khi in. */
    private static final String BLANK = "................................";

    private static final String COMPANY_NAME = "CÔNG TY CỔ PHẦN MIDOMAX VIỆT NAM";
    private static final String COMPANY_ADDRESS = "Địa chỉ: VP TP.HCM – 188 Trần Quang Khải, Quận 1, TP.HCM";

    /**
     * Logo đặt ở đây thì sẽ được nhúng vào đầu trang; thiếu file thì header chỉ có chữ.
     * Đường dẫn tính từ src/main/resources.
     */
    private static final String LOGO_PATH = "static/images/logo-midomax.png";
    private static final int LOGO_WIDTH_PT = 110;
    private static final int LOGO_HEIGHT_PT = 26;

    /** Mục II - các cam kết, cố định theo mẫu. */
    private static final String[] COMMITMENTS = {
            "1. Sử dụng 100% máy tính cho mục đích công việc của Công ty, không sử dụng cho mục đích "
                    + "cá nhân, không cài đặt hoặc dùng phần mềm trái phép.",
            "2. Dữ liệu trong máy tính thuộc sở hữu của Công ty. Bên nhận có trách nhiệm giữ nguyên dữ liệu "
                    + "khi bàn giao lại, không xóa, di chuyển hay sao chép ra ngoài nếu chưa có sự cho phép của Công ty.",
            "3. Bảo quản, giữ gìn tài sản cẩn thận. Nếu hư hỏng, mất mát do lỗi chủ quan, bên nhận chịu "
                    + "trách nhiệm bồi thường theo quy định của Công ty.",
            "4. Khi có yêu cầu thu hồi, thay đổi thiết bị hoặc khi chấm dứt hợp đồng lao động, bên nhận phải "
                    + "bàn giao lại máy tính và toàn bộ dữ liệu đúng hiện trạng cho Công ty."
    };

    /** Mục III - các biện pháp xử lý vi phạm, cố định theo mẫu. */
    private static final String[] SANCTIONS = {
            "- Thu hồi ngay tài sản được bàn giao;",
            "- Xử lý kỷ luật lao động theo quy định của Công ty và pháp luật hiện hành;",
            "- Yêu cầu bồi thường thiệt hại (nếu có) phát sinh từ hành vi vi phạm;",
            "- Các biện pháp khác theo quy chế, nội quy Công ty và quy định pháp luật."
    };

    /**
     * @param assets      các tài sản bàn giao, phải cùng một người nhận; dòng đầu dùng làm thông tin bên nhận
     * @param categoryById danh mục theo id, để lùi về tên danh mục khi thiếu loại tài sản
     * @param accessories dòng phụ kiện kèm theo, có thể rỗng
     */
    public ByteArrayInputStream buildHandoverDoc(List<Asset> assets,
                                                 Map<Long, AssetCategory> categoryById,
                                                 List<Accessory> accessories) throws IOException {
        if (assets == null || assets.isEmpty()) {
            throw new IllegalArgumentException("Cần ít nhất một tài sản để lập biên bản bàn giao.");
        }
        Asset first = assets.get(0);

        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // ===== Đầu trang: lặp lại trên mọi trang, giống mẫu Word =====
            writeCompanyHeader(doc);

            // ===== Tiêu đề =====
            para(doc, "BIÊN BẢN BÀN GIAO TÀI SẢN", ParagraphAlignment.CENTER, 14, true, false);
            blank(doc);

            // ===== Ngày lập =====
            // Ưu tiên thời gian bàn giao đã nhập trên tài sản; chưa có thì lấy ngày lập
            LocalDate today = first.getHandoverDate() != null
                    ? first.getHandoverDate().toLocalDate() : LocalDate.now();
            para(doc, String.format("Hôm nay, ngày %02d tháng %02d năm %d, chúng tôi gồm:",
                            today.getDayOfMonth(), today.getMonthValue(), today.getYear()),
                    ParagraphAlignment.LEFT, 12, false, false);

            // ===== Bên giao: lấy từ dữ liệu tài sản, thiếu thì để trống điền tay khi in =====
            para(doc, "BÊN GIAO (Bên A):", ParagraphAlignment.LEFT, 12, true, false);
            para(doc, "Đại diện: " + nvl(first.getHandoverBy(), BLANK), ParagraphAlignment.LEFT, 12, false, false);
            para(doc, "Chức vụ: " + nvl(first.getHandoverByPosition(), BLANK), ParagraphAlignment.LEFT, 12, false, false);
            para(doc, "Phòng ban: " + nvl(first.getHandoverByDepartment(), BLANK), ParagraphAlignment.LEFT, 12, false, false);

            // ===== Bên nhận: lấy từ tài sản đầu tiên (mọi tài sản cùng một người nhận) =====
            para(doc, "BÊN NHẬN (Bên B):", ParagraphAlignment.LEFT, 12, true, false);
            para(doc, "Đại diện: " + nvl(first.getAssignedToName(), BLANK), ParagraphAlignment.LEFT, 12, false, false);
            para(doc, "Chức vụ: " + nvl(first.getAssignedToPosition(), BLANK), ParagraphAlignment.LEFT, 12, false, false);
            para(doc, "Phòng ban: " + nvl(first.getAssignedToDepartment(), BLANK), ParagraphAlignment.LEFT, 12, false, false);

            // ===== I. Thông tin tài sản bàn giao =====
            para(doc, "I. Thông tin tài sản bàn giao:", ParagraphAlignment.LEFT, 12, true, false);

            int accCount = accessories == null ? 0 : accessories.size();
            XWPFTable table = doc.createTable(1 + assets.size() + accCount, 6);
            table.setWidth("100%");
            // Đệm trong ô cho thoáng, giống mẫu gốc (đơn vị twips: 60 ≈ 3pt)
            table.setCellMargins(60, 80, 60, 80);

            String[] headers = {"STT", "Tên tài sản và phụ kiện kèm theo", "Thông số kỹ thuật",
                    "Serial/ Mã tài sản", "Số lượng", "Hiện trạng"};
            XWPFTableRow head = table.getRow(0);
            for (int i = 0; i < headers.length; i++) {
                cell(head.getCell(i), headers[i], true, ParagraphAlignment.CENTER);
                // Nền hồng nhạt cho hàng tiêu đề, giống mẫu Word
                head.getCell(i).setColor("FCE4D6");
            }

            int rowIdx = 1;
            int stt = 1;

            // Tài sản có mã kiểm kê
            for (Asset a : assets) {
                AssetCategory cat = (categoryById == null || a.getCategoryId() == null)
                        ? null : categoryById.get(a.getCategoryId());
                writeRow(table.getRow(rowIdx++), new String[]{
                        String.valueOf(stt++),
                        assetName(a, cat),
                        nvl(a.getDetails(), ""),
                        serialAndCode(a),
                        a.getQuantity() == null ? "1" : String.valueOf(a.getQuantity()),
                        nvl(a.getInventoryCondition(), nvl(a.getStatus(), ""))
                });
            }

            // Phụ kiện kèm theo: chưa có mã kiểm kê, chỉ in serial nếu người dùng nhập
            if (accessories != null) {
                for (Accessory acc : accessories) {
                    writeRow(table.getRow(rowIdx++), new String[]{
                            String.valueOf(stt++),
                            nvl(acc.name(), ""),
                            nvl(acc.spec(), ""),
                            nvl(acc.serial(), ""),
                            nvl(acc.quantity(), "1"),
                            nvl(acc.condition(), "")
                    });
                }
            }

            blank(doc);

            // ===== II. Cam kết sử dụng tài sản =====
            para(doc, "II. Cam kết sử dụng tài sản", ParagraphAlignment.LEFT, 12, true, false);
            for (String c : COMMITMENTS) {
                para(doc, c, ParagraphAlignment.BOTH, 12, false, false);
            }

            // ===== III. Xử lý vi phạm =====
            para(doc, "III. Xử lý vi phạm", ParagraphAlignment.LEFT, 12, true, false);
            para(doc, "Trường hợp bên nhận vi phạm bất kỳ cam kết nào nêu trên, Công ty có quyền áp dụng "
                    + "một hoặc nhiều biện pháp sau:", ParagraphAlignment.BOTH, 12, false, false);
            for (String s : SANCTIONS) {
                para(doc, s, ParagraphAlignment.BOTH, 12, false, false);
            }
            para(doc, "Hai bên thống nhất lập Biên bản bàn giao theo những nội dung như trên và Biên bản được "
                    + "lập thành 02 bản, có giá trị như nhau, mỗi bên giữ 01 bản.",
                    ParagraphAlignment.BOTH, 12, false, false);
            blank(doc);

            // ===== Chữ ký: 3 cột =====
            XWPFTable sign = doc.createTable(2, 3);
            sign.setWidth("100%");
            sign.removeBorders();
            String[] signers = {"BÊN GIAO", "TRƯỞNG BỘ PHẬN", "BÊN NHẬN"};
            for (int i = 0; i < signers.length; i++) {
                cell(sign.getRow(0).getCell(i), signers[i], true, ParagraphAlignment.CENTER);
                cellItalic(sign.getRow(1).getCell(i), "(Kí, ghi rõ họ tên)");
            }

            doc.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    /**
     * Đầu trang Word (lặp lại trên mọi trang): logo bên trái, tên và địa chỉ công ty.
     * Không có file logo thì bỏ qua ảnh, phần chữ vẫn giữ nguyên.
     */
    private void writeCompanyHeader(XWPFDocument doc) {
        XWPFHeader header = doc.createHeader(HeaderFooterType.DEFAULT);

        // Bố cục như mẫu gốc: logo bên trái, tên + địa chỉ công ty bên phải (cùng hàng).
        // Dùng bảng 2 cột không viền để giữ vị trí cố định.
        XWPFTable t = header.createTable(1, 2);
        t.setWidth("100%");
        t.removeBorders();
        XWPFTableRow row = t.getRow(0);
        row.getCell(0).setWidth("25%");
        row.getCell(1).setWidth("75%");

        // Cột trái: logo, căn giữa theo chiều dọc
        XWPFTableCell logoCell = row.getCell(0);
        logoCell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
        XWPFParagraph logoPara = logoCell.getParagraphs().isEmpty()
                ? logoCell.addParagraph() : logoCell.getParagraphs().get(0);
        logoPara.setAlignment(ParagraphAlignment.LEFT);
        logoPara.setSpacingAfter(0);
        addLogo(logoPara);

        // Cột phải: tên công ty + địa chỉ, căn giữa
        XWPFTableCell textCell = row.getCell(1);
        textCell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
        headerLine(textCell, COMPANY_NAME, 12, true, true);
        headerLine(textCell, COMPANY_ADDRESS, 10, false, false);
    }

    /** Một dòng chữ căn giữa trong ô header. */
    private void headerLine(XWPFTableCell cell, String text, int size, boolean bold, boolean firstLine) {
        XWPFParagraph p = (firstLine && !cell.getParagraphs().isEmpty())
                ? cell.getParagraphs().get(0) : cell.addParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        p.setSpacingAfter(0);
        XWPFRun r = p.createRun();
        r.setFontFamily(FONT);
        r.setFontSize(size);
        r.setBold(bold);
        r.setText(text);
    }

    /**
     * Nhúng logo nếu có file trong resources. Lỗi đọc ảnh không được làm hỏng
     * cả biên bản — chỉ bỏ logo và đi tiếp.
     */
    private void addLogo(XWPFParagraph p) {
        ClassPathResource logo = new ClassPathResource(LOGO_PATH);
        if (!logo.exists()) {
            return;
        }
        try (InputStream in = logo.getInputStream()) {
            XWPFRun r = p.createRun();
            r.addPicture(in, XWPFDocument.PICTURE_TYPE_PNG, "logo.png",
                    Units.toEMU(LOGO_WIDTH_PT), Units.toEMU(LOGO_HEIGHT_PT));
        } catch (Exception e) {
            // Không có logo thì biên bản vẫn dùng được, chỉ thiếu ảnh
        }
    }

    /** Ghi một hàng dữ liệu của bảng tài sản. */
    private void writeRow(XWPFTableRow row, String[] values) {
        for (int i = 0; i < values.length; i++) {
            cell(row.getCell(i), values[i], false, ParagraphAlignment.CENTER);
        }
    }

    /**
     * Cột "Tên tài sản và phụ kiện kèm theo": ghép loại tài sản + nhà sản xuất
     * (ví dụ "Laptop DELL"). Thiếu dữ liệu thì lùi về tên danh mục.
     */
    private String assetName(Asset asset, AssetCategory category) {
        StringBuilder sb = new StringBuilder();
        if (notBlank(asset.getAssetType())) {
            sb.append(asset.getAssetType().trim());
        }
        if (notBlank(asset.getManufacturer())) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(asset.getManufacturer().trim());
        }
        if (sb.length() == 0 && category != null) {
            sb.append(category.getName());
        }
        return sb.toString();
    }

    /** Cột "Serial/ Mã tài sản": "CY75D24 / MDM-00112". Thiếu serial thì chỉ ghi mã. */
    private String serialAndCode(Asset asset) {
        String code = nvl(asset.getInventoryCode(), "");
        if (!notBlank(asset.getSerialNumber())) {
            return code;
        }
        return asset.getSerialNumber().trim() + " / " + code;
    }

    /** Thêm một đoạn văn với font / căn lề / in đậm / in nghiêng chỉ định. */
    private void para(XWPFDocument doc, String text, ParagraphAlignment align,
                      int size, boolean bold, boolean italic) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(align);
        // Giãn cách giống mẫu Word gốc: dòng 1.3, cách sau 6pt; tiêu đề mục cách trên 8pt
        p.setSpacingBetween(1.3, LineSpacingRule.AUTO);
        p.setSpacingAfter(120);
        if (bold) {
            p.setSpacingBefore(160);
        }
        XWPFRun r = p.createRun();
        r.setFontFamily(FONT);
        r.setFontSize(size);
        r.setBold(bold);
        r.setItalic(italic);
        writeMultiline(r, text);
    }

    /** Một dòng trống ngăn cách các khối. */
    private void blank(XWPFDocument doc) {
        para(doc, "", ParagraphAlignment.LEFT, 12, false, false);
    }

    /** Ghi nội dung vào một ô của bảng. */
    private void cell(XWPFTableCell tc, String text, boolean bold, ParagraphAlignment align) {
        XWPFRun r = cellRun(tc, align);
        r.setBold(bold);
        writeMultiline(r, text);
    }

    /** Ô chữ ký in nghiêng cỡ nhỏ, chừa khoảng trống bên dưới để ký tay. */
    private void cellItalic(XWPFTableCell tc, String text) {
        XWPFRun r = cellRun(tc, ParagraphAlignment.CENTER);
        r.setItalic(true);
        r.setFontSize(10);
        writeMultiline(r, text);
        for (int i = 0; i < 4; i++) {
            r.addBreak();
        }
    }

    /** Ô mới luôn có sẵn 1 paragraph rỗng — dùng lại thay vì tạo thêm. */
    private XWPFRun cellRun(XWPFTableCell tc, ParagraphAlignment align) {
        XWPFParagraph p = tc.getParagraphs().isEmpty() ? tc.addParagraph() : tc.getParagraphs().get(0);
        p.setAlignment(align);
        p.setSpacingAfter(0);
        XWPFRun r = p.createRun();
        r.setFontFamily(FONT);
        r.setFontSize(11);
        return r;
    }

    /**
     * POI không tự xuống dòng với "\n" — phải tách dòng và gọi addBreak().
     * setText() nối thêm vào run nên gọi nhiều lần là an toàn.
     */
    private void writeMultiline(XWPFRun r, String text) {
        String[] lines = nvl(text, "").split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) r.addBreak();
            r.setText(lines[i]);
        }
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String nvl(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
