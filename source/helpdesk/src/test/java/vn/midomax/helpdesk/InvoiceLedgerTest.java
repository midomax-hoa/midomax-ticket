package vn.midomax.helpdesk;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bảo vệ Sổ Hóa Đơn — trọng tâm là khâu nhập file Excel đang dùng thủ công.
 *
 * File thật KHÔNG có tiêu đề ở dòng đầu: phía trên còn dòng ghi chú tiền tệ và dòng
 * TỔNG SỐ MỤC / TỔNG SỐ TIỀN. Tiêu đề lại song ngữ xuống dòng ("Ngày nhập\nEntry
 * Date"), giá trị cũng song ngữ ("Định kỳ | Recurring"). Test dựng lại đúng bố cục
 * đó để khỏi phải có file thật mới kiểm chứng được.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InvoiceLedgerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InvoiceEntryRepository entryRepository;

    private static final String CATEGORY = "CAPEX — Thiết bị & Bản quyền | Hardware & Licenses";

    /** Dựng file Excel mô phỏng đúng bảng phiếu chi đang dùng. */
    private MockMultipartFile buildExcel() throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Chi tieu");

            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(wb.createDataFormat().getFormat("dd/mm/yyyy"));

            // --- phần đầu file: ghi chú + dòng tổng, KHÔNG phải tiêu đề bảng ---
            sheet.createRow(0).createCell(0)
                    .setCellValue("Tiền tệ: VND | Currency: VND — Nhập chi tiêu bên dưới, KHÔNG sửa dòng của người khác");
            Row totalRow = sheet.createRow(2);
            totalRow.createCell(0).setCellValue("TỔNG SỐ MỤC | TOTAL ENTRIES:");
            totalRow.createCell(1).setCellValue(35);
            totalRow.createCell(3).setCellValue("TỔNG SỐ TIỀN | TOTAL AMOUNT:");
            totalRow.createCell(4).setCellValue(1386514825L);

            // --- tiêu đề bảng thật, song ngữ xuống dòng, nằm ở dòng 4 ---
            String[] headers = {
                    "STT\nNo.", "Ngày nhập\nEntry Date", "Tháng/Kỳ\nMonth/Period", "Ngày GD\nTrans. Date",
                    "Số PO/Ref\nPO/Ref No.", "Danh mục\nCategory", "Phân loại nhỏ\nSub-Category",
                    "NCC/Người nhận\nVendor/Payee", "Mô tả chi tiết\nDescription", "Số tiền (VNĐ)\nAmount (VND)",
                    "Loại chi\nExpense Type", "TT Thanh toán\nPayment Status", "Người nhập\nEntered By", "Ghi chú\nNotes"
            };
            Row headerRow = sheet.createRow(4);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            // --- dữ liệu ---
            addRow(sheet, dateStyle, 5, 39, LocalDate.of(2026, 6, 12), LocalDate.of(2026, 6, 15),
                    "PC_2398_06_2026", "Trang Thiết Bị", "Nam Tiến Anh",
                    "Chi phí 4 tai nghe Micro không dây cho Marketing",
                    5_580_000L, "Một lần | One-time", "Đã thanh toán | Paid");

            addRow(sheet, dateStyle, 6, 40, LocalDate.of(2026, 6, 12), LocalDate.of(2026, 6, 15),
                    "PC_2397_06_2026", "Trang Thiết Bị", "CÔNG TY TNHH MYACC",
                    "Phần mềm So1Design - Gói ChatGPT Plus Dùng riêng - Cho IT",
                    2_320_000L, "Một lần | One-time", "Đã thanh toán | Paid");

            // Hai dòng Adobe: TRÙNG nhà cung cấp + số tiền + mô tả, chỉ khác ngày GD.
            addRow(sheet, dateStyle, 7, 41, LocalDate.of(2026, 6, 23), LocalDate.of(2026, 6, 23),
                    "PC_2425_06_2026", "Trang Thiết Bị", "CÔNG TY TNHH PACISOFT VIETNAM",
                    "Thanh toán đợt 1 tài khoản Adobe cho MKT-R&D-Ecom",
                    108_150_000L, "Định kỳ | Recurring", "Đã thanh toán | Paid");

            addRow(sheet, dateStyle, 8, 42, LocalDate.of(2026, 6, 29), LocalDate.of(2026, 6, 29),
                    "PC_2446_06_2026", "Trang Thiết Bị", "CÔNG TY TNHH PACISOFT VIETNAM",
                    "Thanh toán đợt 1 tài khoản Adobe cho MKT-R&D-Ecom",
                    108_150_000L, "Định kỳ | Recurring", "Đã thanh toán | Paid");

            // Dòng rác không có số tiền -> phải bị bỏ qua
            Row junk = sheet.createRow(9);
            junk.createCell(8).setCellValue("Ghi chú linh tinh không phải khoản chi");

            wb.write(out);
            return new MockMultipartFile("file", "chi-tieu.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    private void addRow(Sheet sheet, CellStyle dateStyle, int rowIdx, int stt,
                        LocalDate entryDate, LocalDate transDate, String poRef, String subCategory,
                        String vendor, String description, long amount, String type, String status) {
        Row r = sheet.createRow(rowIdx);
        r.createCell(0).setCellValue(stt);

        Cell entry = r.createCell(1);
        entry.setCellValue(entryDate);
        entry.setCellStyle(dateStyle);

        r.createCell(2).setCellValue("Jun 2026 | T6/2026");

        Cell trans = r.createCell(3);
        trans.setCellValue(transDate);
        trans.setCellStyle(dateStyle);

        r.createCell(4).setCellValue(poRef);
        r.createCell(5).setCellValue(CATEGORY);
        r.createCell(6).setCellValue(subCategory);
        r.createCell(7).setCellValue(vendor);
        r.createCell(8).setCellValue(description);
        r.createCell(9).setCellValue(amount);
        r.createCell(10).setCellValue(type);
        r.createCell(11).setCellValue(status);
        r.createCell(12).setCellValue("Tùng");
    }

    @Test
    void nhapDuocFileExcelCoDongTongVaTieuDeSongNgu() throws Exception {
        ExcelService.InvoiceImportResult result = new ExcelService().importInvoiceEntriesFromExcel(buildExcel());

        assertThat(result.hasError()).as("không được lỗi: %s", result.getError()).isFalse();
        assertThat(result.getEntries())
                .as("phải nhận đúng 4 khoản chi, bỏ qua dòng ghi chú không có tiền")
                .hasSize(4);

        InvoiceEntry adobe = result.getEntries().stream()
                .filter(e -> e.getVendor().contains("PACISOFT"))
                .findFirst().orElseThrow();

        assertThat(adobe.getAmount()).isEqualTo(108_150_000L);
        assertThat(adobe.getExpenseType()).as("\"Định kỳ | Recurring\" phải ra RECURRING")
                .isEqualTo(InvoiceEntry.TYPE_RECURRING);
        assertThat(adobe.getPaymentStatus()).as("\"Đã thanh toán | Paid\" phải ra PAID")
                .isEqualTo(InvoiceEntry.STATUS_PAID);
        assertThat(adobe.getPeriodKey()).as("kỳ kế toán lấy theo NGÀY GD").isEqualTo("2026-06");
        assertThat(adobe.getCategory()).isEqualTo(CATEGORY);
        assertThat(adobe.getSubCategory()).isEqualTo("Trang Thiết Bị");
        assertThat(adobe.getEnteredBy()).isEqualTo("Tùng");
    }

    /**
     * Hai lần thanh toán Adobe trùng tiền và trùng mô tả, chỉ khác ngày giao dịch —
     * đây là thanh toán nhiều đợt thật, tuyệt đối không được gộp thành một.
     */
    @Test
    void haiDotThanhToanCungSoTienKhacNgayVanGiuDuCaHai() throws Exception {
        ExcelService.InvoiceImportResult result = new ExcelService().importInvoiceEntriesFromExcel(buildExcel());

        List<InvoiceEntry> pacisoft = result.getEntries().stream()
                .filter(e -> e.getVendor().contains("PACISOFT"))
                .toList();

        assertThat(pacisoft).hasSize(2);
        assertThat(pacisoft).extracting(InvoiceEntry::getTransDate)
                .containsExactlyInAnyOrder(LocalDate.of(2026, 6, 23), LocalDate.of(2026, 6, 29));
    }

    @Test
    void baoLoiRoRangKhiFileKhongPhaiSoHoaDon() {
        // File chỉ có chữ linh tinh, không có cột nào khớp
        MockMultipartFile bad = new MockMultipartFile("file", "sai.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "khong phai excel".getBytes());

        ExcelService.InvoiceImportResult result = new ExcelService().importInvoiceEntriesFromExcel(bad);

        assertThat(result.hasError()).isTrue();
        assertThat(result.getEntries()).isEmpty();
    }

    private DefaultOidcUser principal(String role) {
        OidcIdToken idToken = new OidcIdToken("t", Instant.now(), Instant.now().plusSeconds(3600),
                Map.of("sub", "s", "name", "Nguoi Kiem Thu", "email", "kiemthu@midomax.vn"));
        return new DefaultOidcUser(List.of(new SimpleGrantedAuthority(role)), idToken, "name");
    }

    /**
     * Trang phải render TRỌN VẸN. Lỗi Thymeleaf giữa trang vẫn trả HTTP 200 với trang
     * cắt cụt, nên phải kiểm tra thứ nằm CUỐI trang (hàm openEdit và modal nhập Excel).
     */
    @Test
    void trangSoHoaDonRenderDuChoAdmin() throws Exception {
        var result = mockMvc.perform(get("/expenses/invoices")
                        .with(oidcLogin().oidcUser(principal("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andReturn();

        String html = result.getResponse().getContentAsString();
        assertThat(html).contains("Sổ Hóa Đơn");
        assertThat(html).as("modal nhập Excel nằm cuối trang — mất nghĩa là template vỡ giữa chừng")
                .contains("id=\"importModal\"")
                .contains("function openEdit");
    }

    @Test
    void userThuongKhongVaoDuocSoHoaDon() throws Exception {
        mockMvc.perform(get("/expenses/invoices")
                        .with(oidcLogin().oidcUser(principal("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void phanTrangSoHoaDon5DongMoiTrang() throws Exception {
        entryRepository.deleteAll();
        // Tạo 7 hóa đơn
        for (int i = 1; i <= 7; i++) {
            InvoiceEntry e = new InvoiceEntry();
            e.setTransDate(LocalDate.now());
            e.setEntryDate(LocalDate.now());
            e.setDescription("Hóa đơn test " + i);
            e.setAmount(100000L * i);
            e.setPeriodKey("2026-08");
            entryRepository.save(e);
        }

        var result = mockMvc.perform(get("/expenses/invoices")
                        .with(oidcLogin().oidcUser(principal("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andReturn();

        @SuppressWarnings("unchecked")
        List<InvoiceEntry> entries = (List<InvoiceEntry>) result.getModelAndView().getModel().get("entries");
        assertThat(entries).hasSize(5);
        assertThat(result.getModelAndView().getModel().get("totalPages")).isEqualTo(2);
        assertThat(result.getModelAndView().getModel().get("entryCount")).isEqualTo(7);
    }
}
