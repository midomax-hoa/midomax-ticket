package vn.midomax.helpdesk.storage;

import java.io.InputStream;

/**
 * File đã lưu, dùng khi trả về cho trình duyệt.
 *
 * @param content     luồng dữ liệu, người gọi có trách nhiệm đóng lại
 * @param contentType kiểu nội dung, có thể null nếu không xác định được
 * @param size        kích thước byte, -1 nếu không biết
 */
public record StoredFile(InputStream content, String contentType, long size) {
}
