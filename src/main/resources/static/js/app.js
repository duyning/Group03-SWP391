/*
 * ============================================================
 * Dự án: Cinema 2026 — SWP391 Group 03
 * File: app.js
 * Chức năng: Tập tin JavaScript xử lý logic nghiệp vụ phía giao diện (SPA).
 *            Bao gồm:
 *              - Điều khiển Tabs và phân trang dữ liệu.
 *              - Phân hệ Phim: Tìm kiếm, lọc, hiển thị danh mục, mở hộp thoại CRUD,
 *                xử lý tải file video lên server thông qua XMLHttpRequest có thanh tiến trình (progress bar),
 *                trình phát trailer video thông minh tự phát hiện loại nguồn (YouTube iframe hoặc HTML5 Video Player).
 *              - Phân hệ Lịch chiếu: Lập lịch, xem, chỉnh sửa, tự phát hiện loại ngày trong tuần / cuối tuần / ngày lễ.
 *              - Phân hệ Bán vé: Chọn lịch chiếu để tải và vẽ sơ đồ 40 ghế (Thường/VIP), xử lý bán vé,
 *                hoàn trả vé và đồng bộ thống kê doanh thu theo thời gian thực.
 * Người viết: TrienLX - HE182285
 * Ngày tạo: 2026-06-04
 * ============================================================
 * [THAY ĐỔI - TrienLX - 2026-06-11]
 *   - Thêm hàm showToast(type, title, message, duration): hiển thị thông báo toast
 *     góc trên phải màn hình (thay thế hoàn toàn alert() của trình duyệt).
 *   - Thêm hàm showConfirm(title, message, onOk): hiển thị hộp thoại xác nhận
 *     tùy chỉnh (thay thế hoàn toàn confirm() của trình duyệt).
 *   - Thay tất cả alert() và confirm() trong phân hệ Phim bằng showToast / showConfirm.
 * [THAY ĐỔI - TrienLX - 2026-06-12]
 *   - Sửa hàm closeTrailer() giải phóng video player bằng removeAttribute('src')
 *     để khắc phục lỗi không thể phát trailer từ lần thứ hai.
 * [THAY ĐỔI - TrienLX - 2026-06-23]
 *   - Cập nhật hàm editShowtime để xử lý an toàn giá trị st.showTime (hỗ trợ cả dạng mảng
 *     số [HH, MM] và dạng chuỗi từ backend gửi về) giúp nạp đúng giờ chiếu vào modal.
 *   - Bổ sung chức năng Điều chỉnh 1 ngày cụ thể trong dải ngày lịch chiếu: Gom nhóm theo note,
 *     thiết lập màu badge khác biệt, thêm hành động "Chỉnh ngày" và modal "overrideDayModal"
 *     kèm gọi API POST /api/showtimes/override-day.
 * ============================================================
 */


// ==================== CẤU HÌNH API ====================
const API_MOVIES    = '/api/movies';
const API_SHOWTIMES = '/api/showtimes';
const API_TICKETS   = '/api/tickets';
const API_ROOMS     = '/api/rooms';

// ==================== TRẠNG THÁI TOÀN CỤC ====================
let moviesData      = [];   // Toàn bộ phim sau khi lọc
let showtimesData   = [];   // Toàn bộ lịch chiếu sau khi lọc
let ticketsData     = [];   // Vé của suất chiếu đang xem
let ticketsFiltered = [];   // Vé sau khi lọc theo trạng thái

let moviesPage    = 1;
let showtimesPage = 1;
const PAGE_SIZE   = 6;      // Số dòng mỗi trang

let activeShowtimeId = null; // ID suất chiếu đang mở sơ đồ ghế
let currentEditingGroupIds = []; // Danh sách IDs suất chiếu của nhóm đang sửa

// Cache dữ liệu dropdown để tránh fetch lại nhiều lần (giảm lag)
let _cachedMovieList = null;
let _cachedRoomList  = null;

const AVAILABLE_GENRES = [
    'Hành động', 'Tình cảm', 'Kinh dị', 'Hài hước', 
    'Hoạt hình', 'Viễn tưởng', 'Phiêu lưu', 'Kịch tính', 
    'Thần thoại', 'Tội phạm', 'Gia đình', 'Nhạc kịch'
];

function renderGenreCheckboxes() {
    const filterContainer = document.getElementById('filterGenreContainer');
    const modalContainer = document.getElementById('movieGenreContainer');
    
    if (filterContainer) {
        filterContainer.innerHTML = AVAILABLE_GENRES.map(genre => `
            <label style="display: flex; align-items: center; gap: 0.25rem; font-size: 0.8rem; cursor: pointer; background: rgba(0,0,0,0.05); padding: 0.2rem 0.5rem; border-radius: 4px; user-select: none; color: var(--text-main); font-weight: 500;">
                <input type="checkbox" name="filterGenreVal" value="${genre}" style="margin: 0; width: auto; height: auto;" onchange="applyMovieFilter()"> ${genre}
            </label>
        `).join('');
    }
    
    if (modalContainer) {
        modalContainer.innerHTML = AVAILABLE_GENRES.map(genre => `
            <label style="display: flex; align-items: center; gap: 0.25rem; font-size: 0.8rem; cursor: pointer; background: #f1f5f9; padding: 0.25rem 0.5rem; border-radius: 4px; user-select: none; color: #1e293b; font-weight: 500;">
                <input type="checkbox" name="movieGenreVal" value="${genre}" style="margin: 0; width: auto; height: auto;"> ${genre}
            </label>
        `).join('');
    }
}

// ==================== KHỞI TẠO ====================
document.addEventListener('DOMContentLoaded', () => {
    const page = document.body.dataset.page || 'movies';
    initTabs();

    if (page === 'movies') {
        renderGenreCheckboxes();
        initMovieEvents();
        loadMovieStats();
        loadMovies({});
    }

    if (page === 'showtimes') {
        initShowtimeEvents();
        loadShowtimeStats();
        populateMovieDropdowns();
        populateRoomDropdown();
        loadShowtimes({});
    }

    if (page === 'tickets') {
        initTicketEvents();
        populateTicketMovieDropdown();
    }
});

// ==================== ĐIỀU KHIỂN TABS ====================
function initTabs() {
    const tabMoviesBtn = document.getElementById('tabMoviesBtn');
    const tabShowtimesBtn = document.getElementById('tabShowtimesBtn');
    const tabTicketsBtn = document.getElementById('tabTicketsBtn');

    if (tabMoviesBtn) tabMoviesBtn.addEventListener('click', () => window.location.href = '/manage_movies.html');
    if (tabShowtimesBtn) tabShowtimesBtn.addEventListener('click', () => window.location.href = '/manage_showtime.html');
    if (tabTicketsBtn) tabTicketsBtn.addEventListener('click', () => window.location.href = '/manage_ticket.html');
}

function switchTab(tab) {
    // Ẩn tất cả section, bỏ active tất cả tabs
    ['moviesSection', 'showtimesSection', 'ticketsSection'].forEach(id => {
        document.getElementById(id)?.classList.remove('active');
    });
    ['tabMoviesBtn', 'tabShowtimesBtn', 'tabTicketsBtn'].forEach(id => {
        document.getElementById(id)?.classList.remove('active');
    });

    if (tab === 'movies') {
        document.getElementById('moviesSection')?.classList.add('active');
        document.getElementById('tabMoviesBtn')?.classList.add('active');
        loadMovieStats();
        loadMovies({});
    } else if (tab === 'showtimes') {
        document.getElementById('showtimesSection')?.classList.add('active');
        document.getElementById('tabShowtimesBtn')?.classList.add('active');
        loadShowtimeStats();
        populateMovieDropdowns();
        loadShowtimes({});
    } else if (tab === 'tickets') {
        document.getElementById('ticketsSection')?.classList.add('active');
        document.getElementById('tabTicketsBtn')?.classList.add('active');
        populateShowtimeDropdown();
    }
}

// ==================== XỬ LÝ ANTI-XSS ====================
function esc(str) {
    if (str == null) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

// ==================== FORMAT TIỀN ====================
function formatVND(n) {
    if (n == null) return '0';
    return Number(n).toLocaleString('vi-VN');
}

// ==================== FORMAT NGÀY (dd/MM/yyyy) ====================
function formatDate(dateStr) {
    if (!dateStr) return '—';
    const p = dateStr.split('-');
    return p.length === 3 ? `${p[2]}/${p[1]}/${p[0]}` : dateStr;
}

// ==================== FORMAT GIỜ (HH:mm) ====================
function formatTime(timeStr) {
    if (!timeStr) return '—';
    const p = timeStr.split(':');
    return `${p[0]}:${p[1]}`;
}

// ==================== TÍNH GIỜ KẾT THÚC SUẤT CHIẾU ====================
function getEndTime(timeStr, duration) {
    if (!timeStr) return '';
    const p = timeStr.split(':');
    const h = parseInt(p[0]);
    const m = parseInt(p[1]);
    // Giờ kết thúc = Giờ chiếu + 10p quảng cáo + thời lượng phim + 20p dọn phòng
    const totalMinutes = h * 60 + m + 10 + (duration || 120) + 20;
    const endH = Math.floor(totalMinutes / 60) % 24;
    const endM = totalMinutes % 60;
    return `${String(endH).padStart(2, '0')}:${String(endM).padStart(2, '0')}`;
}

// ==================== PHÂN LOẠI NGÀY (Client-side) ====================
function detectDayType(dateStr) {
    if (!dateStr) return 'Chưa xác định';
    const [y, m, d] = dateStr.split('-').map(Number);
    // Kiểm tra ngày lễ dương lịch Việt Nam
    if ((m===1&&d===1)||(m===4&&d===30)||(m===5&&d===1)||(m===9&&d===2)) return 'Ngày lễ';
    const dow = new Date(y, m-1, d).getDay(); // 0=CN, 6=T7
    if (dow === 0 || dow === 6) return 'Cuối tuần';
    return 'Trong tuần';
}

// ==================== PHÂN TRANG ====================
function buildPagination(containerId, infoId, total, currentPage, onPageChange) {
    const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));
    document.getElementById(infoId).textContent =
        `Hiển thị trang ${currentPage} / ${totalPages}`;

    const container = document.getElementById(containerId);
    container.innerHTML = '';

    // Nút lùi
    const prev = document.createElement('button');
    prev.className = 'btn-page';
    prev.innerHTML = '<i class="fa-solid fa-angle-left"></i>';
    prev.disabled = currentPage === 1;
    prev.onclick = () => onPageChange(currentPage - 1);
    container.appendChild(prev);

    // Nút số trang
    for (let i = 1; i <= totalPages; i++) {
        const btn = document.createElement('button');
        btn.className = 'btn-page' + (i === currentPage ? ' active' : '');
        btn.textContent = i;
        btn.onclick = () => onPageChange(i);
        container.appendChild(btn);
    }

    // Nút tiến
    const next = document.createElement('button');
    next.className = 'btn-page';
    next.innerHTML = '<i class="fa-solid fa-angle-right"></i>';
    next.disabled = currentPage === totalPages;
    next.onclick = () => onPageChange(currentPage + 1);
    container.appendChild(next);
}

// ==================== KHOẢNG NGÀY ====================
function getWeekRange() {
    const today = new Date();
    const dow = today.getDay(); // 0=CN
    const diffToMon = dow === 0 ? -6 : 1 - dow;
    const mon = new Date(today); mon.setDate(today.getDate() + diffToMon);
    const sun = new Date(mon);  sun.setDate(mon.getDate() + 6);
    return { startDate: isoDate(mon), endDate: isoDate(sun) };
}

function getMonthRange() {
    const today = new Date();
    const first = new Date(today.getFullYear(), today.getMonth(), 1);
    const last  = new Date(today.getFullYear(), today.getMonth()+1, 0);
    return { startDate: isoDate(first), endDate: isoDate(last) };
}

function isoDate(d) {
    const y = d.getFullYear();
    const m = String(d.getMonth()+1).padStart(2,'0');
    const dd = String(d.getDate()).padStart(2,'0');
    return `${y}-${m}-${dd}`;
}

