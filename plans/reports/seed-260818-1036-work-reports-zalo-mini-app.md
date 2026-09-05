# Seed dữ liệu Báo cáo công việc (cha – con) — dự án "Zalo Mini App"

> **CẢNH BÁO:** File này nằm trong thư mục `plans/` đang được git theo dõi.
> KHÔNG ghi password DB vào file này và KHÔNG commit thông tin kết nối đầy đủ lên repo.

## Bối cảnh

- Thực hiện ngày **18/08/2026**. Trước đó 3 bảng `work_reports`, `work_subtasks`, `work_comments` đã bị xoá sạch (kiểm chứng: `SELECT COUNT(*)` = 0 cả 3 bảng), nên seed lại từ id = 1.
- Seed **62 báo cáo công việc** (10 giai đoạn cha + 52 công việc con), **323 việc nhỏ** (checklist) và **52 bình luận** (toàn bộ do `dev03` viết).
- Người phụ trách (`assignee` + `created_by`): `dev03` — Trần Nhơn Hòa (ROLE_IT). Người theo dõi: `dev01`, `dev02`, `trinhltt`, một số giai đoạn thêm `tungnd` / `tinnt`.
- Timeline **04/05/2026 → 21/08/2026**. Nội dung dựng từ lịch sử git thật của 2 repo:
  - `D:\Project\zbs-zma\midomaxZNS-system` (API NestJS + trang quản trị) — các module `haravan`, `haravan-webhook`, `collections`, `inventory`, `loyalty`, `miniapp`, `miniapp-checkout`, `promotion-gifts`, `san-points`, `sync`, `tracking`, `zma`, `zma-categories`, `zma-popup`
  - `D:\Project\zbs-zma\ZMA` (mini app phía khách)
  - Lưu ý: mỗi thư mục con là 1 repo riêng, `D:\Project\zbs-zma` không phải git root.

## Kết nối DB test

- Host `42.96.59.208`, port `33446`, database/user `midomaxticket`.
- Password: dùng password DB test đã được cung cấp riêng (KHÔNG lưu trong repo).
- Máy không có `mysql` CLI — kết nối bằng Node + gói `mysql2`. Password chứa ký tự `@` nên phải truyền theo từng field, **không** parse từ URL.

```js
// npm install mysql2
const mysql = require('mysql2/promise');
const conn = await mysql.createConnection({
  host: '42.96.59.208', port: 33446,
  user: 'midomaxticket', password: '<password DB test>',
  database: 'midomaxticket', charset: 'utf8mb4',
});
```

## Cấu trúc 3 tầng

```
Giai đoạn (work_reports, parent_id = NULL)              10 dòng, id 1–10
  └── Công việc con (work_reports, parent_id = id cha)   52 dòng, id 11–62
        └── Việc nhỏ / checklist (work_subtasks)         323 dòng
Bình luận (work_comments) gắn ở cả cha lẫn con           52 dòng (chỉ dev03)
```

## Nguyên tắc sắp lịch (quan trọng nhất)

1. **Lịch chạy trên ngày làm việc**: 04/05 → 21/08/2026 có đúng **80 ngày làm việc** (bỏ thứ bảy, chủ nhật). Không công việc nào bắt đầu hay tới hạn vào cuối tuần.
2. **10 giai đoạn là 10 khối liền mạch, không chồng lấn**: giai đoạn sau bắt đầu sau khi giai đoạn trước đã tới hạn. Phân bổ 8 / 9 / 8 / 8 / 8 / 8 / 9 / 8 / 8 / 6 = 80 ngày làm việc.
3. **Ngày của việc cha luôn ăn theo việc con** — không đặt tay:
   - `start_date` cha = `start_date` của việc con **sớm nhất**
   - `due_date` cha = `due_date` của việc con **trễ nhất**
   - `completed_at` cha = `completed_at` của việc con **hoàn thành sau cùng** (chỉ có khi mọi việc con đã xong)
