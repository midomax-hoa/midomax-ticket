package vn.midomax.helpdesk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Đọc dữ liệu chấm công từ máy ZK Device qua giao thức standalone SDK (TCP 4370).
 *
 * Viết thẳng bằng Java thuần vì SDK chính hãng chỉ có bản DLL 32-bit cho Windows,
 * không dùng được từ server Java. Giao thức này là loại máy ZK phổ biến ở VN
 * (gồm cả máy dán nhãn Ronald Jack / Wise Eye).
 */
@Component
public class ZkTecoClient {

    private static final Logger log = LoggerFactory.getLogger(ZkTecoClient.class);

    // Các lệnh của giao thức
    private static final int CMD_CONNECT = 1000;
    private static final int CMD_EXIT = 1001;
    private static final int CMD_ENABLE_DEVICE = 1002;
    private static final int CMD_DISABLE_DEVICE = 1003;
    private static final int CMD_AUTH = 1102;
    private static final int CMD_ACK_OK = 2000;
    private static final int CMD_ACK_UNAUTH = 2005;
    private static final int CMD_PREPARE_DATA = 1500;
    private static final int CMD_DATA = 1501;
    private static final int CMD_ATTLOG_RRQ = 13;
    private static final int CMD_USERTEMP_RRQ = 9;

    /** Tham số của CMD_USERTEMP_RRQ để xin phần thông tin người dùng. */
    private static final byte FCT_USER = 5;

    /** 4 byte mở đầu mọi gói khi chạy trên TCP. */
    private static final byte[] TCP_HEADER_PREFIX = {0x50, 0x50, (byte) 0x82, 0x7d};

    private static final int ATTLOG_RECORD_SIZE = 40;

    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 20000;

    /** Một lần quét đọc được từ máy. */
    public static class Punch {
        public final String employeeCode;
        public final LocalDateTime time;
        public final int verifyMode;
        public final int punchState;

        Punch(String employeeCode, LocalDateTime time, int verifyMode, int punchState) {
            this.employeeCode = employeeCode;
            this.time = time;
            this.verifyMode = verifyMode;
            this.punchState = punchState;
        }
    }

    /**
     * Kết nối tới máy và tải toàn bộ nhật ký chấm công đang lưu trong máy.
     * Máy ZK không hỗ trợ lọc theo ngày ở phía thiết bị nên luôn phải tải hết
     * rồi lọc ở phía server.
     */
    public List<Punch> readAttendanceLogs(String host, int port, int commKey) throws IOException {
        return withSession(host, port, commKey, session -> {
            try {
                // Khoá bàn phím máy trong lúc tải để dữ liệu không đổi giữa chừng.
                session.command(CMD_DISABLE_DEVICE, new byte[0]);
                byte[] blob = session.readDataBlob(CMD_ATTLOG_RRQ);
                return parseAttendance(blob);
            } finally {
                try {
                    session.command(CMD_ENABLE_DEVICE, new byte[0]);
                } catch (IOException e) {
                    log.warn("Không mở lại bàn phím máy {}:{} - {}", host, port, e.getMessage());
                }
            }
        });
    }

    /** Một người dùng khai báo trên máy. */
    public static class DeviceUser {
        public final String employeeCode;
        public final String name;

        DeviceUser(String employeeCode, String name) {
            this.employeeCode = employeeCode;
            this.name = name;
        }
    }

    /** Tải danh sách người dùng (mã + tên) đang khai báo trên máy. */
    public List<DeviceUser> readUsers(String host, int port, int commKey) throws IOException {
        return withSession(host, port, commKey, session -> {
            try {
                session.command(CMD_DISABLE_DEVICE, new byte[0]);
                return parseUsers(session.readDataBlob(CMD_USERTEMP_RRQ, new byte[]{FCT_USER}));
            } finally {
                try {
                    session.command(CMD_ENABLE_DEVICE, new byte[0]);
                } catch (IOException e) {
                    log.warn("Không mở lại bàn phím máy {}:{} - {}", host, port, e.getMessage());
                }
            }
        });
    }

