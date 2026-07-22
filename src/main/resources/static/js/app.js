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
let currentPreviewSlots = []; // Danh sách các suất chiếu xem trước đang hiển thị
let ticketsData     = [];   // Vé của suất chiếu đang xem
let ticketsFiltered = [];   // Vé sau khi lọc theo trạng thái
let seatsData       = [];   // Danh sách ghế của phòng chiếu đang xem

let moviesPage    = 1;
let showtimesPage = 1;
const PAGE_SIZE   = 6;      // Số dòng mỗi trang

let activeShowtimeId = null; // ID suất chiếu đang mở sơ đồ ghế
let currentEditingGroupIds = []; // Danh sách IDs suất chiếu của nhóm đang sửa

// Global variables for Ticket Price Configs and Selling Map
let _cachedPricingConfigs = null;
let selectedMapSeat = null;
let selectedMapShowtime = null;

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
        applyShowtimeFilter();
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
        applyShowtimeFilter();
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
// (không cho phép chọn ngày trong quá khứ khi tạo mới)
function setMinReleaseDateToday(isEdit) {
    const input = document.getElementById('movieReleaseDate');
    if (!input) return;
    if (isEdit) {
        input.removeAttribute('min'); // Cho phép giữ nguyên ngày cũ trong quá khứ khi sửa
    } else {
        const today = new Date();
        const yyyy  = today.getFullYear();
        const mm    = String(today.getMonth() + 1).padStart(2, '0');
        const dd    = String(today.getDate()).padStart(2, '0');
        input.min = `${yyyy}-${mm}-${dd}`; // Giới hạn tối thiểu là ngày hôm nay khi thêm mới
    }
}

