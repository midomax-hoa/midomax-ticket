package vn.midomax.helpdesk;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AssetExportTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private ExcelService excelService;

    private DefaultOidcUser principal(String role) {
        OidcIdToken idToken = new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(3600),
                Map.of("sub", "sub", "name", "Tester", "email", "test@midomax.vn"));
        return new DefaultOidcUser(List.of(new SimpleGrantedAuthority(role)), idToken, "name");
    }

    @Test
    void testExportAssetsToExcelDirect() throws Exception {
        Asset a1 = new Asset();
        a1.setInventoryCode("CCDC-001");
        a1.setAssetType("Laptop");
        a1.setManufacturer("Dell");
        a1.setModel("Latitude 5420");
        a1.setSerialNumber("SN-DELL-123");
        a1.setQuantity(1);
        a1.setAssignedToName("Nguyễn Văn A");
        a1.setStatus("Đang sử dụng");
        a1.setPurchaseDate(LocalDate.of(2026, 1, 15));
        assetRepository.save(a1);

        Asset a2 = new Asset();
        a2.setInventoryCode("CCDC-002");
        a2.setAssetType("Màn hình");
        a2.setManufacturer("LG");
        a2.setModel("27UK850");
        a2.setQuantity(2);
        a2.setStatus("Trong kho");
        assetRepository.save(a2);

        List<Asset> assets = List.of(a1, a2);
        ByteArrayInputStream stream = excelService.exportAssetsToExcel(assets, "Tất cả", "Tất cả", null, java.util.Collections.emptyMap());

        assertThat(stream).isNotNull();
        try (Workbook wb = new XSSFWorkbook(stream)) {
            Sheet sheet = wb.getSheet("Danh Sách Tài Sản");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).contains("BÁO CÁO QUẢN LÝ CÔNG CỤ DỤNG CỤ");
            assertThat(sheet.getRow(4).getCell(1).getStringCellValue()).isEqualTo("Mã kiểm kê");
            assertThat(sheet.getRow(5).getCell(1).getStringCellValue()).isEqualTo("CCDC-001");
            assertThat(sheet.getRow(6).getCell(1).getStringCellValue()).isEqualTo("CCDC-002");
        }
    }

    @Test
    void testExportAssetsEndpoint() throws Exception {
        mockMvc.perform(get("/assets/export")
                        .with(oidcLogin().oidcUser(principal("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("CongCuDungCu_Midomax_")));
    }
}