4. **Đọc từ mới nhất về cũ nhất**: app sắp danh sách theo `updated_at` giảm dần (`WorkReportRepository.findAllByOrderByUpdatedAtDesc`). Vì các giai đoạn không chồng lấn nên thứ tự hiển thị là **#10 (18/08) → #1 (13/05)**, đúng chiều 21/08 → 04/05.
5. **Trong mỗi giai đoạn, việc con hiển thị theo `created_at` tăng dần** (`findByParentIdOrderByCreatedAtAsc`). Ngày bắt đầu và hạn chót của việc con đều tăng dần theo thứ tự đó; hai việc con cùng ngày bắt đầu thì việc đứng trước có giờ sớm hơn — nên thứ tự hiển thị luôn trùng thứ tự id.

## Quy ước phải tuân theo (đọc từ code helpdesk)

1. `assignee` / `created_by` / `watchers` lưu **handle** = phần trước dấu `@` của email, viết thường (`WorkReportController.handleOf`). Bảng user tên `app_users` (không phải `users`).
2. `status`: PLANNING / PROGRESS / COMPLETED. `priority`: URGENT / HIGH / NORMAL / LOW.
3. SLA chấm theo `completed_at` so với **hạn hiệu lực**: việc con ăn theo `due_date` của việc cha (`WorkReport.getEffectiveDueDate`). Yêu cầu đã chốt: **không báo cáo nào trễ hẹn** → mọi dòng đã xong đều có `completed_at <= due_date của cha`, và `delay_reason = NULL` toàn bộ.
4. `delay_reason` **cố tình để NULL**: template `work-reports.html` (dòng 665–668) tô đỏ dòng ghi chú và gắn nhãn "Trễ hạn" ngay khi field này khác rỗng. Điền vào sẽ mâu thuẫn với yêu cầu không trễ hẹn.
5. Tránh để tiến độ đúng **90%** khi `owner_confirmed = 0` — đó là trạng thái "chờ chủ báo cáo xác nhận" (`WorkReport.AWAITING_CONFIRM_PROGRESS`).
6. Tiến độ việc cha phải khớp công thức `WorkReportServiceImpl.recalculateParentProgress`: trung bình tiến độ của (việc con + checklist), `owner_confirmed = 1` thì ép về 100. Nếu để lệch, app sẽ tự ghi đè khi có ai tick checklist.
7. Tiến độ việc con phải khớp tỷ lệ checklist đã tick, cùng công thức trên.
8. Không mốc thời gian nào được nằm ở tương lai so với thời điểm seed: `created_at`, `completed_at`, `timer_started_at`, ngày tạo / tick việc nhỏ, ngày bình luận. Riêng `start_date` và `due_date` của việc còn dở thì được phép ở tương lai.
9. Từ ngữ viết cho **leader không chuyên kỹ thuật** đọc hiểu ngay: nói kết quả cho khách / công ty, không dùng jargon dev (webhook, idempotency, mint, rate limit...).
10. Giờ giấc rải tự nhiên: bắt đầu 8h–10h lẻ phút, hạn chót 17h–18h, hoàn thành 11h–16h; việc nhỏ tick rải dọc thời gian làm.
11. Mỗi công việc con có **5–7 việc nhỏ** trong `work_subtasks`.
12. **Không mốc thời gian nào rơi vào thứ bảy / chủ nhật** — kể cả ngày tick việc nhỏ và ngày viết bình luận. Mốc nào bị rơi vào cuối tuần thì kéo lùi về ngày làm việc liền trước.
13. **Phần "Thảo luận & Hoạt động" chỉ do `dev03` viết** (yêu cầu đã chốt 18/08/2026) — không tạo bình luận thay lời người khác. Nội dung bình luận đi theo mạch thời gian của công việc: mở đầu → giữa chừng → khép lại; việc chưa xong thì không có dòng tổng kết kiểu "đã xong".

## 10 giai đoạn đã seed (thứ tự app hiển thị: mới nhất → cũ nhất)

