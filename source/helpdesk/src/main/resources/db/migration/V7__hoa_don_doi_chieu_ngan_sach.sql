-- Nối sổ hóa đơn với quỹ chi tiêu: mỗi hóa đơn được đối chiếu về đúng một hạng mục
-- ngân sách (budget_items) để khấu trừ. Hóa đơn chưa đối chiếu thì budget_item_id NULL.
ALTER TABLE `invoice_entries` ADD COLUMN `fund_id` BIGINT NULL;
ALTER TABLE `invoice_entries` ADD COLUMN `budget_item_id` BIGINT NULL;

CREATE INDEX `idx_invoice_entries_budget_item` ON `invoice_entries` (`budget_item_id`);
CREATE INDEX `idx_invoice_entries_fund` ON `invoice_entries` (`fund_id`);
