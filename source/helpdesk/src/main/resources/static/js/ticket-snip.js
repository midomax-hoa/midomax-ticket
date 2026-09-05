/*
 * Chụp ảnh màn hình ngay trong form tạo ticket, khỏi phải chụp ra file rồi đi tìm.
 *
 * Ba cách lấy ảnh:
 *   1. Chụp kèm cửa sổ      — chụp nguyên màn hình (có cả cửa sổ trình duyệt này) rồi
 *                             kéo thả khoanh vùng cần gửi.
 *   2. Chụp không kèm cửa sổ — chọn một cửa sổ ứng dụng khác (cửa sổ trình duyệt bị loại
 *                             khỏi danh sách) rồi cũng kéo thả khoanh vùng.
 *                             Cả hai đều chụp -> kéo thả -> gắn ảnh, giống Zalo.
 *   3. Dán ảnh (Ctrl+V)     — dùng Snipping Tool của Windows (Win+Shift+S) rồi dán vào.
 *
 * Cách 1 và 2 dùng API getDisplayMedia, mà API này trình duyệt CHỈ cho chạy trên
 * HTTPS hoặc localhost. Hệ thống đang chạy HTTP nên với máy trạm hai nút đó sẽ không
 * bấm được — lúc đó tự động ẩn đi và chỉ dẫn dùng cách 3. Khi nào bật HTTPS thì hai
 * nút hiện ra, không phải sửa gì thêm.
 */