| ID | Giai đoạn | Trạng thái | Bắt đầu | Hạn chót | Hoàn thành | Ngày LV | Việc con | Giờ |
|----|-----------|-----------|---------|----------|-----------|---------|----------|-----|
| 10 | Đưa lên chạy chính thức, kiểm thử tổng thể và bàn giao | **PROGRESS 65%** | 14/08 | 21/08 | — | 6 | 4 | 45 |
| 9 | Đổi điểm lấy quà và voucher tự động theo vòng đời khách hàng | COMPLETED | 04/08 | 13/08 | 13/08 | 8 | 6 | 197 |
| 8 | Điểm Sao cho đơn sàn thương mại điện tử và quy định ưu đãi | COMPLETED | 23/07 | 03/08 | 03/08 | 8 | 5 | 160 |
| 7 | Báo cáo hành vi khách hàng, quảng bá trong app và xử lý góp ý vận hành | COMPLETED | 10/07 | 22/07 | 22/07 | 9 | 6 | 181 |
| 6 | Kamito Club: hạng thành viên, tích điểm và đổi voucher | COMPLETED | 30/06 | 09/07 | 09/07 | 8 | 5 | 153 |
| 5 | Chăm sóc sau bán: theo dõi đơn, bảo hành và tin nhắn Zalo tự động | COMPLETED | 18/06 | 29/06 | 29/06 | 8 | 5 | 149 |
| 4 | Voucher, mã giảm giá và chương trình khuyến mãi cho khách | COMPLETED | 08/06 | 17/06 | 17/06 | 8 | 5 | 151 |
| 3 | Mua hàng trên Mini App: giỏ hàng, thanh toán, đơn tự về Haravan | COMPLETED | 27/05 | 05/06 | 05/06 | 8 | 5 | 166 |
| 2 | Đồng bộ dữ liệu từ Haravan và môi trường thử nghiệm | COMPLETED | 14/05 | 26/05 | 26/05 | 9 | 6 | 189 |
| 1 | Nền tảng Mini App bán hàng trên Zalo và hạ tầng máy chủ | COMPLETED | 04/05 | 13/05 | 13/05 | 8 | 5 | 161 |

Giai đoạn 10 là phần đang chạy tới hiện tại: `#59 Chuyển cấu hình sang bản chạy chính thức` và `#60 Theo dõi hệ thống những ngày đầu` đã xong; `#61 Kiểm thử tổng thể trước nghiệm thu` đang PROGRESS 60% (bộ đếm giờ chạy từ 18/08 08:52); `#62 Viết tài liệu hướng dẫn và bàn giao` còn PLANNING 0% (19/08 → 21/08). Tiến độ cha 65% = trung bình (100+100+60+0)/4 — đúng công thức app tính.

## Ánh xạ giai đoạn ↔ module nguồn

| Giai đoạn | Module / repo tham chiếu |
|---|---|
| 1 | hạ tầng máy chủ, `zma`, `zma-categories`, repo `ZMA` (trang chủ, danh mục, chi tiết sản phẩm) |
| 2 | `sync`, `haravan`, `haravan-webhook`, `inventory` + môi trường dev/staging |
| 3 | `miniapp`, `miniapp-checkout` |
| 4 | `haravan` (discount), `promotion-gifts`, `collections` |
| 5 | `miniapp` (order lookup, warranty), `haravan-webhook`, module gửi ZNS |
| 6 | `loyalty` (tier, earn, redeem) |
| 7 | `tracking`, `zma-popup`, `zma` (banner/link) + các commit fix theo góp ý vận hành |
| 8 | `san-points`, `loyalty` (refund revoke, wallet split), `miniapp-checkout` (single discount) |
| 9 | `loyalty` (reward catalog, lifecycle voucher), `haravan` (issued code lifecycle), `collections` |
| 10 | các commit `chore(env)` trỏ production, `refactor: remove the staging order simulator`, kiểm thử và bàn giao |

## Kiểm chứng đã chạy

Script `verify-work-reports.js` đọc lại toàn bộ dữ liệu từ DB và kiểm 25+ loại mâu thuẫn. Kết quả: **không phát hiện mâu thuẫn nào**.

