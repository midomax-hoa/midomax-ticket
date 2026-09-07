-- License phần mềm ngoài Microsoft 365 (Adobe, antivirus...) — admin/IT tạo tay.
-- Xem SoftwareLicense.java; license_365 vẫn nằm riêng vì đồng bộ từ Microsoft.
CREATE TABLE software_license (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(200) NOT NULL,
    vendor        VARCHAR(200) NULL,
    purchased_qty INT NULL DEFAULT 0,
    assigned_qty  INT NULL DEFAULT 0,
    expiry_date   DATE NULL,
    note          VARCHAR(500) NULL,
    updated_at    DATETIME NULL,
    updated_by    VARCHAR(150) NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