(function () {
    'use strict';

    var MAX_BYTES = 15 * 1024 * 1024;

    function canCaptureScreen() {
        return !!(navigator.mediaDevices && navigator.mediaDevices.getDisplayMedia)
            && window.isSecureContext;
    }

    /** Gắn file ảnh vào ô chọn tệp của form. DataTransfer là cách duy nhất gán được input file. */
    function attachToInput(scope, file) {
        var input = scope.querySelector('input[type=file][name=imageFile]');
        if (!input) return;
        try {
            var dt = new DataTransfer();
            dt.items.add(file);
            input.files = dt.files;
        } catch (e) {
            showNote(scope, 'Trình duyệt không cho gán ảnh tự động. Vui lòng chọn tệp thủ công.', true);
            return;
        }
        showPreview(scope, file);
    }

    function showNote(scope, msg, isError) {
        var note = scope.querySelector('.snip-note');
        if (!note) return;
        note.textContent = msg;
        note.style.color = isError ? '#dc2626' : '#64748b';
    }

    function showPreview(scope, file) {
        var box = scope.querySelector('.snip-preview');
        if (!box) return;
        box.innerHTML = '';

        var url = URL.createObjectURL(file);
        var wrap = document.createElement('div');
        wrap.style.cssText = 'position:relative; display:inline-block; margin-top:8px;';

        var img = document.createElement('img');
        img.src = url;
        img.style.cssText = 'max-height:120px; max-width:100%; border-radius:8px; border:1px solid #e5e7eb;';
        img.onload = function () { URL.revokeObjectURL(url); };

        var del = document.createElement('button');
        del.type = 'button';
        del.innerHTML = '&times;';
        del.title = 'Bỏ ảnh này';
        del.style.cssText = 'position:absolute; top:-8px; right:-8px; width:22px; height:22px; border-radius:50%;'
            + 'border:none; background:#dc2626; color:#fff; font-size:14px; line-height:1; cursor:pointer;';
        del.onclick = function () {
            var input = scope.querySelector('input[type=file][name=imageFile]');
            if (input) input.value = '';
            box.innerHTML = '';
            showNote(scope, defaultNote(), false);
        };

        var size = document.createElement('div');
        size.style.cssText = 'font-size:11px; color:#64748b; margin-top:2px;';
        size.textContent = (file.size / 1024 / 1024).toFixed(2) + ' MB';

        wrap.appendChild(img);
        wrap.appendChild(del);
        box.appendChild(wrap);
        box.appendChild(size);

        if (file.size > MAX_BYTES) {
            showNote(scope, 'Ảnh nặng hơn 15 MB, có thể gửi không được.', true);
        } else {
            showNote(scope, 'Đã đính kèm ảnh. Bấm dấu × để bỏ và chụp lại.', false);
        }
    }

    function defaultNote() {
        return canCaptureScreen()
            ? 'Mẹo: có thể bấm Win+Shift+S để cắt ảnh rồi dán vào đây bằng Ctrl+V.'
            : 'Bấm Win+Shift+S để cắt ảnh màn hình, rồi bấm Ctrl+V để dán vào đây.';
    }

    function blobToFile(blob, prefix) {
        var stamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
        return new File([blob], prefix + '_' + stamp + '.png', { type: 'image/png' });
    }

    /**
     * Xin quyền chia sẻ màn hình, lấy đúng 1 khung hình rồi tắt ngay.
     *
     * mode = 'screen' : "kèm cửa sổ" — mở sẵn danh sách MÀN HÌNH nguyên cái, cửa sổ
     *                   trình duyệt này nằm trong ảnh chụp.
     * mode = 'window' : "không kèm cửa sổ" — mở sẵn danh sách CỬA SỔ ứng dụng và loại
     *                   cửa sổ trình duyệt này ra, nên không thể chọn nhầm chính nó.
     *
     * displaySurface / selfBrowserSurface chỉ là GỢI Ý; trình duyệt vẫn bắt người dùng
     * tự chọn (không bỏ qua được, đây là quy định bảo mật). Trình duyệt cũ không hiểu
     * hai tuỳ chọn này thì bỏ qua chứ không lỗi.
     */
    function captureScreen(mode, delayMs, onTick) {
        var opts = { audio: false };
        if (mode === 'screen') {
            // KÈM cửa sổ: chụp nguyên màn hình, cửa sổ trình duyệt nằm trong ảnh
            opts.video = { displaySurface: 'monitor' };
            opts.selfBrowserSurface = 'include';
            opts.monitorTypeSurfaces = 'include';
        } else {
            // KHÔNG kèm cửa sổ: chọn một cửa sổ ứng dụng khác. selfBrowserSurface='exclude'
            // loại cửa sổ trình duyệt ra khỏi danh sách nên không thể chọn nhầm chính nó.
            opts.video = { displaySurface: 'window' };
            opts.selfBrowserSurface = 'exclude';
        }

        return navigator.mediaDevices.getDisplayMedia(opts).then(function (stream) {
            var video = document.createElement('video');
            video.srcObject = stream;
            video.muted = true;
            return video.play().then(function () {
                // KHÔNG dùng requestAnimationFrame: nó ngừng chạy khi cửa sổ bị thu nhỏ,
                // mà chế độ hẹn giờ lại cần chụp đúng lúc trình duyệt đang thu nhỏ.
                // Chờ tới khi luồng có kích thước thật rồi mới vẽ, tránh khung hình đen.
                return waitReady(video);
            }).then(function () {
                if (!delayMs) return;
                return countdown(delayMs, onTick);
            }).then(function () {
                var canvas = document.createElement('canvas');
                canvas.width = video.videoWidth;
                canvas.height = video.videoHeight;
                canvas.getContext('2d').drawImage(video, 0, 0);
                stream.getTracks().forEach(function (t) { t.stop(); });
                return canvas;
            }).catch(function (err) {
                stream.getTracks().forEach(function (t) { t.stop(); });
                throw err;
            });
        });
    }

    /** Chờ luồng có khung hình thật. Dùng setTimeout vì cửa sổ có thể đang bị thu nhỏ. */
    function waitReady(video) {
        return new Promise(function (resolve) {
            var start = Date.now();
            (function poll() {
                if (video.videoWidth > 0 && Date.now() - start >= 150) return resolve();
                if (Date.now() - start > 5000) return resolve(); // quá lâu thì cứ vẽ thử
                setTimeout(poll, 60);
            })();
        });
    }

    /**
     * Đếm ngược trước khi chụp, để người dùng kịp thu nhỏ trình duyệt xuống mà lộ desktop.
     * Báo bằng CẢ tiêu đề trang (thấy được trên thanh taskbar khi cửa sổ đã thu nhỏ)
     * lẫn dòng ghi chú trong form — vì lúc đó người dùng không nhìn thấy trang nữa.
     */
    function countdown(delayMs, onTick) {
        return new Promise(function (resolve) {
            var left = Math.ceil(delayMs / 1000);
            var original = document.title;
            function tick() {
                if (left <= 0) {
                    document.title = original;
                    if (onTick) onTick(0);
                    resolve();
                    return;
                }
                document.title = '📸 Chụp sau ' + left + 's...';
                if (onTick) onTick(left);
                left--;
                setTimeout(tick, 1000);
            }
            tick();
        });
    }

    function canvasToFile(canvas, prefix) {
        return new Promise(function (resolve, reject) {
            canvas.toBlob(function (blob) {
                if (blob) resolve(blobToFile(blob, prefix));
                else reject(new Error('Không tạo được ảnh.'));
            }, 'image/png');
        });
    }

    /**
     * Sau khi chụp thì phủ ảnh lên toàn màn hình cho người dùng kéo thả khoanh đúng
     * phần cần gửi — giống Snipping Tool của Windows. Nhờ vậy ảnh gửi đi chỉ có nội
     * dung cần, không dính khung cửa sổ hay những thứ không liên quan trên desktop.
     */
    function pickRegion(canvas) {
        return new Promise(function (resolve, reject) {
            var overlay = document.createElement('div');
            overlay.style.cssText = 'position:fixed; inset:0; z-index:2147483647; cursor:crosshair;'
                + 'background:#000; user-select:none;';

            var img = document.createElement('img');
            img.src = canvas.toDataURL('image/png');
            img.style.cssText = 'position:absolute; inset:0; width:100%; height:100%;'
                + 'object-fit:contain; opacity:0.6; pointer-events:none;';

            // Phần đang khoanh hiện rõ nét trên nền mờ, để thấy đúng cái mình lấy
            var sel = document.createElement('div');
            sel.style.cssText = 'position:absolute; border:2px solid #38bdf8;'
                + 'box-shadow:0 0 0 9999px rgba(0,0,0,0.45); display:none; pointer-events:none;'
                + 'background-repeat:no-repeat;';

            var tip = document.createElement('div');
            tip.textContent = 'Kéo chuột để khoanh vùng cần gửi — bấm Esc để huỷ';
            tip.style.cssText = 'position:absolute; top:18px; left:50%; transform:translateX(-50%);'
                + 'background:rgba(15,23,42,0.92); color:#fff; padding:9px 18px; border-radius:20px;'
                + 'font:14px/1 "Segoe UI",sans-serif; pointer-events:none; z-index:2;';

            var dim = document.createElement('div');
            dim.style.cssText = 'position:absolute; display:none; background:rgba(15,23,42,0.9);'
                + 'color:#fff; padding:3px 8px; border-radius:6px; font:12px/1 "Segoe UI",sans-serif;'
                + 'pointer-events:none; z-index:3;';

            overlay.appendChild(img);
            overlay.appendChild(sel);
            overlay.appendChild(dim);
            overlay.appendChild(tip);
            document.body.appendChild(overlay);

            var sx = 0, sy = 0, dragging = false;

            // Ảnh vẽ theo object-fit:contain nên có viền đen hai bên.
            // Cần tỉ lệ và độ lệch này để quy toạ độ màn hình về toạ độ ảnh gốc.
            function geom() {
                var vw = window.innerWidth, vh = window.innerHeight;
                var scale = Math.min(vw / canvas.width, vh / canvas.height);
                return { scale: scale, offX: (vw - canvas.width * scale) / 2,
                         offY: (vh - canvas.height * scale) / 2 };
            }

            function cleanup() {
                document.removeEventListener('keydown', onKey);
                if (overlay.parentNode) overlay.parentNode.removeChild(overlay);
            }
            function onKey(e) {
                if (e.key === 'Escape') { cleanup(); reject(new Error('CANCELLED')); }
            }
            document.addEventListener('keydown', onKey);

            overlay.addEventListener('mousedown', function (e) {
                dragging = true;
                sx = e.clientX; sy = e.clientY;
                sel.style.display = 'block';
                sel.style.left = sx + 'px'; sel.style.top = sy + 'px';
                sel.style.width = '0px'; sel.style.height = '0px';
                tip.style.display = 'none';
            });

            overlay.addEventListener('mousemove', function (e) {
                if (!dragging) return;
                var x = Math.min(sx, e.clientX), y = Math.min(sy, e.clientY);
                var w = Math.abs(e.clientX - sx), h = Math.abs(e.clientY - sy);
                sel.style.left = x + 'px'; sel.style.top = y + 'px';
                sel.style.width = w + 'px'; sel.style.height = h + 'px';

                // Vẽ lại đúng mảnh ảnh nét bên trong khung chọn
                var g = geom();
                sel.style.backgroundImage = 'url(' + img.src + ')';
                sel.style.backgroundSize = (canvas.width * g.scale) + 'px ' + (canvas.height * g.scale) + 'px';
                sel.style.backgroundPosition = (g.offX - x) + 'px ' + (g.offY - y) + 'px';

                // Kích thước thật của ảnh sẽ cắt ra, hiện ngay cạnh khung chọn
                dim.style.display = 'block';
                dim.textContent = Math.round(w / g.scale) + ' × ' + Math.round(h / g.scale);
                dim.style.left = x + 'px';
                dim.style.top = (y > 26 ? y - 24 : y + h + 6) + 'px';
            });

            overlay.addEventListener('mouseup', function (e) {
                if (!dragging) return;
                dragging = false;

                var x1 = Math.min(sx, e.clientX), y1 = Math.min(sy, e.clientY);
                var w = Math.abs(e.clientX - sx), h = Math.abs(e.clientY - sy);
                cleanup();

                if (w < 5 || h < 5) { reject(new Error('TOO_SMALL')); return; }

                var g = geom();
                var cx = (x1 - g.offX) / g.scale, cy = (y1 - g.offY) / g.scale;
                var cw = w / g.scale, ch = h / g.scale;

                // Kéo lố ra vùng viền đen thì cắt lại cho nằm gọn trong ảnh
                cx = Math.max(0, Math.min(cx, canvas.width));
                cy = Math.max(0, Math.min(cy, canvas.height));
                cw = Math.min(cw, canvas.width - cx);
                ch = Math.min(ch, canvas.height - cy);
                if (cw < 1 || ch < 1) { reject(new Error('TOO_SMALL')); return; }

                var out = document.createElement('canvas');
                out.width = Math.round(cw);
                out.height = Math.round(ch);
                out.getContext('2d').drawImage(canvas, cx, cy, cw, ch, 0, 0, out.width, out.height);
                resolve(out);
            });
        });
    }

    function handleError(scope, err) {
        if (!err) return;
        if (err.name === 'NotAllowedError' || err.message === 'CANCELLED') {
            showNote(scope, 'Đã huỷ chụp màn hình.', false);
        } else if (err.message === 'TOO_SMALL') {
            showNote(scope, 'Vùng chọn quá nhỏ, thử kéo khoanh rộng hơn.', true);
        } else if (err.name === 'NotFoundError') {
            showNote(scope, 'Không tìm thấy màn hình nào để chụp.', true);
        } else {
            showNote(scope, 'Không chụp được màn hình: ' + (err.message || err.name), true);
        }
    }

    /**
     * Cả HAI chế độ đều: chụp -> kéo thả khoanh vùng -> gắn ảnh, giống Zalo.
     * Khác nhau chỉ ở chỗ ảnh chụp có dính cửa sổ trình duyệt này hay không:
     *   'screen' = chụp nguyên màn hình, có cả cửa sổ đang mở form (kèm cửa sổ).
     *   'window' = chọn một cửa sổ ứng dụng khác, cửa sổ trình duyệt bị loại khỏi
     *              danh sách nên ảnh không dính nó (không kèm cửa sổ).
     */
    function doSnip(scope, mode, delayMs) {
        showNote(scope, delayMs
            ? 'Chọn "Toàn bộ màn hình", rồi THU NHỎ trình duyệt ngay để lộ desktop...'
            : 'Chọn màn hình/cửa sổ, sau đó kéo thả khoanh vùng cần gửi...', false);

        captureScreen(mode, delayMs, function (left) {
            showNote(scope, left > 0
                ? 'Thu nhỏ trình duyệt ngay — sẽ chụp sau ' + left + ' giây...'
                : 'Đã chụp xong. Mở lại cửa sổ này để khoanh vùng cần gửi.', false);
        })
            .then(function (canvas) {
                return pickRegion(canvas);
            })
            .then(function (cropped) {
                return canvasToFile(cropped,
                    delayMs ? 'chup-desktop' : (mode === 'screen' ? 'chup-kem-cua-so' : 'chup-vung'));
            })
            .then(function (file) { attachToInput(scope, file); })
            .catch(function (err) { handleError(scope, err); });
    }

    /**
     * Form nào đang nhận ảnh dán: ưu tiên form nằm trong hộp thoại đang mở.
     * Bắt sự kiện ở cấp document chứ không gắn vào riêng form, vì paste chỉ bắn ra ở
     * phần tử đang có con trỏ — gắn vào form thì người dùng bấm Ctrl+V ở chỗ khác
     * trong hộp thoại là mất ảnh.
     */
    function activeScope() {
        var modals = document.querySelectorAll('.modal.show');
        for (var i = 0; i < modals.length; i++) {
            var f = modals[i].querySelector('form input[type=file][name=imageFile]');
            if (f) return f.closest('form');
        }
        return null;
    }

    var pasteWired = false;

    function wirePaste() {
        if (pasteWired) return;
        pasteWired = true;
        document.addEventListener('paste', function (e) {
            var scope = activeScope();
            if (!scope) return; // không có hộp thoại tạo ticket nào đang mở
            var items = (e.clipboardData || {}).items || [];
            for (var i = 0; i < items.length; i++) {
                if (items[i].type && items[i].type.indexOf('image/') === 0) {
                    var blob = items[i].getAsFile();
                    if (blob) {
                        e.preventDefault();
                        attachToInput(scope, blobToFile(blob, 'anh-dan'));
                    }
                    return;
                }
            }
        });
    }

    /** Ba kiểu chụp, hiện trong menu xổ xuống của nút "Chụp màn hình". */
    var CAPTURE_MODES = [
        {
            icon: 'fa-window-restore',
            label: 'Kèm cửa sổ',
            desc: 'Chụp cả màn hình, có cửa sổ trình duyệt này',
            mode: 'screen', delay: 0
        },
        {
            icon: 'fa-crop-simple',
            label: 'Không kèm cửa sổ',
            desc: 'Chọn một cửa sổ ứng dụng khác',
            mode: 'window', delay: 0
        },
        {
            icon: 'fa-stopwatch',
            label: 'Chụp desktop (5 giây)',
            desc: 'Hẹn giờ để kịp thu nhỏ trình duyệt',
            mode: 'screen', delay: 5000
        }
    ];

    /** Đang mở menu nào — mở cái mới thì đóng cái cũ. */
    var openMenu = null;

    function closeMenu() {
        if (openMenu) { openMenu.style.display = 'none'; openMenu = null; }
    }

    // Bấm ra ngoài hoặc Esc thì đóng menu
    document.addEventListener('mousedown', function (e) {
        if (openMenu && !openMenu.parentNode.contains(e.target)) closeMenu();
    });
    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape') closeMenu();
    });

    function buildCaptureMenu(scope) {
        var wrap = document.createElement('div');
        wrap.style.cssText = 'position:relative; display:inline-block;';

        var btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'btn btn-sm btn-outline-primary fw-semibold';
        btn.innerHTML = '<i class="fa-solid fa-camera me-1"></i>Chụp màn hình'
            + '<i class="fa-solid fa-chevron-down ms-2" style="font-size:10px;"></i>';
        btn.title = 'Chọn kiểu chụp';

        var menu = document.createElement('div');
        // z-index cao hơn hộp thoại (1055) để menu không bị hộp thoại che mất
        menu.style.cssText = 'position:absolute; top:100%; left:0; margin-top:4px; z-index:1080;'
            + 'background:#fff; border:1px solid #e5e7eb; border-radius:10px; min-width:262px;'
            + 'box-shadow:0 10px 24px rgba(0,0,0,0.14); padding:6px; display:none;';

        CAPTURE_MODES.forEach(function (m) {
            var item = document.createElement('button');
            item.type = 'button';
            item.style.cssText = 'display:flex; align-items:flex-start; gap:10px; width:100%;'
                + 'background:none; border:none; text-align:left; padding:8px 10px;'
                + 'border-radius:8px; cursor:pointer;';
            item.innerHTML = '<i class="fa-solid ' + m.icon + '" style="color:#1e3a8a; margin-top:3px; width:16px;"></i>'
                + '<span><span style="font-weight:600; font-size:13px; color:#1e293b;">' + m.label + '</span>'
                + '<br><span style="font-size:11px; color:#64748b;">' + m.desc + '</span></span>';
            item.onmouseenter = function () { item.style.background = '#f1f5f9'; };
            item.onmouseleave = function () { item.style.background = 'none'; };
            item.onclick = function () {
                closeMenu();
                doSnip(scope, m.mode, m.delay);
            };
            menu.appendChild(item);
        });

        btn.onclick = function () {
            var isOpen = menu.style.display === 'block';
            closeMenu();
            if (!isOpen) { menu.style.display = 'block'; openMenu = menu; }
        };

        wrap.appendChild(btn);
        wrap.appendChild(menu);
        return wrap;
    }

    function build(scope) {
        var input = scope.querySelector('input[type=file][name=imageFile]');
        if (!input || scope.querySelector('.snip-bar')) return;

        var bar = document.createElement('div');
        bar.className = 'snip-bar d-flex flex-wrap gap-2 mt-2';

        if (canCaptureScreen()) {
            // Gom 3 kiểu chụp vào một nút cho gọn. Dropdown tự viết chứ không dùng của
            // Bootstrap để khỏi phụ thuộc thư viện đã nạp xong hay chưa.
            bar.appendChild(buildCaptureMenu(scope));
        }

        var bPaste = document.createElement('button');
        bPaste.type = 'button';
        bPaste.className = 'btn btn-sm btn-outline-secondary fw-semibold';
        bPaste.innerHTML = '<i class="fa-solid fa-paste me-1"></i>Dán ảnh (Ctrl+V)';
        bPaste.title = 'Cắt ảnh bằng Win+Shift+S rồi bấm Ctrl+V để dán vào đây';
        bPaste.onclick = function () {
            showNote(scope, 'Bấm Win+Shift+S để cắt ảnh, sau đó nhấn Ctrl+V ngay tại đây.', false);
            // Không focus vào ô chọn tệp: một số trình duyệt không bắn sự kiện paste ở đó.
            // Bộ bắt Ctrl+V đặt ở cấp document nên bấm ở đâu trong hộp thoại cũng ăn.
        };
        bar.appendChild(bPaste);

        var preview = document.createElement('div');
        preview.className = 'snip-preview';

        var note = document.createElement('div');
        note.className = 'snip-note';
        note.style.cssText = 'font-size:11px; color:#64748b; margin-top:6px;';

        input.parentNode.appendChild(bar);
        input.parentNode.appendChild(preview);
        input.parentNode.appendChild(note);

        note.textContent = defaultNote();
        wirePaste();

        // Chọn tệp thủ công cũng hiện ảnh xem trước cho đồng nhất
        input.addEventListener('change', function () {
            if (input.files && input.files[0]) showPreview(scope, input.files[0]);
        });
    }

    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('form').forEach(function (f) {
            if (f.querySelector('input[type=file][name=imageFile]')) build(f);
        });
    });
})();