    /**
     * Bản ghi người dùng có hai cỡ tuỳ đời máy: 72 byte (máy mới, mã người dùng nằm
     * cuối) và 28 byte (máy cũ, mã là số nguyên). Tự nhận theo độ dài khối trả về.
     */
    private List<DeviceUser> parseUsers(byte[] data) {
        List<DeviceUser> result = new ArrayList<>();
        if (data == null || data.length == 0) return result;

        int size = data.length % 72 == 0 ? 72 : (data.length % 28 == 0 ? 28 : 0);
        if (size == 0) {
            log.warn("Không nhận ra cỡ bản ghi người dùng từ {} byte dữ liệu", data.length);
            return result;
        }

        for (int offset = 0; offset + size <= data.length; offset += size) {
            ByteBuffer buf = ByteBuffer.wrap(data, offset, size).order(ByteOrder.LITTLE_ENDIAN);
            int uid = buf.getShort() & 0xFFFF;
            buf.get(); // quyền hạn, không dùng

            String code;
            String name;
            if (size == 72) {
                buf.position(offset + 3 + 8);           // bỏ qua mật khẩu 8 byte
                byte[] nameBytes = new byte[24];
                buf.get(nameBytes);
                name = cString(nameBytes);

                buf.position(offset + 48);              // mã người dùng nằm ở 24 byte cuối
                byte[] codeBytes = new byte[24];
                buf.get(codeBytes);
                code = cString(codeBytes);
            } else {
                buf.position(offset + 3 + 5);           // mật khẩu 5 byte trên máy cũ
                byte[] nameBytes = new byte[8];
                buf.get(nameBytes);
                name = cString(nameBytes);
                code = String.valueOf(uid);
            }

            if (code.isEmpty()) code = String.valueOf(uid);
            result.add(new DeviceUser(code, name));
        }
        log.info("Đọc được {} người dùng từ {} byte dữ liệu", result.size(), data.length);
        return result;
    }

    /** Thử kết nối rồi ngắt ngay, dùng cho nút "Kiểm tra kết nối". */
    public void testConnection(String host, int port, int commKey) throws IOException {
        withSession(host, port, commKey, session -> null);
    }

    private interface SessionWork<T> {
        T run(Session session) throws IOException;
    }

    /**
     * Mở phiên và chạy công việc. Thử TCP trước; nhiều máy ZK đời cũ chỉ bật UDP,
     * hoặc mở cổng TCP nhưng không nói giao thức trên đó, nên thất bại thì thử lại UDP.
     */
    private <T> T withSession(String host, int port, int commKey, SessionWork<T> work) throws IOException {
        IOException tcpFailure;
        try {
            return runSession(new TcpTransport(host, port), commKey, work);
        } catch (IOException e) {
            tcpFailure = e;
            log.info("Máy {}:{} không dùng được qua TCP ({}), thử lại bằng UDP", host, port, e.getMessage());
        }

        try {
            return runSession(new UdpTransport(host, port), commKey, work);
        } catch (IOException udpFailure) {
            // Lỗi TCP thường sát nguyên nhân thật hơn (từ chối kết nối, sai IP...),
            // nên giữ nó làm lỗi chính và đính kèm lỗi UDP.
            tcpFailure.addSuppressed(udpFailure);
            throw tcpFailure;
        }
    }

    private <T> T runSession(Transport transport, int commKey, SessionWork<T> work) throws IOException {
        try (Transport t = transport) {
            Session session = new Session(t);
            session.connect(commKey);
            try {
                return work.run(session);
            } finally {
                try {
                    session.command(CMD_EXIT, new byte[0]);
                } catch (IOException e) {
                    log.warn("Không đóng phiên sạch với máy - {}", e.getMessage());
                }
            }
        }
    }

    // --- Tầng truyền tải ---

    /** Gói lệnh ZK giống nhau trên TCP và UDP, chỉ khác cách đóng khung khi gửi. */
    private interface Transport extends java.io.Closeable {
        void sendPacket(byte[] packet) throws IOException;

