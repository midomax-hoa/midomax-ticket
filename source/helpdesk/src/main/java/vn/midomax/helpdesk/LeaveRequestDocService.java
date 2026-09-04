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
import java.time.format.DateTimeFormatter;

/**
 * Sinh phiếu .docx cho đơn nghỉ phép / bổ sung công, dựng theo đúng hai mẫu Word
 * của công ty để nhân sự xem và in:
 *
 *   - PHIẾU ĐĂNG KÝ NGHỈ            (QĐ-NSHT.MDM-03.03)
 *   - GIẤY BỔ SUNG THÔNG TIN CHẤM CÔNG (QĐ-NSHT.MDM-03.01)
 *
 * Ô chữ ký để trống cho người ký tay sau khi in; phần đã duyệt trên hệ thống
 * được ghi chú bên dưới để đối chiếu.
 */
@Service
public class LeaveRequestDocService {

    private static final String FONT = "Times New Roman";
    private static final String COMPANY_NAME = "CÔNG TY CỔ PHẦN\nMIDOMAX VIỆT NAM";
    private static final String NATION = "CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM";
    private static final String MOTTO = "Độc lập – Tự do – Hạnh phúc";
    private static final String LOGO_PATH = "static/images/logo-midomax.png";
    private static final int LOGO_WIDTH_PT = 110;
    private static final int LOGO_HEIGHT_PT = 26;

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public ByteArrayInputStream build(LeaveRequest req) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            boolean isLeave = LeaveRequest.TYPE_LEAVE.equals(req.getType());
            writeHeader(doc, isLeave ? "QĐ-NSHT.MDM-03.03" : "QĐ-NSHT.MDM-03.01",
                    isLeave ? "PHIẾU ĐĂNG KÝ NGHỈ" : "GIẤY BỔ SUNG THÔNG TIN CHẤM CÔNG");

            if (isLeave) {
                writeLeaveBody(doc, req);
            } else {
                writeSupplementBody(doc, req);
            }

            writeSignatures(doc, req, isLeave);
            writeApprovalTrace(doc, req);

            doc.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    /** Tên file tải về, không dấu để mọi trình duyệt đều mở được. */
    public String fileName(LeaveRequest req) {
        String who = req.getRequesterFullName() != null ? req.getRequesterFullName() : req.getRequesterName();
        String prefix = LeaveRequest.TYPE_LEAVE.equals(req.getType()) ? "PhieuDangKyNghi" : "GiayBoSungCong";
        return prefix + "_" + noAccent(who) + "_" + req.getRequestDate() + ".docx";
    }

    // ===== Đầu trang =====

    private void writeHeader(XWPFDocument doc, String formCode, String title) {
        XWPFHeader header = doc.createHeader(HeaderFooterType.DEFAULT);
        XWPFTable t = header.createTable(1, 2);
        t.setWidth("100%");
        removeBorders(t);
        XWPFTableRow row = t.getRow(0);

        XWPFParagraph logoPara = firstParagraph(row.getCell(0));
        logoPara.setAlignment(ParagraphAlignment.LEFT);
        addLogo(logoPara);

        XWPFParagraph codePara = firstParagraph(row.getCell(1));
        codePara.setAlignment(ParagraphAlignment.RIGHT);
        run(codePara, formCode, 10, false, false);

        // Khối quốc hiệu: tên công ty bên trái, quốc hiệu bên phải
        XWPFTable head = doc.createTable(1, 2);
        head.setWidth("100%");
        removeBorders(head);
        XWPFTableRow hr = head.getRow(0);

        XWPFParagraph left = firstParagraph(hr.getCell(0));
        left.setAlignment(ParagraphAlignment.CENTER);
        for (String line : COMPANY_NAME.split("\n")) {
            run(left, line, 12, true, false);
            addBreak(left);
        }

        XWPFParagraph right = firstParagraph(hr.getCell(1));
        right.setAlignment(ParagraphAlignment.CENTER);
        run(right, NATION, 12, true, false);
        addBreak(right);
        run(right, MOTTO, 12, true, true);

        XWPFParagraph place = doc.createParagraph();
        place.setAlignment(ParagraphAlignment.RIGHT);
        LocalDate now = LocalDate.now();
        run(place, "TP.HCM, ngày " + now.getDayOfMonth() + " tháng " + now.getMonthValue()
                + " năm " + now.getYear(), 12, false, true);

        XWPFParagraph titlePara = doc.createParagraph();
        titlePara.setAlignment(ParagraphAlignment.CENTER);
        titlePara.setSpacingBefore(200);
        run(titlePara, title, 16, true, false);
    }

    // ===== Thân phiếu đăng ký nghỉ =====

