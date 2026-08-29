/**
 * time-theme.js — Midomax Helpdesk
 * Tự động áp dụng theme sáng/trưa/chiều/tối theo giờ địa phương (múi giờ browser = VN UTC+7)
 */
(function () {
    /* ── 1. Phân loại giờ ── */
    function getTimeSlot(h) {
        if (h >= 5  && h < 12) return 'morning';
        if (h >= 12 && h < 14) return 'noon';
        if (h >= 14 && h < 18) return 'evening';
        return 'night';
    }

    /* ── 2. Nội dung chào theo slot ── */
    var GREET = {
        morning: { emoji: '🌅', label: 'buổi sáng', message: 'Chào buổi sáng tươi mới! Một ngày làm việc hiệu quả đang chờ bạn.' },
        noon:    { emoji: '☀️',  label: 'buổi trưa', message: 'Giữa ngày rồi! Nhớ nghỉ ngơi đôi chút để nạp năng lượng nhé.' },
        evening: { emoji: '🌆', label: 'buổi chiều', message: 'Chiều muộn rồi, hãy hoàn thành nốt công việc hôm nay.' },
        night:   { emoji: '🌙', label: 'buổi tối',   message: 'Làm việc muộn vậy? Chúc bạn giải quyết xong mọi việc sớm nhé!' }
    };

    /* ── 3. Tạo/lấy sky canvas ── */
    function ensureSky() {
        var sky = document.getElementById('time-sky');
        if (!sky) {
            sky = document.createElement('div');
            sky.id = 'time-sky';
            // Thứ tự lớp = thứ tự chiều sâu: sao → quầng sáng → mây → mưa → haze → vignette.
            // Mây được sinh hoàn toàn bằng CSS (feTurbulence trên .sky-clouds::before/after).
            sky.innerHTML =
                '<div class="sky-stars"></div>' +
                '<div class="sky-celestial"></div>' +
                '<div class="sky-clouds"></div>' +
                '<div class="sky-rain"></div>' +
                '<div class="sky-haze"></div>' +
                '<div class="sky-vignette"></div>';
            document.body.insertBefore(sky, document.body.firstChild);
        }
        return sky;
    }

    /* ── 4. Áp theme ── */
    function applyTheme() {
        var now  = new Date();
        var h    = now.getHours();
        var slot = getTimeSlot(h);
        var g    = GREET[slot];

        ensureSky();

        // Xóa class cũ, gán class mới
        document.body.classList.remove('time-morning', 'time-noon', 'time-evening', 'time-night');
        document.body.classList.add('time-' + slot);

        // Cập nhật greeting trong welcome-banner
        var h1 = document.querySelector('.welcome-banner h1');
        if (h1) {
            // Lấy tên từ element hoặc Thymeleaf đã render
            var nameMatch = h1.textContent.match(/,\s*(.+?)[\s!👋🌅☀️🌆🌙]*$/);
            var name = nameMatch ? nameMatch[1].trim() : '';
            // Tên chuẩn — thay thế emoji đuôi
            name = name.replace(/[👋🌅☀️🌆🌙]/g, '').trim();
            h1.textContent = 'Chào ' + g.label + ', ' + name + '! ' + g.emoji;
        }

        // Cập nhật mô tả nếu có
        var pBanner = document.querySelector('.welcome-banner p');
        if (pBanner) {
            pBanner.textContent = g.message;
        }
    }

    /* ── 5. Thời tiết thật (Open-Meteo, miễn phí, không cần key) ──
       Hỏi vị trí trình duyệt của NGƯỜI ĐANG XEM (Hà Nội thấy trời Hà Nội,
       Đà Nẵng thấy trời Đà Nẵng...). Người dùng từ chối / trình duyệt không
       hỗ trợ / chạy trên HTTP thường → rơi về TP.HCM (trụ sở).
       Gán class wx-* lên body:
         wx-clear  → trời quang (mặc định, không đổi gì)
         wx-cloudy → nhiều mây: mây dày lên, nắng mờ đi
         wx-rain   → mưa: trời xám, hiện vệt mưa rơi
       API lỗi / mất mạng thì bỏ qua, trang vẫn chạy theme theo giờ như thường. */
    var WX_DEFAULT = { lat: 10.762, lon: 106.660 };   // TP.HCM
    var wxCoords = null;                                // toạ độ đã chốt cho phiên này

    function wxClassOf(code) {
        // Mã WMO: 0-1 quang | 2-3,45-48 mây/sương | 51-67,80-82,95-99 mưa/giông
        if (code >= 51) return 'wx-rain';
        if (code >= 2)  return 'wx-cloudy';
        return 'wx-clear';
    }

    function fetchWeather(lat, lon) {
        var url = 'https://api.open-meteo.com/v1/forecast' +
            '?latitude=' + lat.toFixed(3) + '&longitude=' + lon.toFixed(3) +
            '&current=weather_code&timezone=auto';
        fetch(url)
            .then(function (r) { return r.json(); })
            .then(function (d) {
                var cls = wxClassOf(d.current.weather_code);
                document.body.classList.remove('wx-clear', 'wx-cloudy', 'wx-rain');
                document.body.classList.add(cls);
            })
            .catch(function () { /* mất mạng → giữ nguyên theme theo giờ */ });
    }

    function applyWeather() {
        if (wxCoords) { fetchWeather(wxCoords.lat, wxCoords.lon); return; }
        if (!navigator.geolocation) {
            wxCoords = WX_DEFAULT;
            fetchWeather(wxCoords.lat, wxCoords.lon);
            return;
        }
        navigator.geolocation.getCurrentPosition(
            function (pos) {
                wxCoords = { lat: pos.coords.latitude, lon: pos.coords.longitude };
                fetchWeather(wxCoords.lat, wxCoords.lon);
            },
            function () {                                // từ chối / hết giờ / lỗi
                wxCoords = WX_DEFAULT;
                fetchWeather(wxCoords.lat, wxCoords.lon);
            },
            { timeout: 8000, maximumAge: 30 * 60 * 1000 }
        );
    }

    /* ── 6. Cỗ bài lối tắt (coverflow: kéo ngang, thẻ tự nghiêng theo vị trí) ── */
    function initDeck() {
        var rail = document.querySelector('.sc-grid');
        if (!rail) return;
        var cards = Array.prototype.slice.call(rail.querySelectorAll('.sc'));
        if (!cards.length) return;

        // Tính transform mỗi thẻ theo khoảng cách tới tâm khung nhìn của ray.
        // Dùng offsetLeft (vị trí layout, KHÔNG đổi theo transform) để tránh rung.
        function update() {
            var viewCenter = rail.scrollLeft + rail.clientWidth / 2;
            for (var i = 0; i < cards.length; i++) {
                var c = cards[i];
                var cardCenter = c.offsetLeft + c.offsetWidth / 2;
                var d = (cardCenter - viewCenter) / rail.clientWidth;   // ~ -0.5..0.5
                var a = Math.abs(d);
                // Các thẻ nằm NGANG BẰNG nhau (không nghiêng, không tụt),
                // chỉ thẻ giữa phóng to nhẹ để biết đang chọn.
                var sc = Math.max(0.88, 1.06 - a * 0.45);
                c.style.transform = 'scale(' + sc.toFixed(3) + ')';
                c.style.zIndex = String(200 - Math.round(a * 200));
            }
        }

        var raf = 0;
        function schedule() {
            if (!raf) raf = requestAnimationFrame(function () { raf = 0; update(); });
        }
        rail.addEventListener('scroll', schedule, { passive: true });

        // Zoom/đổi cỡ màn cũng bắn 'resize': căn lại thẻ đang gần tâm nhất
        // về đúng giữa, không thì cả cỗ bị lệch khỏi tâm mới.
        var resizeTimer = 0;
        window.addEventListener('resize', function () {
            schedule();
            clearTimeout(resizeTimer);
            resizeTimer = setTimeout(function () {
                var viewCenter = rail.scrollLeft + rail.clientWidth / 2;
                var best = cards[0], bestD = Infinity;
                cards.forEach(function (c) {
                    var d = Math.abs(c.offsetLeft + c.offsetWidth / 2 - viewCenter);
                    if (d < bestD) { bestD = d; best = c; }
                });
                rail.scrollLeft = best.offsetLeft + best.offsetWidth / 2 - rail.clientWidth / 2;
                update();
            }, 150);
        });

        // Kéo bằng CHUỘT để cuộn ngang. Với cảm ứng thì để trình duyệt tự
        // cuộn (touch-action: pan-x).
        // KHÔNG setPointerCapture: bắt con trỏ về ray làm sự kiện click phát
        // vào ray thay vì vào thẻ → link chết. Nghe move/up trên window để
        // kéo ra ngoài ray vẫn mượt.
        var down = false, startX = 0, startScroll = 0, moved = 0;
        rail.addEventListener('pointerdown', function (e) {
            moved = 0;   // reset cho MỌI loại con trỏ, kẻo chạm bị khoá click vĩnh viễn
            if (e.pointerType && e.pointerType !== 'mouse') return;  // chạm → cuộn native
            down = true; startX = e.clientX; startScroll = rail.scrollLeft;
            rail.classList.add('is-dragging');
        });
        window.addEventListener('pointermove', function (e) {
            if (!down) return;
            var dx = e.clientX - startX;
            moved = Math.max(moved, Math.abs(dx));
            rail.scrollLeft = startScroll - dx;
        });
        function endDrag() { down = false; rail.classList.remove('is-dragging'); }
        window.addEventListener('pointerup', endDrag);
        window.addEventListener('pointercancel', endDrag);
        // Thẻ là <a> nên trình duyệt mặc định cho "nhấc" link ra kéo thả —
        // chặn lại kẻo nó nuốt thao tác kéo cuộn của mình.
        rail.addEventListener('dragstart', function (e) { e.preventDefault(); });

        // Click thẻ: nếu chưa ở giữa → cuộn nó vào giữa (không mở link).
        // Thẻ đã ở giữa → mở link như thường. Vừa kéo thì bỏ qua click.
        cards.forEach(function (c) {
            c.addEventListener('click', function (e) {
                if (moved > 6) { e.preventDefault(); return; }
                var viewCenter = rail.scrollLeft + rail.clientWidth / 2;
                var cardCenter = c.offsetLeft + c.offsetWidth / 2;
                if (Math.abs(cardCenter - viewCenter) > 60) {
                    e.preventDefault();
                    // Tắt snap trong lúc trượt để cả cỗ xoay mượt, rồi bật lại
                    rail.classList.add('is-dragging');
                    rail.scrollTo({ left: cardCenter - rail.clientWidth / 2, behavior: 'smooth' });
                    setTimeout(function () { rail.classList.remove('is-dragging'); }, 600);
                }
            });
        });

        // Căn thẻ giữa vào tâm khi mở trang; căn lại lần nữa khi trang tải
        // xong hẳn (font/ảnh về muộn làm layout xê dịch → lệch tâm).
        function centerMid() {
            var mid = cards[Math.floor(cards.length / 2)];
            rail.scrollLeft = mid.offsetLeft + mid.offsetWidth / 2 - rail.clientWidth / 2;
            update();
        }
        centerMid();
        window.addEventListener('load', centerMid);
    }

    /* ── 7. Khởi chạy ── */
    function init() {
        applyTheme();
        applyWeather();
        initDeck();
    }
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

    // Theme theo giờ: soát lại mỗi phút. Thời tiết: mỗi 30 phút.
    setInterval(applyTheme, 60 * 1000);
    setInterval(applyWeather, 30 * 60 * 1000);
})();