        /** Trả về gói lệnh đã bóc khung (lệnh(2) + checksum(2) + session(2) + reply(2) + dữ liệu). */
        byte[] receivePacket() throws IOException;
    }

    /** Trên TCP mỗi gói được bọc thêm 4 byte nhận dạng + 4 byte độ dài. */
    private static class TcpTransport implements Transport {
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;

        TcpTransport(String host, int port) throws IOException {
            socket = new Socket();
            try {
                socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
                socket.setSoTimeout(READ_TIMEOUT_MS);
                in = socket.getInputStream();
                out = socket.getOutputStream();
            } catch (IOException e) {
                socket.close();
                throw e;
            }
        }

        @Override
        public void sendPacket(byte[] packet) throws IOException {
            out.write(wrapTcp(packet));
            out.flush();
        }

        @Override
        public byte[] receivePacket() throws IOException {
            byte[] header = readFully(8);
            for (int i = 0; i < 4; i++) {
                if (header[i] != TCP_HEADER_PREFIX[i]) {
                    throw new IOException("Gói trả về không đúng định dạng ZK");
                }
            }
            int size = ByteBuffer.wrap(header, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            if (size < 8 || size > 1024 * 1024) {
                throw new IOException("Kích thước gói bất thường: " + size);
            }
            return readFully(size);
        }

        private byte[] readFully(int n) throws IOException {
            byte[] buf = new byte[n];
            int read = 0;
            while (read < n) {
                int r = in.read(buf, read, n - read);
                if (r < 0) throw new IOException("Máy đóng kết nối giữa chừng");
                read += r;
            }
            return buf;
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    /** Trên UDP gói lệnh đi thẳng, mỗi datagram là trọn một gói. */
    private static class UdpTransport implements Transport {
        /** Máy ZK đẩy dữ liệu theo khối 1KB, cấp dư để không bị cắt cụt. */
        private static final int MAX_DATAGRAM = 8192;

        private final DatagramSocket socket;
        private final InetAddress address;
        private final int port;

        UdpTransport(String host, int port) throws IOException {
            this.address = InetAddress.getByName(host);
            this.port = port;
            socket = new DatagramSocket();
            try {
                socket.setSoTimeout(READ_TIMEOUT_MS);
            } catch (IOException e) {
                socket.close();
                throw e;
            }
        }

        @Override
        public void sendPacket(byte[] packet) throws IOException {
            socket.send(new DatagramPacket(packet, packet.length, address, port));
        }

        @Override
        public byte[] receivePacket() throws IOException {
            DatagramPacket dg = new DatagramPacket(new byte[MAX_DATAGRAM], MAX_DATAGRAM);
            socket.receive(dg);
            if (dg.getLength() < 8) {
                throw new IOException("Gói UDP trả về quá ngắn: " + dg.getLength() + " byte");
            }
            byte[] packet = new byte[dg.getLength()];
            System.arraycopy(dg.getData(), dg.getOffset(), packet, 0, dg.getLength());
            return packet;
        }

        @Override
        public void close() {
            socket.close();
        }
    }

    // --- Phân tích dữ liệu ---

    /**
     * Mỗi bản ghi 40 byte: uid(2) + mã NV(24, chuỗi kết thúc bằng 0) + trạng thái(1)
     * + thời gian(4) + kiểu quét(1) + dự phòng(8).
     */
    private List<Punch> parseAttendance(byte[] data) {
        List<Punch> result = new ArrayList<>();
        if (data == null) return result;

        for (int offset = 0; offset + ATTLOG_RECORD_SIZE <= data.length; offset += ATTLOG_RECORD_SIZE) {
            ByteBuffer buf = ByteBuffer.wrap(data, offset, ATTLOG_RECORD_SIZE).order(ByteOrder.LITTLE_ENDIAN);
            buf.getShort(); // uid nội bộ của máy, không dùng

            byte[] codeBytes = new byte[24];
            buf.get(codeBytes);
            String code = cString(codeBytes);

            int verifyMode = buf.get() & 0xFF;
            long ts = buf.getInt() & 0xFFFFFFFFL;
            int punchState = buf.get() & 0xFF;

            if (code.isEmpty() || ts == 0) continue;
            LocalDateTime time = decodeTime(ts);
            if (time == null) continue;

            result.add(new Punch(code, time, verifyMode, punchState));
        }
        log.info("Đọc được {} lần quét từ {} byte dữ liệu", result.size(), data.length);
        return result;
    }

    /** Máy ZK nén ngày giờ thành một số nguyên; đây là phép giải nén tương ứng. */
    static LocalDateTime decodeTime(long value) {
        long t = value;
        int second = (int) (t % 60); t /= 60;
        int minute = (int) (t % 60); t /= 60;
        int hour = (int) (t % 24); t /= 24;
        int day = (int) (t % 31) + 1; t /= 31;
        int month = (int) (t % 12) + 1; t /= 12;
        int year = (int) (t + 2000);

        try {
            return LocalDateTime.of(year, month, day, hour, minute, second);
        } catch (java.time.DateTimeException e) {
            // Bản ghi hỏng (ví dụ 31/02) — bỏ qua thay vì làm hỏng cả lần tải.
            return null;
        }
    }

    private static String cString(byte[] raw) {
        int end = 0;
        while (end < raw.length && raw[end] != 0) end++;
        return new String(raw, 0, end, StandardCharsets.UTF_8).trim();
    }

    // --- Tầng giao thức ---

    /** Giữ sessionId/replyId của một phiên làm việc với máy. */
    private static class Session {
        private final Transport transport;
        private int sessionId = 0;
        private int replyId = 0;

        Session(Transport transport) {
            this.transport = transport;
        }

        void connect(int commKey) throws IOException {
            Reply reply = send(CMD_CONNECT, new byte[0]);
            sessionId = reply.sessionId;

            if (reply.command == CMD_ACK_UNAUTH) {
                // Máy có đặt mật khẩu kết nối.
                reply = send(CMD_AUTH, makeCommKey(commKey, sessionId));
                if (reply.command != CMD_ACK_OK) {
                    throw new IOException("Máy từ chối mật khẩu kết nối (comm key). Kiểm tra lại số khoá trên máy.");
                }
            } else if (reply.command != CMD_ACK_OK) {
                throw new IOException("Máy không chấp nhận kết nối, mã trả về: " + reply.command);
            }
        }

        Reply command(int cmd, byte[] data) throws IOException {
            return send(cmd, data);
        }

        /**
         * Gửi một lệnh trả về khối dữ liệu lớn. Máy có thể trả thẳng (CMD_DATA) hoặc
         * báo trước kích thước (CMD_PREPARE_DATA) rồi đẩy dữ liệu theo sau.
         */
        byte[] readDataBlob(int cmd) throws IOException {
            return readDataBlob(cmd, new byte[0]);
        }

        byte[] readDataBlob(int cmd, byte[] param) throws IOException {
            Reply reply = send(cmd, param);

            if (reply.command == CMD_DATA) {
                return stripSizePrefix(reply.data);
            }
            if (reply.command != CMD_PREPARE_DATA) {
                throw new IOException("Máy trả về mã không mong đợi khi tải dữ liệu: " + reply.command);
            }

            int expected = reply.data.length >= 4
                    ? ByteBuffer.wrap(reply.data, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt()
                    : 0;

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            while (buffer.size() < expected) {
                Reply chunk = receive();
                if (chunk.command == CMD_ACK_OK) break; // máy báo hết dữ liệu sớm hơn dự kiến
                buffer.write(chunk.data);
            }
            // Gói ACK kết thúc, nếu máy còn gửi.
            if (buffer.size() >= expected) {
                try {
                    receive();
                } catch (IOException ignored) {
                    // Một số đời máy không gửi ACK cuối; không coi là lỗi.
                }
            }
            // Khối dữ liệu đẩy về cũng mở đầu bằng 4 byte tổng kích thước, y như nhánh CMD_DATA.
            return stripSizePrefix(buffer.toByteArray());
        }

        /**
         * Khối attlog trả về mở đầu bằng 4 byte tổng kích thước. Chỉ cắt bỏ khi con số
         * đó thật sự khớp phần còn lại, tránh nhầm với dữ liệu bản ghi.
         */
        private byte[] stripSizePrefix(byte[] data) {
            if (data.length < 4) return data;
            int declared = ByteBuffer.wrap(data, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            if (declared == data.length - 4) {
                byte[] trimmed = new byte[data.length - 4];
                System.arraycopy(data, 4, trimmed, 0, trimmed.length);
                return trimmed;
            }
            return data;
        }

        private Reply send(int cmd, byte[] data) throws IOException {
            replyId = (replyId + 1) & 0xFFFF;
            transport.sendPacket(buildPacket(cmd, sessionId, replyId, data));
            return receive();
        }

        private Reply receive() throws IOException {
            byte[] body = transport.receivePacket();
            ByteBuffer buf = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN);
            int command = buf.getShort() & 0xFFFF;
            buf.getShort(); // checksum, máy đã tự kiểm
            int sid = buf.getShort() & 0xFFFF;
            int rid = buf.getShort() & 0xFFFF;

            byte[] payload = new byte[body.length - 8];
            buf.get(payload);
            return new Reply(command, sid, rid, payload);
        }
    }

    private static class Reply {
        final int command;
        final int sessionId;
        final int replyId;
        final byte[] data;

        Reply(int command, int sessionId, int replyId, byte[] data) {
            this.command = command;
            this.sessionId = sessionId;
            this.replyId = replyId;
            this.data = data;
        }
    }

    /** Gói lệnh chuẩn: lệnh(2) + checksum(2) + session(2) + reply(2) + dữ liệu. */
    private static byte[] buildPacket(int cmd, int sessionId, int replyId, byte[] data) {
        ByteBuffer buf = ByteBuffer.allocate(8 + data.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) cmd);
        buf.putShort((short) 0); // chỗ trống cho checksum
        buf.putShort((short) sessionId);
        buf.putShort((short) replyId);
        buf.put(data);

        byte[] packet = buf.array();
        int checksum = checksum16(packet);
        ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN).putShort(2, (short) checksum);
        return packet;
    }

    /** Bọc gói lệnh trong khung TCP: 4 byte nhận dạng + 4 byte độ dài. */
    private static byte[] wrapTcp(byte[] packet) {
        ByteBuffer buf = ByteBuffer.allocate(8 + packet.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.put(TCP_HEADER_PREFIX);
        buf.putInt(packet.length);
        buf.put(packet);
        return buf.array();
    }

    /** Tổng bù 1 trên 16 bit, theo đúng cách máy ZK kiểm tra gói. */
    private static int checksum16(byte[] data) {
        long sum = 0;
        int i = 0;
        while (i + 1 < data.length) {
            sum += ((data[i] & 0xFF) | ((data[i + 1] & 0xFF) << 8));
            i += 2;
        }
        if (i < data.length) sum += (data[i] & 0xFF);

        while ((sum >> 16) != 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }
        return (int) (~sum & 0xFFFF);
    }

    /**
     * Trộn mật khẩu kết nối với sessionId theo thuật toán của ZK. Chỉ dùng khi máy
     * báo cần xác thực.
     */
    static byte[] makeCommKey(int key, int sessionId) {
        final int ticks = 50;

        int k = 0;
        for (int i = 0; i < 32; i++) {
            if ((key & (1 << i)) != 0) {
                k = (k << 1) | 1;
            } else {
                k = k << 1;
            }
        }
        k += sessionId;

        byte[] b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(k).array();
        b[0] ^= 'Z';
        b[1] ^= 'K';
        b[2] ^= 'S';
        b[3] ^= 'O';

        // Đảo hai nửa 16 bit cho nhau.
        byte[] swapped = new byte[]{b[2], b[3], b[0], b[1]};

        byte t = (byte) (ticks & 0xFF);
        return new byte[]{
                (byte) (swapped[0] ^ t),
                (byte) (swapped[1] ^ t),
                t,
                (byte) (swapped[3] ^ t)
        };
    }
}