Các mục đã kiểm:
- Không field bắt buộc nào để trống (18 cột), `status` / `priority` đúng tập giá trị.
- `created_at <= start_date < due_date`; `completed_at` nằm sau `start_date`; `updated_at >= completed_at`.
- Không mốc thời gian nào nằm ở tương lai (created_at, completed_at, timer, việc nhỏ, bình luận).
- Không dòng nào trễ hẹn hoặc quá hạn (so với hạn hiệu lực kế thừa từ việc cha).
- Không dòng nào có `delay_reason`; không dòng nào chưa xong mà lại có `completed_at`.
- `status` khớp `progress_percentage`; không dòng nào kẹt mốc 90% chờ xác nhận; `owner_confirmed = 1` thì tiến độ = 100.
- Bộ đếm giờ: bật thì có `timer_started_at`, tắt thì `timer_started_at = NULL`.
- **Ngày của việc cha bằng đúng min / max của việc con** (bắt đầu, hạn chót, hoàn thành).
- **10 giai đoạn không chồng lấn thời gian nhau.**
- **Thứ tự app hiển thị (`updated_at` giảm dần) đúng chiều thời gian giảm dần**, so khớp với thứ tự các giai đoạn xếp theo ngày bắt đầu.
- Trong mỗi giai đoạn, việc con có ngày bắt đầu tăng dần theo đúng thứ tự hiển thị.
- Tiến độ cha khớp công thức `recalculateParentProgress`; tiến độ con khớp tỷ lệ checklist.
- Việc nhỏ không tạo trước ngày tạo báo cáo, không tick trước lúc tạo, không tick sau ngày hoàn thành; việc chưa tick thì `updated_at = created_at`.
- Không mốc thời gian nào rơi vào thứ bảy / chủ nhật (báo cáo, việc nhỏ và bình luận).
- Mọi bình luận đều do `dev03` viết, không có tác giả nào khác.
- Mọi `assignee` / `watchers` / tác giả bình luận đều tồn tại trong `app_users`.

## Cách khôi phục lại nguyên trạng

1. Kết nối DB test theo thông tin trên (điền password riêng).
2. Chạy file `seed-260818-1036-work-reports-zalo-mini-app-restore.sql` (cùng thư mục này) — đã gồm lệnh xoá sạch 3 bảng và reset AUTO_INCREMENT trước khi chèn. **Chỉ chạy trên DB test.**
3. Muốn seed cho dự án khác: giữ nguyên cấu trúc cột, 5 nguyên tắc sắp lịch và 11 quy ước ở trên, thay nội dung.

## Lưu ý vận hành

- App helpdesk đang chạy và trỏ vào chính DB test này — dữ liệu có thể bị sửa từ giao diện song song (đã từng có lần app ghi đè `updated_at` hàng loạt). Nếu số liệu lệch so với bảng trên, đối chiếu lại bằng file SQL khôi phục.
- `updated_at` là cột quyết định thứ tự hiển thị. Sửa bất kỳ báo cáo cũ nào trên UI sẽ đẩy nó lên đầu danh sách và phá thứ tự thời gian — muốn giữ trật tự thì khôi phục lại bằng file SQL.
- Tick / bỏ tick bất kỳ việc nhỏ nào trên UI sẽ khiến app tính lại tiến độ việc cha. Với các giai đoạn đã `owner_confirmed = 1`, app giữ nguyên 100% nên không ảnh hưởng.

## Câu hỏi còn treo

- `delay_reason` hiện để NULL toàn bộ vì mâu thuẫn với yêu cầu "không trễ hẹn". Nếu muốn field này có dữ liệu để demo giao diện giải trình trễ, cần chấp nhận ít nhất 1 báo cáo mang nhãn "Trễ hẹn" — cần xác nhận trước khi sửa.
- Việc con trong một giai đoạn hiển thị theo chiều **tăng dần** thời gian (do `findByParentIdOrderByCreatedAtAsc` cố định trong code), trong khi danh sách giai đoạn hiển thị **giảm dần**. Muốn hai chiều giống nhau thì phải đổi repository sang `findByParentIdOrderByCreatedAtDesc` — là thay đổi code, cần xác nhận trước khi làm.