    private void writeLeaveBody(XWPFDocument doc, LeaveRequest req) {
        XWPFParagraph to = doc.createParagraph();
        to.setSpacingBefore(200);
        run(to, "Kính gửi: ", 12, false, true);
        indented(doc, "BGĐ CÔNG TY", true);
        indented(doc, "Quản lý BP/ Phòng ban: " + orDash(req.getDepartment()), true);
        indented(doc, "Phòng Nhân sự hệ thống.", true);

        line(doc, "Họ và Tên: " + orDash(displayName(req))
                + "                    Chức danh: " + dots(20));
        line(doc, "Bộ phận: " + orDash(req.getDepartment())
                + "                          SĐT liên lạc: " + orDash(req.getPhone()));
        line(doc, "Nay tôi viết đơn này xin Ban Giám Đốc và Quản lý BP/ Phòng ban cho tôi được nghỉ phép:");

        // Bốn ô loại phép, ô đang chọn đánh dấu X
        XWPFTable box = doc.createTable(2, 2);
        box.setWidth("100%");
        removeBorders(box);
        String lt = req.getLeaveType() == null ? "NAM" : req.getLeaveType();
        checkbox(box.getRow(0).getCell(0), "Phép theo chế độ", "CHE_DO".equals(lt));
        checkbox(box.getRow(0).getCell(1), "Phép năm", "NAM".equals(lt));
        checkbox(box.getRow(1).getCell(0), "Phép chính sách BHXH", "BHXH".equals(lt));
        checkbox(box.getRow(1).getCell(1), "Nghỉ không lương", "KHONG_LUONG".equals(lt));

        line(doc, "Lý do: " + orDash(req.getReason()));
        line(doc, "Từ ngày: " + req.getRequestDate().format(D)
                + "            Đến hết ngày: " + req.getEffectiveToDate().format(D));
        line(doc, "Tổng số ngày nghỉ: " + fmtDays(req.getTotalDays()));
        line(doc, "Người hỗ trợ công việc: " + orDash(req.getSupporter())
                + "            Vị trí: " + dots(15));
        line(doc, "        Rất mong nhận được sự chấp thuận từ BGĐ và cấp Quản lý.");
        line(doc, "        Xin chân thành cảm ơn!");
    }

    // ===== Thân giấy bổ sung công =====

    private void writeSupplementBody(XWPFDocument doc, LeaveRequest req) {
        XWPFParagraph to = doc.createParagraph();
        to.setAlignment(ParagraphAlignment.CENTER);
        run(to, "Kính gửi: Trưởng BP/ Phòng ban " + orDash(req.getDepartment()), 12, false, false);
        addBreak(to);
        run(to, "Phòng NSHT phụ trách chấm công", 12, false, true);

        line(doc, "Người đề nghị: " + orDash(displayName(req))
                + "                    Chức danh: " + dots(20));
        line(doc, "Bộ phận: " + orDash(req.getDepartment()));
        line(doc, "Đề nghị bổ sung thông tin chấm công:");

        XWPFTable t = doc.createTable(3, 5);
        t.setWidth("100%");
        String[] headers = {"STT", "Từ", "Đến", "Số thời gian", "Lý do"};
        for (int i = 0; i < headers.length; i++) {
            cell(t.getRow(0).getCell(i), headers[i], true, ParagraphAlignment.CENTER);
        }
        String[] data = {"1", req.getRequestDate().format(D), req.getEffectiveToDate().format(D),
                req.getDurationLabel(), orDash(req.getReason())};
        for (int i = 0; i < data.length; i++) {
            cell(t.getRow(1).getCell(i), data[i], false,
                    i == 4 ? ParagraphAlignment.LEFT : ParagraphAlignment.CENTER);
        }
        cell(t.getRow(2).getCell(0), "Tổng cộng", true, ParagraphAlignment.CENTER);
        cell(t.getRow(2).getCell(3), fmtDays(req.getTotalDays()), true, ParagraphAlignment.CENTER);

        XWPFParagraph note = doc.createParagraph();
        note.setSpacingBefore(200);
        run(note, "Lưu ý:", 11, true, true);
        line(doc, " - Sử dụng trong trường hợp mất điện, máy chấm công hỏng, ra ngoài làm việc đầu và cuối giờ,"
                + " quên chấm công.", 10, true);
        line(doc, " - CBNV có trách nhiệm chuyển cho phòng HCNS giấy bổ sung thông tin chấm công vào ngày đi"
                + " làm kế tiếp để được ghi nhận thời gian làm việc.", 10, true);
    }

    // ===== Ô chữ ký =====

    private void writeSignatures(XWPFDocument doc, LeaveRequest req, boolean isLeave) {
        XWPFParagraph gap = doc.createParagraph();
        gap.setSpacingBefore(300);

        String[] titles = isLeave
                ? new String[]{"Người viết đơn", "Trưởng bộ phận", "Ban lãnh đạo công ty", "Bộ phận nhân sự"}
                : new String[]{"Người đề nghị", "Trưởng bộ phận", "Phòng NSHT"};

        XWPFTable sign = doc.createTable(2, titles.length);
        sign.setWidth("100%");
        removeBorders(sign);
        for (int i = 0; i < titles.length; i++) {
            XWPFParagraph p = firstParagraph(sign.getRow(0).getCell(i));
            p.setAlignment(ParagraphAlignment.CENTER);
            run(p, titles[i], 12, true, false);
            addBreak(p);
            run(p, "(Ký, ghi rõ họ tên)", 10, false, true);

            // Chừa khoảng trống ký tay, riêng cột đầu ghi sẵn tên người làm đơn
            XWPFParagraph name = firstParagraph(sign.getRow(1).getCell(i));
            name.setAlignment(ParagraphAlignment.CENTER);
            name.setSpacingBefore(700);
            run(name, i == 0 ? orDash(displayName(req)) : "", 12, true, true);
        }
    }

