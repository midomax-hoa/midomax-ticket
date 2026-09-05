package vn.midomax.helpdesk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * Tải log từ máy chấm công và tổng hợp thành lịch công theo ngày cho từng nhân viên.
 */
@Service
public class AttendanceService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceService.class);

    private final AttendanceDeviceRepository deviceRepo;
    private final AttendanceLogRepository logRepo;
    private final AttendanceRecordRepository recordRepo;
    private final ShiftRepository shiftRepo;
    private final EmployeeShiftRepository employeeShiftRepo;
    private final EmployeeRepository employeeRepo;
    private final AttendanceDeviceUserRepository deviceUserRepo;
    private final LeaveRequestRepository leaveRequestRepo;
    private final ZkTecoClient zkClient;

    public AttendanceService(AttendanceDeviceRepository deviceRepo,
                             AttendanceLogRepository logRepo,
                             AttendanceRecordRepository recordRepo,
                             ShiftRepository shiftRepo,
                             EmployeeShiftRepository employeeShiftRepo,
                             EmployeeRepository employeeRepo,
                             AttendanceDeviceUserRepository deviceUserRepo,
                             LeaveRequestRepository leaveRequestRepo,
                             ZkTecoClient zkClient) {
        this.leaveRequestRepo = leaveRequestRepo;
        this.deviceUserRepo = deviceUserRepo;
        this.deviceRepo = deviceRepo;
        this.logRepo = logRepo;
        this.recordRepo = recordRepo;
        this.shiftRepo = shiftRepo;
        this.employeeShiftRepo = employeeShiftRepo;
        this.employeeRepo = employeeRepo;
        this.zkClient = zkClient;
    }

    /** Kết quả một lần tải, để hiện thông báo cho người dùng. */
    public static class SyncResult {
        public String deviceName;
        public int fetched;
        public int inserted;
        public String error;

        public boolean isOk() { return error == null; }
        public String getDeviceName() { return deviceName; }
        public int getFetched() { return fetched; }
        public int getInserted() { return inserted; }
        public String getError() { return error; }
    }

    // --- Tải dữ liệu từ máy ---

    /** Tải tất cả máy đang bật. */
    public List<SyncResult> syncAllDevices() {
        List<SyncResult> results = new ArrayList<>();
        for (AttendanceDevice device : deviceRepo.findByActiveTrue()) {
            results.add(syncDevice(device));
        }
        return results;
    }

    public SyncResult syncDevice(Long deviceId) {
        AttendanceDevice device = deviceRepo.findById(deviceId).orElse(null);
        if (device == null) {
            SyncResult r = new SyncResult();
            r.error = "Không tìm thấy thiết bị";
            return r;
        }
        return syncDevice(device);
    }

    /** Ghi theo lô; đủ lớn để nhanh, đủ nhỏ để một lô hỏng không kéo theo cả lần tải. */
    private static final int INSERT_BATCH_SIZE = 500;

    /**
     * Cố ý KHÔNG bọc toàn bộ trong một giao dịch: một máy chứa hàng chục nghìn lần quét,
     * gom hết vào một giao dịch thì vừa nặng vừa mất sạch nếu hỏng ở dòng cuối. Mỗi lô
     * tự commit, nên tải lại lần sau chỉ bù phần còn thiếu.
     */
    public SyncResult syncDevice(AttendanceDevice device) {
        SyncResult result = new SyncResult();
        result.deviceName = device.getName();

        try {
            List<ZkTecoClient.Punch> punches = zkClient.readAttendanceLogs(
                    device.getIpAddress(),
                    device.getPort() == null ? 4370 : device.getPort(),
                    device.getCommKey() == null ? 0 : device.getCommKey());

            result.fetched = punches.size();
            result.inserted = insertNewPunches(device, punches);

            // Kéo luôn danh sách người dùng để có tên hiển thị khi chưa import nhân viên.
            syncDeviceUsers(device);

            saveStatus(device, "Tải thành công: " + result.fetched + " log, thêm mới " + result.inserted);

        } catch (Exception e) {
            log.warn("Tải máy chấm công {} ({}) thất bại", device.getName(), device.getIpAddress(), e);
            result.error = describe(e);
            saveStatus(device, "Lỗi: " + result.error);
        }
        return result;
    }

    /** Lọc trùng rồi ghi phần mới. Trả về số dòng thật sự thêm được. */
    private int insertNewPunches(AttendanceDevice device, List<ZkTecoClient.Punch> punches) {
        // Một truy vấn lấy hết khoá đã có, thay cho việc hỏi cơ sở dữ liệu từng dòng một.
        Set<String> known = new HashSet<>();
        for (Object[] row : logRepo.findKeysByDevice(device.getId())) {
            known.add(key((String) row[0], (LocalDateTime) row[1]));
        }

        List<AttendanceLog> pending = new ArrayList<>();
        int inserted = 0;
        for (ZkTecoClient.Punch punch : punches) {
            // known nhận luôn khoá mới, nên cũng chặn được trùng trong chính lần tải này.
            if (!known.add(key(punch.employeeCode, punch.time))) continue;

            AttendanceLog entry = new AttendanceLog();
            entry.setEmployeeCode(punch.employeeCode);
            entry.setPunchTime(punch.time);
            entry.setDeviceId(device.getId());
            entry.setDeviceName(device.getName());
            entry.setVerifyMode(punch.verifyMode);
            entry.setPunchState(punch.punchState);
            pending.add(entry);

            if (pending.size() >= INSERT_BATCH_SIZE) {
                logRepo.saveAll(pending);
                inserted += pending.size();
                pending.clear();
            }
        }
        if (!pending.isEmpty()) {
            logRepo.saveAll(pending);
            inserted += pending.size();
        }
        return inserted;
    }

    /**
     * Cập nhật danh sách người dùng của một máy. Không xoá người cũ: một người có thể
     * bị gỡ khỏi máy này nhưng log cũ của họ vẫn cần tên để hiển thị.
     */
    private void syncDeviceUsers(AttendanceDevice device) {
        try {
            List<ZkTecoClient.DeviceUser> users = zkClient.readUsers(
                    device.getIpAddress(),
                    device.getPort() == null ? 4370 : device.getPort(),
                    device.getCommKey() == null ? 0 : device.getCommKey());

            for (ZkTecoClient.DeviceUser u : users) {
                if (u.employeeCode == null || u.employeeCode.isBlank()) continue;

                AttendanceDeviceUser row = deviceUserRepo.findByEmployeeCode(u.employeeCode)
                        .orElseGet(AttendanceDeviceUser::new);
                row.setEmployeeCode(u.employeeCode);
                // Máy nào không đặt tên thì để trống, phía hiển thị sẽ tự dùng mã.
                row.setFullName(u.name == null || u.name.isBlank() ? null : u.name);
                row.setDeviceId(device.getId());
                row.setDeviceName(device.getName());
                row.setUpdatedAt(LocalDateTime.now());
                deviceUserRepo.save(row);
            }
            log.info("Cập nhật {} người dùng từ máy {}", users.size(), device.getName());

        } catch (Exception e) {
            // Không có danh sách người dùng thì vẫn còn log chấm công, đừng làm hỏng cả lần tải.
            log.warn("Không đọc được danh sách người dùng của máy {}: {}", device.getName(), e.getMessage());
        }
    }

    // --- Danh sách người được tính công ---

    /**
     * Một người cần hiện trên bảng công. ĐỊNH DANH = (máy chấm công, mã):
     * các văn phòng dùng chung dải mã (101 ở 188Bis và 101 ở Kho HCM là HAI người
     * khác nhau) nên mã đơn lẻ không đủ phân biệt người.
     */
    public static class Person {
        public final String code;
        public final String name;
        public final String department;
        /** true khi người này chỉ có trên máy, chưa có hồ sơ nhân sự. */
        public final boolean fromDeviceOnly;
        /** Máy chấm công (văn phòng) của người này. Null = chỉ có hồ sơ, chưa gắn máy nào. */
        public final Long deviceId;
        public final String deviceName;

        Person(String code, String name, String department, boolean fromDeviceOnly,
               Long deviceId, String deviceName) {
            this.code = code;
            this.name = name;
            this.department = department;
            this.fromDeviceOnly = fromDeviceOnly;
            this.deviceId = deviceId;
            this.deviceName = deviceName;
        }

        public String getCode() { return code; }
        public String getName() { return name; }
        public String getDepartment() { return department; }
        public boolean isFromDeviceOnly() { return fromDeviceOnly; }
        public Long getDeviceId() { return deviceId; }
        public String getDeviceName() { return deviceName; }

        /** Khóa định danh duy nhất: máy + mã. */
        public String personKey() {
            return (deviceId == null ? "-" : deviceId) + "|" + code;
        }
    }

    /**
     * Danh sách người được tính công, mỗi (máy, mã) một dòng. Người có trên máy lấy
     * theo máy (tên ưu tiên tên trên máy, thiếu thì tra hồ sơ nhân sự theo mã);
     * nhân viên có hồ sơ nhưng chưa xuất hiện trên máy nào vẫn được thêm vào cuối
     * (deviceId null) để không mất ai khi chưa đồng bộ máy.
     */
    public List<Person> roster() {
        List<Person> people = new ArrayList<>();
        Set<String> takenKeys = new HashSet<>();
        Set<String> codesOnDevices = new HashSet<>();

        // Hồ sơ nhân sự theo mã, dùng bù tên khi máy không lưu tên
        Map<String, Employee> empByCode = new HashMap<>();
        for (Employee emp : employeeRepo.findAll()) {
            if (emp.getEmployeeCode() != null && !emp.getEmployeeCode().isBlank() && !isResigned(emp)) {
                empByCode.putIfAbsent(emp.getEmployeeCode(), emp);
            }
        }

        for (AttendanceDeviceUser u : deviceUserRepo.findAllByOrderByFullNameAsc()) {
            String code = u.getEmployeeCode();
            if (code == null || code.isBlank()) continue;
            if (!takenKeys.add((u.getDeviceId() == null ? "-" : u.getDeviceId()) + "|" + code)) continue;
            codesOnDevices.add(code);

            Employee emp = empByCode.get(code);
            String name = u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName()
                    : (emp != null && emp.getFullName() != null ? emp.getFullName() : code);
            String dept = emp != null && emp.getDepartment() != null && !emp.getDepartment().isBlank()
                    ? emp.getDepartment() : u.getDeviceName();
            people.add(new Person(code, name, dept, emp == null, u.getDeviceId(), u.getDeviceName()));
        }

        // Nhân viên có hồ sơ nhưng chưa thấy trên máy nào
        for (Map.Entry<String, Employee> e : empByCode.entrySet()) {
            if (codesOnDevices.contains(e.getKey())) continue;
            Employee emp = e.getValue();
            people.add(new Person(e.getKey(), emp.getFullName(), emp.getDepartment(), false, null, null));
        }
        return people;
    }

    private static String key(String employeeCode, LocalDateTime punchTime) {
        return employeeCode + "|" + punchTime;
    }

    /**
     * Ghi trạng thái ở giao dịch riêng: nếu phần tải hỏng thì người dùng vẫn phải đọc
     * được lý do, không để lỗi đó nuốt luôn thông báo.
     */
    private void saveStatus(AttendanceDevice device, String status) {
        try {
            device.setLastSyncAt(LocalDateTime.now());
            device.setLastSyncStatus(status);
            deviceRepo.save(device);
        } catch (Exception e) {
            log.warn("Không ghi được trạng thái tải của máy {}: {}", device.getName(), e.getMessage());
        }
    }

    /** Đổi lỗi kỹ thuật thành câu người dùng đọc được. */
    private String describe(Exception e) {
        if (e instanceof java.net.SocketTimeoutException) {
            return "Máy không phản hồi (quá thời gian chờ). Kiểm tra máy đã bật và cổng 4370 đã mở chưa.";
        }
        if (e instanceof java.net.ConnectException) {
            return "Không kết nối được tới IP này. Kiểm tra IP, mạng, hoặc NAT cổng 4370.";
        }
        String msg = e.getMessage();
        return (msg == null || msg.isBlank()) ? e.getClass().getSimpleName() : msg;
    }

    public void testConnection(AttendanceDevice device) throws Exception {
        zkClient.testConnection(device.getIpAddress(),
                device.getPort() == null ? 4370 : device.getPort(),
                device.getCommKey() == null ? 0 : device.getCommKey());
    }

    // --- Tổng hợp thành công ---

    /**
     * Dựng lại bảng công cho khoảng ngày. Chạy được nhiều lần: những dòng đã sửa tay
     * ({@code manualOverride}) được giữ nguyên.
     */
    @Transactional
    public int rebuild(LocalDate from, LocalDate to) {
        Shift defaultShift = shiftRepo.findFirstByIsDefaultTrue().orElse(null);

        Map<Long, Shift> shiftsById = new HashMap<>();
        for (Shift s : shiftRepo.findAll()) shiftsById.put(s.getId(), s);

        // Đơn nghỉ phép / bổ sung công ĐÃ DUYỆT trong kỳ: gom theo (máy|mã|ngày)
        // để áp vào công — được duyệt thì ngày đó tính đủ công.
        Map<String, LeaveRequest> approvedByPersonDay = new HashMap<>();
        for (LeaveRequest req : leaveRequestRepo.findOverlapping(from, to)) {
            if (!LeaveRequest.ST_APPROVED.equals(req.getStatus())) continue;
            for (LocalDate d = req.getRequestDate(); !d.isAfter(req.getEffectiveToDate()); d = d.plusDays(1)) {
                approvedByPersonDay.put(leaveKey(req.getEmployeeCode(), req.getDeviceId(), d), req);
            }
        }

        int touched = 0;
        for (Person person : roster()) {
            String code = person.code;

            Shift shift = resolveShift(code, person.deviceId, shiftsById, defaultShift);
            if (shift == null) continue; // chưa khai báo ca nào thì chưa tính được

            // Lấy dư mỗi đầu một ngày để ca đêm ghép đúng. Lọc theo MÁY của người này —
            // các văn phòng trùng dải mã, không lọc thì lần quét của người khác lẫn vào.
            List<AttendanceLog> logs = logRepo.findByPersonInRange(
                    code, person.deviceId, from.minusDays(1).atStartOfDay(), to.plusDays(2).atStartOfDay());

            Map<LocalDate, List<LocalDateTime>> byWorkDate = groupByWorkDate(logs, shift);

            for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
                AttendanceRecord record = recordRepo.findByPersonAndWorkDate(code, day, person.deviceId).orElse(null);
                if (record != null && Boolean.TRUE.equals(record.getManualOverride())) continue;

                List<LocalDateTime> punches = byWorkDate.getOrDefault(day, Collections.emptyList());
                boolean workingDay = shift.isWorkingDay(day.getDayOfWeek());
                if (punches.isEmpty() && !workingDay) {
                    // Ngày nghỉ theo ca và không ai quét: không cần lưu dòng nào.
                    if (record != null) recordRepo.delete(record);
                    continue;
                }

                if (record == null) {
                    record = new AttendanceRecord();
                    record.setEmployeeCode(code);
                    record.setWorkDate(day);
                }
                record.setDeviceId(person.deviceId);
                record.setDeviceName(person.deviceName);
                record.setEmployeeName(person.name);
                record.setDepartment(person.department);
                record.setShiftId(shift.getId());
                record.setShiftName(shift.getName());

                compute(record, punches, shift, workingDay);

                // Đơn đã duyệt phủ lên kết quả máy chấm công
                LeaveRequest approved = approvedByPersonDay.get(leaveKey(code, person.deviceId, day));
                if (approved == null && person.deviceId != null) {
                    approved = approvedByPersonDay.get(leaveKey(code, null, day)); // đơn cũ chưa gắn máy
                }
                applyApprovedRequest(record, approved);

                recordRepo.save(record);
                touched++;
            }
        }
        log.info("Đã tổng hợp công {} dòng từ {} đến {}", touched, from, to);
        return touched;
    }

    private boolean isResigned(Employee emp) {
        return emp.getResignDate() != null && emp.getResignDate().isBefore(LocalDate.now());
    }

    private Shift resolveShift(String code, Long deviceId, Map<Long, Shift> shiftsById, Shift defaultShift) {
        return assignedShiftId(code, deviceId)
                .map(shiftsById::get)
                .orElse(defaultShift);
    }

    /**
     * Ca đã gán cho NGƯỜI = (máy, mã): ưu tiên dòng gán đúng máy; chưa có thì
     * rơi về dòng cũ chưa phân máy (deviceId null); vẫn không có thì Optional rỗng
     * (nơi gọi tự dùng ca mặc định).
     */
    public Optional<Long> assignedShiftId(String code, Long deviceId) {
        Optional<EmployeeShift> assignment = employeeShiftRepo.findByPerson(code, deviceId);
        if (assignment.isEmpty() && deviceId != null) {
            assignment = employeeShiftRepo.findByPerson(code, null);
        }
        return assignment.map(EmployeeShift::getShiftId);
    }

    /**
     * Gán mỗi lần quét về đúng ngày công. Với ca đêm, lần quét vào sáng sớm thuộc về
     * ngày làm việc hôm trước.
     */
    private Map<LocalDate, List<LocalDateTime>> groupByWorkDate(List<AttendanceLog> logs, Shift shift) {
        Map<LocalDate, List<LocalDateTime>> map = new HashMap<>();
        boolean crossMidnight = Boolean.TRUE.equals(shift.getCrossMidnight());

        for (AttendanceLog entry : logs) {
            LocalDateTime punch = entry.getPunchTime();
            LocalDate workDate = punch.toLocalDate();

            if (crossMidnight && shift.getEndTime() != null
                    && punch.toLocalTime().isBefore(shift.getEndTime().plusHours(3))) {
                workDate = workDate.minusDays(1);
            }
            map.computeIfAbsent(workDate, k -> new ArrayList<>()).add(punch);
        }
        for (List<LocalDateTime> list : map.values()) Collections.sort(list);
        return map;
    }

    /** Tính giờ vào/ra, đi muộn, về sớm, tăng ca và quy ra số công. */
    /**
     * Khoảng cách tối thiểu giữa lần quét đầu và cuối để coi là có đủ vào/ra.
     * Ngắn hơn mức này nghĩa là chỉ quét được một lượt (bấm máy hai nhịp) —
     * xếp vào "thiếu lần quét" để người phụ trách xác nhận, đừng tính đi trễ.
     */
    private static final int MIN_SESSION_MINUTES = 30;

    private void compute(AttendanceRecord record, List<LocalDateTime> punches, Shift shift, boolean workingDay) {
        record.setLateMinutes(0);
        record.setEarlyMinutes(0);
        record.setWorkedMinutes(0);
        record.setOtMinutes(0);
        record.setWorkDays(0.0);
        record.setFirstIn(null);
        record.setLastOut(null);

        // Ngày hôm nay trở đi thì chưa kết thúc ca làm, chưa đủ căn cứ kết luận vắng
        // hay thiếu quét. Không chốt sớm, nếu không cả tháng còn lại sẽ hiện "vắng
        // không phép" cho mọi người ngay từ đầu tháng.
        boolean notFinishedYet = record.getWorkDate() != null
                && !record.getWorkDate().isBefore(LocalDate.now());

        if (punches.isEmpty()) {
            if (!workingDay) {
                record.setStatus(AttendanceRecord.ST_OFF);
            } else {
                record.setStatus(notFinishedYet
                        ? AttendanceRecord.ST_NONE : AttendanceRecord.ST_ABSENT);
            }
            return;
        }

        LocalDateTime firstIn = punches.get(0);
        record.setFirstIn(firstIn);

        if (punches.size() == 1) {
            // Hôm nay mới quét vào, chưa tan làm -> đang trong ca chứ không phải quên quét.
            // Ngày đã qua mà chỉ có 1 lần quét thì mới là thiếu, để người phụ trách xác nhận tay.
            record.setStatus(notFinishedYet
                    ? AttendanceRecord.ST_WORKING : AttendanceRecord.ST_MISSING);
            return;
        }

        LocalDateTime lastOut = punches.get(punches.size() - 1);
        record.setLastOut(lastOut);

        // Các lần quét sát nhau (VD 17:35:57 và 17:36:02) thực chất là MỘT lần bấm máy
        // hai nhịp, nghĩa là hôm đó người này quên quét lượt còn lại. Không được coi
        // lần quét cuối ngày là "giờ vào", nếu không sẽ tính thành đi trễ cả 9 tiếng.
        if (Duration.between(firstIn, lastOut).toMinutes() < MIN_SESSION_MINUTES) {
            // Hai lần quét sát nhau (bấm máy hai nhịp). Nếu là ngày chưa xong thì người ta
            // vẫn đang làm, chưa kết luận thiếu quét được — để cuối ngày tính lại.
            record.setStatus(notFinishedYet
                    ? AttendanceRecord.ST_WORKING : AttendanceRecord.ST_MISSING);
            return;
        }

        LocalDateTime shiftStart = LocalDateTime.of(record.getWorkDate(), shift.getStartTime());
        LocalDateTime shiftEnd = LocalDateTime.of(record.getWorkDate(), shift.getEndTime());
        if (!shiftEnd.isAfter(shiftStart)) shiftEnd = shiftEnd.plusDays(1); // ca đêm

        int lateGrace = orZero(shift.getLateGraceMinutes());
        int earlyGrace = orZero(shift.getEarlyGraceMinutes());
        int breakMinutes = orZero(shift.getBreakMinutes());
        int standard = shift.getStandardMinutes();

        // Khung nghỉ trưa nằm giữa ca: nửa số phút làm việc chuẩn tính từ giờ vào.
        // VD ca 08:30–17:30 nghỉ 60' -> nghỉ trưa 12:30–13:30.
        LocalDateTime breakStart = shiftStart.plusMinutes(standard / 2L);
        LocalDateTime breakEnd = breakStart.plusMinutes(breakMinutes);

        // Chỉ làm MỘT BUỔI: tính từ lúc vào (hoặc đến lúc về) mà dù ở lại trọn ca cũng
        // chỉ làm được tối đa ~nửa ca. Người đi công tác nửa ngày rơi vào đây và sẽ nộp
        // Giấy bổ sung công cho buổi vắng, nên KHÔNG tính đi trễ / về sớm.
        // Ngưỡng 55% cho dư một chút, để người vào lúc 12:27 (sát giờ nghỉ trưa)
        // vẫn được coi là làm buổi chiều thay vì bị tính trễ 4 tiếng.
        LocalDateTime arriveAt = firstIn.isBefore(shiftStart) ? shiftStart : firstIn;
        LocalDateTime leaveAt = lastOut.isAfter(shiftEnd) ? shiftEnd : lastOut;
        double halfLimit = standard * 0.55;

        int workableAfterArrival = arriveAt.isBefore(shiftEnd)
                ? (int) Duration.between(arriveAt, shiftEnd).toMinutes()
                        - overlapMinutes(arriveAt, shiftEnd, breakStart, breakEnd)
                : 0;
        int workableBeforeLeave = leaveAt.isAfter(shiftStart)
                ? (int) Duration.between(shiftStart, leaveAt).toMinutes()
                        - overlapMinutes(shiftStart, leaveAt, breakStart, breakEnd)
                : 0;

        boolean afternoonOnly = standard > 0 && workableAfterArrival <= halfLimit;
        boolean morningOnly = standard > 0 && workableBeforeLeave <= halfLimit;
        boolean halfSessionOnly = afternoonOnly || morningOnly;

        if (afternoonOnly) {
            record.setLateMinutes(0);
        } else {
            int late = (int) Duration.between(shiftStart, firstIn).toMinutes();
            record.setLateMinutes(Math.max(0, late - lateGrace));
        }

        if (morningOnly) {
            record.setEarlyMinutes(0);
        } else {
            int early = (int) Duration.between(lastOut, shiftEnd).toMinutes();
            record.setEarlyMinutes(Math.max(0, early - earlyGrace));
        }

        int otAfter = orZero(shift.getOtStartAfterMinutes());
        int overtime = (int) Duration.between(shiftEnd, lastOut).toMinutes();
        record.setOtMinutes(overtime > otAfter ? overtime : 0);

        // Chỉ tính phần giao giữa thời gian có mặt và khung ca; trừ giờ nghỉ trưa
        // theo phần THỰC SỰ giao với khung nghỉ — người vào lúc 13:00 không nghỉ trưa
        // trọn 60' nên trừ đủ 60' sẽ ăn mất công của họ.
        LocalDateTime effIn = firstIn.isBefore(shiftStart) ? shiftStart : firstIn;
        LocalDateTime effOut = lastOut.isAfter(shiftEnd) ? shiftEnd : lastOut;
        int worked = effOut.isAfter(effIn) ? (int) Duration.between(effIn, effOut).toMinutes() : 0;
        worked = Math.max(0, worked - overlapMinutes(effIn, effOut, breakStart, breakEnd));
        record.setWorkedMinutes(worked);

        double ratio = standard > 0 ? (double) worked / standard : 0;
        if (ratio >= 0.9) {
            record.setWorkDays(1.0);
        } else if (ratio >= 0.45) {
            record.setWorkDays(0.5);
        } else {
            record.setWorkDays(0.0);
        }

        boolean penalised = record.getLateMinutes() > 0 || record.getEarlyMinutes() > 0;
        if (penalised) {
            record.setStatus(AttendanceRecord.ST_LATE);
        } else if (halfSessionOnly) {
            // Nửa buổi hợp lệ: chờ Giấy bổ sung công cho buổi còn lại, không phạt trễ.
            record.setStatus(AttendanceRecord.ST_HALF);
        } else {
            record.setStatus(AttendanceRecord.ST_OK);
        }
    }

    /** Khóa tra đơn theo người + ngày. */
    private String leaveKey(String code, Long deviceId, LocalDate day) {
        return (deviceId == null ? "-" : deviceId) + "|" + code + "|" + day;
    }

    /**
     * Áp đơn ĐÃ DUYỆT lên một ngày công: được duyệt thì ngày đó tính đủ công và
     * bỏ mọi khoản phạt trễ / về sớm.
     *
     * - Phiếu đăng ký nghỉ  -> trạng thái Nghỉ phép, 1 công.
     * - Giấy bổ sung công   -> cộng bù phần vắng: bổ sung cả ngày thành 1 công;
     *   bổ sung nửa buổi thì cộng thêm 0,5 vào phần đã chấm được (tối đa 1 công).
     */
    private void applyApprovedRequest(AttendanceRecord record, LeaveRequest request) {
        if (request == null) return;

        record.setLateMinutes(0);
        record.setEarlyMinutes(0);

        if (LeaveRequest.TYPE_LEAVE.equals(request.getType())) {
            record.setStatus(AttendanceRecord.ST_LEAVE);
            record.setWorkDays(1.0);
            return;
        }

        double current = record.getWorkDays() == null ? 0.0 : record.getWorkDays();
        boolean halfSession = "MORNING".equals(request.getDuration()) || "AFTERNOON".equals(request.getDuration());
        double total = halfSession ? Math.min(1.0, current + 0.5) : 1.0;
        record.setWorkDays(total);
        record.setStatus(AttendanceRecord.ST_OK);
    }

    /** Số phút giao nhau giữa hai khoảng thời gian; không giao thì 0. */
    private int overlapMinutes(LocalDateTime aStart, LocalDateTime aEnd,
                               LocalDateTime bStart, LocalDateTime bEnd) {
        LocalDateTime start = aStart.isAfter(bStart) ? aStart : bStart;
        LocalDateTime end = aEnd.isBefore(bEnd) ? aEnd : bEnd;
        return end.isAfter(start) ? (int) Duration.between(start, end).toMinutes() : 0;
    }

    private int orZero(Integer value) { return value == null ? 0 : value; }

    // --- Đọc cho màn hình lịch ---

    public List<AttendanceRecord> findRecords(LocalDate from, LocalDate to) {
        return recordRepo.findInRange(from, to);
    }

    public List<AttendanceRecord> findRecordsForEmployee(String employeeCode, LocalDate from, LocalDate to) {
        return recordRepo.findByEmployeeInRange(employeeCode, from, to);
    }

    /**
     * Bản ghi công của MỘT người theo (máy, mã) — dùng khi các văn phòng trùng dải mã.
     * Chưa có bản ghi gắn máy thì rơi về bản ghi cũ chưa phân máy, để dữ liệu tạo
     * trước khi nâng cấp định danh vẫn hiện chứ không biến mất khỏi lịch.
     */
    public List<AttendanceRecord> findRecordsForPerson(String employeeCode, Long deviceId,
                                                       LocalDate from, LocalDate to) {
        List<AttendanceRecord> exact = recordRepo.findByPersonInRange(employeeCode, deviceId, from, to);
        if (!exact.isEmpty() || deviceId == null) {
            return exact;
        }
        return recordRepo.findLegacyByCodeInRange(employeeCode, from, to);
    }

    public List<AttendanceLog> findLogs(LocalDate from, LocalDate to) {
        return logRepo.findInRange(from.atStartOfDay(), to.plusDays(1).atStartOfDay());
    }

    /** Các lần quét của MỘT người trong khoảng thời gian — trang chấm công cá nhân dùng. */
    public List<AttendanceLog> findLogsForPerson(String code, Long deviceId,
                                                 LocalDateTime from, LocalDateTime to) {
        return logRepo.findByPersonInRange(code, deviceId, from, to);
    }

    public Optional<AttendanceRecord> findRecord(Long id) {
        return recordRepo.findById(id);
    }

    @Transactional
    public void saveManual(Long id, String status, Double workDays, String note) {
        recordRepo.findById(id).ifPresent(record -> {
            record.setStatus(status);
            record.setWorkDays(workDays == null ? 0.0 : workDays);
            record.setNote(note);
            record.setManualOverride(true);
            recordRepo.save(record);
        });
    }

    /** Bỏ đánh dấu sửa tay để lần tổng hợp sau tính lại từ log máy. */
    @Transactional
    public void clearManual(Long id) {
        recordRepo.findById(id).ifPresent(record -> {
            record.setManualOverride(false);
            recordRepo.save(record);
        });
    }
}
