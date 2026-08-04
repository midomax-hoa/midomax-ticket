package vn.midomax.helpdesk;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Sinh biên bản bàn giao mẫu ra file để xem thử định dạng (không assert nội dung). */
class HandoverDocPreviewTest {

    @Test
    void generatePreview() throws Exception {
        Asset a = new Asset();
        a.setAssetType("Laptop");
        a.setManufacturer("Lenovo");
        a.setDetails("ThinkPad T14, i5-1335U, 16GB RAM, 512GB SSD");
        a.setSerialNumber("PF4X8Z1");
        a.setInventoryCode("MDM-00274");
        a.setInventoryCondition("Mới");
        a.setAssignedToName("Nguyễn Văn A");
        a.setAssignedToPosition("Nhân viên");
        a.setAssignedToDepartment("CNTT");

        List<AssetHandoverService.Accessory> accs = List.of(
                new AssetHandoverService.Accessory("Chuột không dây", "Logitech M331", "SN-M331-01", "1", "Mới"));

        AssetHandoverService service = new AssetHandoverService();
        var in = service.buildHandoverDoc(List.of(a), Map.of(), accs);

        Path out = Path.of(System.getProperty("preview.out", "target/bienban_preview.docx"));
        Files.write(out, in.readAllBytes());
        System.out.println("Saved: " + out.toAbsolutePath());
    }
}