    /** Vết duyệt trên hệ thống, để nhân sự đối chiếu với chữ ký giấy. */
    private void writeApprovalTrace(XWPFDocument doc, LeaveRequest req) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(400);
        run(p, "Ghi nhận trên hệ thống Helpdesk Midomax:", 10, true, true);

        StringBuilder sb = new StringBuilder();
        sb.append("Trạng thái: ").append(req.getStatusLabel());
        if (req.getHeadBy() != null) sb.append("  •  Trưởng bộ phận duyệt: ").append(req.getHeadBy());
        if (req.getHrBy() != null) sb.append("  •  Phòng NSHT duyệt: ").append(req.getHrBy());
        if (req.getRejectBy() != null) {
            sb.append("  •  Từ chối bởi: ").append(req.getRejectBy());
            if (req.getRejectReason() != null) sb.append(" (").append(req.getRejectReason()).append(")");
        }
        line(doc, sb.toString(), 10, true);
    }

    // ===== Tiện ích dựng Word =====

    private void line(XWPFDocument doc, String text) {
        line(doc, text, 12, false);
    }

    private void line(XWPFDocument doc, String text, int size, boolean italic) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingAfter(60);
        XWPFRun r = p.createRun();
        r.setFontFamily(FONT);
        r.setFontSize(size);
        r.setItalic(italic);
        r.setText(text);
    }

    private void indented(XWPFDocument doc, String text, boolean bold) {
        XWPFParagraph p = doc.createParagraph();
        p.setIndentationLeft(1200);
        p.setSpacingAfter(40);
        XWPFRun r = p.createRun();
        r.setFontFamily(FONT);
        r.setFontSize(12);
        r.setBold(bold);
        r.setText(text);
    }

    private void checkbox(XWPFTableCell cellRef, String label, boolean checked) {
        XWPFParagraph p = firstParagraph(cellRef);
        XWPFRun r = p.createRun();
        r.setFontFamily(FONT);
        r.setFontSize(12);
        r.setBold(checked);
        r.setText((checked ? "[ X ]  " : "[    ]  ") + label);
    }

    private void cell(XWPFTableCell cellRef, String text, boolean bold, ParagraphAlignment align) {
        XWPFParagraph p = firstParagraph(cellRef);
        p.setAlignment(align);
        XWPFRun r = p.createRun();
        r.setFontFamily(FONT);
        r.setFontSize(11);
        r.setBold(bold);
        r.setText(text == null ? "" : text);
    }

    private XWPFParagraph firstParagraph(XWPFTableCell cellRef) {
        return cellRef.getParagraphs().isEmpty() ? cellRef.addParagraph() : cellRef.getParagraphs().get(0);
    }

    private void run(XWPFParagraph p, String text, int size, boolean bold, boolean last) {
        XWPFRun r = p.createRun();
        r.setFontFamily(FONT);
        r.setFontSize(size);
        r.setBold(bold);
        r.setText(text == null ? "" : text);
        if (!last) {
            // để nơi gọi tự quyết định xuống dòng
        }
    }

    private void addBreak(XWPFParagraph p) {
        if (!p.getRuns().isEmpty()) {
            p.getRuns().get(p.getRuns().size() - 1).addBreak();
        }
    }

    private void removeBorders(XWPFTable t) {
        t.setInsideHBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        t.setInsideVBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        t.setTopBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        t.setBottomBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        t.setLeftBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        t.setRightBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
    }

    private void addLogo(XWPFParagraph p) {
        ClassPathResource logo = new ClassPathResource(LOGO_PATH);
        if (!logo.exists()) return;
        try (InputStream in = logo.getInputStream()) {
            XWPFRun r = p.createRun();
            r.addPicture(in, XWPFDocument.PICTURE_TYPE_PNG, "logo.png",
                    Units.toEMU(LOGO_WIDTH_PT), Units.toEMU(LOGO_HEIGHT_PT));
        } catch (Exception e) {
            // Thiếu logo thì phiếu vẫn dùng được
        }
    }

    // ===== Tiện ích dữ liệu =====

    private String displayName(LeaveRequest req) {
        return req.getRequesterFullName() != null && !req.getRequesterFullName().isBlank()
                ? req.getRequesterFullName() : req.getRequesterName();
    }

    private String fmtDays(Double days) {
        if (days == null) return "1";
        return days == Math.floor(days) ? String.valueOf(days.intValue())
                : String.valueOf(days).replace('.', ',');
    }

    private String orDash(String s) {
        return s == null || s.isBlank() ? "................" : s;
    }

    private String dots(int n) {
        return ".".repeat(Math.max(4, n));
    }

    private String noAccent(String s) {
        if (s == null) return "NhanVien";
        String norm = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace('đ', 'd').replace('Đ', 'D');
        return norm.replaceAll("[^a-zA-Z0-9]", "_");
    }
}