function openMovieModal(isEdit) {
    document.getElementById('modalTitle').textContent = isEdit ? 'Sửa thông tin phim' : 'Thêm phim mới';
    if (!isEdit) {
        document.getElementById('movieForm').reset();
        document.getElementById('movieParamId').value = '';
        document.getElementById('movieReleaseDate').dataset.original = '';
        showPosterPreview('');
        resetUploadZone();
        document.querySelectorAll('input[name="movieGenreVal"]').forEach(cb => cb.checked = false);
        const hint = document.getElementById('movieStatusHint');
        if (hint) { hint.textContent = ''; }
    }
    setMinReleaseDateToday(isEdit);
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
        const originalReleaseDate = document.getElementById('movieReleaseDate').dataset.original || '';
        const isChanged = !id || body.releaseDate !== originalReleaseDate;
        if (isChanged) {
            const today    = new Date(); today.setHours(0, 0, 0, 0);
            const releaseD = new Date(body.releaseDate + 'T00:00:00');
            if (releaseD < today) {
                showToast('warning', 'Ngày khởi chiếu không hợp lệ',
                    'Ngày khởi chiếu không được là ngày trong quá khứ. Vui lòng chọn từ hôm nay trở đi.');
                return;
            }
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
            headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
            credentials: 'same-origin',
            body: JSON.stringify(body)
        });
        const responseText = await r.text();
        let payload = null;
        try {
            payload = responseText ? JSON.parse(responseText) : null;
        } catch (parseError) {
            throw new Error('Phiên đăng nhập không hợp lệ hoặc máy chủ trả về dữ liệu không đúng. Vui lòng đăng nhập lại rồi thử thêm phim.');
        }
        if (!r.ok) {
            throw new Error(payload.message || payload.error || 'Lỗi khi lưu phim. Vui lòng thử lại.');
        }
        if (!payload || !payload.id) {
            throw new Error('Không nhận được dữ liệu phim sau khi lưu. Vui lòng đăng nhập lại rồi thử thêm phim lần nữa.');
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
        setMinReleaseDateToday(true);
        const relDateInput = document.getElementById('movieReleaseDate');
        relDateInput.value = mv.releaseDate || '';
        relDateInput.dataset.original = mv.releaseDate || '';
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
    const confirmed = await showConfirm(
        'Xác nhận xóa phim',
        'Bạn có chắc chắn muốn xóa phim này không?\nPhim sẽ bị ẩn khỏi hệ thống (không bị xóa hoàn toàn khỏi dữ liệu).',
        'Xóa phim'
    );
    if (!confirmed) return;

    try {
        const r = await fetch(`${API_MOVIES}/${id}`, { method: 'DELETE' });
        const resData = await r.json().catch(() => ({}));
        if (!r.ok) {
            throw new Error(resData.message || 'Không thể xóa phim.');
        }
        showToast('success', 'Xóa phim thành công!', 'Phim đã được ẩn khỏi hệ thống.');
        loadMovieStats();    // Cập nhật lại thống kê
        applyMovieFilter();  // Cập nhật lại danh sách phim
    } catch(e) {
        showToast('error', 'Lỗi khi xóa phim', e.message || 'Không thể xóa phim. Vui lòng thử lại.');
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
        if (!r.ok) {
            const errData = await r.json().catch(() => ({}));
            const errMsg = errData.message || `Không thể ${action} phim. Vui lòng thử lại.`;
            showToast('error', 'Thông báo', errMsg);
            return;
        }
        const data = await r.json();
        
        let result;
        if (currentActive) {
            result = { type:'warning', title:'Đã tạm ẩn phim!', msg:'Phim đã được ẩn khỏi giao diện khách hàng.' };
        } else {
            result = { type:'success', title:'Đã mở lại phim!', msg:'Phim đã hiển thị trở lại cho khách hàng.' };
        }
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
    ['filterShowtimeMovie', 'filterShowtimeRoom', 'filterShowtimeViewMode', 'filterShowtimeDayType', 'filterShowtimeStatus', 'filterShowtimeDate'].forEach(id => {
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

    // Cập nhật xem trước các suất chiếu khi thay đổi thông tin đầu vào
    ['showtimeMovieSelect', 'showtimeTimeInput', 'showtimeSlotCount'].forEach(id => {
        document.getElementById(id)?.addEventListener('input', recalculateShowtimeSlotsPreview);
        document.getElementById(id)?.addEventListener('change', recalculateShowtimeSlotsPreview);
    });
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
            try {
                const rShowtimes = await fetch(API_SHOWTIMES);
                const showtimes = await rShowtimes.json();
                const activeMovieIds = new Set(showtimes.map(s => s.movie ? s.movie.id : null).filter(id => id != null));
                const listWithShowtimes = list.filter(mv => activeMovieIds.has(mv.id));
                
                listWithShowtimes.forEach(mv => {
                    const opt = document.createElement('option');
                    opt.value = mv.id; opt.textContent = mv.title;
                    filterSel.appendChild(opt);
                });
            } catch (err) {
                console.error('Lỗi lọc phim có suất chiếu:', err);
                list.forEach(mv => {
                    const opt = document.createElement('option');
                    opt.value = mv.id; opt.textContent = mv.title;
                    filterSel.appendChild(opt);
                });
            }
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
// [SỬA - TrienLX - 2026-06-23]: Hỗ trợ nạp phòng cho cả form điều chỉnh 1 ngày
async function populateRoomDropdown(selectedRoomName = '', forceRefresh = false) {
    const roomContainer = document.getElementById('showtimeRoomsCheckboxContainer');
    const overrideSel = document.getElementById('overrideRoomInput');
    const filterSel = document.getElementById('filterShowtimeRoom');
    if (!roomContainer && !overrideSel && !filterSel) return;

    try {
        if (!_cachedRoomList || forceRefresh) {
            const r = await fetch(API_ROOMS);
            if (!r.ok) throw new Error();
            _cachedRoomList = await r.json();
        }
        const rooms = _cachedRoomList;

        // Nạp checkbox cho form Thêm/Sửa suất chiếu chính
        if (roomContainer) {
            roomContainer.innerHTML = '';
            rooms.forEach(room => {
                const label = document.createElement('label');
                label.style.cssText = 'display:flex; align-items:center; gap:0.5rem; cursor:pointer; padding:0.4rem 0.6rem; border-radius:6px; background:var(--bg-app); border:1px solid var(--border-color); font-size:0.85rem; color:var(--text-main); font-weight:500; transition:all 0.2s;';
                label.className = 'room-checkbox-label';

                const input = document.createElement('input');
                input.type = 'checkbox';
                input.name = 'showtimeRoom';
                input.value = room.roomName || '';
                input.style.cssText = 'cursor:pointer; width:16px; height:16px; border-radius:4px;';

                const details = [room.roomType, room.audioTech, room.totalSeats ? `${room.totalSeats} ghế` : '']
                    .filter(Boolean).join(' · ');
                const textSpan = document.createElement('span');
                textSpan.textContent = details ? `${room.roomName} (${details})` : room.roomName;

                label.appendChild(input);
                label.appendChild(textSpan);
                roomContainer.appendChild(label);
            });

            // Nếu có phòng được chọn sẵn (khi sửa)
            if (selectedRoomName) {
                const selectedRooms = selectedRoomName.split(',').map(r => r.trim());
                document.querySelectorAll('input[name="showtimeRoom"]').forEach(cb => {
                    cb.checked = selectedRooms.includes(cb.value);
                });
            }
        }

        const populateOptions = (sel, isFilter = false) => {
            if (!sel) return;
            sel.innerHTML = isFilter ? '<option value="">-- Tất cả phòng --</option>' : '<option value="">-- Chọn phòng --</option>';
            rooms.forEach(room => {
                const opt = document.createElement('option');
                opt.value = room.roomName || '';
                const details = [room.roomType, room.audioTech, room.totalSeats ? `${room.totalSeats} ghế` : '']
                    .filter(Boolean).join(' · ');
                opt.textContent = details ? `${room.roomName} (${details})` : room.roomName;
                sel.appendChild(opt);
            });
            if (!isFilter && selectedRoomName && [...sel.options].some(o => o.value === selectedRoomName)) {
                sel.value = selectedRoomName;
            }
        };

        populateOptions(overrideSel);
        populateOptions(filterSel, true);
    } catch (e) {
        console.error('Lỗi nạp danh sách phòng chiếu:', e);
        if (roomContainer) roomContainer.innerHTML = '<span style="color:var(--stat-red);">Không thể tải danh sách phòng</span>';
        if (overrideSel) overrideSel.innerHTML = '<option value="">Không thể tải danh sách phòng</option>';
        if (filterSel) filterSel.innerHTML = '<option value="">Không thể tải danh sách phòng</option>';
    }
}

// --- Tính toán và hiển thị danh sách suất chiếu xem trước ---
function recalculateShowtimeSlotsPreview() {
    const movieSel = document.getElementById('showtimeMovieSelect');
    const timeInputEl = document.getElementById('showtimeTimeInput');
    if (!movieSel || !timeInputEl) return;

    const movieId = movieSel.value;
    const timeInput = timeInputEl.value;
    const slotCountInput = document.getElementById('showtimeSlotCount');
    const slotCountVal = slotCountInput && slotCountInput.style.display !== 'none' && slotCountInput.closest('#slotCountGroup')?.style.display !== 'none'
        ? (parseInt(slotCountInput.value) || 1) : 1;

    const previewRow = document.getElementById('showtimeSlotsPreviewRow');
    const previewList = document.getElementById('showtimeSlotsPreviewList');
    if (!previewRow || !previewList) return;

    if (!movieId || !timeInput) {
        previewRow.style.display = 'none';
        currentPreviewSlots = [];
        return;
    }

    const movie = _cachedMovieList ? _cachedMovieList.find(m => m.id == movieId) : null;
    const duration = movie ? (movie.duration || 120) : 120;

    currentPreviewSlots = [];
    let currentSlotTime = timeInput; // format: "HH:mm"

    for (let i = 0; i < slotCountVal; i++) {
        const parts = currentSlotTime.split(':');
        const startHrs = parseInt(parts[0]);
        const startMins = parseInt(parts[1]);

        const startTotalMinutes = startHrs * 60 + startMins;
        const endTotalMinutes = startTotalMinutes + duration;

        const endHrs = Math.floor(endTotalMinutes / 60) % 24;
        const endMins = endTotalMinutes % 60;
        const endTimeStr = String(endHrs).padStart(2, '0') + ':' + String(endMins).padStart(2, '0');

        currentPreviewSlots.push({
            id: i,
            start: currentSlotTime,
            end: endTimeStr
        });

        // Tính giờ suất tiếp theo: thời điểm bắt đầu + 10m + duration + 20m
        let nextStartTotalMinutes = startTotalMinutes + 10 + duration + 20;
        const rem = nextStartTotalMinutes % 5;
        if (rem > 0) nextStartTotalMinutes += (5 - rem);

        const nextHrs = Math.floor(nextStartTotalMinutes / 60) % 24;
        const nextMins = nextStartTotalMinutes % 60;
        currentSlotTime = String(nextHrs).padStart(2, '0') + ':' + String(nextMins).padStart(2, '0');
    }

    renderShowtimeSlotsPreview();
}

function renderShowtimeSlotsPreview() {
    const previewRow = document.getElementById('showtimeSlotsPreviewRow');
    const previewList = document.getElementById('showtimeSlotsPreviewList');
    if (!previewRow || !previewList) return;

    if (currentPreviewSlots.length === 0) {
        previewRow.style.display = 'none';
        return;
    }

    previewRow.style.display = 'flex';
    previewList.innerHTML = '';

    currentPreviewSlots.forEach((slot, index) => {
        const item = document.createElement('div');
        item.style.cssText = 'display:flex; justify-content:space-between; align-items:center; background:var(--bg-app); border:1px solid var(--border-color); border-radius:6px; padding:0.4rem 0.6rem; font-size:0.85rem; transition:all 0.2s; width:100%;';
        item.className = 'preview-slot-item';

        const info = document.createElement('span');
        info.style.cssText = 'font-weight:500; color:var(--text-main);';
        info.innerHTML = `<span style="color:var(--text-muted); font-size:0.75rem;">Suất ${index + 1}:</span> <strong style="color:var(--primary-color);">${slot.start}</strong> - <strong>${slot.end}</strong>`;

        const btnDel = document.createElement('button');
        btnDel.type = 'button';
        btnDel.className = 'btn btn-outline';
        btnDel.style.cssText = 'padding:0.2rem 0.4rem; font-size:0.75rem; color:var(--stat-red); border-color:transparent; background:transparent; cursor:pointer;';
        btnDel.innerHTML = '<i class="fa-solid fa-trash-can"></i>';
        btnDel.onclick = () => deletePreviewSlot(index);

        item.appendChild(info);
        item.appendChild(btnDel);
        previewList.appendChild(item);
    });
}

function deletePreviewSlot(index) {
    currentPreviewSlots.splice(index, 1);
    renderShowtimeSlotsPreview();
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
    const groupMap = new Map();
    list.forEach(st => {
        const mv = st.movie || {};
        const key = `${st.showDate}|${mv.id||'?'}`;
        if (!groupMap.has(key)) {
            groupMap.set(key, {
                movie: mv,
                showDate: st.showDate,
                minDate: st.showDate,
                maxDate: st.showDate,
                dayType: st.dayType,
                hasOverride: !!(st.override || st.isOverride),
                note: st.note,
                ids: [st.id],
                slots: [{
                    id: st.id,
                    showTime: st.showTime,
                    room: st.room,
                    hasOverride: !!(st.override || st.isOverride),
                    note: st.note,
                    entries: [{
                        id: st.id,
                        showDate: st.showDate,
                        showTime: st.showTime,
                        room: st.room,
                        hasOverride: !!(st.override || st.isOverride)
                    }]
                }]
            });
        } else {
            const g = groupMap.get(key);
            g.ids.push(st.id);
            g.hasOverride = g.hasOverride || !!(st.override || st.isOverride);
            g.slots.push({
                id: st.id,
                showTime: st.showTime,
                room: st.room,
                hasOverride: !!(st.override || st.isOverride),
                note: st.note,
                entries: [{
                    id: st.id,
                    showDate: st.showDate,
                    showTime: st.showTime,
                    room: st.room,
                    hasOverride: !!(st.override || st.isOverride)
                }]
            });
        }
    });

    groupMap.forEach(g => {
        g.slots.sort((a, b) => (a.showTime || '').localeCompare(b.showTime || ''));
    });

    return Array.from(groupMap.values()).sort((a, b) => {
        const byDate = (a.showDate || '').localeCompare(b.showDate || '');
        if (byDate !== 0) return byDate;
        return (a.movie.title || '').localeCompare(b.movie.title || '');
    });
}

function isGroupEnded(g, todayStr) {
    if (g.maxDate < todayStr) return true;
    if (g.maxDate === todayStr) {
        const now = new Date();
        const currentMinutes = now.getHours() * 60 + now.getMinutes();
        return g.slots.every(slot => {
            if (!slot.showTime) return true;
            const p = slot.showTime.split(':');
            const h = parseInt(p[0]);
            const m = parseInt(p[1]);
            // Giờ kết thúc = Giờ chiếu + 10p quảng cáo + thời lượng phim + 20p dọn phòng
            const endMinutes = h * 60 + m + 10 + (g.movie.duration || 120) + 20;
            return endMinutes <= currentMinutes;
        });
    }
    return false;
}

// --- Render bảng lịch chiếu (hiển thị riêng từng ngày) ---
async function renderShowtimeTable() {
    const tbody = document.getElementById('showtimeTableBody');
    tbody.innerHTML = '';

    if (!showtimesData.length) {
        tbody.innerHTML = `<tr><td colspan="7" class="text-center" style="padding:3rem;color:var(--text-muted);">
            <i class="fa-regular fa-calendar-xmark" style="font-size:2rem;display:block;margin-bottom:.5rem;"></i>
            Không tìm thấy lịch chiếu nào.
        </td></tr>`;
        buildPagination('showtimePaginationControls','showtimePaginationInfo', 0, 1, ()=>{});
        return;
    }

    let groups = groupShowtimes(showtimesData);

    // Lọc theo phòng chiếu (Client-side)
    const roomVal = document.getElementById('filterShowtimeRoom')?.value;
    if (roomVal) {
        groups = groups.map(g => {
            return {
                ...g,
                slots: g.slots.filter(s => s.room === roomVal)
            };
        }).filter(g => g.slots.length > 0);
    }

    // Lọc theo trạng thái lịch chiếu
    const statusVal = document.getElementById('filterShowtimeStatus')?.value;
    if (statusVal) {
        const todayStr = new Date().toLocaleDateString('sv');
        groups = groups.filter(g => {
            const isEnded = isGroupEnded(g, todayStr);
            const isComingSoon = g.minDate > todayStr;
            const isActive = !isEnded && !isComingSoon;

            if (statusVal === 'Đã kết thúc') {
                return isEnded;
            } else if (statusVal === 'Sắp chiếu') {
                return isComingSoon;
            } else if (statusVal === 'Đang hoạt động') {
                return isActive;
            }
            return true;
        });
    }

    const countEl = document.getElementById('showtimeResultsCount');
    if (countEl) {
        countEl.textContent = `Tìm thấy ${groups.length} lịch chiếu`;
    }

    const start  = (showtimesPage - 1) * PAGE_SIZE;
    const slice  = groups.slice(start, start + PAGE_SIZE);
    const badgeMap = { 'Trong tuần':'badge-weekday', 'Cuối tuần':'badge-weekend', 'Ngày lễ':'badge-holiday' };

    slice.forEach((g) => {
        const mv = g.movie;
        const poster = mv.posterUrl
            ? `<img class="movie-poster-thumb" src="${esc(mv.posterUrl)}" alt="Poster"
                    onerror="this.outerHTML='<div class=\\'movie-poster-placeholder\\'><i class=\\'fa-regular fa-image\\'></i></div>'">`
            : `<div class="movie-poster-placeholder"><i class="fa-regular fa-image"></i></div>`;

        const isAdjustedGroup = g.hasOverride;
        const adjustedLabel = isAdjustedGroup
            ? `<span style="background:#fef3c7;color:#d97706;border:1px solid #fcd34d;padding:1px 6px;border-radius:4px;font-size:0.7rem;font-weight:600;margin-left:5px;vertical-align:middle;display:inline-flex;align-items:center;gap:3px;"><i class="fa-solid fa-pen-to-square"></i> Đã điều chỉnh</span>`
            : '';

        const totalSlots = g.slots.length;
        const dateRange = `<strong>${formatDate(g.showDate)}</strong>${adjustedLabel}`;

        const slotBadges = g.slots.map(slot => {
            const slotIdsJson = JSON.stringify([slot.id]);
            const startT = formatTime(slot.showTime);
            const endT   = getEndTime(slot.showTime, mv.duration);
            const isAdjustedSlot = slot.hasOverride;
            const badgeBg = isAdjustedSlot ? '#f59e0b' : 'var(--primary-color)';
            const iconHtml = isAdjustedSlot ? '<i class="fa-solid fa-pen" style="font-size:0.7rem;margin-right:4px;"></i>' : '';
            const titleTooltip = isAdjustedSlot
                ? `Giờ chiếu đã được điều chỉnh riêng.`
                : `Suất chiếu ${startT}`;
            const roomSuffix = slot.room ? ` (${slot.room})` : '';
            return `<span class="slot-time-badge" style="
                display:inline-flex; align-items:center; gap:8px; margin:2px 3px 2px 0;
                background:${badgeBg};
                color:#fff; padding:4px 12px; border-radius:20px;
                font-size:.82rem; font-weight:600; white-space:nowrap;
                " title="${titleTooltip}">
                <span>${iconHtml}${startT} – ${endT}</span>
                <span style="display:inline-flex; gap:6px; margin-left:4px; border-left:1px solid rgba(255,255,255,0.3); padding-left:6px;">
                    <i class="fa-solid fa-pen-to-square" onclick="event.stopPropagation(); editShowtime(${slot.id}, ${slotIdsJson})" title="Sửa suất chiếu này" style="cursor:pointer; opacity:0.8; transition:opacity 0.2s;" onmouseover="this.style.opacity=1" onmouseout="this.style.opacity=0.8"></i>
                    <i class="fa-solid fa-trash-can" onclick="event.stopPropagation(); deleteShowtime(${slot.id})" title="Xóa suất chiếu này" style="cursor:pointer; opacity:0.8; transition:opacity 0.2s;" onmouseover="this.style.opacity=1" onmouseout="this.style.opacity=0.8"></i>
                </span>
            </span>`;
        }).join('');

        const uniqueRooms = Array.from(new Set(g.slots.map(s => s.room).filter(Boolean))).join(', ');

        const actionHtml = `<div class="text-center" style="font-weight: 600; color: var(--text-main); font-size: 0.88rem;">${totalSlots} suất</div>`;

        const todayStr = new Date().toLocaleDateString('sv');
        let statusBadge = '';
        const isEnded = isGroupEnded(g, todayStr);
        if (isEnded) {
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
            <td>${dateRange}</td>
            <td style="min-width:160px;">${slotBadges}</td>
            <td>${esc(uniqueRooms||'—')}</td>
            <td><span class="badge-daytype ${badgeMap[g.dayType]||''}">${esc(g.dayType||'—')}</span></td>
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
        viewMode: document.getElementById('filterShowtimeViewMode').value || 'week',
        dayType:  document.getElementById('filterShowtimeDayType').value  || null,
        startDate:document.getElementById('filterShowtimeDate').value     || null
    });
}
function resetShowtimeFilter() {
    ['filterShowtimeMovie','filterShowtimeRoom','filterShowtimeDayType','filterShowtimeDate','filterShowtimeStatus']
        .forEach(id => {
            const el = document.getElementById(id);
            if (el) el.value = '';
        });
    document.getElementById('filterShowtimeViewMode').value = 'week';
    applyShowtimeFilter();
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
        document.querySelectorAll('input[name="showtimeRoom"]').forEach(cb => cb.checked = false);

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
    recalculateShowtimeSlotsPreview();
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

    const checkedRoomCbs = Array.from(document.querySelectorAll('input[name="showtimeRoom"]:checked'));
    const roomVal = checkedRoomCbs.map(cb => cb.value).join(', ');
    if (!timeVal || !roomVal) {
        showToast('warning', 'Thiếu thông tin', 'Vui lòng chọn ít nhất một phòng chiếu và điền đầy đủ các trường bắt buộc (*)');
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

        const showTimes = currentPreviewSlots.map(slot => slot.start + ':00');
        if (showTimes.length === 0) {
            showToast('warning', 'Thiếu suất chiếu', 'Danh sách suất chiếu không được trống. Vui lòng thêm/để lại ít nhất một suất chiếu.');
            return;
        }

        body = {
            movieId:   parseInt(movieId),
            startDate: startDate,
            endDate:   endDate,
            showTime:  timeVal + ':00',
            room:      roomVal,
            slotCount: slotCount,
            showTimes: showTimes
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

        const showTimes = currentPreviewSlots.map(slot => slot.start + ':00');
        if (showTimes.length === 0) {
            showToast('warning', 'Thiếu suất chiếu', 'Danh sách suất chiếu không được trống. Vui lòng thêm/để lại ít nhất một suất chiếu.');
            return;
        }

        body = {
            movieId:   parseInt(movieId),
            startDate: startDate,
            endDate:   endDate,
            showTime:  timeVal + ':00',
            room:      roomVal,
            groupIds:  currentEditingGroupIds,
            showTimes: showTimes
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
        const selectedRooms = (st.room || '').split(',').map(r => r.trim());
        document.querySelectorAll('input[name="showtimeRoom"]').forEach(cb => {
            cb.checked = selectedRooms.includes(cb.value);
        });
        document.getElementById('showtimeDayTypeDisplay').value  = st.dayType   || '—';
        recalculateShowtimeSlotsPreview();
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



// ======================================================
//   QUẢN LÝ BÁN VÉ & SƠ ĐỒ GHẾ (POS Terminal Flow)
// ======================================================

let selectedSeatId    = null;
let selectedSeatLabel = null;
let selectedTicket    = null;

function initTicketEvents() {
    const movieSelect = document.getElementById('ticketMovieSelect');
    if (movieSelect) {
        movieSelect.addEventListener('change', async function(e) {
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

    const showtimeSelect = document.getElementById('ticketShowtimeSelect');
    if (showtimeSelect) {
        showtimeSelect.addEventListener('change', function(e) {
            const id = e.target.value;
            activeShowtimeId = id ? parseInt(id) : null;
            if (activeShowtimeId) loadTicketView(activeShowtimeId);
            else hideSeatMap();
        });
    }

    const sellCustType = document.getElementById('sellCustomerType');
    if (sellCustType) sellCustType.addEventListener('change', function() { updateBookingPriceBreakdown(); });

    const btnConfirmSell = document.getElementById('btnActionConfirmSell');
    if (btnConfirmSell) btnConfirmSell.addEventListener('click', function(e) { e.preventDefault(); handleConfirmSellTicket(); });

    const btnConfirmHold = document.getElementById('btnActionConfirmHold');
    if (btnConfirmHold) btnConfirmHold.addEventListener('click', function(e) { e.preventDefault(); handleConfirmHoldSeat(); });

    const btnOpenUpdateCust = document.getElementById('btnActionOpenUpdateCustomer');
    if (btnOpenUpdateCust) btnOpenUpdateCust.addEventListener('click', function() { if (selectedTicket) openUpdateCustomerModal(selectedTicket); });

    const btnOpenChangeSeat = document.getElementById('btnActionOpenChangeSeat');
    if (btnOpenChangeSeat) btnOpenChangeSeat.addEventListener('click', function() { if (selectedTicket) openChangeSeatModal(selectedTicket); });

    const btnRefund = document.getElementById('btnActionRefundTicket');
    if (btnRefund) btnRefund.addEventListener('click', function() { if (selectedTicket) confirmCancelSeat(selectedTicket.id, selectedTicket.seatNumber); });

    const elBCUM = document.getElementById('btnCloseUpdateCustomerModal');
    if (elBCUM) elBCUM.addEventListener('click', function() { closeUpdateCustomerModal(); });
    const elBCancUM = document.getElementById('btnCancelUpdateCustomer');
    if (elBCancUM) elBCancUM.addEventListener('click', function() { closeUpdateCustomerModal(); });
    const elBConfUM = document.getElementById('btnConfirmUpdateCustomer');
    if (elBConfUM) elBConfUM.addEventListener('click', function() { handleConfirmUpdateCustomer(); });
    const elUCS = document.getElementById('updateCustomerSelect');
    if (elUCS) elUCS.addEventListener('change', function() { updateModalPriceSuggestion(); });

    const elBCCSM = document.getElementById('btnCloseChangeSeatModal');
    if (elBCCSM) elBCCSM.addEventListener('click', function() { closeChangeSeatModal(); });
    const elBCancCS = document.getElementById('btnCancelChangeSeat');
    if (elBCancCS) elBCancCS.addEventListener('click', function() { closeChangeSeatModal(); });
    const elBConfCS = document.getElementById('btnConfirmChangeSeat');
    if (elBConfCS) elBConfCS.addEventListener('click', function() { handleConfirmChangeSeat(); });

    const elBCECM = document.getElementById('btnCloseEditConfigModal');
    if (elBCECM) elBCECM.addEventListener('click', closeEditConfigModal);
    const elBCancEC = document.getElementById('btnCancelEditConfig');
    if (elBCancEC) elBCancEC.addEventListener('click', function(e) { e.preventDefault(); closeEditConfigModal(); });
    const elBSaveC = document.getElementById('btnSaveConfig');
    if (elBSaveC) elBSaveC.addEventListener('click', savePricingConfig);

    switchMainTicketTab('sell');
}

async function populateTicketMovieDropdown() {
    try {
        const r = await fetch(API_MOVIES);
        const list = await r.json();
        const movieSelect = document.getElementById('ticketMovieSelect');
        if (movieSelect) {
            movieSelect.innerHTML = '<option value="">-- Chọn phim --</option>';
            list.forEach(function(mv) {
                const opt = document.createElement('option');
                opt.value = mv.id;
                opt.textContent = mv.title;
                movieSelect.appendChild(opt);
            });
        }
    } catch (e) {
        console.error('Lỗi nạp phim bán vé:', e);
    }
}

async function populateTicketShowtimes(movieId) {
    try {
        const r = await fetch(API_SHOWTIMES + '?movieId=' + movieId);
        const list = await r.json();
        const showtimeSelect = document.getElementById('ticketShowtimeSelect');
        if (!showtimeSelect) return;
        showtimeSelect.innerHTML = '<option value="">-- Chọn suất chiếu --</option>';

        const now = new Date();
        const todayStr = now.toLocaleDateString('sv'); // format: YYYY-MM-DD
        const nowTimeStr = now.toTimeString().substring(0, 5); // format: HH:MM

        list.forEach(function(st) {
            let showTimeStr = '';
            if (st.showTime) {
                if (typeof st.showTime === 'string') {
                    showTimeStr = st.showTime.substring(0, 5);
                } else if (Array.isArray(st.showTime)) {
                    showTimeStr = String(st.showTime[0]).padStart(2, '0') + ':' + String(st.showTime[1]).padStart(2, '0');
                }
            }
            const isExpired = st.showDate < todayStr || (st.showDate === todayStr && showTimeStr < nowTimeStr);
            if (isExpired) return;

            const opt = document.createElement('option');
            opt.value = st.id;
            const dateStr = formatDate ? formatDate(st.showDate) : (st.showDate || '');
            const timeStr = formatTime ? formatTime(st.showTime) : (st.showTime || '');
            opt.textContent = dateStr + ' ' + timeStr + ' | ' + (st.room || '?');
            showtimeSelect.appendChild(opt);
        });
        activeShowtimeId = null;
        hideSeatMap();
    } catch (e) {
        console.error('Lỗi nạp suất chiếu theo phim:', e);
    }
}

async function loadTicketView(showtimeId) {
    try {
        const seatsRes  = await fetch(API_TICKETS + '/seats?showtimeId=' + showtimeId);
        const ticketRes = await fetch(API_TICKETS + '/showtime/' + showtimeId);
        const statsRes  = await fetch(API_TICKETS + '/stats/' + showtimeId);
        seatsData   = await seatsRes.json();
        ticketsData = await ticketRes.json();
        const stats = await statsRes.json();
        updateTicketStats(stats);
        renderSeatGrid(seatsData, ticketsData);
        showSeatMap();
        resetBookingPanel();
    } catch (e) {
        console.error('Lỗi tải dữ liệu vé:', e);
        hideSeatMap();
    }
}

// --- Hàm tương thích ngược khi lịch chiếu thay đổi để tự refresh dropdown ---
async function populateShowtimeDropdown() {
    const movieSelect = document.getElementById('ticketMovieSelect');
    if (movieSelect && movieSelect.value) {
        await populateTicketShowtimes(movieSelect.value);
    }
}

function showSeatMap() {
    const el  = document.getElementById('seatMapSection');
    const emp = document.getElementById('seatMapEmpty');
    if (el)  el.style.display  = 'grid';
    if (emp) emp.style.display = 'none';
}

function hideSeatMap() {
    const el  = document.getElementById('seatMapSection');
    const emp = document.getElementById('seatMapEmpty');
    if (el)  el.style.display  = 'none';
    if (emp) emp.style.display = 'block';
}

function updateTicketStats(stats) {
    const statSold = document.getElementById('ticketStatSold');
    if (statSold) statSold.textContent = stats.soldCount != null ? stats.soldCount : 0;
    const statOcc = document.getElementById('ticketStatOccupancy');
    if (statOcc) statOcc.textContent = (stats.occupancyRate != null ? stats.occupancyRate : 0) + '%';
    const statTotal = document.getElementById('ticketStatTotal');
    if (statTotal) statTotal.textContent = stats.totalCount != null ? stats.totalCount : 0;
    const statEmpty = document.getElementById('ticketStatEmpty');
    if (statEmpty) statEmpty.textContent = stats.emptyCount != null ? stats.emptyCount : 0;
    const statRev = document.getElementById('ticketStatRevenue');
    if (statRev) statRev.textContent = formatVND(stats.revenue || 0);
}

function resetBookingPanel() {
    selectedSeatId    = null;
    selectedSeatLabel = null;
    selectedTicket    = null;

    const titleEl = document.getElementById('panelActionTitle');
    if (titleEl) titleEl.textContent = 'Thông tin chọn ghế';
    const formEl = document.getElementById('bookingFormContainer');
    if (formEl) formEl.style.display = 'block';
    const detailEl = document.getElementById('bookingDetailContainer');
    if (detailEl) detailEl.style.display = 'none';

    ['sellSeatLabel','sellSeatId','sellCustomerName','sellCustomerPhone'].forEach(function(id) {
        const el = document.getElementById(id); if (el) el.value = '';
    });
    const sctEl = document.getElementById('sellCustomerType');
    if (sctEl) sctEl.value = 'ADULT';
    ['breakdownBasePrice','breakdownSeatSurcharge','breakdownFormatSurcharge','breakdownTotal'].forEach(function(id) {
        const el = document.getElementById(id); if (el) el.textContent = '0đ';
    });
    const bdEl = document.getElementById('breakdownDiscount');
    if (bdEl) bdEl.textContent = '-0đ';
}

function renderSeatGrid(seats, soldTickets) {
    const grid = document.getElementById('seatGrid');
    if (!grid) return;
    grid.innerHTML = '';

    const rowMap = {};
    seats.forEach(function(s) {
        const row = s.seatLabel.charAt(0);
        if (!rowMap[row]) rowMap[row] = [];
        rowMap[row].push(s);
    });

    Object.keys(rowMap).sort().forEach(function(row) {
        const rowSeats = rowMap[row].sort(function(a, b) {
            return parseInt(a.seatLabel.slice(1)) - parseInt(b.seatLabel.slice(1));
        });

        const rowDiv = document.createElement('div');
        rowDiv.className = 'seat-row';
        rowDiv.style.cssText = 'display:flex;gap:8px;margin-bottom:8px;align-items:center;justify-content:center;';

        const lbl = document.createElement('span');
        lbl.textContent    = row;
        lbl.style.cssText  = 'font-weight:700;width:24px;color:var(--text-muted);';
        rowDiv.appendChild(lbl);

        rowSeats.forEach(function(seat) {
            const ticket   = soldTickets ? soldTickets.find(function(t) { return t.seat && t.seat.id === seat.id; }) : null;
            const isVIP    = seat.seatType === 'vip';
            const isCouple = seat.seatType === 'couple';

            let stateClass = 'seat-empty-standard';
            if (isVIP)    stateClass = 'seat-empty-vip';
            else if (isCouple) stateClass = 'seat-empty-couple';

            if (ticket) {
                const s = ticket.status;
                const isBookedStatus = s === 'BOOKED' || s === 'CONFIRMED' || s === 'PAID';
                if (isBookedStatus)  stateClass = 'seat-state-booked';
                else if (s === 'PENDING' || s === 'HOLDING') stateClass = 'seat-state-pending';
            }

            const btn = document.createElement('div');
            btn.className        = 'seat-item ' + stateClass;
            btn.dataset.seatId   = seat.id;
            btn.dataset.seatLabel= seat.seatLabel;
            btn.textContent      = seat.seatLabel;

            let tooltip = 'Ghế ' + seat.seatLabel + ' - ' + (isVIP ? 'VIP' : (isCouple ? 'Đôi' : 'Thường'));
            if (ticket) {
                const isBookedStatus = ticket.status === 'BOOKED' || ticket.status === 'CONFIRMED' || ticket.status === 'PAID';
                tooltip += ' [' + (isBookedStatus ? 'Đã bán' : 'Đang giữ chỗ') + ']';
            } else {
                tooltip += ' [Còn trống]';
            }
            btn.title = tooltip;

            btn.addEventListener('click', function() {
                document.querySelectorAll('.seat-item').forEach(function(el) { el.style.outline = 'none'; });
                btn.style.outline       = '3px solid var(--primary-color)';
                btn.style.outlineOffset = '2px';
                if (ticket) {
                    const isBookedStatus = ticket.status === 'BOOKED' || ticket.status === 'CONFIRMED' || ticket.status === 'PAID';
                    if (isBookedStatus) selectBookedSeat(ticket);
                    else selectPendingSeat(seat, ticket);
                } else {
                    selectEmptySeat(seat);
                }
            });

            rowDiv.appendChild(btn);
        });

        grid.appendChild(rowDiv);
    });
}

function selectEmptySeat(seat) {
    selectedSeatId    = seat.id;
    selectedSeatLabel = seat.seatLabel;
    selectedTicket    = null;
    document.getElementById('panelActionTitle').textContent    = 'Bán vé - Ghế ' + seat.seatLabel;
    document.getElementById('bookingFormContainer').style.display  = 'block';
    document.getElementById('bookingDetailContainer').style.display = 'none';
    document.getElementById('sellSeatLabel').value       = seat.seatLabel;
    document.getElementById('sellSeatId').value          = seat.id;
    document.getElementById('sellCustomerType').value    = 'ADULT';
    const holdBtn = document.getElementById('btnActionConfirmHold');
    if (holdBtn) { holdBtn.style.display = 'inline-flex'; holdBtn.innerHTML = '<i class="fa-solid fa-clock"></i> Giữ chỗ'; holdBtn.onclick = null; }
    const sellBtn = document.getElementById('btnActionConfirmSell');
    if (sellBtn) sellBtn.innerHTML = '<i class="fa-solid fa-cart-shopping"></i> Bán vé';
    updateBookingPriceBreakdown();
}

function selectPendingSeat(seat, ticket) {
    selectedSeatId    = seat.id;
    selectedSeatLabel = seat.seatLabel;
    selectedTicket    = ticket;
    document.getElementById('panelActionTitle').textContent    = 'Ghế đang giữ chỗ - Ghế ' + seat.seatLabel;
    document.getElementById('bookingFormContainer').style.display  = 'block';
    document.getElementById('bookingDetailContainer').style.display = 'none';
    document.getElementById('sellSeatLabel').value       = seat.seatLabel;
    document.getElementById('sellSeatId').value          = seat.id;
    document.getElementById('sellCustomerType').value    = ticket.customerType  || 'ADULT';
    const holdBtn = document.getElementById('btnActionConfirmHold');
    if (holdBtn) {
        holdBtn.style.display = 'inline-flex';
        holdBtn.innerHTML = '<i class="fa-solid fa-clock-rotate-left"></i> Hủy giữ chỗ';
        holdBtn.onclick = function(e) { e.preventDefault(); handleReleaseHoldSeat(ticket.seat.id); };
    }
    const sellBtn = document.getElementById('btnActionConfirmSell');
    if (sellBtn) sellBtn.innerHTML = '<i class="fa-solid fa-circle-check"></i> Xác nhận thanh toán';
    updateBookingPriceBreakdown();
}

function selectBookedSeat(ticket) {
    selectedSeatId    = ticket.seat.id;
    selectedSeatLabel = ticket.seatNumber;
    selectedTicket    = ticket;
    document.getElementById('panelActionTitle').textContent    = 'Thông tin vé - Ghế ' + ticket.seatNumber;
    document.getElementById('bookingFormContainer').style.display  = 'none';
    document.getElementById('bookingDetailContainer').style.display = 'block';

    document.getElementById('detailTicketId').textContent   = 'TK-' + String(ticket.id).padStart(5, '0');
    document.getElementById('detailSeatLabel').textContent  = ticket.seatNumber + ' (' + (ticket.seatType || '') + ')';
    const badge = document.getElementById('detailStatus');
    if (badge) { badge.textContent = ticket.status; badge.className = 'badge badge-ongoing'; }

    const typeLabels = { 'ADULT':'Người lớn (Adult)', 'STUDENT':'Học sinh/SV (Student)', 'CHILD':'Trẻ em (Child)', 'VIP':'Khách hàng VIP' };
    document.getElementById('detailCustType').textContent = typeLabels[ticket.customerType] || ticket.customerType || '--';

    let timeStr = '--';
    if (ticket.createdAt) {
        const dt = new Date(ticket.createdAt);
        timeStr = dt.getHours().toString().padStart(2,'0') + ':' + dt.getMinutes().toString().padStart(2,'0')
            + ' - ' + dt.getDate().toString().padStart(2,'0') + '/' + (dt.getMonth()+1).toString().padStart(2,'0') + '/' + dt.getFullYear();
    }
    document.getElementById('detailBookingTime').textContent = timeStr;

    document.getElementById('detailPriceBase').textContent    = formatVND(ticket.basePrice     || 0) + 'đ';
    document.getElementById('detailPriceSeat').textContent    = '+' + formatVND(ticket.seatSurcharge  || 0) + 'đ';
    document.getElementById('detailPriceFormat').textContent  = '+' + formatVND(ticket.formatSurcharge|| 0) + 'đ';
    document.getElementById('detailPriceDiscount').textContent= '-' + formatVND(ticket.discountAmount || 0) + 'đ';
    document.getElementById('detailPriceTotal').textContent   = formatVND(ticket.finalPrice    || 0) + 'đ';
}

async function updateBookingPriceBreakdown() {
    if (!selectedSeatId) return;
    const customerType = document.getElementById('sellCustomerType').value;
    let url;
    if (selectedTicket) {
        url = '/api/tickets/' + selectedTicket.id + '/price-suggestion?customerType=' + customerType;
    } else {
        url = '/api/tickets/price-suggestion?showtimeId=' + activeShowtimeId + '&seatId=' + selectedSeatId + '&customerType=' + customerType;
    }
    try {
        const res = await fetch(url);
        const breakdown = await res.json();
        document.getElementById('breakdownBasePrice').textContent     = formatVND(breakdown.basePrice      || 0) + 'đ';
        document.getElementById('breakdownSeatSurcharge').textContent = '+' + formatVND(breakdown.seatSurcharge   || 0) + 'đ';
        document.getElementById('breakdownFormatSurcharge').textContent = '+' + formatVND(breakdown.formatSurcharge || 0) + 'đ';
        document.getElementById('breakdownDiscount').textContent      = '-' + formatVND(breakdown.discountAmount  || 0) + 'đ';
        document.getElementById('breakdownTotal').textContent         = formatVND(breakdown.finalPrice     || 0) + 'đ';
    } catch (err) {
        console.error('Lỗi tải gợi ý giá:', err);
    }
}

async function handleConfirmHoldSeat() {
    if (!selectedSeatId) return;
    try {
        const r = await fetch('/api/tickets/hold?showtimeId=' + activeShowtimeId + '&seatId=' + selectedSeatId, { method: 'POST' });
        if (!r.ok) { const data = await r.json(); throw new Error(data.error || 'Giữ chỗ thất bại.'); }
        showToast('success', 'Đã giữ chỗ ghế!', 'Ghế ' + selectedSeatLabel + ' đã được chuyển sang trạng thái giữ chỗ.');
        await loadTicketView(activeShowtimeId);
    } catch (err) {
        showToast('error', 'Lỗi giữ chỗ', err.message);
    }
}

async function handleReleaseHoldSeat(seatId) {
    try {
        const r = await fetch('/api/tickets/release?showtimeId=' + activeShowtimeId + '&seatId=' + seatId, { method: 'POST' });
        if (!r.ok) { const data = await r.json(); throw new Error(data.error || 'Giải phóng ghế thất bại.'); }
        showToast('success', 'Đã giải phóng ghế!', 'Đã hủy giữ chỗ thành công.');
        const holdBtn = document.getElementById('btnActionConfirmHold');
        if (holdBtn) { holdBtn.innerHTML = '<i class="fa-solid fa-clock"></i> Giữ chỗ'; holdBtn.onclick = null; }
        await loadTicketView(activeShowtimeId);
    } catch (err) {
        showToast('error', 'Lỗi thao tác', err.message);
    }
}

async function handleConfirmSellTicket() {
    if (!selectedSeatId) return;
    const customerType  = document.getElementById('sellCustomerType').value;
    try {
        const url = '/api/tickets/sell?showtimeId=' + activeShowtimeId
            + '&seatId='       + selectedSeatId
            + '&customerType=' + customerType;
        const r = await fetch(url, { method: 'POST' });
        if (!r.ok) { const data = await r.json(); throw new Error(data.error || 'Lỗi từ máy chủ.'); }
        showToast('success', 'Bán vé thành công!', 'Vé cho ghế ' + selectedSeatLabel + ' đã được ghi nhận.');
        await loadTicketView(activeShowtimeId);
    } catch (err) {
        showToast('error', 'Bán vé thất bại', err.message);
    }
}

function openUpdateCustomerModal(ticket) {
    document.getElementById('updateCustomerTicketId').value = ticket.id;
    document.getElementById('updateCustomerSelect').value   = ticket.customerType || 'ADULT';
    updateModalPriceSuggestion();
    document.getElementById('updateCustomerTypeModal').style.display = 'flex';
}
function closeUpdateCustomerModal() {
    document.getElementById('updateCustomerTypeModal').style.display = 'none';
}

async function updateModalPriceSuggestion() {
    const ticketId = document.getElementById('updateCustomerTicketId').value;
    const custType = document.getElementById('updateCustomerSelect').value;
    try {
        const res = await fetch('/api/tickets/' + ticketId + '/price-suggestion?customerType=' + custType);
        const breakdown = await res.json();
        document.getElementById('updatePriceSuggestionBox').style.display = 'block';
        document.getElementById('upSuggestBase').textContent       = formatVND(breakdown.basePrice || 0) + 'đ';
        document.getElementById('upSuggestSurcharges').textContent = '+' + formatVND((breakdown.seatSurcharge||0) + (breakdown.formatSurcharge||0)) + 'đ';
        document.getElementById('upSuggestDiscount').textContent   = '-' + formatVND(breakdown.discountAmount || 0) + 'đ';
        document.getElementById('upSuggestTotal').textContent      = formatVND(breakdown.finalPrice || 0) + 'đ';
    } catch (e) {
        console.error('Lỗi gợi ý giá modal:', e);
    }
}

async function handleConfirmUpdateCustomer() {
    const ticketId = document.getElementById('updateCustomerTicketId').value;
    const custType = document.getElementById('updateCustomerSelect').value;
    try {
        const r = await fetch('/api/tickets/' + ticketId + '/update-customer?customerType=' + custType, { method: 'PUT' });
        if (!r.ok) { const data = await r.json(); throw new Error(data.error || 'Cập nhật đối tượng thất bại.'); }
        showToast('success', 'Đã lưu thay đổi!', 'Cấu trúc giá và đối tượng của vé đã được cập nhật.');
        closeUpdateCustomerModal();
        await loadTicketView(activeShowtimeId);
    } catch (err) {
        showToast('error', 'Lỗi cập nhật', err.message);
    }
}

function openChangeSeatModal(ticket) {
    document.getElementById('changeSeatTicketId').value = ticket.id;
    document.getElementById('changeSeatCurrentLabel').textContent = ticket.seatNumber + ' (' + (ticket.seatType || '') + ')';
    const select = document.getElementById('changeSeatSelect');
    select.innerHTML = '<option value="">-- Chọn ghế mới --</option>';
    seatsData.forEach(function(seat) {
        const taken = ticketsData.some(function(t) { return t.seat && t.seat.id === seat.id; });
        const isSkipped = seat.seatType === 'broken' || seat.seatType === 'empty' || seat.seatType === 'skip';
        if (!taken && !isSkipped) {
            const opt = document.createElement('option');
            opt.value = seat.id;
            opt.textContent = 'Ghế ' + seat.seatLabel + ' - Loại: ' + (seat.seatType === 'vip' ? 'VIP' : (seat.seatType === 'couple' ? 'Đôi' : 'Thường'));
            select.appendChild(opt);
        }
    });
    document.getElementById('changeSeatModal').style.display = 'flex';
}
function closeChangeSeatModal() {
    document.getElementById('changeSeatModal').style.display = 'none';
}

async function handleConfirmChangeSeat() {
    const ticketId = document.getElementById('changeSeatTicketId').value;
    const newSeatId = document.getElementById('changeSeatSelect').value;
    if (!newSeatId) { showToast('warning', 'Chưa chọn ghế mới', 'Vui lòng chọn một ghế trống để tiến hành đổi.'); return; }
    try {
        const r = await fetch('/api/tickets/' + ticketId + '/change-seat?newSeatId=' + newSeatId, { method: 'POST' });
        if (!r.ok) { const data = await r.json(); throw new Error(data.error || 'Đổi ghế thất bại.'); }
        showToast('success', 'Đổi ghế thành công!', 'Ghế ngồi mới đã được chỉ định cho khách.');
        closeChangeSeatModal();
        await loadTicketView(activeShowtimeId);
    } catch (err) {
        showToast('error', 'Lỗi đổi ghế', err.message);
    }
}

async function confirmCancelSeat(ticketId, seatNum) {
    const confirmed = await showConfirm(
        'Hủy vé & Hoàn tiền',
        'Ghế ' + seatNum + ' đang có vé bán hoạt động.\nBạn có muốn hủy bán vé này không? Trạng thái sẽ được chuyển sang REFUNDED để hoàn trả ghế trống, nhưng hóa đơn vẫn lưu lại để đối soát doanh thu.',
        'Xác nhận hủy bán'
    );
    if (!confirmed) return;
    try {
        const r = await fetch(API_TICKETS + '/' + ticketId, { method: 'DELETE' });
        if (!r.ok) { const errData = await r.json(); throw new Error(errData.error || 'Lỗi máy chủ.'); }
        showToast('success', 'Đã hủy vé thành công!', 'Vé của ghế ' + seatNum + ' đã được hoàn trả.');
        await loadTicketView(activeShowtimeId);
    } catch (e) {
        showToast('error', 'Hủy vé thất bại', e.message);
    }
}


// =========================================================================
//   SUB-TAB 2: TRA CỨU & BỘ LỌC TỔNG THỂ
// =========================================================================
let globalCurrentPage = 1;
let globalTotalPages  = 1;

async function populateGlobalMoviesAndRooms() {
    try {
        const mvRes  = await fetch(API_MOVIES);
        const movies = await mvRes.json();
        const filterMovie = document.getElementById('filterGlobalMovie');
        if (filterMovie) {
            filterMovie.innerHTML = '<option value="">-- Tất cả phim --</option>';
            movies.forEach(function(m) {
                const opt = document.createElement('option'); opt.value = m.id; opt.textContent = m.title; filterMovie.appendChild(opt);
            });
        }
        const filterRoom = document.getElementById('filterGlobalRoom');
        if (filterRoom) {
            filterRoom.innerHTML = '<option value="">-- Tất cả phòng --</option>';
            ['Phòng 1','Phòng 2','Phòng 3','Phòng 4','Phòng 5'].forEach(function(r) {
                const opt = document.createElement('option'); opt.value = r; opt.textContent = r; filterRoom.appendChild(opt);
            });
        }
        searchGlobalTickets(1);
    } catch (e) { console.error('Lỗi nạp phim/phòng cho bộ lọc:', e); }
}

function handleGlobalTimeShortcutChange() {
    const val = document.getElementById('filterGlobalTimeShortcut').value;
    const rangeBox = document.getElementById('groupGlobalDateRange');
    if (val === 'range') rangeBox.style.display = 'flex';
    else {
        rangeBox.style.display = 'none';
        document.getElementById('filterGlobalStartDate').value = '';
        document.getElementById('filterGlobalEndDate').value   = '';
    }
}

function resetGlobalTicketFilter() {
    const form = document.getElementById('globalTicketFilterForm');
    if (form) form.reset();
    const rangeBox = document.getElementById('groupGlobalDateRange');
    if (rangeBox) rangeBox.style.display = 'none';
    const tbody = document.getElementById('globalTicketTableBody');
    if (tbody) tbody.innerHTML = '<tr><td colspan="9" class="text-center" style="padding:3rem;color:var(--text-muted);">Chưa có dữ liệu tìm kiếm. Hãy nhấn Lọc danh sách.</td></tr>';
    const countEl = document.getElementById('globalTicketsCount');
    if (countEl) countEl.textContent = 'Tìm thấy 0 vé';
    const pgEl = document.getElementById('globalTicketPaginationControls');
    if (pgEl) pgEl.innerHTML = '';
    const pgInfo = document.getElementById('globalTicketPaginationInfo');
    if (pgInfo) pgInfo.textContent = 'Hiển thị trang 1 / 1';
}

async function searchGlobalTickets(page) {
    if (page == null) page = 1;
    globalCurrentPage = page;
    const movieId    = document.getElementById('filterGlobalMovie').value;
    const room       = document.getElementById('filterGlobalRoom').value;
    const status     = document.getElementById('filterGlobalStatus').value;
    const shortcut   = document.getElementById('filterGlobalTimeShortcut').value;
    const searchTerm = document.getElementById('filterGlobalSearchTerm').value;

    let startDate = '', endDate = '';
    const todayStr = new Date().toISOString().split('T')[0];
    if (shortcut === 'today') { startDate = todayStr; endDate = todayStr; }
    else if (shortcut === 'tomorrow') {
        const t = new Date(); t.setDate(t.getDate() + 1);
        const ts = t.toISOString().split('T')[0]; startDate = ts; endDate = ts;
    } else if (shortcut === 'range') {
        startDate = document.getElementById('filterGlobalStartDate').value;
        endDate   = document.getElementById('filterGlobalEndDate').value;
    }

    let url = '/api/tickets/search?';
    if (movieId)    url += 'movieId='    + movieId    + '&';
    if (room)       url += 'room='       + encodeURIComponent(room)       + '&';
    if (status)     url += 'status='     + status     + '&';
    if (startDate)  url += 'startDate='  + startDate  + '&';
    if (endDate)    url += 'endDate='    + endDate    + '&';
    if (searchTerm) url += 'searchTerm=' + encodeURIComponent(searchTerm) + '&';

    try {
        const r = await fetch(url);
        const results = await r.json();
        const PAGE_SIZE = 10;
        const totalCount = results.length;
        const countEl = document.getElementById('globalTicketsCount');
        if (countEl) countEl.textContent = 'Tìm thấy ' + totalCount + ' vé';
        globalTotalPages = Math.max(1, Math.ceil(totalCount / PAGE_SIZE));
        if (globalCurrentPage > globalTotalPages) globalCurrentPage = globalTotalPages;
        const startIdx = (globalCurrentPage - 1) * PAGE_SIZE;
        renderGlobalTicketTable(results.slice(startIdx, startIdx + PAGE_SIZE), startIdx);
        renderGlobalPagination(totalCount, PAGE_SIZE);
    } catch (e) {
        console.error('Lỗi tìm kiếm vé:', e);
        showToast('error', 'Lỗi tìm kiếm', 'Không thể nạp dữ liệu vé.');
    }
}

function renderGlobalTicketTable(list, startIdx) {
    const tbody = document.getElementById('globalTicketTableBody');
    tbody.innerHTML = '';

    // Reset select all checkbox
    const selectAllBox = document.getElementById('selectAllTickets');
    if (selectAllBox) selectAllBox.checked = false;
    updateBatchDeleteButtonState();

    if (!list.length) {
        tbody.innerHTML = `
            <tr>
                <td colspan="10" class="text-center" style="padding: 4rem 2rem; color: var(--text-muted);">
                    <div style="font-size: 3rem; margin-bottom: 1rem; opacity: 0.3;"><i class="fa-solid fa-folder-open"></i></div>
                    <div style="font-size: 1.1rem; font-weight: 600; color: var(--text-main); margin-bottom: 0.5rem;">Không tìm thấy dữ liệu vé</div>
                    <div style="font-size: 0.88rem; max-width: 400px; margin: 0 auto; line-height: 1.4;">
                        Không có vé nào thỏa mãn điều kiện tìm kiếm hiện tại của bạn. Vui lòng thử thay đổi từ khóa hoặc các bộ lọc bên trên.
                    </div>
                </td>
            </tr>
        `;
        return;
    }
    const statusColors = { 'BOOKED':'background:#d1fae5;color:#065f46;', 'PENDING':'background:#fef3c7;color:#92400e;', 'REFUNDED':'background:#fee2e2;color:#991b1b;' };
    const customerLabels = { 'ADULT':'Người lớn', 'STUDENT':'Học sinh/SV', 'CHILD':'Trẻ em', 'VIP':'Khách VIP' };

    list.forEach(function(t) {
        let showtimeStr = '--';
        if (t.showtime) {
            const dateStr = formatDate ? formatDate(t.showtime.showDate) : (t.showtime.showDate || '');
            const tStr    = formatTime ? formatTime(t.showtime.showTime)  : (t.showtime.showTime  || '');
            showtimeStr = dateStr + ' ' + tStr;
        }
        const statusStyle = statusColors[t.status] || 'background:#e2e8f0;color:#475569;';
        const movieTitle  = (t.showtime && t.showtime.movie) ? t.showtime.movie.title : '--';
        const roomName    = t.showtime ? (t.showtime.room || '--') : '--';

        const tr = document.createElement('tr');
        tr.innerHTML = '<td style="text-align: center;"><input type="checkbox" class="ticket-select-checkbox" value="' + t.id + '" onchange="updateBatchDeleteButtonState()" style="cursor: pointer;"></td>'
            + '<td><strong>TK-' + String(t.id).padStart(5,'0') + '</strong></td>'
            + '<td style="max-width:200px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;" title="' + esc(movieTitle) + '"><strong>' + esc(movieTitle) + '</strong></td>'
            + '<td>' + esc(roomName) + '</td>'
            + '<td>' + showtimeStr + '</td>'
            + '<td>'
            + '  <div style="display:inline-flex; align-items:center; gap:6px;">'
            + '    <span class="badge-seat">' + esc(t.seatNumber || '') + '</span>'
            + '    <i class="fa-solid fa-map-location-dot" style="cursor:pointer; color:var(--primary-color); font-size:1rem;" onclick="viewSeatMapForTicket(' + t.id + ')" title="Xem vị trí ghế trên sơ đồ"></i>'
            + '  </div>'
            + '</td>'
            + '<td><span class="badge-daytype badge-upcoming">' + (customerLabels[t.customerType] || t.customerType || '--') + '</span></td>'
            + '<td><strong style="color:var(--primary-color);">' + formatVND(t.finalPrice || 0) + 'đ</strong></td>'
            + '<td><span style="padding:0.2rem 0.5rem;border-radius:4px;font-weight:700;font-size:0.78rem;' + statusStyle + '">' + (t.status || '') + '</span></td>'
            + '<td class="text-center">'
            + '  <div class="action-cell" style="display:flex; gap:0.5rem; justify-content:center;">'
            + '    <button class="action-btn action-btn-edit" onclick="openEditTicketModal(' + t.id + ')" title="Sửa thông tin vé"><i class="fa-solid fa-pen-to-square"></i> Sửa</button>'
            + '    <button class="action-btn action-btn-delete" onclick="handleDeleteTicket(' + t.id + ')" title="Xóa vé" style="background:#ef4444; border-color:#ef4444;"><i class="fa-solid fa-trash-can"></i> Xóa</button>'
            + '  </div>'
            + '</td>';
        tbody.appendChild(tr);
    });
}

function toggleSelectAllTickets(isChecked) {
    const checkboxes = document.querySelectorAll('.ticket-select-checkbox');
    checkboxes.forEach(cb => {
        cb.checked = isChecked;
    });
    updateBatchDeleteButtonState();
}

function updateBatchDeleteButtonState() {
    const checkboxes = document.querySelectorAll('.ticket-select-checkbox');
    const checkedCount = Array.from(checkboxes).filter(cb => cb.checked).length;
    const btn = document.getElementById('btnBatchDelete');
    if (btn) {
        btn.style.display = checkedCount > 0 ? 'inline-block' : 'none';
        btn.innerHTML = `<i class="fa-solid fa-trash-can"></i> Hủy ${checkedCount} vé đã chọn`;
    }

    const selectAllBox = document.getElementById('selectAllTickets');
    if (selectAllBox && checkboxes.length > 0) {
        selectAllBox.checked = (checkedCount === checkboxes.length);
    }
}

function handleBatchDeleteTickets() {
    const checkedBoxes = Array.from(document.querySelectorAll('.ticket-select-checkbox:checked'));
    const ids = checkedBoxes.map(cb => parseInt(cb.value));
    if (ids.length === 0) return;

    showConfirm(
        'Hủy vé hàng loạt',
        `Bạn có chắc chắn muốn hủy/hoàn trả ${ids.length} vé đang chọn? Ghế của các vé này sẽ được giải phóng lập tức.`,
        async function() {
            try {
                const promises = ids.map(id =>
                    fetch('/api/tickets/' + id, { method: 'DELETE' })
                        .then(async r => {
                            if (!r.ok) {
                                const data = await r.json().catch(() => ({}));
                                throw new Error(data.error || `Lỗi hủy vé TK-${String(id).padStart(5,'0')}`);
                            }
                        })
                );
                await Promise.all(promises);
                showToast('success', 'Thành công', `Đã hủy thành công ${ids.length} vé.`);
                searchGlobalTickets(globalCurrentPage);
                updateBatchDeleteButtonState();
            } catch (e) {
                showToast('error', 'Lỗi', e.message);
            }
        }
    );
}

function initQueryTabFilters() {
    populateGlobalMoviesAndRooms();
}

async function openCreateTicketModal() {
    document.getElementById('ticketCrudModalTitle').textContent = 'Thêm vé mới';
    document.getElementById('ticketCrudId').value = '';
    document.getElementById('ticketCrudCustomerType').value = 'ADULT';
    document.getElementById('ticketCrudStatus').value = 'BOOKED';
    document.getElementById('ticketCrudStatus').disabled = false;

    const movieSelect = document.getElementById('ticketCrudMovie');
    movieSelect.innerHTML = '<option value="">-- Chọn phim --</option>';

    const showtimeSelect = document.getElementById('ticketCrudShowtime');
    showtimeSelect.innerHTML = '<option value="">-- Chọn phim trước --</option>';
    showtimeSelect.disabled = true;

    const seatSelect = document.getElementById('ticketCrudSeat');
    seatSelect.innerHTML = '<option value="">-- Chọn suất chiếu trước --</option>';
    seatSelect.disabled = true;

    document.getElementById('crudPriceBreakdownBox').style.display = 'none';

    try {
        const res = await fetch(API_MOVIES);
        const movies = await res.json();
        movies.forEach(mv => {
            const opt = document.createElement('option');
            opt.value = mv.id;
            opt.textContent = mv.title;
            movieSelect.appendChild(opt);
        });

        document.getElementById('ticketCrudModal').classList.add('show');
        document.getElementById('ticketCrudModal').style.display = 'flex';
    } catch (e) {
        showToast('error', 'Lỗi', 'Không thể tải danh sách phim.');
    }
}

async function openEditTicketModal(ticketId) {
    try {
        const res = await fetch(`/api/tickets/search?searchTerm=${ticketId}`);
        const data = await res.json();
        if (data.length === 0) {
            showToast('error', 'Lỗi', 'Không tìm thấy vé.');
            return;
        }

        const ticket = data.find(t => t.id === ticketId);
        if (!ticket) {
            showToast('error', 'Lỗi', 'Không tìm thấy vé.');
            return;
        }

        document.getElementById('ticketCrudModalTitle').textContent = 'Sửa thông tin vé';
        document.getElementById('ticketCrudId').value = ticket.id;
        document.getElementById('ticketCrudCustomerType').value = ticket.customerType || 'ADULT';
        document.getElementById('ticketCrudStatus').value = ticket.status || 'BOOKED';

        document.getElementById('ticketCrudStatus').disabled = (ticket.status === 'REFUNDED');

        // Nạp danh sách phim
        const movieSelect = document.getElementById('ticketCrudMovie');
        movieSelect.innerHTML = '';
        const moviesRes = await fetch(API_MOVIES);
        const movies = await moviesRes.json();
        movies.forEach(mv => {
            const opt = document.createElement('option');
            opt.value = mv.id;
            opt.textContent = mv.title;
            movieSelect.appendChild(opt);
        });

        const selectedMovieId = ticket.showtime?.movie?.id;
        movieSelect.value = selectedMovieId || '';

        // Nạp danh sách suất chiếu
        const showtimeSelect = document.getElementById('ticketCrudShowtime');
        showtimeSelect.innerHTML = '';
        if (selectedMovieId) {
            const stRes = await fetch(API_SHOWTIMES + '?movieId=' + selectedMovieId);
            const showtimes = await stRes.json();
            showtimes.forEach(st => {
                const opt = document.createElement('option');
                opt.value = st.id;
                const dateStr = formatDate(st.showDate);
                const timeStr = formatTime(st.showTime);
                opt.textContent = `${dateStr} ${timeStr} | ${st.room || '?'}`;
                showtimeSelect.appendChild(opt);
            });
            showtimeSelect.value = ticket.showtime?.id || '';
            showtimeSelect.disabled = false;
        } else {
            showtimeSelect.innerHTML = '<option value="">-- Chọn phim trước --</option>';
            showtimeSelect.disabled = true;
        }

        // Nạp danh sách ghế
        if (ticket.showtime?.id) {
            await handleCrudShowtimeChange(ticket.seat?.id);
        } else {
            const seatSelect = document.getElementById('ticketCrudSeat');
            seatSelect.innerHTML = '<option value="">-- Chọn suất chiếu trước --</option>';
            seatSelect.disabled = true;
        }

        document.getElementById('ticketCrudModal').classList.add('show');
        document.getElementById('ticketCrudModal').style.display = 'flex';
    } catch (e) {
        showToast('error', 'Lỗi', 'Không thể tải thông tin vé để sửa.');
    }
}

function closeTicketCrudModal() {
    document.getElementById('ticketCrudModal').classList.remove('show');
    document.getElementById('ticketCrudModal').style.display = 'none';
    document.getElementById('ticketCrudForm').reset();
}

async function handleCrudMovieChange() {
    const movieId = document.getElementById('ticketCrudMovie').value;
    const showtimeSelect = document.getElementById('ticketCrudShowtime');
    const seatSelect = document.getElementById('ticketCrudSeat');

    showtimeSelect.innerHTML = '<option value="">-- Chọn suất chiếu --</option>';
    seatSelect.innerHTML = '<option value="">-- Chọn suất chiếu trước --</option>';
    showtimeSelect.disabled = true;
    seatSelect.disabled = true;
    document.getElementById('crudPriceBreakdownBox').style.display = 'none';

    if (!movieId) return;

    try {
        const r = await fetch(API_SHOWTIMES + '?movieId=' + movieId);
        const list = await r.json();
        showtimeSelect.innerHTML = '<option value="">-- Chọn suất chiếu --</option>';

        const now = new Date();
        const todayStr = now.toLocaleDateString('sv'); // format: YYYY-MM-DD
        const nowTimeStr = now.toTimeString().substring(0, 5); // format: HH:MM

        list.forEach(st => {
            let showTimeStr = '';
            if (st.showTime) {
                if (typeof st.showTime === 'string') {
                    showTimeStr = st.showTime.substring(0, 5);
                } else if (Array.isArray(st.showTime)) {
                    showTimeStr = String(st.showTime[0]).padStart(2, '0') + ':' + String(st.showTime[1]).padStart(2, '0');
                }
            }
            const isExpired = st.showDate < todayStr || (st.showDate === todayStr && showTimeStr < nowTimeStr);
            if (isExpired) return;

            const opt = document.createElement('option');
            opt.value = st.id;
            const dateStr = formatDate(st.showDate);
            const timeStr = formatTime(st.showTime);
            opt.textContent = `${dateStr} ${timeStr} | ${st.room || '?'}`;
            showtimeSelect.appendChild(opt);
        });
        showtimeSelect.disabled = false;
    } catch (e) {
        showToast('error', 'Lỗi', 'Không thể nạp danh sách suất chiếu.');
    }
}

async function handleCrudShowtimeChange(selectedSeatIdToPreselect) {
    const showtimeId = document.getElementById('ticketCrudShowtime').value;
    const seatSelect = document.getElementById('ticketCrudSeat');

    seatSelect.innerHTML = '<option value="">-- Chọn vị trí ghế --</option>';
    seatSelect.disabled = true;
    document.getElementById('crudPriceBreakdownBox').style.display = 'none';

    if (!showtimeId) return;

    try {
        const seatsRes = await fetch(API_TICKETS + '/seats?showtimeId=' + showtimeId);
        const seats = await seatsRes.json();

        const ticketsRes = await fetch(API_TICKETS + '/showtime/' + showtimeId);
        const tickets = await ticketsRes.json();

        seatSelect.innerHTML = '<option value="">-- Chọn vị trí ghế --</option>';
        seats.forEach(seat => {
            const isTaken = tickets.some(t => t.seat && t.seat.id === seat.id && t.status !== 'REFUNDED');
            const isCurrent = selectedSeatIdToPreselect && parseInt(selectedSeatIdToPreselect) === seat.id;

            if (!isTaken || isCurrent) {
                const opt = document.createElement('option');
                opt.value = seat.id;

                let seatTypeLabel = 'Thường';
                if (seat.seatType === 'vip') seatTypeLabel = 'VIP';
                else if (seat.seatType === 'couple') seatTypeLabel = 'Đôi';

                opt.textContent = `${seat.seatLabel} (${seatTypeLabel})`;
                seatSelect.appendChild(opt);
            }
        });

        seatSelect.disabled = false;

        if (selectedSeatIdToPreselect) {
            seatSelect.value = selectedSeatIdToPreselect;
            handlePriceBreakdownTrigger();
        }
    } catch (e) {
        showToast('error', 'Lỗi', 'Không thể nạp sơ đồ ghế của suất chiếu.');
    }
}

async function handlePriceBreakdownTrigger() {
    const showtimeId = document.getElementById('ticketCrudShowtime').value;
    const seatId = document.getElementById('ticketCrudSeat').value;
    const customerType = document.getElementById('ticketCrudCustomerType').value;
    const box = document.getElementById('crudPriceBreakdownBox');

    if (!showtimeId || !seatId) {
        box.style.display = 'none';
        return;
    }

    try {
        const url = `/api/tickets/price-suggestion?showtimeId=${showtimeId}&seatId=${seatId}&customerType=${customerType}`;
        const res = await fetch(url);
        if (!res.ok) throw new Error();
        const bd = await res.json();

        document.getElementById('crudBasePrice').textContent = formatVND(bd.basePrice || 0) + 'đ';
        document.getElementById('crudSeatSurcharge').textContent = formatVND(bd.seatSurcharge || 0) + 'đ';
        document.getElementById('crudFormatSurcharge').textContent = formatVND(bd.formatSurcharge || 0) + 'đ';
        document.getElementById('crudDiscount').textContent = '-' + formatVND(bd.discountAmount || 0) + 'đ';
        document.getElementById('crudFinalPrice').textContent = formatVND(bd.finalPrice || 0) + 'đ';
        box.style.display = 'block';
    } catch (e) {
        box.style.display = 'none';
    }
}

async function handleSaveTicketCrud() {
    const id = document.getElementById('ticketCrudId').value;
    const showtimeId = document.getElementById('ticketCrudShowtime').value;
    const seatId = document.getElementById('ticketCrudSeat').value;
    const customerType = document.getElementById('ticketCrudCustomerType').value;
    const status = document.getElementById('ticketCrudStatus').value;

    if (!showtimeId || !seatId || !customerType || !status) {
        showToast('warning', 'Thiếu thông tin', 'Vui lòng điền đầy đủ các trường thông tin bắt buộc (*).');
        return;
    }

    const payload = {
        showtimeId: parseInt(showtimeId),
        seatId: parseInt(seatId),
        customerType: customerType,
        status: status
    };

    try {
        let url = '/api/tickets';
        let method = 'POST';
        if (id) {
            url = `/api/tickets/${id}`;
            method = 'PUT';
        }

        const res = await fetch(url, {
            method: method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        if (!res.ok) {
            const err = await res.json();
            throw new Error(err.error || 'Lỗi lưu vé.');
        }

        showToast('success', 'Thành công', id ? 'Đã cập nhật vé thành công!' : 'Đã tạo vé mới thành công!');
        closeTicketCrudModal();
        searchGlobalTickets(globalCurrentPage);
    } catch (e) {
        showToast('error', 'Lỗi', e.message || 'Không thể lưu vé.');
    }
}

async function handleDeleteTicket(ticketId) {
    const confirmed = await showConfirm(
        'Xác nhận hoàn trả vé',
        'Bạn có chắc chắn muốn xóa/hoàn trả vé này không? Dữ liệu vé sẽ vẫn được lưu trong cơ sở dữ liệu và chuyển trạng thái thành REFUNDED.',
        'Hủy vé'
    );
    if (!confirmed) return;

    try {
        const res = await fetch(`/api/tickets/${ticketId}`, {
            method: 'DELETE'
        });

        if (!res.ok) {
            const err = await res.json();
            throw new Error(err.error || 'Lỗi hủy vé.');
        }

        showToast('success', 'Thành công', 'Đã hủy/hoàn trả vé thành công.');
        searchGlobalTickets(globalCurrentPage);
    } catch (e) {
        showToast('error', 'Lỗi', e.message || 'Không thể hủy vé.');
    }
}

function renderGlobalPagination(totalCount, pageSize) {
    const container = document.getElementById('globalTicketPaginationControls');
    container.innerHTML = '';
    const info = document.getElementById('globalTicketPaginationInfo');
    if (info) info.textContent = 'Hiển thị trang ' + globalCurrentPage + ' / ' + globalTotalPages + ' (Tổng ' + totalCount + ' vé)';
    if (globalTotalPages <= 1) return;

    const prevBtn = document.createElement('button');
    prevBtn.className = 'pagination-btn'; prevBtn.innerHTML = '&laquo;'; prevBtn.disabled = (globalCurrentPage === 1);
    prevBtn.onclick = function() { searchGlobalTickets(globalCurrentPage - 1); };
    container.appendChild(prevBtn);

    for (let i = 1; i <= globalTotalPages; i++) {
        const btn = document.createElement('button');
        btn.className = 'pagination-btn' + (i === globalCurrentPage ? ' active' : '');
        btn.textContent = i;
        (function(pg) { btn.onclick = function() { searchGlobalTickets(pg); }; })(i);
        container.appendChild(btn);
    }

    const nextBtn = document.createElement('button');
    nextBtn.className = 'pagination-btn'; nextBtn.innerHTML = '&raquo;'; nextBtn.disabled = (globalCurrentPage === globalTotalPages);
    nextBtn.onclick = function() { searchGlobalTickets(globalCurrentPage + 1); };
    container.appendChild(nextBtn);
}


// =========================================================================
//   SUB-TAB 3: CHUYỂN SUB-TAB & CẤU HÌNH MA TRẬN GIÁ VÉ
// =========================================================================

function switchTicketSubTab(tab) {
    populateGlobalMoviesAndRooms();
}

async function viewSeatMapForTicket(ticketId) {
    try {
        const res = await fetch(`/api/tickets/search?searchTerm=${ticketId}`);
        const data = await res.json();
        const ticket = data.find(t => t.id === ticketId);
        if (!ticket) {
            showToast('error', 'Lỗi', 'Không tìm thấy thông tin vé.');
            return;
        }

        const showtime = ticket.showtime;
        if (!showtime) {
            showToast('error', 'Lỗi', 'Không tìm thấy thông tin suất chiếu.');
            return;
        }

        document.getElementById('vsmMovie').textContent = showtime.movie?.title || 'Phim đã xóa';
        const dateStr = formatDate(showtime.showDate);
        const timeStr = formatTime(showtime.showTime);
        document.getElementById('vsmShowtime').textContent = `${dateStr} ${timeStr}`;
        document.getElementById('vsmRoom').textContent = showtime.room || '--';
        document.getElementById('vsmTargetSeat').textContent = ticket.seatNumber || '--';

        let targetTypeLabel = 'Thường';
        if (ticket.seat?.seatType === 'vip') targetTypeLabel = 'VIP';
        else if (ticket.seat?.seatType === 'couple') targetTypeLabel = 'Đôi';
        document.getElementById('vsmTargetSeatType').textContent = targetTypeLabel;

        // Định dạng phòng chiếu
        document.getElementById('vsmRoomType').textContent = showtime.movie?.format || '2D';

        // Lấy danh sách ghế và các vé của suất chiếu
        const seatsRes = await fetch(API_TICKETS + '/seats?showtimeId=' + showtime.id);
        const seats = await seatsRes.json();
        const ticketsRes = await fetch(API_TICKETS + '/showtime/' + showtime.id);
        const tickets = await ticketsRes.json();

        // Vẽ sơ đồ ghế
        const grid = document.getElementById('vsmSeatGrid');
        grid.innerHTML = '';

        const rowMap = {};
        seats.forEach(s => {
            const row = s.seatLabel.charAt(0);
            if (!rowMap[row]) rowMap[row] = [];
            rowMap[row].push(s);
        });

        Object.keys(rowMap).sort().forEach(row => {
            const rowSeats = rowMap[row].sort((a, b) => parseInt(a.seatLabel.slice(1)) - parseInt(b.seatLabel.slice(1)));

            const rowDiv = document.createElement('div');
            rowDiv.className = 'seat-row';
            rowDiv.style.cssText = 'display:flex;gap:8px;margin-bottom:8px;align-items:center;justify-content:center;';

            const lbl = document.createElement('span');
            lbl.textContent = row;
            lbl.style.cssText = 'font-weight:700;width:24px;color:var(--text-muted);text-align:center;';
            rowDiv.appendChild(lbl);

            rowSeats.forEach(seat => {
                const isTarget = ticket.seat && seat.id === ticket.seat.id;
                const seatTicket = tickets.find(t => t.seat && t.seat.id === seat.id && t.status !== 'REFUNDED');

                const seatDiv = document.createElement('div');
                seatDiv.className = 'seat-cell';
                seatDiv.textContent = seat.seatLabel;

                let baseStyle = 'width:32px;height:32px;border-radius:6px;font-size:0.75rem;font-weight:700;display:inline-flex;align-items:center;justify-content:center;user-select:none;color:#fff;';

                if (isTarget) {
                    baseStyle += 'background:var(--primary-color);box-shadow:0 0 10px var(--primary-color);border:2px solid #fff;transform:scale(1.1);animation:pulse-seat 1.5s infinite;';
                } else if (seatTicket) {
                    baseStyle += 'background:#ef4444;opacity:0.9;cursor:not-allowed;';
                } else {
                    if (seat.seatType === 'vip') {
                        baseStyle += 'background:#8b5cf6;opacity:0.4;';
                    } else if (seat.seatType === 'couple') {
                        baseStyle += 'background:#ec4899;opacity:0.4;';
                    } else {
                        baseStyle += 'background:#475569;opacity:0.4;';
                    }
                }

                seatDiv.style.cssText = baseStyle;
                rowDiv.appendChild(seatDiv);
            });

            grid.appendChild(rowDiv);
        });

        document.getElementById('viewSeatMapModal').classList.add('show');
        document.getElementById('viewSeatMapModal').style.display = 'flex';
    } catch (e) {
        showToast('error', 'Lỗi', 'Không thể tải sơ đồ ghế của vé này.');
    }
}

function closeViewSeatMapModal() {
    document.getElementById('viewSeatMapModal').classList.remove('show');
    document.getElementById('viewSeatMapModal').style.display = 'none';
}

// =========================================================================
//   TABS & SƠ ĐỒ BÁN VÉ & CẤU HÌNH GIÁ VÉ TẬP TRUNG
// =========================================================================

let currentActiveMainTab = 'sell';
let selectedMapMovieId = null;
let localPricingInfo = {
    basePrice: 60000,
    formatSurcharge: 0,
    vipSurcharge: 15000,
    coupleSurcharge: 30000
};

// 1. Chuyển đổi Tab chính
function switchMainTicketTab(tab) {
    currentActiveMainTab = tab;
    ['Sell', 'Pricing'].forEach(t => {
        const btn = document.getElementById('tab' + t + 'Btn');
        if (btn) {
            if (t.toLowerCase() === tab) {
                btn.classList.add('active');
                btn.style.color = 'var(--primary-color)';
                btn.style.borderBottom = '3px solid var(--primary-color)';
            } else {
                btn.classList.remove('active');
                btn.style.color = 'var(--text-muted)';
                btn.style.borderBottom = 'none';
            }
        }
    });

    ['sell', 'pricing'].forEach(t => {
        const div = document.getElementById(t + 'TabContent');
        if (div) div.style.display = (t === tab) ? 'block' : 'none';
    });

    if (tab === 'sell') {
        populateMapMovies();
    } else if (tab === 'pricing') {
        loadPricingDashboard();
    }
}

// 2. Tab 1: Sơ đồ giá ghế (Chế độ Xem & Kiểm tra giá)
async function populateMapMovies() {
    try {
        const r = await fetch(API_MOVIES);
        const list = await r.json();
        _cachedMovieList = list; // Cache lại danh sách phim

        // Fetch showtimes to filter movies that have showtimes
        const rShowtimes = await fetch(API_SHOWTIMES);
        const showtimes = await rShowtimes.json();
        const activeMovieIds = new Set(showtimes.map(s => s.movie ? s.movie.id : null).filter(id => id != null));
        const listWithShowtimes = list.filter(m => activeMovieIds.has(m.id));

        const sel = document.getElementById('mapMovieSelect');
        if (!sel) return;
        sel.innerHTML = '<option value="">-- Chọn phim --</option>';
        listWithShowtimes.forEach(m => {
            const opt = document.createElement('option');
            opt.value = m.id;
            opt.textContent = m.title;
            sel.appendChild(opt);
        });
        document.getElementById('mapShowtimeSelect').innerHTML = '<option value="">-- Chọn phim trước --</option>';
        document.getElementById('mapShowtimeSelect').disabled = true;

        // Clear visual seat map display state
        document.getElementById('mapSeatGridArea').style.display = 'none';
        document.getElementById('mapSeatGridEmpty').style.display = 'flex';

        resetMapBookingPanel();
    } catch (e) {
        console.error('Lỗi nạp phim cho sơ đồ:', e);
    }
}

async function handleMapMovieChange() {
    const movieId = document.getElementById('mapMovieSelect').value;
    selectedMapMovieId = movieId ? parseInt(movieId) : null;
    const sel = document.getElementById('mapShowtimeSelect');
    sel.innerHTML = '<option value="">-- Chọn suất chiếu --</option>';
    sel.disabled = true;
    resetMapBookingPanel();
    document.getElementById('mapSeatGridArea').style.display = 'none';
    document.getElementById('mapSeatGridEmpty').style.display = 'flex';

    if (!movieId) return;

    try {
        const r = await fetch(API_SHOWTIMES + '?movieId=' + movieId);
        const list = await r.json();

        const now = new Date();
        const todayStr = now.toLocaleDateString('sv');
        const nowTimeStr = now.toTimeString().substring(0, 5);

        list.forEach(st => {
            let showTimeStr = '';
            if (st.showTime) {
                if (typeof st.showTime === 'string') {
                    showTimeStr = st.showTime.substring(0, 5);
                } else if (Array.isArray(st.showTime)) {
                    showTimeStr = String(st.showTime[0]).padStart(2, '0') + ':' + String(st.showTime[1]).padStart(2, '0');
                }
            }
            const isExpired = st.showDate < todayStr || (st.showDate === todayStr && showTimeStr < nowTimeStr);
            if (isExpired) return;

            const opt = document.createElement('option');
            opt.value = st.id;
            opt.textContent = `${formatDate(st.showDate)} ${formatTime(st.showTime)} | ${st.room || '?'}`;
            sel.appendChild(opt);
        });
        sel.disabled = false;
    } catch (e) {
        showToast('error', 'Lỗi', 'Không thể tải suất chiếu.');
    }
}

// Phân cấp so khớp Giá cơ bản (Hierarchical Matching) ở phía Client
function calculateBasePriceForShowtime(showtime) {
    if (!_cachedPricingConfigs || !_cachedPricingConfigs.basePrices) {
        return 60000;
    }
    const dateStr = showtime.showDate || ''; // YYYY-MM-DD
    const dayType = showtime.dayType || 'Trong tuần';
    const slotName = jsDetermineTimeSlot(showtime.showTime);
    const movieId = selectedMapMovieId;

    // Ưu tiên 1: Giá riêng cho Phim + Ngày cụ thể (Ví dụ ngày lễ đặc biệt cho phim hot)
    if (movieId) {
        const p1 = _cachedPricingConfigs.basePrices.find(bp => bp.movieId === movieId && bp.dayType === dateStr && bp.slotName === slotName);
        if (p1) return p1.basePrice;
    }

    // Ưu tiên 2: Giá riêng cho Phim + Loại ngày (Trong tuần / Cuối tuần / Ngày lễ)
    if (movieId) {
        const p2 = _cachedPricingConfigs.basePrices.find(bp => bp.movieId === movieId && bp.dayType.toLowerCase() === dayType.toLowerCase() && bp.slotName === slotName);
        if (p2) return p2.basePrice;
    }

    // Ưu tiên 3: Giá chung + Ngày cụ thể (Ví dụ ngày lễ đặc biệt áp dụng cho mọi phim)
    const p3 = _cachedPricingConfigs.basePrices.find(bp => !bp.movieId && bp.dayType === dateStr && bp.slotName === slotName);
    if (p3) return p3.basePrice;

    // Ưu tiên 4: Giá chung + Loại ngày (Trong tuần / Cuối tuần / Ngày lễ mặc định)
    const p4 = _cachedPricingConfigs.basePrices.find(bp => !bp.movieId && bp.dayType.toLowerCase() === dayType.toLowerCase() && bp.slotName === slotName);
    if (p4) return p4.basePrice;

    // Dự phòng mặc định
    if (dayType === 'Trong tuần') {
        if (slotName === 'Suất sớm') return 50000;
        if (slotName === 'Giờ thường') return 60000;
        if (slotName === 'Giờ vàng') return 75000;
        return 65000;
    } else if (dayType === 'Cuối tuần') {
        if (slotName === 'Suất sớm') return 65000;
        if (slotName === 'Giờ thường') return 80000;
        if (slotName === 'Giờ vàng') return 95000;
        return 85000;
    } else {
        if (slotName === 'Suất sớm') return 80000;
        if (slotName === 'Giờ thường') return 95000;
        if (slotName === 'Giờ vàng') return 110000;
        return 100000;
    }
}

async function handleMapShowtimeChange() {
    const showtimeId = document.getElementById('mapShowtimeSelect').value;
    resetMapBookingPanel();
    document.getElementById('mapSeatGridArea').style.display = 'none';
    document.getElementById('mapSeatGridEmpty').style.display = 'flex';

    if (!showtimeId) return;

    try {
        const showtimeRes = await fetch(`${API_SHOWTIMES}/${showtimeId}`);
        selectedMapShowtime = await showtimeRes.json();

        // 1. Tải cấu hình giá vé để tính toán giá cục bộ
        const configRes = await fetch('/api/tickets/configs');
        const configs = await configRes.json();
        _cachedPricingConfigs = configs;

        // Tính giá cơ bản sử dụng thuật toán phân cấp
        localPricingInfo.basePrice = calculateBasePriceForShowtime(selectedMapShowtime);

        // Tính phụ thu phòng
        const roomOpt = _cachedRoomList ? _cachedRoomList.find(r => (r.roomName || '').toLowerCase() === (selectedMapShowtime.room || '').toLowerCase()) : null;
        const formatCode = roomOpt ? roomOpt.roomType : '2D';
        const formatConfig = configs.formatSurcharges.find(fs => (fs.formatCode || '').toUpperCase() === (formatCode || '').toUpperCase());
        localPricingInfo.formatSurcharge = formatConfig ? formatConfig.surchargeAmount : 0;

        // Tính phụ thu ghế
        const vipConfig = configs.seatSurcharges.find(ss => ss.seatTypeCode === 'vip');
        localPricingInfo.vipSurcharge = vipConfig ? vipConfig.surchargeAmount : 15000;
        const coupleConfig = configs.seatSurcharges.find(ss => ss.seatTypeCode === 'couple');
        localPricingInfo.coupleSurcharge = coupleConfig ? coupleConfig.surchargeAmount : 30000;

        // 2. Tải danh sách ghế và trạng thái ghế (đã bán + đang giữ chỗ qua cả 2 nguồn: Admin + Online booking)
        const seatsRes = await fetch(API_TICKETS + '/seats?showtimeId=' + showtimeId);
        const seats = await seatsRes.json();
        const seatStatusRes = await fetch(API_TICKETS + '/seat-status/' + showtimeId);
        const occupiedSeats = await seatStatusRes.json();

        renderMapSeatGrid(seats, occupiedSeats);
    } catch (e) {
        showToast('error', 'Lỗi', 'Không thể tải sơ đồ ghế.');
    }
}

function jsDetermineTimeSlot(timeVal) {
    if (!timeVal) return 'Giờ thường';
    let timeStr = '';
    if (typeof timeVal === 'string') {
        timeStr = timeVal.substring(0, 5);
    } else if (Array.isArray(timeVal)) {
        timeStr = String(timeVal[0]).padStart(2, '0') + ':' + String(timeVal[1]).padStart(2, '0');
    }
    const parts = timeStr.split(':');
    const hrs = parseInt(parts[0]);
    const mins = parseInt(parts[1]);
    const totalMins = hrs * 60 + mins;
    if (totalMins < 12 * 60) return 'Suất sớm';
    if (totalMins < 17 * 60) return 'Giờ thường';
    if (totalMins < 22 * 60) return 'Giờ vàng';
    return 'Suất khuya';
}

function renderMapSeatGrid(seats, soldTickets) {
    const grid = document.getElementById('mapSeatGridContainer');
    if (!grid) return;
    grid.innerHTML = '';

    const rowMap = {};
    seats.forEach(s => {
        const row = s.seatLabel.charAt(0);
        if (!rowMap[row]) rowMap[row] = [];
        rowMap[row].push(s);
    });

    Object.keys(rowMap).sort().forEach(row => {
        const rowSeats = rowMap[row].sort((a, b) => parseInt(a.seatLabel.slice(1)) - parseInt(b.seatLabel.slice(1)));

        const rowDiv = document.createElement('div');
        rowDiv.className = 'seat-row';
        rowDiv.style.cssText = 'display:flex; gap:10px; margin-bottom:8px; align-items:center; justify-content:center;';

        const lbl = document.createElement('span');
        lbl.textContent = row;
        lbl.style.cssText = 'font-weight:700; width:24px; color:var(--text-muted); text-align:center;';
        rowDiv.appendChild(lbl);

        rowSeats.forEach(seat => {
            // Ho tro ca 2 format: Ticket object cu (t.seat.id) va Map moi tu /seat-status/ (t.seatId)
            const ticket = soldTickets.find(t => {
                const tSeatId = t.seatId != null ? t.seatId : (t.seat ? t.seat.id : null);
                return tSeatId === seat.id && t.status !== 'REFUNDED';
            });
            const isVIP = seat.seatType === 'vip';
            const isCouple = seat.seatType === 'couple';

            let stateClass = 'seat-empty-standard';
            if (isVIP) stateClass = 'seat-empty-vip';
            else if (isCouple) stateClass = 'seat-empty-couple';

            if (ticket) {
                const s = ticket.status;
                if (s === 'BOOKED' || s === 'CONFIRMED') stateClass = 'seat-state-booked';
                else if (s === 'PENDING' || s === 'HOLDING') stateClass = 'seat-state-pending';
            }

            const btn = document.createElement('div');
            btn.className = 'seat-item ' + stateClass;
            btn.dataset.seatId = seat.id;
            btn.textContent = seat.seatLabel;

            // Tính giá ghế cục bộ để hiển thị tooltip nhanh
            let seatPrice = localPricingInfo.basePrice + localPricingInfo.formatSurcharge;
            if (isVIP) seatPrice += localPricingInfo.vipSurcharge;
            else if (isCouple) seatPrice += localPricingInfo.coupleSurcharge;

            let tooltip = `Ghế ${seat.seatLabel} - ${isVIP ? 'VIP' : (isCouple ? 'Đôi' : 'Thường')}`;
            if (ticket) {
                const isBookedStatus = ticket.status === 'BOOKED' || ticket.status === 'CONFIRMED' || ticket.status === 'PAID';
                tooltip += ` [${isBookedStatus ? 'Đã bán' : 'Đang giữ chỗ'}]`;
            } else {
                tooltip += ` - Giá vé: ${formatVND(seatPrice)}`;
            }
            btn.title = tooltip;

            btn.addEventListener('click', () => {
                document.querySelectorAll('#mapSeatGridContainer .seat-item').forEach(el => {
                    el.style.outline = 'none';
                    el.style.outlineOffset = '0px';
                });
                btn.style.outline = '3px solid var(--primary-color)';
                btn.style.outlineOffset = '2px';
                selectMapSeat(seat, ticket);
            });

            rowDiv.appendChild(btn);
        });

        grid.appendChild(rowDiv);
    });

    document.getElementById('mapSeatGridArea').style.display = 'flex';
    document.getElementById('mapSeatGridEmpty').style.display = 'none';
}

function calculateLocalPriceForType(subtotal, seatSurchargeVal, formatSurchargeVal, dayType, customerType) {
    if (customerType === 'ADULT') {
        return { finalPrice: subtotal, discountAmount: 0 };
    }
    if (!_cachedPricingConfigs || !_cachedPricingConfigs.customerDiscounts) {
        return { finalPrice: subtotal, discountAmount: 0 };
    }
    const discount = _cachedPricingConfigs.customerDiscounts.find(cd => cd.customerType === customerType);
    if (!discount) {
        return { finalPrice: subtotal, discountAmount: 0 };
    }

    // Kiểm tra ngưỡng tiền tối thiểu để áp dụng chiết khấu
    if (subtotal < discount.minPriceToApply) {
        return { finalPrice: subtotal, discountAmount: 0 };
    }

    let discountAmount = 0;
    if (dayType === 'Trong tuần' && discount.fixedPriceWeekday && discount.fixedPriceWeekday > 0) {
        const flatPrice = discount.fixedPriceWeekday + seatSurchargeVal + formatSurchargeVal;
        const potentialDiscount = subtotal - flatPrice;
        if (potentialDiscount > 0) {
            discountAmount = potentialDiscount;
        }
    } else {
        discountAmount = subtotal * discount.discountRate;
    }

    // Giới hạn giảm giá tối đa
    if (discountAmount > discount.maxDiscountAmount) {
        discountAmount = discount.maxDiscountAmount;
    }

    return { finalPrice: subtotal - discountAmount, discountAmount: discountAmount };
}

function selectMapSeat(seat, ticket) {
    selectedMapSeat = seat;
    document.getElementById('bookSeatLabel').textContent = seat.seatLabel;
    let typeLabel = 'Standard (Thường)';
    if (seat.seatType === 'vip') typeLabel = 'VIP (Vip)';
    else if (seat.seatType === 'couple') typeLabel = 'Sweetbox (Đôi)';
    document.getElementById('bookSeatType').textContent = typeLabel;

    document.getElementById('bookRoomLabel').textContent = selectedMapShowtime.room || '--';
    document.getElementById('bookShowtimeLabel').textContent = `${formatDate(selectedMapShowtime.showDate)} ${formatTime(selectedMapShowtime.showTime)}`;

    document.getElementById('bookingPanelActive').style.display = 'block';
    document.getElementById('bookingPanelEmpty').style.display = 'none';

    const bookedPanel = document.getElementById('bookedSeatInfoPanel');
    const priceBreakdown = document.getElementById('bookPriceBreakdown');

    if (ticket) {
        // Ghế đã bán hoặc đang giữ chỗ — hiển thị thông tin vé
        bookedPanel.style.display = 'block';
        priceBreakdown.style.display = 'none';

        const isBooked = ticket.status === 'BOOKED' || ticket.status === 'CONFIRMED' || ticket.status === 'PAID';
        const header = document.getElementById('bookedSeatInfoHeader');
        const icon = document.getElementById('bookedSeatInfoIcon');
        const title = document.getElementById('bookedSeatInfoTitle');

        if (isBooked) {
            header.style.cssText = 'padding: 0.6rem 1rem; font-weight: 700; font-size: 0.85rem; display: flex; align-items: center; gap: 0.5rem; background: #ef4444; color: #fff; border-radius: 10px 10px 0 0;';
            icon.className = 'fa-solid fa-ticket-alt';
            title.textContent = 'Ghế đã được bán';
        } else {
            header.style.cssText = 'padding: 0.6rem 1rem; font-weight: 700; font-size: 0.85rem; display: flex; align-items: center; gap: 0.5rem; background: #eab308; color: #fff; border-radius: 10px 10px 0 0;';
            icon.className = 'fa-solid fa-hourglass-half';
            title.textContent = 'Ghế đang được giữ chỗ';
        }

        document.getElementById('bookedTicketId').textContent = '#' + ticket.id;

        const customerTypeMap = { 'ADULT': 'Người lớn', 'CHILD': 'Trẻ em', 'STUDENT': 'Học sinh/SV', 'SENIOR': 'Người cao tuổi' };
        document.getElementById('bookedCustomerType').textContent = customerTypeMap[ticket.customerType] || ticket.customerType || '--';

        const statusEl = document.getElementById('bookedStatus');
        if (isBooked) {
            statusEl.textContent = `✅ Đã bán (${ticket.status || 'BOOKED'})`;
            statusEl.style.color = '#ef4444';
        } else {
            statusEl.textContent = `⏳ Đang giữ chỗ (${ticket.status || 'PENDING'})`;
            statusEl.style.color = '#ca8a04';
        }

        const priceVal = ticket.finalPrice || ticket.price;
        if (priceVal && priceVal > 0) {
            document.getElementById('bookedFinalPrice').textContent = formatVND(priceVal) + 'đ';
            document.getElementById('bookedPriceRow').style.display = 'block';
        } else {
            document.getElementById('bookedPriceRow').style.display = 'none';
        }

    } else {
        // Ghế trống — hiển thị cơ cấu giá ước tính
        bookedPanel.style.display = 'none';
        priceBreakdown.style.display = 'block';

        const isVIP = seat.seatType === 'vip';
        const isCouple = seat.seatType === 'couple';
        const seatSurchargeVal = isVIP ? localPricingInfo.vipSurcharge : (isCouple ? localPricingInfo.coupleSurcharge : 0);
        const formatSurchargeVal = localPricingInfo.formatSurcharge;
        const subtotal = localPricingInfo.basePrice + seatSurchargeVal + formatSurchargeVal;

        document.getElementById('bookBasePrice').textContent = formatVND(localPricingInfo.basePrice);
        document.getElementById('bookSeatSurcharge').textContent = formatVND(seatSurchargeVal);
        document.getElementById('bookFormatSurcharge').textContent = formatVND(formatSurchargeVal);
        document.getElementById('bookFinalPrice').textContent = formatVND(subtotal);
    }
}

function resetMapBookingPanel() {
    selectedMapSeat = null;
    document.getElementById('bookingPanelActive').style.display = 'none';
    document.getElementById('bookingPanelEmpty').style.display = 'flex';
}


// 3. Tab 2: Cấu hình bảng giá vé (Dashboard CRUD)
let _cachedShowtimes = [];
async function populatePriceMovieFilter() {
    const sel = document.getElementById('filterPriceMovie');
    if (!sel) return;

    if (!_cachedMovieList || _cachedMovieList.length === 0) {
        try {
            const r = await fetch(API_MOVIES);
            _cachedMovieList = await r.json();
        } catch (e) {
            console.error('Lỗi nạp phim cho bộ lọc cấu hình:', e);
        }
    }

    const currentVal = sel.value;
    sel.innerHTML = `
        <option value="">-- Tất cả phim --</option>
        <option value="all_movies">Áp dụng cho mọi phim</option>
    `;

    if (_cachedMovieList) {
        _cachedMovieList.forEach(m => {
            const opt = document.createElement('option');
            opt.value = m.id;
            opt.textContent = m.title;
            sel.appendChild(opt);
        });
    }

    sel.value = currentVal;
}

function populatePriceSlotFilter() {
    const sel = document.getElementById('filterPriceSlot');
    if (!sel) return;

    const currentVal = sel.value;
    sel.innerHTML = '<option value="">-- Tất cả khung giờ --</option>';

    if (_cachedPricingConfigs && _cachedPricingConfigs.basePrices) {
        const uniqueSlots = [...new Set(_cachedPricingConfigs.basePrices.map(c => c.slotName).filter(Boolean))];
        uniqueSlots.sort().forEach(s => {
            const opt = document.createElement('option');
            opt.value = s;
            opt.textContent = s;
            sel.appendChild(opt);
        });
    }

    if (Array.from(sel.options).some(opt => opt.value === currentVal)) {
        sel.value = currentVal;
    } else {
        sel.value = '';
    }
}

function filterBasePrices() {
    const movieVal = document.getElementById('filterPriceMovie')?.value || '';
    const slotVal = document.getElementById('filterPriceSlot')?.value || '';
    const dayVal = document.getElementById('filterPriceDayType')?.value.trim().toLowerCase() || '';
    const statusVal = document.getElementById('filterPriceStatus')?.value || '';

    if (!_cachedPricingConfigs || !_cachedPricingConfigs.basePrices) return;

    let filteredList = _cachedPricingConfigs.basePrices;

    if (movieVal) {
        if (movieVal === 'all_movies') {
            filteredList = filteredList.filter(c => !c.movieId);
        } else {
            filteredList = filteredList.filter(c => c.movieId === parseInt(movieVal));
        }
    }
    if (slotVal) {
        filteredList = filteredList.filter(c => c.slotName === slotVal);
    }
    if (dayVal) {
        filteredList = filteredList.filter(c => {
            const dayType = (c.dayType || '').toLowerCase();
            const note = (c.note || '').toLowerCase();
            return dayType.includes(dayVal) || note.includes(dayVal);
        });
    }
    if (statusVal) {
        const todayStr = new Date().toLocaleDateString('sv');
        const datePattern = /^\d{4}-\d{2}-\d{2}$/;
        filteredList = filteredList.filter(c => {
            const isExpired = datePattern.test(c.dayType) && c.dayType < todayStr;
            return statusVal === 'expired' ? isExpired : !isExpired;
        });
    }

    renderPricingBaseTable(filteredList);
}

function toggleSelectAllBaseConfigs(checked) {
    const checkboxes = document.querySelectorAll('.base-config-checkbox');
    checkboxes.forEach(cb => cb.checked = checked);
    updateBatchDeleteConfigsButtonState();
}

function updateBatchDeleteConfigsButtonState() {
    const checkboxes = document.querySelectorAll('.base-config-checkbox');
    const checkedBoxes = Array.from(checkboxes).filter(cb => cb.checked);
    const btnBatch = document.getElementById('btnBatchDeleteConfigs');
    if (btnBatch) {
        if (checkedBoxes.length > 0) {
            btnBatch.style.display = 'inline-block';
            btnBatch.innerHTML = `<i class="fa-solid fa-trash-can"></i> Xóa ${checkedBoxes.length} mục đã chọn`;
        } else {
            btnBatch.style.display = 'none';
        }
    }
}

async function handleBatchDeleteConfigs() {
    const checkboxes = document.querySelectorAll('.base-config-checkbox:checked');
    const ids = Array.from(checkboxes).map(cb => parseInt(cb.value));
    if (ids.length === 0) return;

    const confirmed = await showConfirm(
        'Xác nhận xóa hàng loạt',
        `Bạn có chắc chắn muốn xóa ${ids.length} cấu hình giá vé cơ bản đã chọn không?`,
        'Đồng ý xóa'
    );
    if (!confirmed) return;

    try {
        const responses = await Promise.all(ids.map(id =>
            fetch('/api/tickets/configs/base/' + id, { method: 'DELETE' })
        ));

        let okCount = 0;
        responses.forEach(r => {
            if (r.ok) okCount++;
        });

        if (okCount > 0) {
            showToast('success', 'Xóa thành công', `Đã xóa thành công ${okCount}/${ids.length} cấu hình giá vé cơ bản!`);
        } else {
            showToast('error', 'Lỗi', 'Không thể xóa các cấu hình đã chọn.');
        }
        await loadPricingDashboard();
    } catch (e) {
        console.error('Lỗi khi xóa nhiều cấu hình:', e);
        showToast('error', 'Lỗi', 'Có lỗi xảy ra khi xóa các cấu hình giá vé!');
    }
}

async function loadPricingDashboard() {
    try {
        const r = await fetch('/api/tickets/configs');
        const data = await r.json();
        _cachedPricingConfigs = data;

        await populatePriceMovieFilter();
        populatePriceSlotFilter();
        filterBasePrices();
        renderPricingSeatsTable(data.seatSurcharges);
        renderPricingFormatsTable(data.formatSurcharges);
        renderPricingDiscountsTable(data.customerDiscounts);
    } catch (e) {
        console.error('Lỗi nạp cấu hình giá:', e);
    }
}

function renderPricingBaseTable(list) {
    const tbody = document.getElementById('tblPricingBase');
    if (!tbody) return;
    tbody.innerHTML = '';

    // Check if any filters are active
    const movieVal = document.getElementById('filterPriceMovie')?.value || '';
    const slotVal = document.getElementById('filterPriceSlot')?.value || '';
    const dayVal = document.getElementById('filterPriceDayType')?.value.trim() || '';
    const isFiltered = (movieVal !== '' || slotVal !== '' || dayVal !== '');

    if (!list || list.length === 0) {
        if (isFiltered) {
            tbody.innerHTML = '<tr><td colspan="10" class="text-center" style="color:var(--stat-red); padding:2rem; font-weight:500;"><i class="fa-solid fa-circle-info"></i> Không tìm thấy cấu hình giá vé phù hợp với bộ lọc.</td></tr>';
        } else {
            tbody.innerHTML = '<tr><td colspan="10" class="text-center" style="color:var(--text-muted); padding:1.5rem;">Chưa có cấu hình giá cơ bản.</td></tr>';
        }
        const chkSelectAll = document.getElementById('selectAllBaseConfigs');
        if (chkSelectAll) chkSelectAll.checked = false;
        updateBatchDeleteConfigsButtonState();
        return;
    }

    const chkSelectAll = document.getElementById('selectAllBaseConfigs');
    if (chkSelectAll) chkSelectAll.checked = false;

    list.forEach(c => {
        let movieTitle = 'Tất cả phim';
        if (c.movieId && _cachedMovieList) {
            const mv = _cachedMovieList.find(m => m.id === c.movieId);
            if (mv) movieTitle = mv.title;
        }

        let dayTypeDisplay = esc(c.dayType);
        if (c.note) {
            dayTypeDisplay += ` <span style="color:var(--text-muted); font-size:0.8rem; font-weight:normal;">(${esc(c.note)})</span>`;
        }

        let statusDisplay = `<span class="badge-status badge-active" style="background:#22c55e; color:#fff; padding:2px 8px; border-radius:12px; font-size:0.75rem; font-weight:500;">Đang hoạt động</span>`;
        const datePattern = /^\d{4}-\d{2}-\d{2}$/;
        if (datePattern.test(c.dayType)) {
            const todayStr = new Date().toLocaleDateString('sv');
            if (c.dayType < todayStr) {
                statusDisplay = `<span class="badge-status badge-expired" style="background:#ef4444; color:#fff; padding:2px 8px; border-radius:12px; font-size:0.75rem; font-weight:500;">Hết hạn</span>`;
            }
        }

        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td style="text-align: center;"><input type="checkbox" class="base-config-checkbox" value="${c.id}" onchange="updateBatchDeleteConfigsButtonState()" style="cursor: pointer;"></td>
            <td>${c.id}</td>
            <td><strong style="color:var(--primary-color);">${esc(movieTitle)}</strong></td>
            <td><strong>${dayTypeDisplay}</strong></td>
            <td><span class="badge-daytype badge-weekday" style="background:var(--primary-color); padding:2px 8px; border-radius:12px; color:#fff; font-size:0.75rem;">${esc(c.slotName)}</span></td>
            <td>${c.startTime}</td>
            <td>${c.endTime}</td>
            <td><strong style="color:var(--primary-color);">${formatVND(c.basePrice)}</strong></td>
            <td>${statusDisplay}</td>
            <td class="text-center">
                <button class="btn btn-outline" style="padding:0.25rem 0.5rem; font-size:0.8rem; margin-right:4px;" onclick="openEditConfigModal('base', ${c.id})"><i class="fa-solid fa-pen"></i> Sửa</button>
                <button class="btn btn-outline" style="padding:0.25rem 0.5rem; font-size:0.8rem; color:var(--stat-red); border-color:transparent;" onclick="deletePricingConfig('base', ${c.id})"><i class="fa-solid fa-trash-can"></i> Xóa</button>
            </td>
        `;
        tbody.appendChild(tr);
    });

    updateBatchDeleteConfigsButtonState();
}

function renderPricingSeatsTable(list) {
    const tbody = document.getElementById('tblPricingSeats');
    if (!tbody) return;
    tbody.innerHTML = '';
    if (!list || list.length === 0) {
        tbody.innerHTML = '<tr><td colspan="3" class="text-center" style="color:var(--text-muted);">Chưa có phụ thu loại ghế.</td></tr>';
        return;
    }
    list.forEach(c => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td><strong>${esc(c.seatTypeCode.toUpperCase())}</strong></td>
            <td><strong style="color:var(--primary-color);">${formatVND(c.surchargeAmount)}</strong></td>
            <td class="text-center">
                <button class="btn btn-outline" style="padding:0.25rem 0.5rem; font-size:0.8rem; margin-right:4px;" onclick="openEditConfigModal('seats', ${c.id})"><i class="fa-solid fa-pen"></i> Sửa</button>
                <button class="btn btn-outline" style="padding:0.25rem 0.5rem; font-size:0.8rem; color:var(--stat-red); border-color:transparent;" onclick="deletePricingConfig('seats', ${c.id})"><i class="fa-solid fa-trash-can"></i> Xóa</button>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

function renderPricingFormatsTable(list) {
    const tbody = document.getElementById('tblPricingFormats');
    if (!tbody) return;
    tbody.innerHTML = '';
    if (!list || list.length === 0) {
        tbody.innerHTML = '<tr><td colspan="3" class="text-center" style="color:var(--text-muted);">Chưa có phụ thu định dạng phòng.</td></tr>';
        return;
    }
    list.forEach(c => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td><strong>${esc(c.formatCode.toUpperCase())}</strong></td>
            <td><strong style="color:var(--primary-color);">${formatVND(c.surchargeAmount)}</strong></td>
            <td class="text-center">
                <button class="btn btn-outline" style="padding:0.25rem 0.5rem; font-size:0.8rem; margin-right:4px;" onclick="openEditConfigModal('formats', ${c.id})"><i class="fa-solid fa-pen"></i> Sửa</button>
                <button class="btn btn-outline" style="padding:0.25rem 0.5rem; font-size:0.8rem; color:var(--stat-red); border-color:transparent;" onclick="deletePricingConfig('formats', ${c.id})"><i class="fa-solid fa-trash-can"></i> Xóa</button>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

function renderPricingDiscountsTable(list) {
    const tbody = document.getElementById('tblPricingDiscounts');
    if (!tbody) return;
    tbody.innerHTML = '';
    if (!list || list.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" class="text-center" style="color:var(--text-muted);">Chưa có chiết khấu đối tượng.</td></tr>';
        return;
    }
    list.forEach(c => {
        const tr = document.createElement('tr');
        const rateLabel = (c.discountRate * 100).toFixed(0) + '%';
        const fixedLabel = c.fixedPriceWeekday ? formatVND(c.fixedPriceWeekday) : '—';
        const minPriceLabel = formatVND(c.minPriceToApply || 0);
        const maxDiscLabel = c.maxDiscountAmount >= 999999 ? 'Không giới hạn' : formatVND(c.maxDiscountAmount);

        tr.innerHTML = `
            <td><strong>${esc(c.customerType)}</strong></td>
            <td><span style="color:#ef4444; font-weight:700;">-${rateLabel}</span></td>
            <td>${fixedLabel}</td>
            <td>${minPriceLabel}</td>
            <td><strong style="color:#ef4444;">${maxDiscLabel}</strong></td>
            <td class="text-center">
                <button class="btn btn-outline" style="padding:0.25rem 0.5rem; font-size:0.8rem; margin-right:4px;" onclick="openEditConfigModal('discounts', ${c.id})"><i class="fa-solid fa-pen"></i> Sửa</button>
                <button class="btn btn-outline" style="padding:0.25rem 0.5rem; font-size:0.8rem; color:var(--stat-red); border-color:transparent;" onclick="deletePricingConfig('discounts', ${c.id})"><i class="fa-solid fa-trash-can"></i> Xóa</button>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

async function openAddConfigModal(type) {
    document.getElementById('editConfigType').value = type;
    document.getElementById('editConfigId').value = '';
    document.getElementById('editConfigTitle').textContent = 'Thêm cấu hình giá mới';
    document.getElementById('editConfigForm').reset();

    // Cache showtimes for movie-showtime verification
    try {
        const r = await fetch(API_SHOWTIMES);
        _cachedShowtimes = await r.json();
    } catch (e) {
        console.error('Không thể tải danh sách suất chiếu:', e);
    }

    renderDynamicConfigFields(type, false);
    if (type === 'base') {
        const selectEl = document.getElementById('cfgDayTypeSelect');
        const customEl = document.getElementById('cfgDayType');
        if (selectEl && customEl) {
            customEl.value = selectEl.value;
            customEl.style.display = 'none';
        }
    }
    document.getElementById('editConfigModal').classList.add('show');
    document.getElementById('editConfigModal').style.display = 'flex';
}

function toggleCfgDayTypeInput() {
    const sel = document.getElementById('cfgDayTypeSelect');
    const inp = document.getElementById('cfgDayType');
    if (!sel || !inp) return;
    if (sel.value === 'custom') {
        inp.style.display = 'block';
        inp.value = '';
        inp.placeholder = 'Ví dụ: 2026-09-02';
        inp.required = true;
    } else {
        inp.style.display = 'none';
        inp.value = sel.value;
        inp.required = false;
    }
}

function handleCfgAllMoviesChange(checked) {
    const checkboxes = document.querySelectorAll('input[name="cfgMovieIds"]');
    checkboxes.forEach(cb => {
        cb.checked = false;
        cb.disabled = checked;
    });
    handleCfgMovieChange();
}

async function handleCfgMovieChange() {
    const isAllMoviesChecked = document.getElementById('cfgAllMoviesCheckbox') ? document.getElementById('cfgAllMoviesCheckbox').checked : false;
    const movieIdSelect = document.getElementById('cfgMovieId');
    
    // Determine selected movie IDs depending on edit vs creation mode
    let selectedMovieIds = [];
    if (movieIdSelect) {
        // Edit mode (single select dropdown)
        const val = movieIdSelect.value;
        if (val) selectedMovieIds = [parseInt(val)];
    } else {
        // Creation mode (multiple checkboxes)
        selectedMovieIds = Array.from(document.querySelectorAll('input[name="cfgMovieIds"]:checked')).map(el => parseInt(el.value));
    }

    const container = document.getElementById('cfgDayTypeWrapper');
    const slotWrapper = document.getElementById('cfgSlotWrapper');
    const noteWrapper = document.getElementById('cfgNoteWrapper');
    if (!container) return;

    if (selectedMovieIds.length > 0 && !isAllMoviesChecked) {
        // Show note input wrapper for movie-specific config
        if (noteWrapper) noteWrapper.style.display = 'block';

        // Hide slot selection since base prices apply to all showtimes of the movie on selected dates
        if (slotWrapper) slotWrapper.style.display = 'none';

        // Filter showtimes for all selected movies, keeping only future showtimes
        const now = new Date();
        const list = _cachedShowtimes.filter(st => {
            if (!selectedMovieIds.includes(st.movie && st.movie.id)) return false;
            let showTimeStr = '00:00:00';
            if (st.showTime) {
                if (typeof st.showTime === 'string') {
                    showTimeStr = st.showTime;
                } else if (Array.isArray(st.showTime)) {
                    showTimeStr = String(st.showTime[0]).padStart(2, '0') + ':' + String(st.showTime[1]).padStart(2, '0') + ':00';
                }
            }
            const showtimeStart = new Date(st.showDate + 'T' + showTimeStr);
            return showtimeStart >= now;
        });
        
        // Extract unique show dates
        const uniqueDates = [...new Set(list.map(st => st.showDate))].sort();

        if (uniqueDates.length === 0) {
            container.innerHTML = `
                <label style="font-weight:500; display:block; margin-bottom:0.5rem; color:var(--text-main);">Ngày chiếu áp dụng:</label>
                <div style="color:var(--text-muted); font-size:0.85rem; padding:0.8rem; border:1px dashed var(--border-color); border-radius:6px; background:var(--bg-app);">
                    Phim hiện chưa có lịch chiếu nào để chọn ngày.
                </div>
                <input type="hidden" id="cfgDayType" value="">
            `;
        } else {
            let checkboxes = '';
            uniqueDates.forEach((date, idx) => {
                const labelId = `lbl_date_${idx}`;
                checkboxes += `
                    <label class="date-chip-label" id="${labelId}">
                        <input type="checkbox" name="cfgDayTypeDates" value="${date}" onchange="toggleDateChipActive('${labelId}', this.checked)">
                        <i class="fa-regular fa-calendar date-chip-icon"></i>
                        <span class="date-chip-text">${formatDate(date)}</span>
                        <span class="date-chip-subtext">${date}</span>
                    </label>
                `;
            });
            container.innerHTML = `
                <label style="font-weight:500; display:block; margin-bottom:0.5rem; color:var(--text-main);">Chọn ngày chiếu áp dụng (chọn một hoặc nhiều):</label>
                <div class="date-chip-grid" style="max-height: 180px; overflow-y: auto; padding: 0.2rem;">
                    ${checkboxes}
                </div>
            `;
        }
    } else {
        // Hide note input wrapper when not specific to a movie
        if (noteWrapper) {
            noteWrapper.style.display = 'none';
            const noteInp = document.getElementById('cfgNote');
            if (noteInp) noteInp.value = '';
        }

        // Show slot selection for general configs
        if (slotWrapper) slotWrapper.style.display = 'block';

        // Restore default Day Type dropdown
        container.innerHTML = `
            <label style="font-weight:500; display:block; margin-bottom:0.5rem; color:var(--text-main);">Loại ngày / Ngày đặc biệt:</label>
            <select id="cfgDayTypeSelect" class="form-control" onchange="toggleCfgDayTypeInput()">
                <option value="Trong tuần">Trong tuần</option>
                <option value="Cuối tuần">Cuối tuần</option>
                <option value="Ngày lễ">Ngày lễ</option>
                <option value="custom">-- Nhập ngày cụ thể (YYYY-MM-DD) --</option>
            </select>
            <input type="text" id="cfgDayType" class="form-control" style="margin-top: 0.5rem; display: none;" placeholder="Trong tuần" required>
        `;
        const selectEl = document.getElementById('cfgDayTypeSelect');
        const customEl = document.getElementById('cfgDayType');
        if (selectEl && customEl) {
            customEl.value = selectEl.value;
        }
    }
}

function toggleSlotChipActive(labelId, isChecked) {
    const label = document.getElementById(labelId);
    if (!label) return;
    if (isChecked) {
        label.classList.add('active');
    } else {
        label.classList.remove('active');
    }
}

function toggleDateChipActive(labelId, isChecked) {
    const label = document.getElementById(labelId);
    if (!label) return;
    if (isChecked) {
        label.classList.add('active');
    } else {
        label.classList.remove('active');
    }
}

async function openEditConfigModal(type, id) {
    document.getElementById('editConfigType').value = type;
    document.getElementById('editConfigId').value = id;
    document.getElementById('editConfigTitle').textContent = 'Chỉnh sửa cấu hình giá';
    document.getElementById('editConfigForm').reset();

    // Cache showtimes
    try {
        const r = await fetch(API_SHOWTIMES);
        _cachedShowtimes = await r.json();
    } catch (e) {
        console.error('Không thể tải danh sách suất chiếu:', e);
    }

    renderDynamicConfigFields(type, true);

    try {
        const r = await fetch('/api/tickets/configs');
        const data = await r.json();

        if (type === 'base') {
            const c = data.basePrices.find(bp => bp.id === id);
            if (c) {
                document.getElementById('cfgMovieId').value = c.movieId || '';

                // Synchronously populate dates list
                await handleCfgMovieChange();

                if (c.movieId) {
                    const noteInp = document.getElementById('cfgNote');
                    if (noteInp) noteInp.value = c.note || '';

                    const cb = document.querySelector(`input[name="cfgDayTypeDates"][value="${c.dayType}"]`);
                    if (cb) {
                        cb.checked = true;
                        const label = cb.closest('.date-chip-label');
                        if (label) label.classList.add('active');
                    }
                } else {
                    const selectEl = document.getElementById('cfgDayTypeSelect');
                    const customEl = document.getElementById('cfgDayType');
                    if (['Trong tuần', 'Cuối tuần', 'Ngày lễ'].includes(c.dayType)) {
                        selectEl.value = c.dayType;
                        customEl.value = c.dayType;
                        customEl.style.display = 'none';
                    } else {
                        selectEl.value = 'custom';
                        customEl.value = c.dayType;
                        customEl.style.display = 'block';
                    }
                }

                // Check slot name
                const checkboxes = document.querySelectorAll('input[name="cfgSlotNames"]');
                checkboxes.forEach(cb => {
                    cb.checked = (cb.value === c.slotName);
                    const label = cb.closest('.slot-chip-label');
                    if (label) {
                        if (cb.checked) label.classList.add('active');
                        else label.classList.remove('active');
                    }
                });

                document.getElementById('editConfigValue').value = c.basePrice;
            }
        } else if (type === 'seats') {
            const c = data.seatSurcharges.find(ss => ss.id === id);
            if (c) {
                document.getElementById('cfgSeatTypeCode').value = c.seatTypeCode;
                document.getElementById('editConfigValue').value = c.surchargeAmount;
            }
        } else if (type === 'formats') {
            const c = data.formatSurcharges.find(fs => fs.id === id);
            if (c) {
                document.getElementById('cfgFormatCode').value = c.formatCode;
                document.getElementById('editConfigValue').value = c.surchargeAmount;
            }
        } else if (type === 'discounts') {
            const c = data.customerDiscounts.find(cd => cd.id === id);
            if (c) {
                document.getElementById('cfgCustomerType').value = c.customerType;
                document.getElementById('editConfigDiscountRate').value = c.discountRate;
                document.getElementById('editConfigFixedPriceWeekday').value = c.fixedPriceWeekday || '';
                document.getElementById('editConfigMinPriceToApply').value = c.minPriceToApply || 0;
                document.getElementById('editConfigMaxDiscountAmount').value = c.maxDiscountAmount || 999999;
                document.getElementById('editConfigValue').value = 0; // Không dùng
            }
        }

        document.getElementById('editConfigModal').classList.add('show');
        document.getElementById('editConfigModal').style.display = 'flex';
    } catch (e) {
        showToast('error', 'Lỗi', 'Không thể tải cấu hình chỉnh sửa.');
    }
}

function renderDynamicConfigFields(type, isEdit = false) {
    const container = document.getElementById('dynamicConfigFields');
    container.innerHTML = '';

    const groupPrice = document.getElementById('groupEditPrice');
    const groupRate = document.getElementById('groupEditDiscountRate');
    const groupFixed = document.getElementById('groupEditFixedPriceWeekday');
    const groupMinPrice = document.getElementById('groupEditMinPriceToApply');
    const groupMaxDisc = document.getElementById('groupEditMaxDiscountAmount');

    groupPrice.style.display = 'block';
    groupRate.style.display = 'none';
    groupFixed.style.display = 'none';
    groupMinPrice.style.display = 'none';
    groupMaxDisc.style.display = 'none';

    if (type === 'base') {
        if (isEdit) {
            let movieOptions = '<option value="">-- Áp dụng cho tất cả phim --</option>';
            if (_cachedMovieList) {
                _cachedMovieList.forEach(m => {
                    movieOptions += `<option value="${m.id}">${esc(m.title)}</option>`;
                });
            }

            container.innerHTML = `
                <div class="form-group" style="margin-bottom: 1rem;">
                    <label style="font-weight:500; display:block; margin-bottom:0.5rem; color:var(--text-main);">Áp dụng cho Phim:</label>
                    <select id="cfgMovieId" class="form-control" onchange="handleCfgMovieChange()">
                        ${movieOptions}
                    </select>
                </div>
                <div id="cfgNoteWrapper" class="form-group" style="margin-bottom: 1rem; display: none;">
                    <label style="font-weight:500; display:block; margin-bottom:0.5rem; color:var(--text-main);">Tên loại ngày (Ví dụ: ngày Tết, khởi chiếu...):</label>
                    <input type="text" id="cfgNote" class="form-control" placeholder="Ví dụ: Tết Nguyên Đán, Valentine..." maxlength="100">
                </div>
                <div id="cfgDayTypeWrapper" class="form-group" style="margin-bottom: 1rem;">
                    <label style="font-weight:500; display:block; margin-bottom:0.5rem; color:var(--text-main);">Loại ngày / Ngày đặc biệt:</label>
                    <select id="cfgDayTypeSelect" class="form-control" onchange="toggleCfgDayTypeInput()">
                        <option value="Trong tuần">Trong tuần</option>
                        <option value="Cuối tuần">Cuối tuần</option>
                        <option value="Ngày lễ">Ngày lễ</option>
                        <option value="custom">-- Nhập ngày cụ thể (YYYY-MM-DD) --</option>
                    </select>
                    <input type="text" id="cfgDayType" class="form-control" style="margin-top: 0.5rem; display: none;" placeholder="Trong tuần" required>
                </div>
                <div id="cfgSlotWrapper" class="form-group" style="margin-bottom: 1rem;">
                    <label style="font-weight:500; display:block; margin-bottom:0.5rem; color:var(--text-main);">Khung giờ áp dụng (chọn một hoặc nhiều):</label>
                    <div class="slot-chip-grid">
                        <label class="slot-chip-label" id="lbl_slot_suatsom">
                            <input type="checkbox" name="cfgSlotNames" value="Suất sớm" onchange="toggleSlotChipActive('lbl_slot_suatsom', this.checked)">
                            <span class="slot-chip-title">Suất sớm</span>
                            <span class="slot-chip-subtitle">00:00 - 11:59</span>
                        </label>
                        <label class="slot-chip-label" id="lbl_slot_giothuong">
                            <input type="checkbox" name="cfgSlotNames" value="Giờ thường" onchange="toggleSlotChipActive('lbl_slot_giothuong', this.checked)">
                            <span class="slot-chip-title">Giờ thường</span>
                            <span class="slot-chip-subtitle">12:00 - 16:59</span>
                        </label>
                        <label class="slot-chip-label" id="lbl_slot_giovang">
                            <input type="checkbox" name="cfgSlotNames" value="Giờ vàng" onchange="toggleSlotChipActive('lbl_slot_giovang', this.checked)">
                            <span class="slot-chip-title">Giờ vàng</span>
                            <span class="slot-chip-subtitle">17:00 - 21:59</span>
                        </label>
                        <label class="slot-chip-label" id="lbl_slot_suatkhuya">
                            <input type="checkbox" name="cfgSlotNames" value="Suất khuya" onchange="toggleSlotChipActive('lbl_slot_suatkhuya', this.checked)">
                            <span class="slot-chip-title">Suất khuya</span>
                            <span class="slot-chip-subtitle">22:00 - 23:59</span>
                        </label>
                    </div>
                </div>
            `;
        } else {
            let movieCheckboxes = '';
            const moviesWithShowtimes = [];
            const movieIdsSet = new Set();
            const now = new Date();
            _cachedShowtimes.forEach(st => {
                if (st.movie && !movieIdsSet.has(st.movie.id)) {
                    let showTimeStr = '00:00:00';
                    if (st.showTime) {
                        if (typeof st.showTime === 'string') {
                            showTimeStr = st.showTime;
                        } else if (Array.isArray(st.showTime)) {
                            showTimeStr = String(st.showTime[0]).padStart(2, '0') + ':' + String(st.showTime[1]).padStart(2, '0') + ':00';
                        }
                    }
                    const showtimeStart = new Date(st.showDate + 'T' + showTimeStr);
                    if (showtimeStart >= now) {
                        movieIdsSet.add(st.movie.id);
                        moviesWithShowtimes.push(st.movie);
                    }
                }
            });

            moviesWithShowtimes.forEach((m, idx) => {
                movieCheckboxes += `
                    <label style="display:flex; align-items:center; gap:0.5rem; margin-bottom:0.4rem; cursor:pointer; color:var(--text-main); font-size:0.88rem;">
                        <input type="checkbox" name="cfgMovieIds" value="${m.id}" onchange="handleCfgMovieChange()" style="cursor:pointer; width:16px; height:16px;">
                        <span>${esc(m.title)}</span>
                    </label>
                `;
            });

            container.innerHTML = `
                <div class="form-group" style="margin-bottom: 1rem;">
                    <label style="font-weight:500; display:block; margin-bottom:0.3rem; color:var(--text-main);">Áp dụng cho Phim (chọn một hoặc nhiều):</label>
                    <label style="display:flex; align-items:center; gap:0.5rem; margin-bottom:0.5rem; cursor:pointer; color:var(--text-main); font-weight:bold; font-size:0.9rem;">
                        <input type="checkbox" id="cfgAllMoviesCheckbox" onchange="handleCfgAllMoviesChange(this.checked)" style="cursor:pointer; width:18px; height:18px;">
                        <span>Áp dụng cho tất cả phim</span>
                    </label>
                    <div id="cfgMovieCheckboxesContainer" class="movie-checkbox-grid" style="max-height:160px; overflow-y:auto; padding:0.5rem; border:1px solid var(--border-color); border-radius:6px; background:var(--bg-app);">
                        ${movieCheckboxes || '<span style="color:var(--text-muted); font-size:0.85rem;">Không tìm thấy phim nào có suất chiếu.</span>'}
                    </div>
                </div>
                <div id="cfgNoteWrapper" class="form-group" style="margin-bottom: 1rem; display: none;">
                    <label style="font-weight:500; display:block; margin-bottom:0.5rem; color:var(--text-main);">Tên loại ngày (Ví dụ: ngày Tết, khởi chiếu...):</label>
                    <input type="text" id="cfgNote" class="form-control" placeholder="Ví dụ: Tết Nguyên Đán, Valentine..." maxlength="100">
                </div>
                <div id="cfgDayTypeWrapper" class="form-group" style="margin-bottom: 1rem;">
                    <label style="font-weight:500; display:block; margin-bottom:0.5rem; color:var(--text-main);">Loại ngày / Ngày đặc biệt:</label>
                    <select id="cfgDayTypeSelect" class="form-control" onchange="toggleCfgDayTypeInput()">
                        <option value="Trong tuần">Trong tuần</option>
                        <option value="Cuối tuần">Cuối tuần</option>
                        <option value="Ngày lễ">Ngày lễ</option>
                        <option value="custom">-- Nhập ngày cụ thể (YYYY-MM-DD) --</option>
                    </select>
                    <input type="text" id="cfgDayType" class="form-control" style="margin-top: 0.5rem; display: none;" placeholder="Trong tuần" required>
                </div>
                <div id="cfgSlotWrapper" class="form-group" style="margin-bottom: 1rem;">
                    <label style="font-weight:500; display:block; margin-bottom:0.5rem; color:var(--text-main);">Khung giờ áp dụng (chọn một hoặc nhiều):</label>
                    <div class="slot-chip-grid">
                        <label class="slot-chip-label" id="lbl_slot_suatsom">
                            <input type="checkbox" name="cfgSlotNames" value="Suất sớm" onchange="toggleSlotChipActive('lbl_slot_suatsom', this.checked)">
                            <span class="slot-chip-title">Suất sớm</span>
                            <span class="slot-chip-subtitle">00:00 - 11:59</span>
                        </label>
                        <label class="slot-chip-label" id="lbl_slot_giothuong">
                            <input type="checkbox" name="cfgSlotNames" value="Giờ thường" onchange="toggleSlotChipActive('lbl_slot_giothuong', this.checked)">
                            <span class="slot-chip-title">Giờ thường</span>
                            <span class="slot-chip-subtitle">12:00 - 16:59</span>
                        </label>
                        <label class="slot-chip-label" id="lbl_slot_giovang">
                            <input type="checkbox" name="cfgSlotNames" value="Giờ vàng" onchange="toggleSlotChipActive('lbl_slot_giovang', this.checked)">
                            <span class="slot-chip-title">Giờ vàng</span>
                            <span class="slot-chip-subtitle">17:00 - 21:59</span>
                        </label>
                        <label class="slot-chip-label" id="lbl_slot_suatkhuya">
                            <input type="checkbox" name="cfgSlotNames" value="Suất khuya" onchange="toggleSlotChipActive('lbl_slot_suatkhuya', this.checked)">
                            <span class="slot-chip-title">Suất khuya</span>
                            <span class="slot-chip-subtitle">22:00 - 23:59</span>
                        </label>
                    </div>
                </div>
            `;
        }
    } else if (type === 'seats') {
        container.innerHTML = `
            <div class="form-group" style="margin-bottom: 1rem;">
                <label style="font-weight:500; display:block; margin-bottom:0.5rem; color:var(--text-main);">Mã loại ghế:</label>
                <input type="text" id="cfgSeatTypeCode" class="form-control" placeholder="Ví dụ: vip, couple..." required>
            </div>
        `;
    } else if (type === 'formats') {
        container.innerHTML = `
            <div class="form-group" style="margin-bottom: 1rem;">
                <label style="font-weight:500; display:block; margin-bottom:0.5rem; color:var(--text-main);">Mã định dạng phòng:</label>
                <input type="text" id="cfgFormatCode" class="form-control" placeholder="Ví dụ: 3D, IMAX, PREMIUM..." required>
            </div>
        `;
    } else if (type === 'discounts') {
        groupPrice.style.display = 'none';
        groupRate.style.display = 'block';
        groupFixed.style.display = 'block';
        groupMinPrice.style.display = 'block';
        groupMaxDisc.style.display = 'block';

        container.innerHTML = `
            <div class="form-group" style="margin-bottom: 1rem;">
                <label style="font-weight:500; display:block; margin-bottom:0.5rem; color:var(--text-main);">Đối tượng khách hàng:</label>
                <input type="text" id="cfgCustomerType" class="form-control" placeholder="Ví dụ: STUDENT, CHILD..." required>
            </div>
        `;
    }
}

async function savePricingConfig() {
    const type = document.getElementById('editConfigType').value;
    const id = document.getElementById('editConfigId').value;
    const baseVal = parseFloat(document.getElementById('editConfigValue').value) || 0;

    let body = {};
    let url = '';

    if (id) body.id = parseInt(id);

    if (type === 'base') {
        url = '/api/tickets/configs/base';
        const noteVal = document.getElementById('cfgNote') ? document.getElementById('cfgNote').value.trim() : '';

        // Read selected movie IDs
        const movieIdSelect = document.getElementById('cfgMovieId');
        let selectedMovieIds = [];
        let isAllMoviesChecked = false;
        
        if (movieIdSelect) {
            // Edit mode (single movie selection dropdown)
            const val = movieIdSelect.value;
            if (val) selectedMovieIds = [parseInt(val)];
            else isAllMoviesChecked = true;
        } else {
            // Creation mode (multiple movie checkboxes)
            isAllMoviesChecked = document.getElementById('cfgAllMoviesCheckbox') ? document.getElementById('cfgAllMoviesCheckbox').checked : false;
            selectedMovieIds = isAllMoviesChecked ? [] : Array.from(document.querySelectorAll('input[name="cfgMovieIds"]:checked')).map(el => parseInt(el.value));
        }

        if (selectedMovieIds.length === 0 && !isAllMoviesChecked) {
            showToast('error', 'Lỗi', 'Vui lòng chọn ít nhất một phim hoặc chọn "Áp dụng cho tất cả phim".');
            return;
        }

        // Determine checked slots
        let checkedSlots = [];
        if (selectedMovieIds.length > 0 && !isAllMoviesChecked) {
            // Surcharges on specific movies apply to all their slots dynamically
            checkedSlots = ['Suất sớm', 'Giờ thường', 'Giờ vàng', 'Suất khuya'];
        } else {
            checkedSlots = Array.from(document.querySelectorAll('input[name="cfgSlotNames"]:checked')).map(el => el.value);
            if (checkedSlots.length === 0) {
                showToast('error', 'Lỗi', 'Vui lòng chọn ít nhất một khung giờ.');
                return;
            }
        }

        // Determine target dates
        let targetDates = [];
        if (selectedMovieIds.length > 0 && !isAllMoviesChecked) {
            targetDates = Array.from(document.querySelectorAll('input[name="cfgDayTypeDates"]:checked')).map(el => el.value);
            if (targetDates.length === 0) {
                showToast('error', 'Lỗi', 'Vui lòng chọn ít nhất một ngày chiếu áp dụng của phim.');
                return;
            }
        } else {
            const dayTypeVal = document.getElementById('cfgDayType').value.trim();
            if (!dayTypeVal) {
                showToast('error', 'Lỗi', 'Vui lòng điền loại ngày hoặc ngày đặc biệt.');
                return;
            }
            targetDates = [dayTypeVal];
        }

        if (id) {
            // Update single existing configuration
            const date = targetDates[0];
            const movieIdVal = selectedMovieIds.length > 0 ? selectedMovieIds[0] : null;
            if (movieIdVal) {
                try {
                    const rConfigs = await fetch('/api/tickets/configs');
                    const dataConfigs = await rConfigs.json();
                    const matchedConfigs = dataConfigs.basePrices.filter(bp => bp.movieId === movieIdVal && bp.dayType === date);
                    const promises = [];

                    checkedSlots.forEach(slot => {
                        let start = "12:00:00", end = "16:59:59";
                        if (slot === 'Suất sớm') { start = '00:00:00'; end = '11:59:59'; }
                        else if (slot === 'Giờ thường') { start = '12:00:00'; end = '16:59:59'; }
                        else if (slot === 'Giờ vàng') { start = '17:00:00'; end = '21:59:59'; }
                        else if (slot === 'Suất khuya') { start = '22:00:00'; end = '23:59:59'; }

                        const existing = matchedConfigs.find(mc => mc.slotName === slot);
                        const reqBody = {
                            id: existing ? existing.id : null,
                            movieId: movieIdVal,
                            dayType: date,
                            slotName: slot,
                            startTime: start,
                            endTime: end,
                            basePrice: baseVal,
                            note: noteVal || null
                        };

                        promises.push(fetch(url, {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify(reqBody)
                        }).then(async r => {
                            if (!r.ok) {
                                const errData = await r.json().catch(() => ({}));
                                throw new Error(errData.error || `Lỗi cập nhật cấu hình`);
                            }
                        }));
                    });

                    await Promise.all(promises);
                    showToast('success', 'Lưu thành công', 'Cấu hình giá vé đã được cập nhật.');
                    closeEditConfigModal();
                    loadPricingDashboard();
                } catch (e) {
                    showToast('error', 'Lỗi', e.message || 'Không thể lưu cấu hình.');
                }
            } else {
                const slot = checkedSlots[0];
                let start = "12:00:00", end = "16:59:59";
                if (slot === 'Suất sớm') { start = '00:00:00'; end = '11:59:59'; }
                else if (slot === 'Giờ thường') { start = '12:00:00'; end = '16:59:59'; }
                else if (slot === 'Giờ vàng') { start = '17:00:00'; end = '21:59:59'; }
                else if (slot === 'Suất khuya') { start = '22:00:00'; end = '23:59:59'; }

                body.id = parseInt(id);
                body.movieId = null;
                body.dayType = date;
                body.slotName = slot;
                body.startTime = start;
                body.endTime = end;
                body.basePrice = baseVal;
                body.note = noteVal || null;

                try {
                    const r = await fetch(url, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify(body)
                    });
                    if (!r.ok) {
                        const data = await r.json().catch(() => ({}));
                        throw new Error(data.error || 'Không thể cập nhật cấu hình.');
                    }
                    showToast('success', 'Lưu thành công', 'Cấu hình giá vé đã được cập nhật.');
                    closeEditConfigModal();
                    loadPricingDashboard();
                } catch (e) {
                    showToast('error', 'Lỗi', e.message);
                }
            }
        } else {
            // Batch creation flow
            try {
                const promises = [];
                const moviesListToSave = selectedMovieIds.length > 0 ? selectedMovieIds : [null];

                moviesListToSave.forEach(movieIdVal => {
                    targetDates.forEach(date => {
                        let slotsToCreateForDate = [];
                        if (movieIdVal) {
                            const dayShowtimes = _cachedShowtimes.filter(st => st.movie && st.movie.id === movieIdVal && st.showDate === date);
                            const slotsSet = new Set();
                            dayShowtimes.forEach(st => {
                                let showTimeStr = '';
                                if (st.showTime) {
                                    if (typeof st.showTime === 'string') {
                                        showTimeStr = st.showTime;
                                    } else if (Array.isArray(st.showTime)) {
                                        showTimeStr = String(st.showTime[0]).padStart(2, '0') + ':' + String(st.showTime[1]).padStart(2, '0') + ':00';
                                    }
                                }
                                if (showTimeStr >= '00:00:00' && showTimeStr <= '11:59:59') slotsSet.add('Suất sớm');
                                else if (showTimeStr >= '12:00:00' && showTimeStr <= '16:59:59') slotsSet.add('Giờ thường');
                                else if (showTimeStr >= '17:00:00' && showTimeStr <= '21:59:59') slotsSet.add('Giờ vàng');
                                else if (showTimeStr >= '22:00:00' && showTimeStr <= '23:59:59') slotsSet.add('Suất khuya');
                            });
                            slotsToCreateForDate = Array.from(slotsSet);
                        } else {
                            slotsToCreateForDate = checkedSlots;
                        }

                        slotsToCreateForDate.forEach(slot => {
                            let start = "12:00:00", end = "16:59:59";
                            if (slot === 'Suất sớm') { start = '00:00:00'; end = '11:59:59'; }
                            else if (slot === 'Giờ thường') { start = '12:00:00'; end = '16:59:59'; }
                            else if (slot === 'Giờ vàng') { start = '17:00:00'; end = '21:59:59'; }
                            else if (slot === 'Suất khuya') { start = '22:00:00'; end = '23:59:59'; }

                            const reqBody = {
                                movieId: movieIdVal,
                                dayType: date,
                                slotName: slot,
                                startTime: start,
                                endTime: end,
                                basePrice: baseVal,
                                note: noteVal || null
                            };

                            const promise = fetch(url, {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/json' },
                                body: JSON.stringify(reqBody)
                            }).then(async r => {
                                if (!r.ok) {
                                    const data = await r.json().catch(() => ({}));
                                    throw new Error(data.error || `Lỗi tạo giá vé phim`);
                                }
                            });
                            promises.push(promise);
                        });
                    });
                });

                if (promises.length === 0) {
                    showToast('error', 'Lỗi', 'Không tìm thấy suất chiếu thực tế nào của phim vào các ngày được chọn.');
                    return;
                }

                await Promise.all(promises);
                showToast('success', 'Lưu thành công', 'Đã tạo cấu hình giá vé cho các ngày chiếu.');
                closeEditConfigModal();
                loadPricingDashboard();
            } catch (e) {
                showToast('error', 'Lỗi', e.message);
            }
        }
        return;
    } else if (type === 'seats') {
        url = '/api/tickets/configs/seats';
        body.seatTypeCode = document.getElementById('cfgSeatTypeCode').value.trim().toLowerCase();
        body.surchargeAmount = baseVal;
    } else if (type === 'formats') {
        url = '/api/tickets/configs/formats';
        body.formatCode = document.getElementById('cfgFormatCode').value.trim().toUpperCase();
        body.surchargeAmount = baseVal;
    } else if (type === 'discounts') {
        url = '/api/tickets/configs/discounts';
        body.customerType = document.getElementById('cfgCustomerType').value.trim().toUpperCase();
        body.discountRate = parseFloat(document.getElementById('editConfigDiscountRate').value) || 0;
        const fixedVal = document.getElementById('editConfigFixedPriceWeekday').value;
        body.fixedPriceWeekday = fixedVal ? parseFloat(fixedVal) : null;
        body.minPriceToApply = parseFloat(document.getElementById('editConfigMinPriceToApply').value) || 0;
        body.maxDiscountAmount = parseFloat(document.getElementById('editConfigMaxDiscountAmount').value) || 999999;
    }

    try {
        const r = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });
        if (!r.ok) {
            const data = await r.json().catch(() => ({}));
            throw new Error(data.error || 'Không thể lưu cấu hình.');
        }
        showToast('success', 'Lưu thành công', 'Cấu hình giá vé đã được cập nhật.');
        closeEditConfigModal();
        loadPricingDashboard(); // Refresh
    } catch (e) {
        showToast('error', 'Lỗi', e.getMessage ? e.getMessage() : e.message);
    }
}

async function deletePricingConfig(type, id) {
    const confirmed = await showConfirm(
        'Xác nhận xóa',
        'Bạn có chắc chắn muốn xóa cấu hình giá này không?',
        'Đồng ý xóa'
    );
    if (!confirmed) return;

    let url = '';
    if (type === 'base') url = `/api/tickets/configs/base/${id}`;
    else if (type === 'seats') url = `/api/tickets/configs/seats/${id}`;
    else if (type === 'formats') url = `/api/tickets/configs/formats/${id}`;
    else if (type === 'discounts') url = `/api/tickets/configs/discounts/${id}`;

    try {
        const r = await fetch(url, { method: 'DELETE' });
        if (!r.ok) {
            const data = await r.json().catch(() => ({}));
            throw new Error(data.error || 'Không thể xóa cấu hình.');
        }
        showToast('success', 'Xóa thành công', 'Cấu hình giá vé đã được xóa.');
        loadPricingDashboard(); // Refresh
    } catch (e) {
        showToast('error', 'Lỗi', e.getMessage ? e.getMessage() : e.message);
    }
}

function closeEditConfigModal() {
    document.getElementById('editConfigModal').classList.remove('show');
    document.getElementById('editConfigModal').style.display = 'none';
}