// ==================== YOUTUBE ====================
function extractYouTubeId(url) {
    if (!url) return null;                                                    // Trả về null nếu URL rỗng
    const m = url.match(/(?:youtu\.be\/|v=|embed\/)([^#&?]{11})/);
    return m ? m[1] : null;                                                   // Trả về ID YouTube hoặc null
}

// ==================== TOAST NOTIFICATION SYSTEM ====================
// [THÊM MỚI - TrienLX - 2026-06-11]
// Hiển thị thông báo toast góc trên phải màn hình thay thế alert() của trình duyệt.
//
// @param {string} type     - Loại thông báo: 'success' | 'error' | 'warning' | 'info'
// @param {string} title    - Tiêu đề ngắn gọn của thông báo
// @param {string} message  - Nội dung chi tiết (có thể để trống '')
// @param {number} duration - Thời gian hiển thị (ms), mặc định 3500ms
function showToast(type = 'info', title = '', message = '', duration = 3500) {
    // Map loại toast sang icon Font Awesome tương ứng
    const iconMap = {
        success: 'fa-solid fa-circle-check',         // Xanh lá: thao tác thành công
        error:   'fa-solid fa-circle-xmark',          // Đỏ: có lỗi xảy ra
        warning: 'fa-solid fa-triangle-exclamation',  // Cam: cảnh báo người dùng
        info:    'fa-solid fa-circle-info'             // Xanh biển: thông tin thông thường
    };

    const container = document.getElementById('toast-container');
    if (!container) return; // Không làm gì nếu chưa có container trong DOM

    // Tạo phần tử toast mới
    const toast = document.createElement('div');
    toast.className = `toast-item toast-${type}`;  // Class quyết định màu sắc viền trái
    toast.setAttribute('role', 'alert');            // Accessibility: đánh dấu là vùng thông báo
    toast.innerHTML = `
        <i class="toast-icon ${iconMap[type] || iconMap.info}"></i>
        <div class="toast-content">
            ${title   ? `<div class="toast-title">${esc(title)}</div>`   : ''}
            ${message ? `<div class="toast-message">${esc(message)}</div>` : ''}
        </div>
        <button class="toast-close" aria-label="Đóng thông báo">&#x2715;</button>
    `;

    // Hàm đóng toast với hiệu ứng trượt ra
    const closeToast = () => {
        toast.classList.add('hiding');                // Kích hoạt animation trượt ra phải
        toast.addEventListener('animationend', () => {
            if (toast.parentNode) toast.parentNode.removeChild(toast); // Xóa khỏi DOM sau animation
        }, { once: true });
    };

    // Nhấn nút X để đóng ngay
    toast.querySelector('.toast-close').addEventListener('click', e => {
        e.stopPropagation(); // Không lan sự kiện click ra toast cha
        closeToast();
    });

    // Nhấn vào toast cũng đóng toast
    toast.addEventListener('click', closeToast);

    // Thêm toast vào vùng chứa
    container.appendChild(toast);

    // Tự động đóng sau thời gian duration
    setTimeout(closeToast, duration);
}

// ==================== CUSTOM CONFIRM DIALOG ====================
// [THÊM MỚI - TrienLX - 2026-06-11]
// Hiển thị hộp thoại xác nhận tùy chỉnh (thay thế confirm() của trình duyệt).
// Trả về một Promise: resolve(true) nếu người dùng nhấn Xác nhận, resolve(false) nếu nhấn Hủy.
//
// @param {string} title   - Tiêu đề hộp thoại
// @param {string} message - Câu hỏi / nội dung cần xác nhận
// @param {string} okText  - Nhãn nút đồng ý (mặc định: 'Xác nhận')
function showConfirm(title = 'Xác nhận', message = 'Bạn có chắc chắn không?', okText = 'Xác nhận') {
    return new Promise(resolve => {
        const overlay   = document.getElementById('confirmOverlay');
        const btnOk     = document.getElementById('confirmBtnOk');
        const btnCancel = document.getElementById('confirmBtnCancel');

        if (!overlay || !btnOk || !btnCancel) {
            // Fallback về confirm() mặc định nếu chưa có HTML trong DOM
            resolve(window.confirm(message));
            return;
        }

        // Gán nội dung động vào hộp thoại
        document.getElementById('confirmTitle').textContent   = title;
        document.getElementById('confirmMessage').textContent = message;
        btnOk.textContent = okText; // Nhãn nút tuỳ chỉnh

        // Hiển thị overlay với hiệu ứng fade-in (thêm class 'show' qua CSS)
        overlay.classList.add('show');

        const handleOverlayClick = e => {
            if (e.target === overlay) close(false);
        };

        // Hàm đóng hộp thoại và trả kết quả
        const close = result => {
            overlay.classList.remove('show'); // Ẩn overlay
            overlay.removeEventListener('click', handleOverlayClick);
            // Xóa event listener cũ để tránh tích lũy nhiều listener
            btnOk.replaceWith(btnOk.cloneNode(true));
            btnCancel.replaceWith(btnCancel.cloneNode(true));
            resolve(result); // Trả kết quả ra cho caller
        };

        // Sau khi replaceWith, cần lấy lại tham chiếu nút mới
        document.getElementById('confirmBtnOk').addEventListener('click',     () => close(true),  { once: true });
        document.getElementById('confirmBtnCancel').addEventListener('click',  () => close(false), { once: true });

        // Nhấn vào vùng nền mờ bên ngoài cũng đóng (tương đương Hủy)
        overlay.addEventListener('click', handleOverlayClick);
    });
}

// ======================================================
//   QUẢN LÝ PHIM
// ======================================================

// --- Hàm gợi ý Autocomplete cho người dùng khi nhập Đạo diễn/Diễn viên/NSX ---
function initAutocomplete(inputId, type) {
    const input = document.getElementById(inputId);
    if (!input) return;

    // Tạo wrapper định vị tương đối
    const wrapper = document.createElement('div');
    wrapper.style.position = 'relative';
    wrapper.style.width = '100%';
    input.parentNode.insertBefore(wrapper, input);
    wrapper.appendChild(input);

    // Tạo dropdown danh sách gợi ý
    const dropdown = document.createElement('div');
    dropdown.className = 'autocomplete-dropdown';
    dropdown.style.cssText = `
        position: absolute;
        top: 100%;
        left: 0;
        right: 0;
        background: white;
        border: 1px solid #cbd5e1;
        border-radius: 6px;
        max-height: 180px;
        overflow-y: auto;
        z-index: 10000;
        display: none;
        box-shadow: 0 10px 15px -3px rgba(0,0,0,0.1);
    `;
    wrapper.appendChild(dropdown);

    let suggestions = [];

    const fetchSuggestions = async () => {
        try {
            const r = await fetch(`/api/suggestions/persons?type=${type}`);
            if (r.ok) {
                suggestions = await r.json();
            }
        } catch (e) {
            console.error('Lỗi khi tải gợi ý từ máy chủ:', e);
        }
    };

    input.addEventListener('focus', async () => {
        await fetchSuggestions();
        showSuggestions(input.value);
    });

    input.addEventListener('input', () => {
        showSuggestions(input.value);
    });

    document.addEventListener('click', e => {
        if (!wrapper.contains(e.target)) {
            dropdown.style.display = 'none';
        }
    });

    function showSuggestions(val) {
        dropdown.innerHTML = '';
        const inputVal = val.trim().toLowerCase();
        
        // Cú pháp đặc biệt cho danh sách diễn viên ngăn cách bằng dấu phẩy
        const parts = val.split(',').map(s => s.trim());
        const lastPart = parts[parts.length - 1].toLowerCase();

        const query = (type === 'ACTOR') ? lastPart : inputVal;

        if (!query) {
            dropdown.style.display = 'none';
            return;
        }

        const filtered = suggestions.filter(item => 
            item.name && item.name.toLowerCase().includes(query)
        );

        if (!filtered.length) {
            dropdown.style.display = 'none';
            return;
        }

        filtered.forEach(item => {
            const div = document.createElement('div');
            div.style.cssText = `
                padding: 8px 12px;
                cursor: pointer;
                border-bottom: 1px solid #f1f5f9;
                font-size: 0.85rem;
                color: #1e293b;
            `;
            div.addEventListener('mouseover', () => {
                div.style.background = '#f1f5f9';
            });
            div.addEventListener('mouseout', () => {
                div.style.background = 'white';
            });
            div.textContent = item.name;
            div.addEventListener('click', () => {
                if (type === 'ACTOR') {
                    parts[parts.length - 1] = item.name;
                    input.value = parts.join(', ') + ', ';
                } else {
                    input.value = item.name;
                }
                dropdown.style.display = 'none';
                input.focus();
            });
            dropdown.appendChild(div);
        });

        dropdown.style.display = 'block';
    }
}

// --- Đăng ký sự kiện phim ---
function initMovieEvents() {
    document.getElementById('btnOpenAddModal').addEventListener('click',   () => openMovieModal(false));
    document.getElementById('btnCloseModal').addEventListener('click',     closeMovieModal);
    document.getElementById('btnCancelModal').addEventListener('click',    closeMovieModal);
    ['filterTitle', 'filterDirector', 'filterDuration'].forEach(id => {
        document.getElementById(id)?.addEventListener('input', applyMovieFilter);
    });
    ['filterStatus', 'filterReleaseDate'].forEach(id => {
        document.getElementById(id)?.addEventListener('change', applyMovieFilter);
    });
    document.getElementById('filterForm').addEventListener('submit', e => {
        e.preventDefault();
        applyMovieFilter();
    });

    // Khởi tạo tính năng gợi ý thông minh
    initAutocomplete('movieDirector', 'DIRECTOR');
    initAutocomplete('movieActors', 'ACTOR');
    initAutocomplete('movieProducer', 'PRODUCER');

    // Hỗ trợ nhấn phím Enter tự động SEARCH
    ['filterTitle', 'filterDirector', 'filterDuration', 'filterReleaseDate'].forEach(id => {
        const el = document.getElementById(id);
        if (el) {
            el.addEventListener('keyup', ev => {
                if (ev.key === 'Enter') {
                    applyMovieFilter();
                }
            });
        }
    });

    document.getElementById('movieForm').addEventListener('submit',        handleMovieSave);
    document.getElementById('btnCloseTrailerModal').addEventListener('click', closeTrailer);
    document.getElementById('moviePosterFile').addEventListener('change', handlePosterFilePreview);
    document.getElementById('moviePosterUrl').addEventListener('input', e => showPosterPreview(e.target.value.trim()));

    // Sự kiện upload video: nút chọn file
    document.getElementById('btnSelectVideo').addEventListener('click', () => {
        document.getElementById('videoFileInput').click();
    });
    // Khi người dùng chọn file qua hộp thoại
    document.getElementById('videoFileInput').addEventListener('change', e => {
        if (e.target.files.length > 0) handleVideoFileSelected(e.target.files[0]);
    });
    // Nút xóa video đã upload
    document.getElementById('btnRemoveVideo').addEventListener('click', removeUploadedVideo);

    // Sự kiện kéo thả file vào upload zone
    const zone = document.getElementById('videoUploadZone');
    zone.addEventListener('dragover',  e => { e.preventDefault(); zone.classList.add('drag-over'); });
    zone.addEventListener('dragleave', ()  => zone.classList.remove('drag-over'));
    zone.addEventListener('drop',      e  => {
        e.preventDefault();
        zone.classList.remove('drag-over');
        const file = e.dataTransfer.files[0];
        if (file) handleVideoFileSelected(file);
    });
    // Click vào vùng (không phải nút) cũng mở hộp chọn file
    zone.addEventListener('click', e => {
        if (e.target === zone || e.target.classList.contains('upload-zone-content') ||
            e.target.classList.contains('upload-hint-main') ||
            e.target.classList.contains('upload-hint-sub')) {
            document.getElementById('videoFileInput').click();
        }
    });

    // [MỚI - TrienLX - 2026-06-11]
    // Khi người dùng thay đổi ngày khởi chiếu, tự động gợi ý trạng thái phim
    // và hiển thị hint cho Manager biết hệ thống sẽ tính trạng thái nào.
    document.getElementById('movieReleaseDate').addEventListener('change', e => {
        const selectedDate = e.target.value; // Giá trị 'yyyy-MM-dd'
        if (!selectedDate) return;           // Bỏ qua nếu chưa chọn ngày

        const today    = new Date(); today.setHours(0, 0, 0, 0);      // Hôm nay 00:00:00
        const releaseD = new Date(selectedDate + 'T00:00:00');        // Ngày khởi chiếu 00:00:00

        // Lấy select trạng thái và hint label
        const statusSelect = document.getElementById('movieStatus');
        const statusHint   = document.getElementById('movieStatusHint');

        // Kiểm tra trạng thái đặc biệt — nếu đã chọn SPECIAL_SCREENING thì không tự ghi đè
        if (statusSelect.value === 'Suất chiếu đặc biệt') {
            if (statusHint) statusHint.textContent = 'Trạng thái đặc biệt do bạn chọn, hệ thống không tự đổi.';
            return;
        }

        // Tự động tính và SET trạng thái phù hợp
        if (releaseD <= today) {
            // Ngày chiếu đã đến hoặc đã qua → Đang chiếu
            statusSelect.value = 'Đang chiếu';
            if (statusHint) {
                statusHint.textContent = '✓ Hệ thống tự đặt: Đang chiếu (ngày khởi chiếu đã đến).';
                statusHint.style.color = 'var(--stat-green)';
            }
        } else {
            // Ngày chiếu trong tương lai → Sắp chiếu
            statusSelect.value = 'Sắp chiếu';
            if (statusHint) {
                statusHint.textContent = '✓ Hệ thống tự đặt: Sắp chiếu. Sẽ tự chuyển sang Đang chiếu khi đến ngày.';
                statusHint.style.color = 'var(--stat-orange)';
            }
        }
    });

    window.addEventListener('click', e => {
        if (e.target === document.getElementById('movieModal'))    closeMovieModal();
        if (e.target === document.getElementById('trailerModal'))  closeTrailer();
        if (e.target === document.getElementById('showtimeModal')) closeShowtimeModal();
    });
}

// --- Thống kê phim ---
async function loadMovieStats() {
    try {
        const r = await fetch(`${API_MOVIES}/stats`);
        if (!r.ok) return;
        const s = await r.json();
        document.getElementById('statTotal').textContent       = s.total       ?? 0;
        document.getElementById('statNowShowing').textContent  = s.nowShowing  ?? 0;
        document.getElementById('statUpcoming').textContent    = s.upcoming    ?? 0;
        document.getElementById('statSpecialShow').textContent = s.specialShow ?? 0;
        document.getElementById('statInactive').textContent    = s.inactive    ?? 0;
    } catch(e) { console.error('Lỗi thống kê phim:', e); }
}

// ==================== HỆ THỐNG SẮP XẾP PHIM ====================
let currentSortField = 'id';
let currentSortOrder = 'asc';

function toggleSort(field) {
    if (currentSortField === field) {
        currentSortOrder = currentSortOrder === 'asc' ? 'desc' : 'asc';
    } else {
        currentSortField = field;
        currentSortOrder = 'asc';
    }

    moviesData.sort((a, b) => {
        let valA = a[field];
        let valB = b[field];

        if (valA == null) valA = '';
        if (valB == null) valB = '';

        if (field === 'duration' || field === 'id' || field === 'releaseYear') {
            valA = Number(valA) || 0;
            valB = Number(valB) || 0;
        } else {
            valA = String(valA).toLowerCase();
            valB = String(valB).toLowerCase();
        }

        if (valA < valB) return currentSortOrder === 'asc' ? -1 : 1;
        if (valA > valB) return currentSortOrder === 'asc' ? 1 : -1;
        return 0;
    });

    moviesPage = 1;
    updateSortIcons();
    renderMovieTable();
}

function updateSortIcons() {
    const fields = ['id', 'title', 'genre', 'duration', 'format', 'status', 'releaseDate'];
    fields.forEach(f => {
        const icon = document.getElementById(`sort-icon-${f}`);
        if (!icon) return;
        if (f === currentSortField) {
            icon.className = currentSortOrder === 'asc' ? 'fa-solid fa-sort-up' : 'fa-solid fa-sort-down';
            icon.style.opacity = '1';
        } else {
            icon.className = 'fa-solid fa-sort';
            icon.style.opacity = '0.6';
        }
    });
}

// --- Tải danh sách phim ---
async function loadMovies(filters) {
    const qs = new URLSearchParams();
    Object.entries(filters).forEach(([k,v]) => { if (v != null && v !== '') qs.append(k, v); });
    try {
        const r = await fetch(`${API_MOVIES}${qs.toString() ? '?'+qs : ''}`);
        if (!r.ok) throw new Error();
        moviesData  = await r.json();
        
        if (currentSortField) {
            const tempField = currentSortField;
            const tempOrder = currentSortOrder;
            currentSortField = ''; // Reset để ép toggleSort áp dụng đúng chiều
            currentSortOrder = tempOrder === 'asc' ? 'desc' : 'asc'; // toggleSort sẽ đảo ngược lại đúng chiều
            toggleSort(tempField);
        } else {
            moviesPage  = 1;
            renderMovieTable();
        }
        document.getElementById('resultsCount').textContent = `Tìm thấy ${moviesData.length} kết quả`;
    } catch(e) {
        document.getElementById('movieTableBody').innerHTML =
            `<tr><td colspan="8" class="text-center" style="padding:3rem;color:var(--stat-red);">
                <i class="fa-solid fa-triangle-exclamation"></i> Không thể tải danh sách phim.
            </td></tr>`;
    }
}

// --- Render bảng phim ---
function renderMovieTable() {
    const tbody = document.getElementById('movieTableBody');
    tbody.innerHTML = '';

    if (!moviesData.length) {
        tbody.innerHTML = `<tr><td colspan="9" class="text-center" style="padding:3rem;color:var(--text-muted);">
            <i class="fa-regular fa-folder-open" style="font-size:2rem;display:block;margin-bottom:.5rem;"></i>
            Không tìm thấy phim nào.
        </td></tr>`;
        buildPagination('paginationControls','paginationInfo', 0, 1, ()=>{});
        return;
    }

    const start   = (moviesPage - 1) * PAGE_SIZE;
    const slice   = moviesData.slice(start, start + PAGE_SIZE);
    const colorMap = { 'Đang chiếu':'var(--stat-green)', 'Sắp chiếu':'var(--stat-orange)', 'Suất chiếu đặc biệt':'var(--stat-red)' };

    slice.forEach(mv => {
        const isActive = mv.active !== false; // mặc định true nếu không có field

        const poster = mv.posterUrl
            ? `<img class="movie-poster-thumb" src="${esc(mv.posterUrl)}" alt="Poster"
                    onerror="this.outerHTML='<div class=\\'movie-poster-placeholder\\'><i class=\\'fa-regular fa-image\\'></i></div>'">`
            : `<div class="movie-poster-placeholder"><i class="fa-regular fa-image"></i></div>`;
        const trailer = mv.trailerUrl
            ? `<button class="badge-trailer" onclick="openTrailer('${esc(mv.trailerUrl)}')">
                    <i class="fa-solid fa-play"></i> Trailer</button>` : '';

        // Badge + nút toggle trạng thái hiển thị
        const activeBadge = isActive
            ? `<span style="display:inline-flex;align-items:center;gap:4px;background:#d1fae5;color:#065f46;padding:3px 10px;border-radius:20px;font-size:.78rem;font-weight:600;">
                   <i class="fa-solid fa-eye"></i> Hiển thị
               </span>`
            : `<span style="display:inline-flex;align-items:center;gap:4px;background:#fee2e2;color:#991b1b;padding:3px 10px;border-radius:20px;font-size:.78rem;font-weight:600;">
                   <i class="fa-solid fa-eye-slash"></i> Đang ẩn
               </span>`;

        const toggleBtn = isActive
            ? `<button class="action-btn" style="background:#fef3c7;color:#92400e;border:1px solid #fcd34d;font-size:.75rem;padding:3px 8px;border-radius:6px;cursor:pointer;margin-top:4px;"
                       onclick="toggleMovieActive(${mv.id}, true)" title="Nhấn để tạm ẩn phim này">
                   <i class="fa-solid fa-toggle-on"></i> Tạm ẩn
               </button>`
            : `<button class="action-btn" style="background:#dcfce7;color:#166534;border:1px solid #86efac;font-size:.75rem;padding:3px 8px;border-radius:6px;cursor:pointer;margin-top:4px;"
                       onclick="toggleMovieActive(${mv.id}, false)" title="Nhấn để kích hoạt hiển thị lại">
                   <i class="fa-solid fa-toggle-off"></i> Mở lại
               </button>`;

        const tr = document.createElement('tr');
        // Làm mờ hàng nếu phim đang bị ẩn để Admin dễ phân biệt
        if (!isActive) tr.style.opacity = '0.55';

        tr.innerHTML = `
            <td style="font-weight: 600; color: var(--text-main); font-size: 0.9rem;">MV-${mv.id}</td>
            <td>
                <div class="movie-info-cell">
                    ${poster}
                    <div class="movie-meta">
                        <div class="movie-title-row">
                            <span class="movie-title-text" style="font-weight: 700;">${esc(mv.title)}</span>
                            ${trailer}
                        </div>
                        <span class="movie-director-text" style="font-size: 0.75rem; margin-top: 0.2rem;">Đạo diễn: ${esc(mv.director||'Chưa rõ')} | NSX: ${esc(mv.producer||'—')} (${mv.releaseYear||''})</span>
                        <span class="movie-director-text" style="font-size: 0.75rem; font-weight: 600; color: var(--stat-red); margin-top: 0.1rem;">Độ tuổi: ${esc(mv.ageRating||'—')} | Ngôn ngữ: ${esc(mv.language||'—')}</span>
                    </div>
                </div>
            </td>
            <td>${esc(mv.genre||'—')}</td>
            <td>${mv.duration ? mv.duration+' phút' : '—'}</td>
            <td><span style="font-weight: 600; color: var(--text-main);">${esc(mv.format||'2D')}</span></td>
            <td><span class="status-text" style="color:${isActive ? (colorMap[mv.status]||'inherit') : '#64748b'}">${isActive ? esc(mv.status||'—') : 'Ngừng chiếu'}</span></td>
            <td class="text-center">
                <div style="display:flex;flex-direction:column;align-items:center;gap:2px;">
                    ${activeBadge}
                    ${toggleBtn}
                </div>
            </td>
            <td>${formatDate(mv.releaseDate)}</td>
            <td>
                <div class="action-cell">
                    <button class="action-btn action-btn-edit"   onclick="editMovie(${mv.id})">Sửa</button>
                    <button class="action-btn action-btn-delete" onclick="deleteMovie(${mv.id})">Xóa</button>
                </div>
            </td>`;
        tbody.appendChild(tr);
    });

    buildPagination('paginationControls','paginationInfo', moviesData.length, moviesPage, p => {
        moviesPage = p; renderMovieTable();
    });
}

// --- Bộ lọc phim ---
function applyMovieFilter() {
    const checkedGenres = Array.from(document.querySelectorAll('input[name="filterGenreVal"]:checked'))
        .map(cb => cb.value);
    loadMovies({
        title:       document.getElementById('filterTitle').value.trim(),
        genre:       checkedGenres.join(','),
        director:    document.getElementById('filterDirector').value.trim(),
        duration:    document.getElementById('filterDuration').value.trim() || null,
        status:      document.getElementById('filterStatus').value,
        releaseDate: document.getElementById('filterReleaseDate').value || null
    });
}
function resetMovieFilter() {
    ['filterTitle','filterDirector','filterDuration','filterReleaseDate']
        .forEach(id => document.getElementById(id).value = '');
    document.getElementById('filterStatus').value = '';
    document.querySelectorAll('input[name="filterGenreVal"]').forEach(cb => cb.checked = false);
    loadMovies({});
}

// --- Modal phim ---
// [MỚI - TrienLX - 2026-06-11] Đặt ngày tối thiểu cho input ngày khởi chiếu = hôm nay
// (không cho phép chọn ngày trong quá khứ)
function setMinReleaseDateToday() {
    const input = document.getElementById('movieReleaseDate');
    if (!input) return;
    // Định dạng yyyy-MM-dd theo chuẩn HTML date input
    const today = new Date();
    const yyyy  = today.getFullYear();
    const mm    = String(today.getMonth() + 1).padStart(2, '0');
    const dd    = String(today.getDate()).padStart(2, '0');
    input.min = `${yyyy}-${mm}-${dd}`; // Giới hạn tối thiểu là ngày hôm nay
}

function openMovieModal(isEdit) {
    document.getElementById('modalTitle').textContent = isEdit ? 'Sửa thông tin phim' : 'Thêm phim mới';
    if (!isEdit) {
        document.getElementById('movieForm').reset();
        document.getElementById('movieParamId').value = '';
        showPosterPreview('');
        resetUploadZone();
        document.querySelectorAll('input[name="movieGenreVal"]').forEach(cb => cb.checked = false);
        const hint = document.getElementById('movieStatusHint');
        if (hint) { hint.textContent = ''; }
    }
    setMinReleaseDateToday();
    document.getElementById('movieModal').classList.add('show');
}
function closeMovieModal() { document.getElementById('movieModal').classList.remove('show'); }

// --- Lưu phim (có upload video nếu cần) ---
async function handleMovieSave(e) {
    e.preventDefault();
    const id        = document.getElementById('movieParamId').value;
    const trailerUrl = document.getElementById('movieTrailerUrl').value; // URL từ upload

    const checkedGenres = Array.from(document.querySelectorAll('input[name="movieGenreVal"]:checked'))
        .map(cb => cb.value);

    const body = {
        title:       document.getElementById('movieTitle').value.trim(),
        trailerUrl:  trailerUrl || null,
        summary:     document.getElementById('movieSummary').value.trim()   || null,
        genre:       checkedGenres.join(', '),
        duration:    document.getElementById('movieDuration').value
                        ? parseInt(document.getElementById('movieDuration').value) : null,
        director:    document.getElementById('movieDirector').value.trim()  || null,
        language:    document.getElementById('movieLanguage').value         || null,
        actors:      document.getElementById('movieActors').value.trim()    || null,
        posterUrl:   document.getElementById('moviePosterUrl').value.trim() || null,
        releaseDate: document.getElementById('movieReleaseDate').value      || null,
        status:      document.getElementById('movieStatus').value,
        releaseYear: document.getElementById('movieReleaseYear').value
                        ? parseInt(document.getElementById('movieReleaseYear').value) : null,
        producer:    document.getElementById('movieProducer').value.trim()  || null,
        ageRating:   document.getElementById('movieAgeRating').value        || null,
        format:      document.getElementById('movieFormat').value           || null
    };

    const posterUrlValue = document.getElementById('moviePosterUrl').value.trim();
    const hasPosterFile = document.getElementById('moviePosterFile').files.length > 0;

    if (!body.title || !body.summary || !body.genre || !body.duration ||
        !body.director || !body.language || !body.actors || !body.releaseDate ||
        !body.status || !body.trailerUrl || (!posterUrlValue && !hasPosterFile) ||
        !body.releaseYear || !body.producer || !body.ageRating || !body.format) {
        showToast('warning', 'Thiếu thông tin', 'Vui lòng điền đầy đủ tất cả các trường thông tin!');
        return;
    }

    if (isNaN(body.duration) || body.duration <= 0) {
        showToast('warning', 'Thời lượng không hợp lệ', 'Thời lượng phim phải là số lớn hơn 0!');
        return;
    }

    if (isNaN(body.releaseYear) || body.releaseYear < 1800 || body.releaseYear > 2100) {
        showToast('warning', 'Năm phát hành không hợp lệ', 'Năm phát hành phải từ năm 1800 đến 2100!');
        return;
    }

    if (body.releaseDate) {
        const today    = new Date(); today.setHours(0, 0, 0, 0);
        const releaseD = new Date(body.releaseDate + 'T00:00:00');
        if (releaseD < today) {
            showToast('warning', 'Ngày khởi chiếu không hợp lệ',
                'Ngày khởi chiếu không được là ngày trong quá khứ. Vui lòng chọn từ hôm nay trở đi.');
            return;
        }
    }

    try {
        // Upload ảnh poster (nếu chọn từ máy) và lấy URL công khai từ server
        body.posterUrl = await resolvePosterUrl();
        if (!body.posterUrl) {
            showToast('warning', 'Thiếu ảnh poster', 'Vui lòng cung cấp ảnh poster phim!');
            return;
        }

        const url    = id ? `${API_MOVIES}/${id}` : API_MOVIES;
        const method = id ? 'PUT' : 'POST';
        const r = await fetch(url, {
            method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });
        if (!r.ok) {
            const payload = await r.json().catch(() => ({}));
            throw new Error(payload.message || payload.error || 'Lỗi khi lưu phim. Vui lòng thử lại.');
        }
        showToast('success', id ? 'Cập nhật thành công!' : 'Thêm phim thành công!',
                  id ? 'Thông tin phim đã được cập nhật.' : 'Phim mới đã được thêm vào hệ thống.');
        closeMovieModal();
        loadMovieStats();
        applyMovieFilter();
    } catch(err) {
        showToast('error', 'Lưu phim thất bại', err.message || 'Lỗi khi lưu phim. Vui lòng thử lại.');
    }
}

// --- Sửa phim (khôi phục trạng thái upload nếu có video) ---
async function editMovie(id) {
    try {
        const r  = await fetch(`${API_MOVIES}/${id}`);
        const mv = await r.json();
        document.getElementById('movieParamId').value   = mv.id;
        document.getElementById('movieTitle').value     = mv.title       || '';
        document.getElementById('movieSummary').value   = mv.summary     || '';
        const genres = (mv.genre || '').split(',').map(s => s.trim());
        document.querySelectorAll('input[name="movieGenreVal"]').forEach(cb => {
            cb.checked = genres.includes(cb.value);
        });
        document.getElementById('movieDuration').value  = mv.duration    || '';
        document.getElementById('movieDirector').value  = mv.director    || '';
        document.getElementById('movieLanguage').value  = mv.language    || '';
        document.getElementById('movieProducer').value  = mv.producer    || '';
        document.getElementById('movieReleaseYear').value = mv.releaseYear || '';
        document.getElementById('movieAgeRating').value = mv.ageRating   || '';
        document.getElementById('movieActors').value    = mv.actors      || '';
        document.getElementById('moviePosterUrl').value = mv.posterUrl   || '';
        document.getElementById('movieFormat').value    = mv.format      || '';
        showPosterPreview(mv.posterUrl || '');
        setMinReleaseDateToday();
        document.getElementById('movieReleaseDate').value = mv.releaseDate || '';
        document.getElementById('movieStatus').value    = mv.status      || 'Đang chiếu';
        // Cập nhật hint trạng thái theo ngày hiện tại của phim
        const hint = document.getElementById('movieStatusHint');
        if (hint) {
            hint.textContent = `Trạng thái hiện tại: ${mv.status || '—'} (hệ thống sẽ tự cập nhật theo ngày khởi chiếu).`;
            hint.style.color = 'var(--text-muted)';
        }

        // Nếu phim đã có video — khôi phục trạng thái preview trong upload zone
        const trailerUrl = mv.trailerUrl || '';
        document.getElementById('movieTrailerUrl').value = trailerUrl;
        if (trailerUrl) {
            const fileName = trailerUrl.split('/').pop(); // lấy tên file từ URL
            showUploadPreview(fileName);
        } else {
            resetUploadZone();
        }

        openMovieModal(true);
    } catch(e) {
        // [SỬA - TrienLX - 2026-06-11]: Thay alert() bằng showToast()
        showToast('error', 'Không thể tải thông tin phim', 'Vui lòng thử lại hoặc kiểm tra kết nối.');
    }
}

function handlePosterFilePreview(e) {
    const file = e.target.files && e.target.files[0];
    if (!file) return;
    // Kiểm tra định dạng file phải là ảnh (image/*)
    if (!file.type.startsWith('image/')) {
        // [SỬA - TrienLX - 2026-06-11]: Thay alert() bằng showToast()
        showToast('warning', 'Định dạng không hợp lệ', 'Vui lòng chọn file ảnh hợp lệ (JPG, PNG, WebP).');
        e.target.value = ''; // Xóa lựa chọn file không hợp lệ
        return;
    }
    showPosterPreview(URL.createObjectURL(file));
}

function showPosterPreview(src) {
    const img = document.getElementById('moviePosterPreview');
    const placeholder = document.getElementById('moviePosterPlaceholder');
    if (!img || !placeholder) return;

    if (src) {
        img.src = src;
        img.style.display = '';
        placeholder.style.display = 'none';
    } else {
        img.removeAttribute('src');
        img.style.display = 'none';
        placeholder.style.display = '';
    }
}

async function resolvePosterUrl() {
    const fileInput = document.getElementById('moviePosterFile');
    const posterUrlInput = document.getElementById('moviePosterUrl');
    const file = fileInput.files && fileInput.files[0];

    if (!file) {
        return posterUrlInput.value.trim();
    }

    const data = new FormData();
    data.append('file', file);
    const response = await fetch('/api/upload/image', { method: 'POST', body: data });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) {
        throw new Error(payload.error || 'Không thể upload ảnh poster.');
    }

    posterUrlInput.value = payload.url;
    fileInput.value = '';
    showPosterPreview(payload.url);
    return payload.url;
}

// (Hàm handlePosterFilePreview, showPosterPreview, resolvePosterUrl đã được định nghĩa ở trên — không lặp lại)

// --- Xóa phim ---
// --- Xóa phim (soft delete: chỉ đổi trạng thái active = false, không xóa dữ liệu khỏi DB) ---
async function deleteMovie(id) {
    // [SỬA - TrienLX - 2026-06-11]: Thay confirm() bằng showConfirm() — hộp thoại xác nhận tùy chỉnh
    const confirmed = await showConfirm(
        'Xác nhận xóa phim',                          // Tiêu đề hộp thoại
        'Bạn có chắc chắn muốn xóa phim này không?\nPhim sẽ bị ẩn khỏi hệ thống (không bị xóa hoàn toàn khỏi dữ liệu).', // Nội dung
        'Xóa phim'                                    // Nhãn nút đồng ý
    );
    if (!confirmed) return; // Người dùng nhấn Hủy — không làm gì

    try {
        // Gửi yêu cầu xóa (soft delete) lên API
        const r = await fetch(`${API_MOVIES}/${id}`, { method: 'DELETE' });
        if (!r.ok) throw new Error();
        // [SỬA - TrienLX - 2026-06-11]: Thay alert() bằng showToast() — thông báo thành công
        showToast('success', 'Xóa phim thành công!', 'Phim đã được ẩn khỏi hệ thống.');
        loadMovieStats();    // Cập nhật lại thống kê
        applyMovieFilter();  // Cập nhật lại danh sách phim
    } catch(e) {
        // [SỬA - TrienLX - 2026-06-11]: Thay alert() bằng showToast()
        showToast('error', 'Lỗi khi xóa phim', 'Không thể xóa phim. Vui lòng thử lại.');
    }
}

// --- Bật / Tắt hiển thị phim (active ↔ inactive) ---
// currentActive: true  → phim đang hiển thị, nhấn để tạm ẩn
// currentActive: false → phim đang ẩn, nhấn để mở lại
async function toggleMovieActive(id, currentActive) {
    const action  = currentActive ? 'tạm ẩn' : 'hiển thị lại';
    const title   = currentActive ? 'Xác nhận tạm ẩn phim' : 'Xác nhận mở lại phim';
    const message = currentActive
        ? 'Phim sẽ bị ẩn khỏi trang chủ và danh sách chiếu phim của khách hàng.\nDữ liệu phim và lịch chiếu vẫn được giữ nguyên trong hệ thống.'
        : 'Phim sẽ được hiển thị lại trên trang chủ và danh sách chiếu phim cho khách hàng.';
    const confirmed = await showConfirm(title, message, `Xác nhận ${action}`);
    if (!confirmed) return;

    try {
        const r = await fetch(`${API_MOVIES}/${id}/toggle-active`, { method: 'PATCH' });
        if (!r.ok) throw new Error();
        const result = currentActive
            ? { type:'warning', title:'Đã tạm ẩn phim!', msg:'Phim đã được ẩn khỏi giao diện khách hàng.' }
            : { type:'success', title:'Đã mở lại phim!', msg:'Phim đã hiển thị trở lại cho khách hàng.' };
        showToast(result.type, result.title, result.msg);
        loadMovieStats();
        applyMovieFilter();
    } catch(e) {
        showToast('error', 'Lỗi hệ thống', `Không thể ${action} phim. Vui lòng thử lại.`);
    }
}


// =====================================================
//   UPLOAD VIDEO
// =====================================================

// Xử lý khi người dùng chọn file (qua hộp thoại hoặc kéo thả)
function handleVideoFileSelected(file) {
    // Kiểm tra định dạng file video hợp lệ
    // Danh sách MIME type video được hỗ trợ
    const allowed = ['video/mp4', 'video/webm', 'video/x-matroska', 'video/avi', 'video/quicktime'];
    // Kiểm tra định dạng file — theo cả MIME type và phần mở rộng tên file
    if (!allowed.includes(file.type) && !file.name.match(/\.(mp4|webm|mkv|avi|mov)$/i)) {
        // [SỬA - TrienLX - 2026-06-11]: Thay alert() bằng showToast()
        showToast('warning', 'Định dạng không hỗ trợ', 'Vui lòng chọn file MP4, WebM, MKV, AVI hoặc MOV.');
        return;
    }
    // Kiểm tra giới hạn kích thước tối đa 50MB (50 × 1024 × 1024 bytes)
    if (file.size > 50 * 1024 * 1024) {
        // [SỬA - TrienLX - 2026-06-11]: Thay alert() bằng showToast()
        showToast('error', 'File quá lớn!', 'Kích thước tối đa cho phép là 50MB.');
        return;
    }
    uploadVideoFile(file);
}

// Upload file lên server bằng XMLHttpRequest (để theo dõi tiến trình)
function uploadVideoFile(file) {
    showUploadProgress();

    const formData = new FormData();
    formData.append('file', file);

    const xhr = new XMLHttpRequest();

    // Theo dõi tiến trình upload
    xhr.upload.onprogress = e => {
        if (e.lengthComputable) {
            const pct = Math.round(e.loaded / e.total * 100);
            document.getElementById('uploadProgressText').textContent =
                `Đang tải lên... ${pct}%`;
        }
    };

    xhr.onload = () => {
        if (xhr.status === 200) {
            const data = JSON.parse(xhr.responseText);
            // Lưu URL trả về vào hidden input để dùng khi lưu phim
            document.getElementById('movieTrailerUrl').value = data.url;
            showUploadPreview(file.name);
        } else {
            // Lấy thông báo lỗi từ server nếu có, ngược lại dùng thông báo chung
            let msg = 'Upload thất bại.';
            try { msg = JSON.parse(xhr.responseText).error || msg; } catch(_) {}
            // [SỬA - TrienLX - 2026-06-11]: Thay alert() bằng showToast()
            showToast('error', 'Upload thất bại', msg);
            resetUploadZone(); // Đặt lại vùng upload về trạng thái ban đầu
        }
    };

    // Xử lý lỗi mạng (mất kết nối, server không phản hồi)
    xhr.onerror = () => {
        // [SỬA - TrienLX - 2026-06-11]: Thay alert() bằng showToast()
        showToast('error', 'Lỗi kết nối', 'Không thể kết nối đến server. Vui lòng thử lại.');
        resetUploadZone(); // Đặt lại vùng upload
    };

    xhr.open('POST', '/api/upload/video');
    xhr.send(formData);
}

// Hiển thị thanh tiến trình đang chạy
function showUploadProgress() {
    document.getElementById('uploadZoneContent').style.display = 'none';
    document.getElementById('uploadPreview').style.display     = 'none';
    document.getElementById('uploadProgress').style.display    = '';
}

// Hiển thị trạng thái đã upload xong
function showUploadPreview(fileName) {
    document.getElementById('uploadProgress').style.display    = 'none';
    document.getElementById('uploadZoneContent').style.display = 'none';
    document.getElementById('uploadPreviewName').textContent   = fileName;
    document.getElementById('uploadPreview').style.display     = '';
}

// Đặt lại upload zone về trạng thái ban đầu
function resetUploadZone() {
    document.getElementById('uploadProgress').style.display    = 'none';
    document.getElementById('uploadPreview').style.display     = 'none';
    document.getElementById('uploadZoneContent').style.display = '';
    document.getElementById('movieTrailerUrl').value           = '';
    document.getElementById('videoFileInput').value            = '';
}

// Xóa video đã upload và khôi phục upload zone về trạng thái ban đầu
async function removeUploadedVideo() {
    // [SỬA - TrienLX - 2026-06-11]: Thay confirm() bằng showConfirm() — hộp thoại xác nhận tùy chỉnh
    const confirmed = await showConfirm(
        'Xóa video trailer',            // Tiêu đề hộp thoại
        'Bạn có chắc chắn muốn bỏ video trailer này không?', // Nội dung
        'Xóa video'                     // Nhãn nút đồng ý
    );
    if (!confirmed) return; // Người dùng nhấn Hủy — giữ nguyên video
    resetUploadZone();      // Đặt lại upload zone và xóa URL đã lưu
}

// --- Mở trình phát trailer (tự phát hiện YouTube hoặc video cục bộ) ---
function openTrailer(url) {
    // Nếu không có URL trailer thì hiển thị thông báo lỗi và dừng lại
    if (!url) {
        // [SỬA - TrienLX - 2026-06-11]: Thay alert() bằng showToast()
        showToast('info', 'Chưa có trailer', 'Phim này chưa có video trailer.');
        return;
    }

    const youtubeId = extractYouTubeId(url);
    const modal     = document.getElementById('trailerModal');
    const ytCont    = document.getElementById('youtubeContainer');
    const lcCont    = document.getElementById('localVideoContainer');

    if (youtubeId) {
        // Phát YouTube qua iframe embed
        document.getElementById('trailerModalTitle').textContent = 'Xem Trailer (YouTube)';
        document.getElementById('trailerIframe').src =
            `https://www.youtube.com/embed/${youtubeId}?autoplay=1`;
        ytCont.style.display = '';
        lcCont.style.display = 'none';
    } else {
        // Phát video cục bộ qua HTML5 player
        document.getElementById('trailerModalTitle').textContent = 'Xem Trailer';
        const player = document.getElementById('localVideoPlayer');
        player.src = url;
        player.load();
        player.play().catch(() => {}); // Tự động phát, bỏ qua lỗi autoplay
        ytCont.style.display = 'none';
        lcCont.style.display = '';
    }

    modal.classList.add('show');
}

// --- Đóng trình phát trailer ---
// [SỬA - TrienLX - 2026-06-12] Giải phóng nguồn phát của HTML5 video để tránh lỗi không phát được ở lần tiếp theo
function closeTrailer() {
    document.getElementById('trailerModal').classList.remove('show');
    // Dừng YouTube bằng cách xóa URL nguồn trong iframe
    document.getElementById('trailerIframe').src = '';
    // Dừng video HTML5
    const player = document.getElementById('localVideoPlayer');
    player.pause();
    // Thay vì gán src = '', ta loại bỏ thuộc tính src và load lại để trình duyệt reset hoàn toàn trạng thái player
    player.removeAttribute('src');
    player.load();
}

// ======================================================
//   QUẢN LÝ LỊCH CHIẾU
// ======================================================

// Hàm tiện ích: đặt min date = hôm nay cho input ngày chiếu (không cho chọn quá khứ)
function setMinShowtimeDateToday(inputId) {
    const input = document.getElementById(inputId);
    if (!input) return;
    const today = isoDate(new Date());
    input.min = today;
}

function initShowtimeEvents() {
    document.getElementById('btnOpenAddShowtimeModal').addEventListener('click', () => openShowtimeModal(false));
    document.getElementById('btnCloseShowtimeModal').addEventListener('click',   closeShowtimeModal);
    document.getElementById('btnCancelShowtimeModal').addEventListener('click',  closeShowtimeModal);
    ['filterShowtimeMovie', 'filterShowtimeViewMode', 'filterShowtimeDayType', 'filterShowtimeStatus', 'filterShowtimeDate'].forEach(id => {
        document.getElementById(id)?.addEventListener('change', applyShowtimeFilter);
    });
    document.getElementById('showtimeFilterForm').addEventListener('submit', e => {
        e.preventDefault();
        applyShowtimeFilter();
    });
    document.getElementById('showtimeForm').addEventListener('submit',           handleShowtimeSave);

    // Tự phát hiện loại ngày khi chọn ngày chiếu và đồng bộ Đến ngày khi thêm mới
    document.getElementById('showtimeDateInput').addEventListener('change', e => {
        const val = e.target.value;
        document.getElementById('showtimeDayTypeDisplay').value = detectDayType(val) || 'Chưa xác định';

        // Tự động đồng bộ Đến ngày = Từ ngày nếu thêm mới và chưa chọn Đến ngày
        const paramId = document.getElementById('showtimeParamId').value;
        if (!paramId) {
            const endInput = document.getElementById('showtimeEndDateInput');
            // Cập nhật min của Đến ngày theo Từ ngày
            if (val) endInput.min = val;
            if (val && (!endInput.value || endInput.value < val)) {
                endInput.value = val;
            }
        }
    });

    // [SỬA - TrienLX - 2026-06-23]: Đăng ký các sự kiện cho form điều chỉnh 1 ngày cụ thể
    document.getElementById('overrideDayForm')?.addEventListener('submit', handleOverrideDaySave);
    document.getElementById('btnCloseOverrideDayModal')?.addEventListener('click', closeOverrideDayModal);
    document.getElementById('btnCancelOverrideDayModal')?.addEventListener('click', closeOverrideDayModal);
    document.getElementById('btnOverrideDayFromEdit')?.addEventListener('click', handleOverrideDayFromEditClick);
}

// --- Thống kê lịch chiếu ---
async function loadShowtimeStats() {
    try {
        const r = await fetch(`${API_SHOWTIMES}/stats`);
        if (!r.ok) return;
        const s = await r.json();
        document.getElementById('statShowtimeTotal').textContent    = s.total    ?? 0;
        document.getElementById('statShowtimeActive').textContent   = s.active   ?? 0;
        document.getElementById('statShowtimeUpcoming').textContent = s.upcoming ?? 0;
        document.getElementById('statShowtimeEnded').textContent    = s.ended    ?? 0;
        document.getElementById('statShowtimeWeekday').textContent  = s.weekday  ?? 0;
        document.getElementById('statShowtimeWeekend').textContent  = s.weekend  ?? 0;
        document.getElementById('statShowtimeHoliday').textContent  = s.holiday  ?? 0;
    } catch(e) { console.error('Lỗi thống kê lịch chiếu:', e); }
}

// --- Nạp phim vào các Select dropdown (có cache để không bị lag) ---
async function populateMovieDropdowns(forceRefresh = false) {
    try {
        if (!_cachedMovieList || forceRefresh) {
            const r = await fetch(API_MOVIES);
            _cachedMovieList = await r.json();
        }
        const list = _cachedMovieList;

        const filterSel = document.getElementById('filterShowtimeMovie');
        const formSel   = document.getElementById('showtimeMovieSelect');
        if (filterSel) {
            filterSel.innerHTML = '<option value="">-- Tất cả phim --</option>';
            list.forEach(mv => {
                const opt = document.createElement('option');
                opt.value = mv.id; opt.textContent = mv.title;
                filterSel.appendChild(opt);
            });
        }
        if (formSel) {
            formSel.innerHTML = '<option value="">-- Chọn bộ phim --</option>';
            list.forEach(mv => {
                const opt = document.createElement('option');
                opt.value = mv.id; opt.textContent = mv.title;
                formSel.appendChild(opt);
            });
        }
    } catch(e) { console.error('Lỗi nạp dropdown phim:', e); }
}

// --- Nạp phòng chiếu (có cache để không bị lag) ---
// --- Nạp phòng chiếu (có cache để không bị lag) ---
// [SỬA - TrienLX - 2026-06-23]: Hỗ trợ nạp phòng cho cả form điều chỉnh 1 ngày
async function populateRoomDropdown(selectedRoomName = '', forceRefresh = false) {
    const roomSel = document.getElementById('showtimeRoomInput');
    const overrideSel = document.getElementById('overrideRoomInput');
    if (!roomSel && !overrideSel) return;

    try {
        if (!_cachedRoomList || forceRefresh) {
            const r = await fetch(API_ROOMS);
            if (!r.ok) throw new Error();
            _cachedRoomList = await r.json();
        }
        const rooms = _cachedRoomList;

        const populateOptions = (sel) => {
            if (!sel) return;
            sel.innerHTML = '<option value="">-- Chọn phòng --</option>';
            rooms.forEach(room => {
                const opt = document.createElement('option');
                opt.value = room.roomName || '';
                const details = [room.roomType, room.audioTech, room.totalSeats ? `${room.totalSeats} ghế` : '']
                    .filter(Boolean).join(' · ');
                opt.textContent = details ? `${room.roomName} (${details})` : room.roomName;
                sel.appendChild(opt);
            });
            if (selectedRoomName && [...sel.options].some(o => o.value === selectedRoomName)) {
                sel.value = selectedRoomName;
            }
        };

        populateOptions(roomSel);
        populateOptions(overrideSel);
    } catch (e) {
        console.error('Lỗi nạp danh sách phòng chiếu:', e);
        if (roomSel) roomSel.innerHTML = '<option value="">Không thể tải danh sách phòng</option>';
        if (overrideSel) overrideSel.innerHTML = '<option value="">Không thể tải danh sách phòng</option>';
    }
}

// --- Tải lịch chiếu ---
async function loadShowtimes(filters) {
    const qs = new URLSearchParams();
    const { viewMode, movieId, dayType, startDate, endDate } = filters;

    if (movieId) qs.append('movieId', movieId);
    if (dayType)  qs.append('dayType', dayType);

    // Xử lý chế độ xem theo tuần / tháng
    if (viewMode === 'week') {
        const { startDate: sd, endDate: ed } = getWeekRange();
        qs.append('startDate', sd); qs.append('endDate', ed);
    } else if (viewMode === 'month') {
        const { startDate: sd, endDate: ed } = getMonthRange();
        qs.append('startDate', sd); qs.append('endDate', ed);
    } else {
        // Lọc theo ngày cụ thể (startDate=endDate)
        if (startDate) { qs.append('startDate', startDate); qs.append('endDate', startDate); }
        if (endDate && endDate !== startDate) qs.append('endDate', endDate);
    }

    try {
        const r = await fetch(`${API_SHOWTIMES}${qs.toString() ? '?'+qs : ''}`);
        if (!r.ok) throw new Error();
        showtimesData  = await r.json();
        showtimesPage  = 1;
        document.getElementById('showtimeResultsCount').textContent =
            `Tìm thấy ${showtimesData.length} lịch chiếu`;
        renderShowtimeTable();
    } catch(e) {
        document.getElementById('showtimeTableBody').innerHTML =
            `<tr><td colspan="7" class="text-center" style="padding:3rem;color:var(--stat-red);">
                <i class="fa-solid fa-triangle-exclamation"></i> Không thể tải lịch chiếu.
            </td></tr>`;
    }
}

// ==================== NHÓM LỊCH CHIẾU THEO PHIM + PHÒNG + DẢI NGÀY ====================
// Nhóm theo movieId|room|minDate|maxDate để tất cả slot cùng phim+phòng+dải ngày
// hiển thị gọn trên 1 hàng, các khung giờ hiển thị dưới dạng badge
function groupShowtimes(list) {
    // Bước 1: Gom các suất chiếu cùng phim+phòng+giờ+note thành slot (dải ngày)
    // [SỬA - TrienLX - 2026-06-23]: Bổ sung st.note vào key gom nhóm để phân biệt suất chiếu đã điều chỉnh
    const slotMap = new Map();
    list.forEach(st => {
        const mv  = st.movie || {};
        const key = `${mv.id||'?'}|${st.room||''}|${st.showTime||''}|${st.note||''}`;
        if (!slotMap.has(key)) {
            slotMap.set(key, {
                showTime: st.showTime,
                dayType:  st.dayType,
                minDate:  st.showDate,
                maxDate:  st.showDate,
                ids:      [st.id],
                note:     st.note,
                hasOverride: !!(st.override || st.isOverride),
                entries:  [{
                    id: st.id,
                    showDate: st.showDate,
                    showTime: st.showTime,
                    hasOverride: !!(st.override || st.isOverride)
                }]
            });
        } else {
            const slot = slotMap.get(key);
            if (st.showDate < slot.minDate) slot.minDate = st.showDate;
            if (st.showDate > slot.maxDate) slot.maxDate = st.showDate;
            slot.ids.push(st.id);
            slot.hasOverride = slot.hasOverride || !!(st.override || st.isOverride);
            slot.entries.push({
                id: st.id,
                showDate: st.showDate,
                showTime: st.showTime,
                hasOverride: !!(st.override || st.isOverride)
            });
        }
    });

    // Bước 2: Gom các slot cùng phim+phòng+dải ngày+note thành 1 nhóm duy nhất
    const groupMap = new Map();
    slotMap.forEach(slot => {
        // Lấy thông tin phim/phòng từ suất chiếu đầu tiên của slot này
        const firstSt = list.find(s => slot.ids.includes(s.id));
        if (!firstSt) return;
        const mv = firstSt.movie || {};
        // Key nhóm: phim + phòng + dải ngày + note (để slot đã điều chỉnh hiển thị riêng biệt)
        const gKey = `${mv.id||'?'}|${firstSt.room||''}|${slot.minDate}|${slot.maxDate}|${slot.note||''}`;
        if (!groupMap.has(gKey)) {
            groupMap.set(gKey, {
                movie:   mv,
                room:    firstSt.room,
                dayType: slot.dayType,
                minDate: slot.minDate,
                maxDate: slot.maxDate,
                note:    slot.note,
                hasOverride: slot.hasOverride,
                ids:     [...slot.ids],
                slots:   [{
                    showTime: slot.showTime,
                    ids: slot.ids,
                    note: slot.note,
                    hasOverride: slot.hasOverride,
                    entries: slot.entries
                }]
            });
        } else {
            const g = groupMap.get(gKey);
            g.ids.push(...slot.ids);
            g.hasOverride = g.hasOverride || slot.hasOverride;
            g.slots.push({
                showTime: slot.showTime,
                ids: slot.ids,
                note: slot.note,
                hasOverride: slot.hasOverride,
                entries: slot.entries
            });
            // Cập nhật dải ngày nếu cần
            if (slot.minDate < g.minDate) g.minDate = slot.minDate;
            if (slot.maxDate > g.maxDate) g.maxDate = slot.maxDate;
        }
    });

    // Sắp xếp các slot trong mỗi nhóm theo giờ chiếu tăng dần
    groupMap.forEach(g => {
        g.slots.sort((a, b) => (a.showTime || '').localeCompare(b.showTime || ''));
        g.slots.forEach(slot => {
            slot.entries.sort((a, b) => {
                const byDate = (a.showDate || '').localeCompare(b.showDate || '');
                return byDate !== 0 ? byDate : (a.showTime || '').localeCompare(b.showTime || '');
            });
        });
    });

    return Array.from(groupMap.values());
}

// --- Render bảng lịch chiếu (hiển thị dải ngày) ---
async function renderShowtimeTable() {
    const tbody = document.getElementById('showtimeTableBody');
    tbody.innerHTML = '';

    if (!showtimesData.length) {
        tbody.innerHTML = `<tr><td colspan="8" class="text-center" style="padding:3rem;color:var(--text-muted);">
            <i class="fa-regular fa-calendar-xmark" style="font-size:2rem;display:block;margin-bottom:.5rem;"></i>
            Không tìm thấy lịch chiếu nào.
        </td></tr>`;
        buildPagination('showtimePaginationControls','showtimePaginationInfo', 0, 1, ()=>{});
        return;
    }

    // Nhóm lịch chiếu theo phim + phòng + dải ngày
    let groups = groupShowtimes(showtimesData);

    // Lọc theo trạng thái lịch chiếu
    const statusVal = document.getElementById('filterShowtimeStatus')?.value;
    if (statusVal) {
        const todayStr = new Date().toLocaleDateString('sv'); // 'yyyy-MM-dd'
        groups = groups.filter(g => {
            if (statusVal === 'Đã kết thúc') {
                return g.maxDate < todayStr;
            } else if (statusVal === 'Sắp chiếu') {
                return g.minDate > todayStr;
            } else if (statusVal === 'Đang hoạt động') {
                return g.minDate <= todayStr && g.maxDate >= todayStr;
            }
            return true;
        });
    }

    // Cập nhật số lượng hiển thị sau lọc
    const countEl = document.getElementById('showtimeResultsCount');
    if (countEl) {
        countEl.textContent = `Tìm thấy ${groups.length} lịch chiếu`;
    }

    const start  = (showtimesPage - 1) * PAGE_SIZE;
    const slice  = groups.slice(start, start + PAGE_SIZE);
    const badgeMap = { 'Trong tuần':'badge-weekday', 'Cuối tuần':'badge-weekend', 'Ngày lễ':'badge-holiday' };

    // Lấy thống kê ghế từ suất chiếu đầu tiên trong mỗi nhóm (không chặn render)
    const statsPromises = slice.map(g =>
        fetch(`${API_TICKETS}/stats/${g.ids[0]}`).then(r => r.ok ? r.json() : null).catch(() => null)
    );
    const statsResults = await Promise.all(statsPromises);

    slice.forEach((g, idx) => {
        const mv = g.movie;
        const poster = mv.posterUrl
            ? `<img class="movie-poster-thumb" src="${esc(mv.posterUrl)}" alt="Poster"
                    onerror="this.outerHTML='<div class=\\'movie-poster-placeholder\\'><i class=\\'fa-regular fa-image\\'></i></div>'">`
            : `<div class="movie-poster-placeholder"><i class="fa-regular fa-image"></i></div>`;

        // [SỬA - TrienLX - 2026-06-23]: Hiển thị label cảnh báo nếu nhóm có ít nhất một suất đã điều chỉnh (isOverride=true)
        const isAdjustedGroup = g.hasOverride;
        const adjustedLabel = isAdjustedGroup
            ? `<span style="background:#fef3c7;color:#d97706;border:1px solid #fcd34d;padding:1px 6px;border-radius:4px;font-size:0.7rem;font-weight:600;margin-left:5px;vertical-align:middle;display:inline-flex;align-items:center;gap:3px;"><i class="fa-solid fa-pen-to-square"></i> Có ngày điều chỉnh</span>`
            : '';

        // Dải ngày
        const dateRange = g.minDate === g.maxDate
            ? `<strong>${formatDate(g.minDate)}</strong>${adjustedLabel}`
            : `<strong>${formatDate(g.minDate)}</strong>
               <span style="color:var(--text-muted);font-size:.8rem;"> → </span>
               <strong>${formatDate(g.maxDate)}</strong>${adjustedLabel}`;

        const totalSlots = g.ids.length;
        const datePerSlot = totalSlots > 1
            ? `<span style="font-size:.75rem;color:var(--text-muted);display:block;margin-top:2px;">${totalSlots} suất</span>`
            : '';

        // Render các badge khung giờ — 1 badge mỗi slot, hiển thị giờ bắt đầu → kết thúc
        // [SỬA - TrienLX - 2026-06-23]: Nếu suất chiếu đã điều chỉnh thì hiển thị badge màu cam/vàng khác kèm icon ✏️
        const slotBadges = g.slots.map(slot => {
            const slotIdsJson = JSON.stringify(slot.ids);
            const startT = formatTime(slot.showTime);
            const endT   = getEndTime(slot.showTime, mv.duration);
            // [SỬA - TrienLX - 2026-06-23]: Dùng slot.hasOverride thay vì slot.note để xác định badge override
            const isAdjustedSlot = slot.hasOverride;
            const badgeBg = isAdjustedSlot ? '#f59e0b' : 'var(--primary-color)';
            const iconHtml = isAdjustedSlot ? '<i class="fa-solid fa-pen" style="font-size:0.7rem;margin-right:4px;"></i>' : '';
            const titleTooltip = isAdjustedSlot 
                ? `Giờ chiếu đã được điều chỉnh riêng. Nhấn để sửa.`
                : `Nhấn để sửa slot ${startT}`;
            return `<span class="slot-time-badge" style="
                display:inline-block; margin:2px 3px 2px 0;
                background:${badgeBg};
                color:#fff; padding:3px 9px; border-radius:20px;
                font-size:.82rem; font-weight:600; white-space:nowrap;
                cursor:pointer;
                " onclick="editShowtime(${slot.ids[0]}, ${slotIdsJson})" title="${titleTooltip}">${iconHtml}${startT} – ${endT}</span>`;
        }).join('');

        const stats    = statsResults[idx];
        const seatInfo = stats
            ? `<span style="font-weight:600;">${stats.emptyCount}</span> / ${stats.totalCount}
               <span style="font-size:.75rem;color:var(--text-muted);">(trống/tổng)</span>`
            : '—';

        const idsJson = JSON.stringify(g.ids);
        const firstSlot = g.slots[0];
        const deleteButtons = g.slots.flatMap(slot =>
            slot.entries.map(entry => {
                const startT = formatTime(entry.showTime);
                const endT = getEndTime(entry.showTime, mv.duration);
                const dateLabel = formatDate(entry.showDate);
                const adjustedMark = entry.hasOverride ? ' *' : '';
                return `<button class="action-btn action-btn-delete" onclick="deleteShowtime(${entry.id})" title="Xoa suat ${dateLabel} ${startT} - ${endT}">
                    ${dateLabel}<br><span style="font-size:.72rem;">${startT} - ${endT}${adjustedMark}</span>
                </button>`;
            })
        ).join('');
        
        // [SỬA - TrienLX - 2026-06-23]: Bổ sung nút "Chỉnh ngày" trong danh sách để điều chỉnh 1 ngày cụ thể trong dải ngày
        const actionHtml = `
            <button class="action-btn action-btn-edit" onclick="editShowtime(${firstSlot.ids[0]}, ${idsJson})" title="Sửa nhóm lịch chiếu">Sửa</button>
            <button class="action-btn" style="background:#fffbeb;color:#b45309;border:1px solid #fde68a;" onclick="openOverrideFromGroup(${firstSlot.ids[0]}, ${idsJson})" title="Điều chỉnh giờ/phòng cho 1 ngày cụ thể trong dải ngày này">Chỉnh ngày</button>
            <div class="showtime-delete-list" title="Chọn đúng ngày và giờ cần xóa">${deleteButtons}</div>`;

        const todayStr = new Date().toLocaleDateString('sv'); // 'yyyy-MM-dd'
        let statusBadge = '';
        if (g.maxDate < todayStr) {
            statusBadge = `<span style="background:#64748b;color:#fff;padding:4px 10px;border-radius:20px;font-size:0.78rem;font-weight:600;display:inline-block;white-space:nowrap;">Đã kết thúc</span>`;
        } else if (g.minDate > todayStr) {
            statusBadge = `<span style="background:#f59e0b;color:#fff;padding:4px 10px;border-radius:20px;font-size:0.78rem;font-weight:600;display:inline-block;white-space:nowrap;">Sắp chiếu</span>`;
        } else {
            statusBadge = `<span style="background:#10b981;color:#fff;padding:4px 10px;border-radius:20px;font-size:0.78rem;font-weight:600;display:inline-block;white-space:nowrap;">Đang hoạt động</span>`;
        }

        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td>
                <div class="movie-info-cell">
                    ${poster}
                    <div class="movie-meta">
                        <span class="movie-title-text">${esc(mv.title||'Phim đã xóa')}</span>
                        <span class="movie-director-text">ĐD: ${esc(mv.director||'—')}</span>
                    </div>
                </div>
            </td>
            <td>${dateRange}${datePerSlot}</td>
            <td style="min-width:160px;">${slotBadges}</td>
            <td>${esc(g.room||'—')}</td>
            <td><span class="badge-daytype ${badgeMap[g.dayType]||''}">${esc(g.dayType||'—')}</span></td>
            <td>${seatInfo}</td>
            <td>${statusBadge}</td>
            <td>
                <div class="action-cell">
                    ${actionHtml}
                </div>
            </td>`;
        tbody.appendChild(tr);
    });

    buildPagination('showtimePaginationControls','showtimePaginationInfo',
        groups.length, showtimesPage, p => { showtimesPage = p; renderShowtimeTable(); });
}

// --- Bộ lọc lịch chiếu ---
function applyShowtimeFilter() {
    loadShowtimes({
        movieId:  document.getElementById('filterShowtimeMovie').value   || null,
        viewMode: document.getElementById('filterShowtimeViewMode').value || 'all',
        dayType:  document.getElementById('filterShowtimeDayType').value  || null,
        startDate:document.getElementById('filterShowtimeDate').value     || null
    });
}
function resetShowtimeFilter() {
    ['filterShowtimeMovie','filterShowtimeDayType','filterShowtimeDate','filterShowtimeStatus']
        .forEach(id => {
            const el = document.getElementById(id);
            if (el) el.value = '';
        });
    document.getElementById('filterShowtimeViewMode').value = 'all';
    loadShowtimes({});
}

function openShowtimeModal(isEdit) {
    document.getElementById('showtimeModalTitle').textContent =
        isEdit ? 'Sửa lịch chiếu phim' : 'Thêm lịch chiếu mới';

    const dateRow        = document.getElementById('showtimeDateRow');
    const endDateGroup   = document.getElementById('endDateGroup');
    const endDateInput   = document.getElementById('showtimeEndDateInput');
    const lblStart       = document.getElementById('lblShowtimeDateStart');
    const timeSlotRow    = document.getElementById('timeSlotRow');
    const slotCountGroup = document.getElementById('slotCountGroup');

    // Luôn giữ layout dải ngày (Từ ngày -> Đến ngày) ở cả 2 chế độ
    dateRow.classList.add('split-2');
    endDateGroup.style.display = '';
    endDateInput.setAttribute('required', 'true');
    lblStart.innerHTML = 'Từ ngày <span class="required">*</span>';

    const btnOverride = document.getElementById('btnOverrideDayFromEdit');
    if (btnOverride) {
        btnOverride.style.display = isEdit ? 'inline-block' : 'none';
    }

    if (!isEdit) {
        document.getElementById('showtimeForm').reset();
        document.getElementById('showtimeParamId').value        = '';
        document.getElementById('showtimeDayTypeDisplay').value = 'Chưa xác định ngày';
        document.getElementById('showtimeSlotCount').value      = '1';
        
        if (timeSlotRow) timeSlotRow.classList.add('split-2');
        if (slotCountGroup) slotCountGroup.style.display = '';

        // Đặt min = hôm nay cho cả hai input ngày
        setMinShowtimeDateToday('showtimeDateInput');
        setMinShowtimeDateToday('showtimeEndDateInput');
    } else {
        if (timeSlotRow) timeSlotRow.classList.remove('split-2');
        if (slotCountGroup) slotCountGroup.style.display = 'none';

        // Đặt min = hôm nay cho cả hai input ngày khi sửa
        setMinShowtimeDateToday('showtimeDateInput');
        setMinShowtimeDateToday('showtimeEndDateInput');
    }

    populateMovieDropdowns();
    populateRoomDropdown();
    document.getElementById('showtimeModal').classList.add('show');
}
function closeShowtimeModal() { document.getElementById('showtimeModal').classList.remove('show'); }

// --- Lưu lịch chiếu ---
async function handleShowtimeSave(e) {
    e.preventDefault();
    const id      = document.getElementById('showtimeParamId').value;
    const movieId = document.getElementById('showtimeMovieSelect').value;
    const timeVal = document.getElementById('showtimeTimeInput').value;

    if (!movieId) {
        showToast('warning', 'Thiếu thông tin', 'Vui lòng chọn bộ phim.');
        return;
    }

    const roomVal = document.getElementById('showtimeRoomInput').value;
    if (!timeVal || !roomVal) {
        showToast('warning', 'Thiếu thông tin', 'Vui lòng điền đầy đủ các trường bắt buộc (*)');
        return;
    }

    const today = isoDate(new Date());
    let body = {};

    if (!id) {
        // Chế độ thêm mới (hỗ trợ khoảng ngày + số suất)
        const startDate = document.getElementById('showtimeDateInput').value;
        const endDate   = document.getElementById('showtimeEndDateInput').value;

        if (!startDate || !endDate) {
            showToast('warning', 'Thiếu thông tin', 'Vui lòng chọn Từ ngày và Đến ngày.');
            return;
        }
        // Kiểm tra ngày không được là quá khứ
        if (startDate < today) {
            showToast('warning', 'Ngày không hợp lệ', 'Ngày bắt đầu không được là ngày trong quá khứ.');
            return;
        }
        if (endDate < startDate) {
            showToast('warning', 'Ngày không hợp lệ', 'Ngày kết thúc không được trước ngày bắt đầu.');
            return;
        }
        // Kiểm tra giờ chiếu không được là quá khứ (nếu thêm cho hôm nay)
        if (startDate === today) {
            const nowTime = new Date().toTimeString().substring(0, 5);
            if (timeVal < nowTime) {
                showToast('warning', 'Giờ chiếu không hợp lệ',
                    'Giờ chiếu không được là giờ đã qua hôm nay. Vui lòng chọn giờ trong tương lai.');
                return;
            }
        }

        const slotCountRaw = document.getElementById('showtimeSlotCount')?.value || '1';
        const slotCount = parseInt(slotCountRaw) || 1;
        if (slotCount < 1 || slotCount > 15) {
            showToast('warning', 'Số suất không hợp lệ', 'Số suất chiếu trong ngày phải từ 1 đến 15.');
            return;
        }

        body = {
            movieId:   parseInt(movieId),
            startDate: startDate,
            endDate:   endDate,
            showTime:  timeVal + ':00',
            room:      roomVal,
            slotCount: slotCount
        };
    } else {
        // Chế độ chỉnh sửa (hỗ trợ dải ngày)
        const startDate = document.getElementById('showtimeDateInput').value;
        const endDate   = document.getElementById('showtimeEndDateInput').value;

        if (!startDate || !endDate) {
            showToast('warning', 'Thiếu thông tin', 'Vui lòng chọn Từ ngày và Đến ngày.');
            return;
        }
        // Kiểm tra ngày không được là quá khứ
        if (startDate < today) {
            showToast('warning', 'Ngày không hợp lệ', 'Ngày bắt đầu không được là ngày trong quá khứ.');
            return;
        }
        if (endDate < startDate) {
            showToast('warning', 'Ngày không hợp lệ', 'Ngày kết thúc không được trước ngày bắt đầu.');
            return;
        }
        // Kiểm tra giờ chiếu không được là quá khứ (nếu thêm cho hôm nay)
        if (startDate === today) {
            const nowTime = new Date().toTimeString().substring(0, 5);
            if (timeVal < nowTime) {
                showToast('warning', 'Giờ chiếu không hợp lệ',
                    'Giờ chiếu không được là giờ đã qua hôm nay. Vui lòng chọn giờ trong tương lai.');
                return;
            }
        }

        body = {
            movieId:   parseInt(movieId),
            startDate: startDate,
            endDate:   endDate,
            showTime:  timeVal + ':00',
            room:      roomVal,
            groupIds:  currentEditingGroupIds
        };
    }

    try {
        const url    = id ? `${API_SHOWTIMES}/${id}` : API_SHOWTIMES;
        const method = id ? 'PUT' : 'POST';
        const r = await fetch(url, {
            method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });

        // Đọc response body một lần duy nhất
        const payload = await r.json().catch(() => ({}));

        if (!r.ok) {
            throw new Error(payload.error || 'Lỗi khi lưu lịch chiếu. Vui lòng kiểm tra lại.');
        }

        // Tính số suất đã tạo/cập nhật từ kết quả trả về
        const countCreated = Array.isArray(payload) ? payload.length : 1;
        const successMsg = id
            ? 'Lịch chiếu đã được cập nhật thành công.'
            : `Đã tạo thành công ${countCreated} suất chiếu.`;

        showToast('success', id ? 'Cập nhật thành công!' : 'Thêm lịch chiếu thành công!', successMsg);
        closeShowtimeModal();
        loadShowtimeStats();
        applyShowtimeFilter();
        populateShowtimeDropdown();
    } catch(err) {
        showToast('error', 'Lưu lịch chiếu thất bại', err.message || 'Lỗi khi lưu lịch chiếu. Vui lòng thử lại.');
    }
}

// --- Sửa lịch chiếu ---
// [SỬA - TrienLX - 2026-06-12] Nạp thông tin lịch chiếu và đổi thông báo lỗi thành showToast
async function editShowtime(id, groupIds = []) {
    try {
        currentEditingGroupIds = groupIds;
        const r  = await fetch(`${API_SHOWTIMES}/${id}`);
        const st = await r.json();

        // Mở modal trước để kích hoạt các trạng thái giao diện sửa
        openShowtimeModal(true);

        await populateMovieDropdowns();
        await populateRoomDropdown(st.room || '');

        // Tìm ngày bắt đầu nhỏ nhất và ngày kết thúc lớn nhất của nhóm từ showtimesData
        let minDate = st.showDate;
        let maxDate = st.showDate;
        if (showtimesData && groupIds && groupIds.length > 0) {
            const groupItems = showtimesData.filter(item => groupIds.includes(item.id));
            if (groupItems.length > 0) {
                minDate = groupItems.reduce((min, item) => item.showDate < min ? item.showDate : min, groupItems[0].showDate);
                maxDate = groupItems.reduce((max, item) => item.showDate > max ? item.showDate : max, groupItems[0].showDate);
            }
        }

        document.getElementById('showtimeParamId').value         = st.id;
        document.getElementById('showtimeMovieSelect').value     = st.movie?.id || '';
        document.getElementById('showtimeDateInput').value       = minDate      || st.showDate || '';
        document.getElementById('showtimeEndDateInput').value    = maxDate      || '';
        let timeStr = '';
        if (st.showTime) {
            if (typeof st.showTime === 'string') {
                timeStr = st.showTime.substring(0, 5);
            } else if (Array.isArray(st.showTime)) {
                timeStr = String(st.showTime[0]).padStart(2, '0') + ':' + String(st.showTime[1]).padStart(2, '0');
            }
        }
        document.getElementById('showtimeTimeInput').value       = timeStr;
        document.getElementById('showtimeRoomInput').value       = st.room      || '';
        document.getElementById('showtimeDayTypeDisplay').value  = st.dayType   || '—';
    } catch(e) {
        showToast('error', 'Lỗi tải dữ liệu', 'Không thể tải thông tin lịch chiếu.');
    }
}

// --- Xóa lịch chiếu đơn ---
async function deleteShowtime(id) {
    const target = showtimesData.find(item => item.id === id);
    const targetLabel = target
        ? `${formatDate(target.showDate)} - ${formatTime(target.showTime)}`
        : 'lịch chiếu này';
    const confirmed = await showConfirm(
        'Xác nhận xóa lịch chiếu',
        `Bạn có chắc chắn muốn xóa suất chiếu ${targetLabel} không?\nTất cả vé liên quan cũng sẽ bị xóa khỏi hệ thống.`,
        'Xóa lịch chiếu'
    );
    if (!confirmed) return;
    try {
        const r = await fetch(`${API_SHOWTIMES}/${id}`, { method:'DELETE' });
        if (!r.ok) throw new Error();
        showToast('success', 'Xóa thành công!', 'Lịch chiếu đã được xóa khỏi hệ thống.');
        loadShowtimeStats();
        applyShowtimeFilter();
        populateShowtimeDropdown();
    } catch(e) {
        showToast('error', 'Xóa thất bại', 'Lỗi khi xóa lịch chiếu. Vui lòng thử lại.');
    }
}

// --- Xóa nhóm lịch chiếu (nhiều suất cùng phim + phòng + giờ) ---
async function deleteShowtimeGroup(ids) {
    if (!ids || ids.length === 0) return;
    const confirmed = await showConfirm(
        'Xác nhận xóa nhóm lịch chiếu',
        `Bạn có chắc chắn muốn xóa tất cả ${ids.length} suất chiếu trong nhóm này không?\nTất cả vé liên quan cũng sẽ bị xóa.`,
        `Xóa tất ${ids.length} suất`
    );
    if (!confirmed) return;
    try {
        // Xóa tuần tự từng suất trong nhóm
        await Promise.all(ids.map(id => fetch(`${API_SHOWTIMES}/${id}`, { method: 'DELETE' })));
        showToast('success', 'Xóa nhóm thành công!', `Đã xóa ${ids.length} suất chiếu khỏi hệ thống.`);
        loadShowtimeStats();
        applyShowtimeFilter();
        populateShowtimeDropdown();
    } catch(e) {
        showToast('error', 'Xóa thất bại', 'Lỗi khi xóa nhóm lịch chiếu. Vui lòng thử lại.');
    }
}

// [SỬA - TrienLX - 2026-06-23]: Các hàm xử lý giao diện cho chức năng điều chỉnh lịch chiếu 1 ngày cụ thể
function openOverrideDayModal() {
    document.getElementById('overrideDayModal').classList.add('show');
}

function closeOverrideDayModal() {
    document.getElementById('overrideDayModal').classList.remove('show');
    document.getElementById('overrideDayForm').reset();
}

function handleOverrideDayFromEditClick() {
    // Đóng modal edit group
    closeShowtimeModal();
    // Mở modal điều chỉnh ngày từ thông tin đang edit
    const editId = document.getElementById('showtimeParamId').value;
    if (editId) {
        openOverrideFromGroup(editId, currentEditingGroupIds);
    }
}

// [SỬA - TrienLX - 2026-06-23]: Map từ ngày → showtimeId, dùng để truyền originalShowtimeId lên backend
// giúp backend tìm chính xác bản ghi cần điều chỉnh mà không tạo bản ghi trùng.
let _overrideDateToIdMap = {};

async function openOverrideFromGroup(firstShowtimeId, groupIds = []) {
    try {
        const r  = await fetch(`${API_SHOWTIMES}/${firstShowtimeId}`);
        if (!r.ok) throw new Error("Không thể tải thông tin lịch chiếu");
        const st = await r.json();

        // Nạp danh sách phòng
        await populateRoomDropdown(st.room || '');

        // Điền ID phim và Tên phim
        document.getElementById('overrideMovieId').value   = st.movie?.id    || '';
        document.getElementById('overrideMovieTitle').value = st.movie?.title || 'Phim đã xóa';

        // Xây dựng map: date -> showtimeId để truyền chính xác khi override
        _overrideDateToIdMap = {};
        const dateSelect = document.getElementById('overrideTargetDateSelect');
        dateSelect.innerHTML = '<option value="">-- Chọn ngày cần điều chỉnh --</option>';

        if (showtimesData && groupIds && groupIds.length > 0) {
            // Lọc các showtime trong group
            const groupItems = showtimesData.filter(item => groupIds.includes(item.id));

            // Xây dựng map ngày -> danh sách ID (1 ngày có thể có nhiều slot)
            // Ưu tiên ID của suất CŨa điều chỉnh (isOverride=false) để override lần đầu,
            // hoặc ID của suất đã override nếu lần này là sửa lại override cũ.
            const dateMap = {};
            groupItems.forEach(item => {
                const d = item.showDate;
                if (!dateMap[d]) {
                    dateMap[d] = item.id; // lấy ID suất đầu tiên của ngày này
                } else {
                    // Nếu ngày đó đã có suất override, nhớ lại để override lần sau cũng dùng chính bản ghi override đó
                    if (item.isOverride) dateMap[d] = item.id;
                }
            });
            _overrideDateToIdMap = dateMap;

            // Lấy danh sách ngày duy nhất và sắp xếp
            const uniqueDates = Object.keys(dateMap).sort();
            uniqueDates.forEach(d => {
                const opt = document.createElement('option');
                opt.value = d;
                // Đánh dấu ngày đã được override bằng biểu tượng ✏️
                const item = groupItems.find(i => i.showDate === d);
                const overrideFlag = item?.isOverride ? ' ✏️' : '';
                opt.textContent = formatDate(d) + overrideFlag;
                dateSelect.appendChild(opt);
            });

            // Tự động chọn ngày đầu tiên
            if (uniqueDates.length > 0) {
                dateSelect.value = uniqueDates[0];
            }
        } else {
            // Fallback nếu không có group, dùng showDate đơn lẻ
            _overrideDateToIdMap[st.showDate] = st.id;
            const opt = document.createElement('option');
            opt.value = st.showDate;
            opt.textContent = formatDate(st.showDate);
            dateSelect.appendChild(opt);
            dateSelect.value = st.showDate;
        }

        // Điền giờ chiếu cũ mặc định (theo suất đầu tiên)
        let timeStr = '';
        if (st.showTime) {
            if (typeof st.showTime === 'string') {
                timeStr = st.showTime.substring(0, 5);
            } else if (Array.isArray(st.showTime)) {
                timeStr = String(st.showTime[0]).padStart(2, '0') + ':' + String(st.showTime[1]).padStart(2, '0');
            }
        }
        document.getElementById('overrideTimeInput').value = timeStr;
        document.getElementById('overrideRoomInput').value = st.room || '';

        openOverrideDayModal();
    } catch(e) {
        showToast('error', 'Lỗi tải dữ liệu', 'Không thể tải thông tin để điều chỉnh lịch chiếu.');
    }
}

async function handleOverrideDaySave(e) {
    e.preventDefault();
    const movieId    = parseInt(document.getElementById('overrideMovieId').value);
    const targetDate = document.getElementById('overrideTargetDateSelect').value;
    let newShowTime  = document.getElementById('overrideTimeInput').value;
    const room       = document.getElementById('overrideRoomInput').value;

    if (!movieId || !targetDate || !newShowTime || !room) {
        showToast('warning', 'Thiếu thông tin', 'Vui lòng nhập đầy đủ thông tin để điều chỉnh.');
        return;
    }

    // Đảm bảo định dạng hh:mm:ss cho LocalTime ở backend
    if (newShowTime.length === 5) {
        newShowTime += ':00';
    }

    // [SỬA - TrienLX - 2026-06-23]: Lấy originalShowtimeId từ map ngày -> ID
    // giúp backend tìm chính xác bản ghi gốc cần điều chỉnh, không tạo bản ghi mới
    const originalShowtimeId = _overrideDateToIdMap[targetDate] || null;

    try {
        const payload = { originalShowtimeId, movieId, targetDate, newShowTime, room };
        const response = await fetch(`${API_SHOWTIMES}/override-day`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        if (!response.ok) {
            const errData = await response.json();
            throw new Error(errData.error || 'Lỗi từ phía server.');
        }

        showToast('success', 'Điều chỉnh thành công!', `Đã cập nhật giờ chiếu cho ngày ${formatDate(targetDate)}.`);
        closeOverrideDayModal();
        loadShowtimeStats();
        applyShowtimeFilter();
        populateShowtimeDropdown();
    } catch (err) {
        showToast('error', 'Lỗi điều chỉnh', err.message || 'Không thể lưu thay đổi.');
    }
}

// ======================================================
//   QUẢN LÝ BÁN VÉ & SƠ ĐỒ GHẾ
// ======================================================

function initTicketEvents() {
    const movieSelect = document.getElementById('ticketMovieSelect');
    if (movieSelect) {
        movieSelect.addEventListener('change', async e => {
            const movieId = e.target.value;
            const showtimeSelect = document.getElementById('ticketShowtimeSelect');
            if (!movieId) {
                showtimeSelect.innerHTML = '<option value="">-- Chọn phim trước --</option>';
                showtimeSelect.disabled = true;
                activeShowtimeId = null;
                hideSeatMap();
            } else {
                showtimeSelect.disabled = false;
                await populateTicketShowtimes(movieId);
            }
        });
    }

    document.getElementById('ticketShowtimeSelect').addEventListener('change', e => {
        const id = e.target.value;
        activeShowtimeId = id ? parseInt(id) : null;
        if (activeShowtimeId) loadTicketView(activeShowtimeId);
        else hideSeatMap();
    });
    document.getElementById('btnFilterAllTickets').addEventListener('click',   () => filterTicketTable('all'));
    document.getElementById('btnFilterSoldTickets').addEventListener('click',  () => filterTicketTable('sold'));
    document.getElementById('btnFilterEmptyTickets').addEventListener('click', () => filterTicketTable('empty'));

    // Bắt sự kiện Close/Confirm của các hộp thoại vé
    document.getElementById('btnCloseSellTicketModal').addEventListener('click', closeSellTicketModal);
    document.getElementById('btnCancelSellTicket').addEventListener('click', (e) => { e.preventDefault(); closeSellTicketModal(); });
    document.getElementById('btnConfirmSellTicket').addEventListener('click', (e) => { e.preventDefault(); handleConfirmSellTicket(); });
    
    const btnRefund = document.getElementById('btnRefundTicket');
    if (btnRefund) {
        btnRefund.addEventListener('click', async (e) => {
            e.preventDefault();
            if (activeTicketToSell) {
                closeSellTicketModal();
                await confirmCancelSeat(activeTicketToSell.id, activeTicketToSell.seatNumber);
            }
        });
    }

    document.getElementById('btnCloseEditConfigModal').addEventListener('click', closeEditConfigModal);
    document.getElementById('btnCancelEditConfig').addEventListener('click', (e) => { e.preventDefault(); closeEditConfigModal(); });
    document.getElementById('btnSaveConfig').addEventListener('click', savePricingConfig);

    // Tải trước cấu hình để cache
    loadPricingConfigs();
}

// --- Nạp danh sách phim vào dropdown Bán vé ---
async function populateTicketMovieDropdown() {
    try {
        const r = await fetch(API_MOVIES);
        const list = await r.json();
        const movieSelect = document.getElementById('ticketMovieSelect');
        if (!movieSelect) return;
        movieSelect.innerHTML = '<option value="">-- Chọn phim --</option>';
        list.forEach(mv => {
            const opt = document.createElement('option');
            opt.value = mv.id;
            opt.textContent = mv.title;
            movieSelect.appendChild(opt);
        });
    } catch(e) {
        console.error('Lỗi nạp danh sách phim bán vé:', e);
    }
}

// --- Nạp danh sách suất chiếu của phim được chọn vào dropdown Bán vé ---
async function populateTicketShowtimes(movieId) {
    try {
        const r = await fetch(`${API_SHOWTIMES}?movieId=${movieId}`);
        const list = await r.json();
        const showtimeSelect = document.getElementById('ticketShowtimeSelect');
        if (!showtimeSelect) return;
        showtimeSelect.innerHTML = '<option value="">-- Chọn suất chiếu --</option>';
        
        list.forEach(st => {
            const opt = document.createElement('option');
            opt.value = st.id;
            opt.textContent = `${formatDate(st.showDate)} ${formatTime(st.showTime)} | ${esc(st.room||'?')}`;
            showtimeSelect.appendChild(opt);
        });
        
        activeShowtimeId = null;
        hideSeatMap();
    } catch(e) {
        console.error('Lỗi nạp suất chiếu theo phim:', e);
    }
}

// --- Hàm tương thích ngược khi lịch chiếu thay đổi để tự refresh dropdown ---
async function populateShowtimeDropdown() {
    const movieSelect = document.getElementById('ticketMovieSelect');
    if (movieSelect && movieSelect.value) {
        await populateTicketShowtimes(movieSelect.value);
    }
}

// --- Tải toàn bộ giao diện vé của một suất chiếu ---
async function loadTicketView(showtimeId) {
    try {
        // Tải song song: danh sách vé + thống kê
        const [ticketRes, statsRes] = await Promise.all([
            fetch(`${API_TICKETS}/showtime/${showtimeId}`),
            fetch(`${API_TICKETS}/stats/${showtimeId}`)
        ]);
        ticketsData     = await ticketRes.json();
        const stats     = await statsRes.json();
        ticketsFiltered = [...ticketsData];

        updateTicketStats(stats);
        renderSeatGrid(ticketsData);
        renderTicketTable(ticketsFiltered);
        showSeatMap();
    } catch(e) {
        console.error('Lỗi tải vé:', e);
        hideSeatMap();
    }
}

// --- Hiển thị / Ẩn sơ đồ ghế ---
function showSeatMap() {
    document.getElementById('seatMapSection').style.display = '';
    document.getElementById('seatMapEmpty').style.display   = 'none';
}
function hideSeatMap() {
    document.getElementById('seatMapSection').style.display = 'none';
    document.getElementById('seatMapEmpty').style.display   = '';
}

// --- Cập nhật thẻ thống kê ---
function updateTicketStats(stats) {
    document.getElementById('ticketStatTotal').textContent   = stats.totalCount   ?? 0;
    document.getElementById('ticketStatEmpty').textContent   = stats.emptyCount   ?? 0;
    document.getElementById('ticketStatSold').textContent    = stats.soldCount    ?? 0;

    // Hiển thị doanh thu và tỷ lệ lấp đầy
    const rev  = stats.revenue ?? 0;
    const rate = stats.occupancyRate ?? 0;
    document.getElementById('ticketStatRevenue').textContent = formatVND(rev);
    document.getElementById('ticketStatRevenue').closest('.stat-info')
        .querySelector('.stat-label').textContent =
        `Doanh thu | Lấp đầy ${rate}%`;
}

function renderSeatGrid(tickets) {
    const grid = document.getElementById('seatGrid');
    grid.innerHTML = '';

    // Nhóm vé theo hàng (A,B,C,D,E)
    const rowMap = {};
    tickets.forEach(t => {
        const row  = t.seatNumber.charAt(0);     // 'A','B',...
        if (!rowMap[row]) rowMap[row] = [];
        rowMap[row].push(t);
    });

    // Render từng hàng
    Object.keys(rowMap).sort().forEach(row => {
        const seats = rowMap[row].sort((a,b) =>
            parseInt(a.seatNumber.slice(1)) - parseInt(b.seatNumber.slice(1)));

        const rowDiv = document.createElement('div');
        rowDiv.className = 'seat-row';

        // Nhãn hàng chữ cái
        const lbl = document.createElement('span');
        lbl.className   = 'seat-row-label';
        lbl.textContent = row;
        rowDiv.appendChild(lbl);

        seats.forEach(ticket => {
            const isSold     = ticket.status === 'Đã bán';
            const isVIP      = ticket.seatType === 'VIP';
            const isCouple   = ticket.seatType === 'Đôi';
            const typeClass  = isVIP ? 'vip' : (isCouple ? 'couple' : 'standard');
            const stateClass = isSold ? 'is-sold' : 'available';
            const priceLabel = formatVND(ticket.price) + '\u0111';
            const icon       = isSold ? '\uD83D\uDD12' : ticket.seatNumber;

            const btn = document.createElement('button');
            btn.className        = `seat-btn ${typeClass} ${stateClass}`;
            btn.dataset.ticketId = ticket.id;
            btn.dataset.price    = priceLabel;
            btn.dataset.seatNumber = ticket.seatNumber;
            btn.textContent      = icon;
            btn.title            = `Gh\u1ebf ${ticket.seatNumber} \u2014 ${ticket.seatType} \u2014 ${priceLabel} \u2014 ${ticket.status}`;
            btn.setAttribute('aria-label', `Gh\u1ebf ${ticket.seatNumber}`);

            if (!isSold) {
                btn.addEventListener('click', () => openSellTicketModal(ticket.id));
            } else {
                // Ghế đã bán: click để mở modal sửa thông tin vé
                btn.addEventListener('click', () => openEditTicketModal(ticket.id));
            }

            rowDiv.appendChild(btn);
        });

        grid.appendChild(rowDiv);
    });

    // Render thông tin giá vé theo loại
    renderPriceInfo(tickets);
}

// --- Thông tin giá vé từng loại ghế ---
function renderPriceInfo(tickets) {
    const info    = document.getElementById('seatPriceInfo');
    info.innerHTML = '';
    const priceSet = {};
    tickets.forEach(t => {
        const key = t.seatType;
        if (!priceSet[key]) priceSet[key] = t.price;
    });
    const colors = { 'Th\u01b0\u1eddng':'#10b981', 'VIP':'#f59e0b', '\u0110\u00f4i':'#ec4899' };
    Object.entries(priceSet).forEach(([type, price]) => {
        const div = document.createElement('div');
        div.className = 'price-info-item';
        div.innerHTML = `
            <span class="price-info-dot" style="background:${colors[type]||'#aaa'};"></span>
            Gh\u1ebf ${type}: <strong>${formatVND(price)}\u0111</strong>`;
        info.appendChild(div);
    });
}

// --- Toggle đặt vé / hủy đặt (dành cho Admin click bán nhanh trên sơ đồ) ---
async function toggleSeat(ticketId) {
    const btn = document.querySelector(`.seat-btn[data-ticket-id="${ticketId}"]`);
    let isCurrentlySold = false;
    let seatNumber = '';
    let priceLabel = '';
    let seatType = '';

    if (btn) {
        isCurrentlySold = btn.classList.contains('is-sold');
        seatNumber = btn.dataset.seatNumber || '';
        priceLabel = btn.dataset.price || '';
        seatType = btn.classList.contains('vip') ? 'VIP' : (btn.classList.contains('couple') ? '\u0110\u00f4i' : 'Th\u01b0\u1eddng');

        // Cập nhật UI ngay lập tức (optimistic update)
        if (isCurrentlySold) {
            btn.classList.remove('is-sold');
            btn.classList.add('available');
            btn.textContent = seatNumber;
            btn.title = `Gh\u1ebf ${seatNumber} \u2014 ${seatType} \u2014 ${priceLabel} \u2014 C\u00f2n tr\u1ed1ng`;
        } else {
            btn.classList.remove('available');
            btn.classList.add('is-sold');
            btn.textContent = '\uD83D\uDD12';
            btn.title = `Gh\u1ebf ${seatNumber} \u2014 ${seatType} \u2014 ${priceLabel} \u2014 \u0110\u00e3 b\u00e1n`;
        }
    }

    try {
        const r = await fetch(`${API_TICKETS}/${ticketId}/status`, { method:'PUT' });
        if (!r.ok) throw new Error();
        // Tải lại toàn bộ để cập nhật sơ đồ + thống kê + bảng
        await loadTicketView(activeShowtimeId);
    } catch(e) {
        // Revert UI nếu thất bại
        if (btn) {
            if (isCurrentlySold) {
                btn.classList.remove('available');
                btn.classList.add('is-sold');
                btn.textContent = '\uD83D\uDD12';
                btn.title = `Gh\u1ebf ${seatNumber} \u2014 ${seatType} \u2014 ${priceLabel} \u2014 \u0110\u00e3 b\u00e1n`;
            } else {
                btn.classList.remove('is-sold');
                btn.classList.add('available');
                btn.textContent = seatNumber;
                btn.title = `Gh\u1ebf ${seatNumber} \u2014 ${seatType} \u2014 ${priceLabel} \u2014 C\u00f2n tr\u1ed1ng`;
            }
        }
        showToast('error', 'Cập nhật thất bại', 'Không thể cập nhật trạng thái ghế. Vui lòng thử lại.');
    }
}

// --- Hỏi hủy vé đã bán ---
async function confirmCancelSeat(ticketId, seatNum) {
    const confirmed = await showConfirm(
        'Hủy vé đã bán',
        `Ghế ${seatNum} đang ở trạng thái "Đã bán".\nBạn có muốn hủy vé này và đặt lại thành "Còn trống" không?`,
        'Hủy vé'
    );
    if (!confirmed) return;
    await toggleSeat(ticketId);
}

// --- Render bảng vé chi tiết (không hiển thị cột Số ghế và Loại ghế) ---
function renderTicketTable(tickets) {
    const tbody = document.getElementById('ticketTableBody');
    tbody.innerHTML = '';

    if (!tickets.length) {
        tbody.innerHTML = `<tr><td colspan="3" class="text-center" style="padding:2rem;color:var(--text-muted);">
            Không có vé nào.
        </td></tr>`;
        return;
    }

    const customerLabels = {
        'ADULT':   'Người lớn',
        'STUDENT': 'Học sinh/SV',
        'CHILD':   'Trẻ em',
        'ELDERLY': 'Cao tuổi'
    };

    tickets.forEach(t => {
        const isSold = t.status === 'Đã bán';

        const statusBadge = isSold
            ? `<span class="badge-sold">Đã bán</span>`
            : `<span class="badge-available">Còn trống</span>`;

        // Hiển thị đối tượng khách hàng (chỉ có ý nghĩa khi vé đã bán)
        const custLabel = customerLabels[t.customerType] || t.customerType || 'Người lớn';
        const customerBadge = isSold
            ? `<span style="font-size:0.8rem; padding:0.2rem 0.5rem; border-radius:4px; background:rgba(99,102,241,0.15); color:var(--primary-color); font-weight:600;">${custLabel}</span>`
            : `<span style="color:var(--text-muted); font-size:0.8rem;">—</span>`;

        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td><strong>${formatVND(t.price)}đ</strong></td>
            <td>${statusBadge}</td>
            <td>${customerBadge}</td>`;
        tbody.appendChild(tr);
    });
}

// --- Lọc bảng vé theo trạng thái ---
function filterTicketTable(mode) {
    // Cập nhật trạng thái active cho nút lọc
    ['btnFilterAllTickets','btnFilterSoldTickets','btnFilterEmptyTickets'].forEach(id => {
        document.getElementById(id).classList.remove('btn-primary');
        document.getElementById(id).classList.add('btn-outline');
    });
    const activeId = mode === 'sold' ? 'btnFilterSoldTickets'
                  : mode === 'empty' ? 'btnFilterEmptyTickets'
                  : 'btnFilterAllTickets';
    document.getElementById(activeId).classList.add('btn-primary');
    document.getElementById(activeId).classList.remove('btn-outline');

    if (mode === 'sold')  ticketsFiltered = ticketsData.filter(t => t.status === 'Đã bán');
    else if (mode === 'empty') ticketsFiltered = ticketsData.filter(t => t.status === 'Còn trống');
    else ticketsFiltered = [...ticketsData];

    renderTicketTable(ticketsFiltered);
}


// =========================================================================
//   CHỨC NĂNG PHỤ THÊM: CHUYỂN SUB-TAB & CẤU HÌNH MA TRẬN GIÁ VÉ
// =========================================================================

function switchTicketSubTab(tab) {
    document.querySelectorAll('.sub-tab-btn').forEach(btn => {
        btn.classList.remove('active');
        btn.style.borderBottomColor = 'transparent';
        btn.style.color = 'var(--text-muted)';
    });
    const activeBtn = tab === 'sell' ? 'btnSubTabSell' : 'btnSubTabConfig';
    const activeEl = document.getElementById(activeBtn);
    if (activeEl) {
        activeEl.classList.add('active');
        activeEl.style.borderBottomColor = 'var(--primary-color)';
        activeEl.style.color = 'var(--primary-color)';
    }
    
    if (tab === 'sell') {
        document.getElementById('ticketSubTabSell').style.display = 'block';
        document.getElementById('ticketSubTabConfig').style.display = 'none';
        if (activeShowtimeId) loadTicketView(activeShowtimeId);
    } else {
        document.getElementById('ticketSubTabSell').style.display = 'none';
        document.getElementById('ticketSubTabConfig').style.display = 'block';
        loadPricingConfigs();
    }
}

let pricingConfigs = {}; // cache dữ liệu giá cấu hình

async function loadPricingConfigs() {
    try {
        const res = await fetch('/api/tickets/configs');
        pricingConfigs = await res.json();
        
        renderBasePrices(pricingConfigs.basePrices || []);
        renderSeatSurcharges(pricingConfigs.seatSurcharges || []);
        renderFormatSurcharges(pricingConfigs.formatSurcharges || []);
        renderCustomerDiscounts(pricingConfigs.customerDiscounts || []);
    } catch (err) {
        console.error('Lỗi tải cấu hình ma trận giá:', err);
        showToast('error', 'Lỗi tải giá', 'Không thể nạp dữ liệu cấu hình giá vé.');
    }
}

// Chuyển LocalTime từ Jackson (có thể là array [H, M, S] hoặc chuỗi "HH:mm:ss") sang định dạng "HH:mm"
function formatLocalTime(t) {
    if (!t) return '--';
    if (Array.isArray(t)) {
        const h = String(t[0]).padStart(2, '0');
        const m = String(t[1]).padStart(2, '0');
        return `${h}:${m}`;
    }
    if (typeof t === 'string') return t.slice(0, 5);
    return String(t);
}

function renderBasePrices(list) {
    const tbody = document.getElementById('configBasePriceBody');
    if (!tbody) return;
    tbody.innerHTML = '';
    
    // Sắp xếp theo thứ tự ngày rồi giờ
    list.sort((a,b) => a.dayType.localeCompare(b.dayType)).forEach(c => {
        const tr = document.createElement('tr');
        const badgeColor = c.slotName === 'Giờ vàng' ? 'badge-ongoing' : 'badge-upcoming';
        tr.innerHTML = `
            <td><strong>${esc(c.dayType)}</strong></td>
            <td><span class="badge-daytype ${badgeColor}">${esc(c.slotName)}</span></td>
            <td>${formatLocalTime(c.startTime)} - ${formatLocalTime(c.endTime)}</td>
            <td><strong style="color:var(--primary-color);">${formatVND(c.basePrice)}đ</strong></td>
            <td class="text-center">
                <button class="action-btn action-btn-edit" onclick="openEditConfigModal('base', ${c.id}, ${c.basePrice})">
                    <i class="fas fa-edit"></i>
                </button>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

function renderSeatSurcharges(list) {
    const tbody = document.getElementById('configSeatSurchargeBody');
    if (!tbody) return;
    tbody.innerHTML = '';
    
    const typeLabels = { 'std': 'Ghế thường (std)', 'vip': 'Ghế VIP (vip)', 'couple': 'Ghế đôi (couple)' };
    list.forEach(c => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td><strong>${typeLabels[c.seatTypeCode] || esc(c.seatTypeCode)}</strong></td>
            <td><strong style="color:#10b981;">+${formatVND(c.surchargeAmount)}đ</strong></td>
            <td class="text-center">
                <button class="action-btn action-btn-edit" onclick="openEditConfigModal('seat', ${c.id}, ${c.surchargeAmount})">
                    <i class="fas fa-edit"></i>
                </button>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

function renderFormatSurcharges(list) {
    const tbody = document.getElementById('configFormatSurchargeBody');
    if (!tbody) return;
    tbody.innerHTML = '';
    list.forEach(c => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td><span class="badge-vip" style="background:#4f46e5; color:#fff; padding:0.2rem 0.5rem; border-radius:4px; font-weight:700;">${esc(c.formatCode)}</span></td>
            <td><strong style="color:#3b82f6;">+${formatVND(c.surchargeAmount)}đ</strong></td>
            <td class="text-center">
                <button class="action-btn action-btn-edit" onclick="openEditConfigModal('format', ${c.id}, ${c.surchargeAmount})">
                    <i class="fas fa-edit"></i>
                </button>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

function renderCustomerDiscounts(list) {
    const tbody = document.getElementById('configDiscountBody');
    if (!tbody) return;
    tbody.innerHTML = '';
    
    const labels = { 
        'ADULT': 'Người lớn (Adult)', 
        'STUDENT': 'Học sinh / Sinh viên (Student)', 
        'CHILD': 'Trẻ em (Child)', 
        'ELDERLY': 'Người cao tuổi (Elderly)' 
    };
    
    list.forEach(c => {
        const tr = document.createElement('tr');
        const fixedVal = c.fixedPriceWeekday ? `${formatVND(c.fixedPriceWeekday)}đ` : 'Không áp dụng';
        tr.innerHTML = `
            <td><strong>${labels[c.customerType] || esc(c.customerType)}</strong></td>
            <td><strong style="color:#ef4444;">Giảm ${Math.round(c.discountRate * 100)}%</strong></td>
            <td><strong>${fixedVal}</strong></td>
            <td class="text-center">
                <button class="action-btn action-btn-edit" onclick="openEditConfigModal('discount', ${c.id}, null, ${c.discountRate}, ${c.fixedPriceWeekday ?? ''})">
                    <i class="fas fa-edit"></i>
                </button>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

// --- Hộp thoại Sửa cấu hình giá ---
let activeConfigData = {};
function openEditConfigModal(type, id, value, rate, fixedPrice) {
    activeConfigData = { type, id, value, rate, fixedPrice };
    document.getElementById('editConfigType').value = type;
    document.getElementById('editConfigId').value = id;
    
    document.getElementById('editConfigModal').style.display = 'flex';
    
    const groupPrice = document.getElementById('groupEditPrice');
    const groupRate = document.getElementById('groupEditDiscountRate');
    const groupFixed = document.getElementById('groupEditFixedPriceWeekday');
    
    if (type === 'discount') {
        groupPrice.style.display = 'none';
        groupRate.style.display = 'block';
        groupFixed.style.display = 'block';
        document.getElementById('editConfigDiscountRate').value = (rate != null && rate !== '') ? rate : 0;
        document.getElementById('editConfigFixedPriceWeekday').value = (fixedPrice != null && fixedPrice !== '') ? fixedPrice : '';
        document.getElementById('editConfigTitle').textContent = 'Sửa chiết khấu đối tượng';
    } else {
        groupPrice.style.display = 'block';
        groupRate.style.display = 'none';
        groupFixed.style.display = 'none';
        document.getElementById('editConfigValue').value = value;
        document.getElementById('editConfigTitle').textContent = 
            type === 'base' ? 'Sửa giá vé cơ bản' : 
            type === 'seat' ? 'Sửa phụ thu loại ghế' : 'Sửa phụ thu định dạng';
    }
}

function closeEditConfigModal() {
    document.getElementById('editConfigModal').style.display = 'none';
}

async function savePricingConfig(e) {
    if (e) e.preventDefault();
    const type = document.getElementById('editConfigType').value;
    const id = parseInt(document.getElementById('editConfigId').value);
    
    let url = '/api/tickets/configs/';
    let body = { id };
    
    if (type === 'discount') {
        url += 'discounts';
        body.discountRate = parseFloat(document.getElementById('editConfigDiscountRate').value);
        const fixedVal = document.getElementById('editConfigFixedPriceWeekday').value;
        body.fixedPriceWeekday = fixedVal ? parseFloat(fixedVal) : null;
    } else {
        url += type === 'base' ? 'base' : type === 'seat' ? 'seats' : 'formats';
        const value = parseFloat(document.getElementById('editConfigValue').value);
        if (type === 'base') body.basePrice = value;
        else body.surchargeAmount = value;
    }
    
    try {
        const res = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });
        
        if (!res.ok) throw new Error();
        
        showToast('success', 'Cập nhật thành công', 'Đã lưu thay đổi cấu hình bảng giá vé.');
        closeEditConfigModal();
        loadPricingConfigs();
    } catch (err) {
        showToast('error', 'L\u01b0u th\u1ea5t b\u1ea1i', 'Kh\u00f4ng th\u1ec3 l\u01b0u thay \u0111\u1ed5i c\u1ea5u h\u00ecnh.');
    }
}

// =========================================================================
//   LOGIC HỘP THOạI BáN VÉ THEO ĐỐI TƯỢNG (Sell Ticket Modal)
// =========================================================================

let activeTicketToSell = null;
let isEditingTicket = false; // true = đang sửa vé đã bán, false = đang bán vé mới

/**
 * Mở hộp thoại bán vé cho ghế chưa bán.
 */
function openSellTicketModal(ticketId) {
    const ticket = ticketsData.find(t => t.id === ticketId);
    if (!ticket) return;

    isEditingTicket = false;
    activeTicketToSell = ticket;
    document.getElementById('sellTicketId').value = ticket.id;
    document.getElementById('sellTicketModalTitle').textContent = `Bán vé - Ghế ${ticket.seatNumber}`;
    document.getElementById('sellCustomerType').value = ticket.customerType || 'ADULT';
    document.getElementById('btnConfirmSellTicket').innerHTML = '<i class="fa-solid fa-circle-check"></i> Xác nhận bán vé';

    const btnRefund = document.getElementById('btnRefundTicket');
    if (btnRefund) btnRefund.style.display = 'none';

    updateSellTicketPriceDisplay();
    document.getElementById('sellTicketModal').style.display = 'flex';
}

/**
 * Mở hộp thoại sửa thông tin vé đã bán (đổi đối tượng khách hàng & tính lại giá).
 */
function openEditTicketModal(ticketId) {
    const ticket = ticketsData.find(t => t.id === ticketId);
    if (!ticket) return;

    isEditingTicket = true;
    activeTicketToSell = ticket;
    document.getElementById('sellTicketId').value = ticket.id;
    document.getElementById('sellTicketModalTitle').textContent = `Sửa thông tin vé - Ghế ${ticket.seatNumber}`;
    document.getElementById('sellCustomerType').value = ticket.customerType || 'ADULT';
    document.getElementById('btnConfirmSellTicket').innerHTML = '<i class="fa-solid fa-pen-to-square"></i> Cập nhật thông tin vé';

    const btnRefund = document.getElementById('btnRefundTicket');
    if (btnRefund) btnRefund.style.display = 'inline-flex';

    updateSellTicketPriceDisplay();
    document.getElementById('sellTicketModal').style.display = 'flex';
}

function closeSellTicketModal() {
    document.getElementById('sellTicketModal').style.display = 'none';
    isEditingTicket = false;
}

/**
 * Tính toán và hiển thị giá vé theo đối tượng khách hàng đang chọn.
 */
function updateSellTicketPriceDisplay() {
    if (!activeTicketToSell) return;
    const customerType = document.getElementById('sellCustomerType').value;

    const base = activeTicketToSell.basePrice > 0 ? activeTicketToSell.basePrice : activeTicketToSell.price;
    let discountRate = 0.0;
    let fixedWeekday = null;

    if (pricingConfigs.customerDiscounts) {
        const disc = pricingConfigs.customerDiscounts.find(d => d.customerType === customerType);
        if (disc) {
            discountRate = disc.discountRate;
            fixedWeekday = disc.fixedPriceWeekday;
        }
    } else {
        const fallbackRates = { 'ADULT': 0.0, 'STUDENT': 0.20, 'CHILD': 0.30, 'ELDERLY': 0.30 };
        discountRate = fallbackRates[customerType] || 0.0;
    }

    let finalPrice = base;
    const dayType = (ticketsData.length > 0 && ticketsData[0].showtime)
        ? (ticketsData[0].showtime.dayType || 'Trong tuần')
        : 'Trong tuần';

    if (dayType === 'Trong tuần' && fixedWeekday && fixedWeekday > 0) {
        let seatSurcharge = 0.0;
        const seatType = activeTicketToSell.seatType;
        if (pricingConfigs.seatSurcharges) {
            const scode = seatType === 'VIP' ? 'vip' : seatType === 'Đôi' ? 'couple' : 'std';
            const sOpt = pricingConfigs.seatSurcharges.find(s => s.seatTypeCode === scode);
            if (sOpt) seatSurcharge = sOpt.surchargeAmount;
        }
        finalPrice = fixedWeekday + seatSurcharge;
    } else {
        finalPrice = base * (1 - discountRate);
    }

    const discountAmount = base - finalPrice;

    document.getElementById('sellOriginalPrice').textContent = formatVND(base) + 'đ';
    document.getElementById('sellDiscountAmount').textContent = '-' + formatVND(Math.max(0, discountAmount)) + 'đ';
    document.getElementById('sellFinalPrice').textContent = formatVND(Math.round(finalPrice)) + 'đ';
}

/**
 * Xử lý nýt xác nhận trong modal bán/sửa vé.
 */
async function handleConfirmSellTicket() {
    const ticketId = document.getElementById('sellTicketId').value;
    const customerType = document.getElementById('sellCustomerType').value;

    try {
        if (isEditingTicket) {
            const res = await fetch(`/api/tickets/${ticketId}/update-customer?customerType=${customerType}`, {
                method: 'PUT'
            });
            if (!res.ok) {
                const errData = await res.json();
                throw new Error(errData.error || 'Lỗi cập nhật vé.');
            }
            showToast('success', 'Cập nhật vé thành công!', `Đã cập nhật thông tin khách hàng cho ghế ${activeTicketToSell.seatNumber}.`);
        } else {
            const res = await fetch(`/api/tickets/${ticketId}/sell?customerType=${customerType}`, {
                method: 'POST'
            });
            if (!res.ok) {
                const errData = await res.json();
                throw new Error(errData.error || 'Lỗi đặt vé.');
            }
            showToast('success', 'Bán vé thành công!', `Ghế ${activeTicketToSell.seatNumber} đã được chuyển sang trạng thái Đã bán.`);
        }

        closeSellTicketModal();
        await loadTicketView(activeShowtimeId);
    } catch (err) {
        showToast('error', 'Lỗi', err.message || 'Không thể ghi nhận thay đổi.');
    }
}

