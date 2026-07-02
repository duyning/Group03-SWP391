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
let showtimeStatsMap = {};  // Thống kê ghế theo từng lịch chiếu
let activeShowtimeView = 'movie';
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
                <input type="checkbox" name="filterGenreVal" value="${genre}" style="margin: 0; width: auto; height: auto;"> ${genre}
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
        populateMovieDropdowns();
        populateRoomDropdown();
        initShowtimeDefaultFilters();
        loadShowtimes(getShowtimeFiltersFromUI());
    }

    if (page === 'tickets') {
        initTicketEvents();
        populateShowtimeDropdown();
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
        populateMovieDropdowns();
        initShowtimeDefaultFilters();
        loadShowtimes(getShowtimeFiltersFromUI());
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

// ==================== THÔNG BÁO TỰ ẨN ====================
function ensureNoticeStyles() {
    if (document.getElementById('appNoticeStyles')) return;
    const style = document.createElement('style');
    style.id = 'appNoticeStyles';
    style.textContent = `
        .app-notice-stack {
            position: fixed;
            top: 22px;
            right: 22px;
            z-index: 100000;
            display: grid;
            gap: 10px;
            max-width: min(380px, calc(100vw - 32px));
        }
        .app-notice {
            display: flex;
            align-items: center;
            gap: 10px;
            padding: 12px 16px;
            border-radius: 10px;
            font-weight: 700;
            font-size: 14px;
            box-shadow: 0 12px 28px rgba(15, 23, 42, 0.16);
            transition: opacity 0.35s ease, transform 0.35s ease;
        }
        .app-notice.success {
            background: #dcfce7;
            color: #15803d;
            border: 1px solid #bbf7d0;
        }
        .app-notice.error {
            background: #fee2e2;
            color: #b91c1c;
            border: 1px solid #fecaca;
        }
    `;
    document.head.appendChild(style);
}

function showAppNotice(type, message) {
    ensureNoticeStyles();
    let stack = document.getElementById('appNoticeStack');
    if (!stack) {
        stack = document.createElement('div');
        stack.id = 'appNoticeStack';
        stack.className = 'app-notice-stack';
        document.body.appendChild(stack);
    }

    const notice = document.createElement('div');
    notice.className = `app-notice ${type === 'error' ? 'error' : 'success'}`;
    notice.innerHTML = `<i class="fa-solid ${type === 'error' ? 'fa-triangle-exclamation' : 'fa-circle-check'}"></i><span>${esc(message)}</span>`;
    stack.appendChild(notice);

    setTimeout(() => {
        notice.style.opacity = '0';
        notice.style.transform = 'translateY(-6px)';
        setTimeout(() => notice.remove(), 360);
    }, 5000);
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

function todayISO() {
    return isoDate(new Date());
}

function addDaysISO(days) {
    const date = new Date();
    date.setDate(date.getDate() + days);
    return isoDate(date);
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

        // Hàm đóng hộp thoại và trả kết quả
        const close = result => {
            overlay.classList.remove('show'); // Ẩn overlay
            // Xóa event listener cũ để tránh tích lũy nhiều listener
            btnOk.replaceWith(btnOk.cloneNode(true));
            btnCancel.replaceWith(btnCancel.cloneNode(true));
            resolve(result); // Trả kết quả ra cho caller
        };

        // Sau khi replaceWith, cần lấy lại tham chiếu nút mới
        document.getElementById('confirmBtnOk').addEventListener('click',     () => close(true),  { once: true });
        document.getElementById('confirmBtnCancel').addEventListener('click',  () => close(false), { once: true });

        // Nhấn vào vùng nền mờ bên ngoài cũng đóng (tương đương Hủy)
        overlay.addEventListener('click', e => {
            if (e.target === overlay) close(false);
        }, { once: true });
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
    const onClick = (id, handler) => {
        const element = document.getElementById(id);
        if (element) element.addEventListener('click', handler);
    };
    const onChange = (id, handler) => {
        const element = document.getElementById(id);
        if (element) element.addEventListener('change', handler);
    };
    const onInput = (id, handler) => {
        const element = document.getElementById(id);
        if (element) element.addEventListener('input', handler);
    };

    onClick('btnOpenAddModal', () => openMovieModal(false));
    onClick('btnCloseModal', closeMovieModal);
    onClick('btnCancelModal', closeMovieModal);
    onClick('btnApplyFilter', applyMovieFilter);
    onClick('btnResetFilter', resetMovieFilter);

    const filterForm = document.getElementById('filterForm');
    if (filterForm) filterForm.addEventListener('submit', e => {
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

    const movieForm = document.getElementById('movieForm');
    if (movieForm) movieForm.addEventListener('submit', handleMovieSave);
    onClick('btnCloseTrailerModal', closeTrailer);
    onChange('moviePosterFile', handlePosterFilePreview);
    onInput('moviePosterUrl', e => showPosterPreview(e.target.value.trim()));

    // Sự kiện upload video: nút chọn file
    onClick('btnSelectVideo', () => {
        document.getElementById('videoFileInput').click();
    });
    // Khi người dùng chọn file qua hộp thoại
    onChange('videoFileInput', e => {
        if (e.target.files.length > 0) handleVideoFileSelected(e.target.files[0]);
    });
    // Nút xóa video đã upload
    onClick('btnRemoveVideo', removeUploadedVideo);

    // Sự kiện kéo thả file vào upload zone
    const zone = document.getElementById('videoUploadZone');
    if (zone) {
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
    }

    // [MỚI - TrienLX - 2026-06-11]
    // Khi người dùng thay đổi ngày khởi chiếu, tự động gợi ý trạng thái phim
    // và hiển thị hint cho Manager biết hệ thống sẽ tính trạng thái nào.
    onChange('movieReleaseDate', e => {
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
            `<tr><td colspan="9" class="text-center" style="padding:3rem;color:var(--stat-red);">
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
    const colorMap = {
        'Đang chiếu':'var(--stat-green)',
        'Sắp chiếu':'var(--stat-orange)',
        'Suất chiếu đặc biệt':'var(--stat-red)',
        'Ngừng chiếu':'var(--text-muted)'
    };

    slice.forEach(mv => {
        const poster = mv.posterUrl
            ? `<img class="movie-poster-thumb" src="${esc(mv.posterUrl)}" alt="Poster"
                    onerror="this.outerHTML='<div class=\\'movie-poster-placeholder\\'><i class=\\'fa-regular fa-image\\'></i></div>'">`
            : `<div class="movie-poster-placeholder"><i class="fa-regular fa-image"></i></div>`;
        const trailer = mv.trailerUrl
            ? `<button class="badge-trailer" onclick="openTrailer('${esc(mv.trailerUrl)}')">
                    <i class="fa-solid fa-play"></i> Trailer</button>` : '';

        const tr = document.createElement('tr');
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
            <td><span class="status-text" style="color:${colorMap[mv.status]||'inherit'}">${esc(mv.status||'—')}</span></td>
            <td class="text-center">
                <span class="status-text" style="color:${mv.active ? 'var(--stat-green)' : 'var(--text-muted)'}">
                    ${mv.active ? 'Đang hiện' : 'Đã ẩn'}
                </span>
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
    document.getElementById('btnApplyShowtimeFilter').addEventListener('click',  applyShowtimeFilter);
    document.getElementById('btnResetShowtimeFilter').addEventListener('click',  resetShowtimeFilter);
    document.getElementById('showtimeFilterForm').addEventListener('submit', e => {
        e.preventDefault();
        applyShowtimeFilter();
    });
    document.getElementById('showtimeForm').addEventListener('submit',           handleShowtimeSave);
    document.querySelectorAll('[data-showtime-view]').forEach(btn => {
        btn.addEventListener('click', () => setShowtimeView(btn.dataset.showtimeView));
    });
    renderShowtimeQuickDates();

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
    document.getElementById('filterShowtimeDate').addEventListener('change', syncShowtimeQuickDates);
}

function initShowtimeDefaultFilters() {
    const viewMode = document.getElementById('filterShowtimeViewMode');
    if (viewMode && !viewMode.value) {
        viewMode.value = 'next7';
    }
}

function getShowtimeFiltersFromUI() {
    return {
        movieId:  document.getElementById('filterShowtimeMovie').value   || null,
        viewMode: document.getElementById('filterShowtimeViewMode').value || 'next7',
        dayType:  document.getElementById('filterShowtimeDayType').value  || null,
        startDate:document.getElementById('filterShowtimeDate').value     || null
    };
}

function setShowtimeView(view) {
    activeShowtimeView = view === 'room' ? 'room' : 'movie';

    document.querySelectorAll('[data-showtime-view]').forEach(btn => {
        const isActive = btn.dataset.showtimeView === activeShowtimeView;
        btn.classList.toggle('active', isActive);
        btn.setAttribute('aria-selected', String(isActive));
    });

    document.getElementById('showtimeMovieView')?.classList.toggle('active', activeShowtimeView === 'movie');
    document.getElementById('showtimeRoomView')?.classList.toggle('active', activeShowtimeView === 'room');

    const title = document.getElementById('showtimeBoardTitle');
    const subtitle = document.getElementById('showtimeBoardSubtitle');
    if (title) title.textContent = activeShowtimeView === 'movie' ? 'Góc nhìn theo phim' : 'Góc nhìn theo phòng';
    if (subtitle) {
        subtitle.textContent = activeShowtimeView === 'movie'
            ? 'Các suất chiếu được nhóm theo phim và phòng.'
            : 'Timeline phân bổ suất chiếu theo từng phòng trong ngày.';
    }
}

function renderShowtimeQuickDates() {
    const container = document.getElementById('showtimeQuickDateStrip');
    if (!container) return;

    const weekdays = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'];
    const selectedDate = document.getElementById('filterShowtimeDate')?.value || '';
    const days = Array.from({ length: 8 }, (_, index) => {
        const date = new Date();
        date.setDate(date.getDate() + index);
        const iso = isoDate(date);
        const label = index === 0 ? 'Hôm nay' : weekdays[date.getDay()];
        return { iso, label, display: formatDate(iso).slice(0, 5) };
    });

    container.innerHTML = days.map(day => `
        <button type="button" class="showtime-date-chip ${selectedDate === day.iso ? 'active' : ''}" data-showtime-date="${day.iso}">
            <span>${day.label}</span>
            <strong>${day.display}</strong>
        </button>
    `).join('');

    container.querySelectorAll('[data-showtime-date]').forEach(btn => {
        btn.addEventListener('click', () => {
            document.getElementById('filterShowtimeDate').value = btn.dataset.showtimeDate;
            syncShowtimeQuickDates();
            applyShowtimeFilter();
        });
    });
}

function syncShowtimeQuickDates() {
    const selectedDate = document.getElementById('filterShowtimeDate')?.value || '';
    document.querySelectorAll('[data-showtime-date]').forEach(btn => {
        btn.classList.toggle('active', btn.dataset.showtimeDate === selectedDate);
    });
}

function updateShowtimeStatsFromData() {
    const stats = showtimesData.reduce((acc, st) => {
        const status = getShowtimeStatus(st).key;
        acc.total++;
        if (status === 'running') acc.running++;
        if (status === 'upcoming') acc.upcoming++;
        if (status === 'finished') acc.finished++;
        return acc;
    }, { total: 0, running: 0, upcoming: 0, finished: 0 });

    document.getElementById('statShowtimeTotal').textContent   = stats.total;
    document.getElementById('statShowtimeWeekday').textContent = stats.running;
    document.getElementById('statShowtimeWeekend').textContent = stats.upcoming;
    document.getElementById('statShowtimeHoliday').textContent = stats.finished;
}

function getShowtimeViewRange(filters) {
    const selectedDate = filters.startDate;
    if (selectedDate) {
        return { startDate: selectedDate, endDate: selectedDate, label: 'ngày ' + formatDate(selectedDate) };
    }

    switch (filters.viewMode) {
        case 'today':
            return { startDate: todayISO(), endDate: todayISO(), label: 'hôm nay' };
        case 'next7':
            return { startDate: todayISO(), endDate: addDaysISO(6), label: '7 ngày tới' };
        case 'next30':
            return { startDate: todayISO(), endDate: addDaysISO(29), label: '30 ngày tới' };
        case 'week':
            return { ...getWeekRange(), label: 'tuần này' };
        case 'month':
            return { ...getMonthRange(), label: 'tháng này' };
        case 'past':
            return { startDate: null, endDate: addDaysISO(-1), label: 'lịch đã qua' };
        case 'all':
            return { startDate: null, endDate: null, label: 'tất cả lịch chiếu' };
        case 'upcoming':
            return { startDate: todayISO(), endDate: null, label: 'từ hôm nay trở đi' };
        default:
            return { startDate: todayISO(), endDate: addDaysISO(6), label: '7 ngày tới' };
    }
}

function getShowtimeStartDateTime(st) {
    if (!st.showDate || !st.showTime) return null;
    const time = st.showTime.length >= 5 ? st.showTime.substring(0, 5) : st.showTime;
    return new Date(`${st.showDate}T${time}:00`);
}

function sortShowtimesForView(list, viewMode) {
    const sorted = [...list].sort((a, b) => {
        const first = getShowtimeStartDateTime(a)?.getTime() || 0;
        const second = getShowtimeStartDateTime(b)?.getTime() || 0;
        return first - second;
    });

    return viewMode === 'past' ? sorted.reverse() : sorted;
}

function getShowtimeDateLabel(dateString) {
    const date = new Date(`${dateString}T00:00:00`);
    if (Number.isNaN(date.getTime())) return formatDate(dateString);

    if (dateString === todayISO()) {
        return `Hôm nay · ${formatDate(dateString)}`;
    }
    if (dateString === addDaysISO(1)) {
        return `Ngày mai · ${formatDate(dateString)}`;
    }

    const weekdays = ['Chủ nhật', 'Thứ 2', 'Thứ 3', 'Thứ 4', 'Thứ 5', 'Thứ 6', 'Thứ 7'];
    return `${weekdays[date.getDay()]} · ${formatDate(dateString)}`;
}

function groupBy(list, keyFn) {
    return list.reduce((map, item) => {
        const key = keyFn(item);
        if (!map.has(key)) map.set(key, []);
        map.get(key).push(item);
        return map;
    }, new Map());
}

function getShowtimeStartMinutes(st) {
    if (!st.showTime) return null;
    const parts = st.showTime.split(':').map(Number);
    if (parts.length < 2 || Number.isNaN(parts[0]) || Number.isNaN(parts[1])) return null;
    return parts[0] * 60 + parts[1];
}

function getMovieDuration(st) {
    const duration = Number(st.movie?.duration);
    return Number.isFinite(duration) && duration > 0 ? duration : 120;
}

function addMinutesToTimeLabel(st, minutesToAdd) {
    const startMinutes = getShowtimeStartMinutes(st);
    if (startMinutes == null) return '—';
    const total = startMinutes + minutesToAdd;
    const hours = Math.floor(total / 60) % 24;
    const minutes = total % 60;
    return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}`;
}

function getShowtimeEndTimeLabel(st) {
    return addMinutesToTimeLabel(st, getMovieDuration(st));
}

function getShowtimeSeatStats(st) {
    const stats = showtimeStatsMap[st.id];
    const total = Number(stats?.totalCount || 0);
    const empty = Number(stats?.emptyCount || 0);
    const sold = Math.max(total - empty, 0);
    const soldPercent = total > 0 ? Math.round((sold / total) * 100) : 0;
    return { total, empty, sold, soldPercent };
}

function getShowtimeSlotClass(st) {
    if (st.hasRoomConflict) return 'conflict';
    const key = getShowtimeStatus(st).key;
    if (key === 'running') return 'running';
    if (key === 'finished') return 'finished';
    return 'upcoming';
}

function decorateShowtimesWithConflicts(list) {
    list.forEach(st => { st.hasRoomConflict = false; });
    const groups = groupBy(list, st => `${st.showDate || ''}__${(st.room || '').toLowerCase()}`);
    groups.forEach(group => {
        const sorted = [...group].sort((a, b) => (getShowtimeStartMinutes(a) ?? 0) - (getShowtimeStartMinutes(b) ?? 0));
        let latestEnd = null;
        let latestShowtime = null;
        sorted.forEach(st => {
            const start = getShowtimeStartMinutes(st);
            const end = start == null ? null : start + getMovieDuration(st);
            if (start != null && latestEnd != null && start < latestEnd) {
                st.hasRoomConflict = true;
                if (latestShowtime) latestShowtime.hasRoomConflict = true;
            }
            if (end != null && (latestEnd == null || end > latestEnd)) {
                latestEnd = end;
                latestShowtime = st;
            }
        });
    });
    return list;
}

function getShowtimeConflictCount() {
    return showtimesData.filter(st => st.hasRoomConflict).length;
}

async function hydrateShowtimeTicketStats() {
    showtimeStatsMap = {};
    const pairs = await Promise.all(showtimesData.map(st =>
        fetch(`${API_TICKETS}/stats/${st.id}`)
            .then(r => r.ok ? r.json() : null)
            .then(stats => [st.id, stats])
            .catch(() => [st.id, null])
    ));

    pairs.forEach(([id, stats]) => {
        if (stats) showtimeStatsMap[id] = stats;
    });
}

function getShowtimeStatus(st) {
    const start = getShowtimeStartDateTime(st);
    if (!start || Number.isNaN(start.getTime())) {
        return { key: 'upcoming', text: 'Chưa xác định', icon: 'fa-circle-question', className: 'showtime-status-upcoming', locked: false };
    }

    const duration = Number(st.movie?.duration || 120);
    const end = new Date(start.getTime() + duration * 60 * 1000);
    const now = new Date();

    if (now >= start && now <= end) {
        return { key: 'running', text: 'Đang chiếu', icon: 'fa-circle-play', className: 'showtime-status-running', locked: true };
    }
    if (now > end) {
        return { key: 'finished', text: 'Đã chiếu', icon: 'fa-lock', className: 'showtime-status-finished', locked: true };
    }
    return { key: 'upcoming', text: 'Sắp chiếu', icon: 'fa-clock', className: 'showtime-status-upcoming', locked: false };
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
async function populateRoomDropdown(selectedRoomName = '', forceRefresh = false) {
    const roomSel = document.getElementById('showtimeRoomInput');
    if (!roomSel) return;

    try {
        if (!_cachedRoomList || forceRefresh) {
            const r = await fetch(API_ROOMS);
            if (!r.ok) throw new Error();
            _cachedRoomList = await r.json();
        }
        const rooms = _cachedRoomList;

        roomSel.innerHTML = '<option value="">-- Chọn phòng từ danh mục phòng --</option>';
        rooms.forEach(room => {
            const opt = document.createElement('option');
            opt.value = room.roomName || '';
            const details = [room.roomType, room.audioTech, room.totalSeats ? `${room.totalSeats} ghế` : '']
                .filter(Boolean).join(' · ');
            opt.textContent = details ? `${room.roomName} (${details})` : room.roomName;
            roomSel.appendChild(opt);
        });

        if (selectedRoomName && [...roomSel.options].some(o => o.value === selectedRoomName)) {
            roomSel.value = selectedRoomName;
        }
    } catch (e) {
        console.error('Lỗi nạp danh sách phòng chiếu:', e);
        roomSel.innerHTML = '<option value="">Không thể tải danh sách phòng</option>';
    }
}

// --- Tải lịch chiếu ---
async function loadShowtimes(filters) {
    const qs = new URLSearchParams();
    const { viewMode, movieId, dayType, startDate, endDate } = filters;
    const range = getShowtimeViewRange({ viewMode, startDate, endDate });

    if (movieId) qs.append('movieId', movieId);
    if (dayType)  qs.append('dayType', dayType);
    if (range.startDate) qs.append('startDate', range.startDate);
    if (range.endDate)   qs.append('endDate', range.endDate);

    try {
        const r = await fetch(`${API_SHOWTIMES}${qs.toString() ? '?'+qs : ''}`);
        if (!r.ok) throw new Error();
        showtimesData  = decorateShowtimesWithConflicts(sortShowtimesForView(await r.json(), viewMode));
        showtimesPage  = 1;
        await hydrateShowtimeTicketStats();
        updateShowtimeStatsFromData();
        const roomCount = new Set(showtimesData.map(st => st.room).filter(Boolean)).size;
        const conflictCount = getShowtimeConflictCount();
        document.getElementById('showtimeResultsCount').textContent =
            `${showtimesData.length} suất chiếu · ${roomCount} phòng · ${range.label}${conflictCount ? ` · ${conflictCount} trùng phòng` : ' · không trùng phòng'}`;
        syncShowtimeQuickDates();
        renderShowtimeTable();
    } catch(e) {
        showtimesData = [];
        showtimeStatsMap = {};
        showtimesPage = 1;
        updateShowtimeStatsFromData();
        document.getElementById('showtimeResultsCount').textContent =
            'Không thể tải lịch chiếu trong phạm vi lọc hiện tại';
        renderShowtimeErrorState();
    }
}

// ==================== NHÓM LỊCH CHIẾU THEO PHIM + PHÒNG + DẢI NGÀY ====================
// Nhóm theo movieId|room|minDate|maxDate để tất cả slot cùng phim+phòng+dải ngày
// hiển thị gọn trên 1 hàng, các khung giờ hiển thị dưới dạng badge
function groupShowtimes(list) {
    // Bước 1: Gom các suất chiếu cùng phim+phòng+giờ thành slot (dải ngày)
    const slotMap = new Map();
    list.forEach(st => {
        const mv  = st.movie || {};
        const key = `${mv.id||'?'}|${st.room||''}|${st.showTime||''}`;
        if (!slotMap.has(key)) {
            slotMap.set(key, {
                showTime: st.showTime,
                dayType:  st.dayType,
                minDate:  st.showDate,
                maxDate:  st.showDate,
                ids:      [st.id]
            });
        } else {
            const slot = slotMap.get(key);
            if (st.showDate < slot.minDate) slot.minDate = st.showDate;
            if (st.showDate > slot.maxDate) slot.maxDate = st.showDate;
            slot.ids.push(st.id);
        }
    });

    // Bước 2: Gom các slot cùng phim+phòng+dải ngày thành 1 nhóm duy nhất
    const groupMap = new Map();
    slotMap.forEach(slot => {
        // Lấy thông tin phim/phòng từ suất chiếu đầu tiên của slot này
        const firstSt = list.find(s => slot.ids.includes(s.id));
        if (!firstSt) return;
        const mv = firstSt.movie || {};
        // Key nhóm: phim + phòng + dải ngày
        const gKey = `${mv.id||'?'}|${firstSt.room||''}|${slot.minDate}|${slot.maxDate}`;
        if (!groupMap.has(gKey)) {
            groupMap.set(gKey, {
                movie:   mv,
                room:    firstSt.room,
                dayType: slot.dayType,
                minDate: slot.minDate,
                maxDate: slot.maxDate,
                ids:     [...slot.ids],
                slots:   [{ showTime: slot.showTime, ids: slot.ids }]
            });
        } else {
            const g = groupMap.get(gKey);
            g.ids.push(...slot.ids);
            g.slots.push({ showTime: slot.showTime, ids: slot.ids });
            // Cập nhật dải ngày nếu cần
            if (slot.minDate < g.minDate) g.minDate = slot.minDate;
            if (slot.maxDate > g.maxDate) g.maxDate = slot.maxDate;
        }
    });

    // Sắp xếp các slot trong mỗi nhóm theo giờ chiếu tăng dần
    groupMap.forEach(g => {
        g.slots.sort((a, b) => (a.showTime || '').localeCompare(b.showTime || ''));
    });

    return Array.from(groupMap.values());
}

// --- Render lịch chiếu theo 2 góc nhìn vận hành ---
async function renderShowtimeTable() {
    updateShowtimeStatsFromData();
    renderShowtimeMovieBoard();
    renderShowtimeRoomTimeline();
}

function renderShowtimeErrorState() {
    const errorHtml = `
        <div class="empty-state">
            <i class="fa-solid fa-triangle-exclamation"></i>
            <strong>Không thể tải lịch chiếu</strong>
            <span>Vui lòng kiểm tra kết nối hoặc thử lại bộ lọc khác.</span>
        </div>`;
    const movieBoard = document.getElementById('showtimeMovieBoard');
    const roomTimeline = document.getElementById('showtimeRoomTimeline');
    if (movieBoard) movieBoard.innerHTML = errorHtml;
    if (roomTimeline) roomTimeline.innerHTML = errorHtml;
}

function renderShowtimeEmptyState(container, icon, title, message) {
    if (!container) return;
    container.innerHTML = `
        <div class="empty-state">
            <i class="fa-solid ${icon}"></i>
            <strong>${esc(title)}</strong>
            <span>${esc(message)}</span>
        </div>`;
}

function renderShowtimeMovieBoard() {
    const container = document.getElementById('showtimeMovieBoard');
    if (!container) return;

    if (!showtimesData.length) {
        renderShowtimeEmptyState(container, 'fa-calendar-xmark', 'Chưa có lịch chiếu', 'Không tìm thấy suất chiếu nào trong phạm vi lọc hiện tại.');
        return;
    }

    const movieGroups = [...groupBy(showtimesData, st => String(st.movie?.id || st.movie?.title || 'unknown')).values()];
    container.innerHTML = movieGroups.map(group => {
        const first = group[0];
        const movie = first.movie || {};
        const roomGroups = [...groupBy(group, st => st.room || 'Chưa chọn phòng').entries()];
        const totalSeats = group.reduce((sum, st) => sum + getShowtimeSeatStats(st).total, 0);
        const soldSeats = group.reduce((sum, st) => sum + getShowtimeSeatStats(st).sold, 0);
        const poster = movie.posterUrl
            ? `<img class="schedule-poster" src="${esc(movie.posterUrl)}" alt="Poster ${esc(movie.title || '')}" onerror="this.outerHTML='<div class=\\'schedule-poster-placeholder\\'><i class=\\'fa-regular fa-image\\'></i></div>'">`
            : `<div class="schedule-poster-placeholder"><i class="fa-regular fa-image"></i></div>`;

        return `
            <article class="schedule-movie-card">
                <div class="schedule-movie-head">
                    ${poster}
                    <div>
                        <h2 class="schedule-movie-title">${esc(movie.title || 'Phim đã xóa')}</h2>
                        <div class="schedule-meta">
                            <span class="schedule-chip"><i class="fa-regular fa-clock"></i>${getMovieDuration(first)} phút</span>
                            <span class="schedule-chip"><i class="fa-solid fa-door-open"></i>${roomGroups.length} phòng</span>
                            <span class="schedule-chip"><i class="fa-solid fa-ticket"></i>${group.length} suất</span>
                            <span class="schedule-chip"><i class="fa-solid fa-user-tie"></i>${esc(movie.director || 'Chưa cập nhật')}</span>
                        </div>
                    </div>
                    <div class="schedule-movie-summary">
                        <div class="summary-box">
                            <span>Đã đặt</span>
                            <strong>${soldSeats}</strong>
                        </div>
                        <div class="summary-box">
                            <span>Sức chứa</span>
                            <strong>${totalSeats || '—'}</strong>
                        </div>
                    </div>
                </div>
                <div class="movie-room-list">
                    ${roomGroups.map(([room, items]) => renderMovieRoomRow(room, items)).join('')}
                </div>
            </article>`;
    }).join('');
}

function renderMovieRoomRow(room, items) {
    const sorted = [...items].sort((a, b) => (getShowtimeStartMinutes(a) ?? 0) - (getShowtimeStartMinutes(b) ?? 0));
    const totalSeats = sorted.reduce((sum, st) => sum + getShowtimeSeatStats(st).total, 0);
    return `
        <div class="movie-room-row">
            <div class="room-stamp">
                <i class="fa-solid fa-door-open"></i>
                <div>
                    ${esc(room)}
                    <span>${sorted.length} suất · ${totalSeats || '—'} ghế</span>
                </div>
            </div>
            <div class="time-slot-grid">
                ${sorted.map(renderShowtimeSlotCard).join('')}
            </div>
        </div>`;
}

function renderShowtimeSlotCard(st) {
    const status = getShowtimeStatus(st);
    const statusClass = getShowtimeSlotClass(st);
    const seatStats = getShowtimeSeatStats(st);
    const statusText = st.hasRoomConflict ? 'Trùng phòng' : status.text;
    const statusIcon = st.hasRoomConflict ? 'fa-triangle-exclamation' : status.icon;
    const actionButtons = status.locked
        ? `<span class="locked-note"><i class="fa-solid fa-lock"></i> Đã khóa</span>`
        : `<span class="slot-actions">
                <button type="button" class="slot-action-btn" title="Sửa lịch chiếu" onclick="editShowtime(${st.id})"><i class="fa-solid fa-pen"></i></button>
                <button type="button" class="slot-action-btn delete" title="Xóa lịch chiếu" onclick="deleteShowtime(${st.id})"><i class="fa-solid fa-trash"></i></button>
           </span>`;

    return `
        <div class="showtime-slot ${statusClass}">
            <div class="slot-topline">
                <div>
                    <div class="slot-time">${formatTime(st.showTime)}</div>
                    <div class="slot-date">${getShowtimeDateLabel(st.showDate)}</div>
                </div>
                <span class="showtime-status ${st.hasRoomConflict ? 'showtime-status-conflict' : status.className}">
                    <i class="fa-solid ${statusIcon}"></i>${statusText}
                </span>
            </div>
            <div class="seat-meter" aria-label="Tỷ lệ ghế đã đặt">
                <div class="seat-meter-fill" style="width:${seatStats.soldPercent}%;"></div>
            </div>
            <div class="slot-footer">
                <span>${seatStats.sold}/${seatStats.total || '—'} ghế đã đặt</span>
                ${actionButtons}
            </div>
        </div>`;
}

function renderShowtimeRoomTimeline() {
    const container = document.getElementById('showtimeRoomTimeline');
    if (!container) return;

    if (!showtimesData.length) {
        renderShowtimeEmptyState(container, 'fa-calendar-xmark', 'Chưa có lịch chiếu', 'Không tìm thấy suất chiếu nào trong phạm vi lọc hiện tại.');
        return;
    }

    const dateGroups = [...groupBy(showtimesData, st => st.showDate || 'unknown').entries()]
        .sort(([a], [b]) => a.localeCompare(b));

    container.innerHTML = dateGroups.map(([date, items]) => {
        const roomGroups = [...groupBy(items, st => st.room || 'Chưa chọn phòng').entries()]
            .sort(([a], [b]) => a.localeCompare(b, 'vi'));
        const conflictCount = items.filter(st => st.hasRoomConflict).length;
        return `
            <article class="timeline-day-card">
                <div class="timeline-day-head">
                    <span><i class="fa-regular fa-calendar-days"></i> ${esc(getShowtimeDateLabel(date))}</span>
                    <span>${items.length} suất · ${roomGroups.length} phòng${conflictCount ? ` · ${conflictCount} trùng phòng` : ''}</span>
                </div>
                <div class="timeline-scroll">
                    <div class="timeline-wrap">
                        <div class="timeline-header">
                            <div class="timeline-room-heading">Phòng chiếu</div>
                            <div class="timeline-scale">
                                <span>08:00</span><span>10:00</span><span>12:00</span><span>14:00</span>
                                <span>16:00</span><span>18:00</span><span>20:00</span><span>22:00</span>
                            </div>
                        </div>
                        ${roomGroups.map(([room, roomItems]) => renderTimelineRoomRow(room, roomItems)).join('')}
                    </div>
                </div>
            </article>`;
    }).join('');
}

function renderTimelineRoomRow(room, items) {
    const sorted = [...items].sort((a, b) => (getShowtimeStartMinutes(a) ?? 0) - (getShowtimeStartMinutes(b) ?? 0));
    const totalSeats = sorted.reduce((sum, st) => sum + getShowtimeSeatStats(st).total, 0);
    return `
        <div class="timeline-row">
            <div class="timeline-room-cell">
                <strong>${esc(room)}</strong>
                <span>${sorted.length} suất · ${totalSeats || '—'} ghế</span>
            </div>
            <div class="timeline-track">
                ${sorted.map(renderTimelineSlot).join('')}
            </div>
        </div>`;
}

function renderTimelineSlot(st) {
    const startMinutes = getShowtimeStartMinutes(st);
    const dayStart = 8 * 60;
    const dayLength = 16 * 60;
    const duration = getMovieDuration(st);
    const left = Math.max(0, Math.min(100, (((startMinutes ?? dayStart) - dayStart) / dayLength) * 100));
    const width = Math.max(5.5, Math.min(100 - left, (duration / dayLength) * 100));
    const statusClass = getShowtimeSlotClass(st);
    const movie = st.movie || {};
    const seatStats = getShowtimeSeatStats(st);
    const title = `${movie.title || 'Phim đã xóa'} · ${formatTime(st.showTime)} - ${getShowtimeEndTimeLabel(st)} · ${seatStats.sold}/${seatStats.total || '—'} ghế`;

    return `
        <div class="timeline-slot ${statusClass}" style="left:${left.toFixed(2)}%; width:${width.toFixed(2)}%;" title="${esc(title)}" onclick="${getShowtimeStatus(st).locked ? '' : `editShowtime(${st.id})`}">
            <strong>${esc(movie.title || 'Phim đã xóa')}</strong>
            <span>${formatTime(st.showTime)} - ${getShowtimeEndTimeLabel(st)}</span>
            <span>${st.hasRoomConflict ? 'Trùng phòng' : `${seatStats.sold}/${seatStats.total || '—'} ghế`}</span>
        </div>`;
}

// --- Bộ lọc lịch chiếu ---
function applyShowtimeFilter() {
    loadShowtimes(getShowtimeFiltersFromUI());
}
function resetShowtimeFilter() {
    ['filterShowtimeMovie','filterShowtimeDayType','filterShowtimeDate']
        .forEach(id => document.getElementById(id).value = '');
    document.getElementById('filterShowtimeViewMode').value = 'next7';
    syncShowtimeQuickDates();
    loadShowtimes(getShowtimeFiltersFromUI());
}

function openShowtimeModal(isEdit) {
    document.getElementById('showtimeModalTitle').textContent =
        isEdit ? 'Sửa lịch chiếu phim' : 'Thêm lịch chiếu mới';

    const dateRow      = document.getElementById('showtimeDateRow');
    const endDateGroup = document.getElementById('endDateGroup');
    const endDateInput = document.getElementById('showtimeEndDateInput');
    const lblStart     = document.getElementById('lblShowtimeDateStart');
    const slotRow      = document.getElementById('slotCountRow');

    // Luôn giữ layout dải ngày (Từ ngày -> Đến ngày) ở cả 2 chế độ
    dateRow.classList.add('split-2');
    endDateGroup.style.display = '';
    endDateInput.setAttribute('required', 'true');
    lblStart.innerHTML = 'Từ ngày <span class="required">*</span>';

    if (!isEdit) {
        document.getElementById('showtimeForm').reset();
        document.getElementById('showtimeParamId').value        = '';
        document.getElementById('showtimeDayTypeDisplay').value = 'Chưa xác định ngày';
        document.getElementById('showtimeSlotCount').value      = '1';
        if (slotRow) slotRow.style.display = '';

        // Đặt min = hôm nay cho cả hai input ngày
        setMinShowtimeDateToday('showtimeDateInput');
        setMinShowtimeDateToday('showtimeEndDateInput');
        populateMovieDropdowns();
        populateRoomDropdown();
    } else {
        // Ẩn slotCount khi chỉnh sửa
        if (slotRow) slotRow.style.display = 'none';

        // Đặt min = hôm nay cho cả hai input ngày khi sửa
        setMinShowtimeDateToday('showtimeDateInput');
        setMinShowtimeDateToday('showtimeEndDateInput');
    }
    document.getElementById('showtimeModal').classList.add('show');
}
function closeShowtimeModal() { document.getElementById('showtimeModal').classList.remove('show'); }

async function readShowtimeApiError(response) {
    try {
        const data = await response.json();
        return data.message || data.error || 'Lỗi khi lưu lịch chiếu. Vui lòng thử lại.';
    } catch (e) {
        return 'Lỗi khi lưu lịch chiếu. Vui lòng thử lại.';
    }
}

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
            throw new Error(payload.error || payload.message || 'Lỗi khi lưu lịch chiếu. Vui lòng kiểm tra lại.');
        }

        // Tính số suất đã tạo/cập nhật từ kết quả trả về
        const countCreated = Array.isArray(payload) ? payload.length : 1;
        const successMsg = id
            ? 'Lịch chiếu đã được cập nhật thành công.'
            : `Đã tạo thành công ${countCreated} suất chiếu.`;

        showToast('success', id ? 'Cập nhật thành công!' : 'Thêm lịch chiếu thành công!', successMsg);
        closeShowtimeModal();
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
        document.getElementById('showtimeTimeInput').value       = st.showTime
            ? st.showTime.substring(0,5) : '';
        document.getElementById('showtimeRoomInput').value       = st.room      || '';
        document.getElementById('showtimeDayTypeDisplay').value  = st.dayType   || '—';
    } catch(e) {
        showToast('error', 'Lỗi tải dữ liệu', 'Không thể tải thông tin lịch chiếu.');
    }
}

// --- Xóa lịch chiếu đơn ---
async function deleteShowtime(id) {
    const confirmed = await showConfirm(
        'Xác nhận xóa lịch chiếu',
        'Bạn có chắc chắn muốn xóa lịch chiếu này không?\nTất cả vé liên quan cũng sẽ bị xóa khỏi hệ thống.',
        'Xóa lịch chiếu'
    );
    if (!confirmed) return;
    try {
        const r = await fetch(`${API_SHOWTIMES}/${id}`, { method:'DELETE' });
        if (!r.ok) throw new Error();
        showToast('success', 'Xóa thành công!', 'Lịch chiếu đã được xóa khỏi hệ thống.');
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
        applyShowtimeFilter();
        populateShowtimeDropdown();
    } catch(e) {
        showToast('error', 'Xóa thất bại', 'Lỗi khi xóa nhóm lịch chiếu. Vui lòng thử lại.');
    }
}

// ======================================================
//   QUẢN LÝ BÁN VÉ & SƠ ĐỒ GHẾ
// ======================================================

function initTicketEvents() {
    document.getElementById('ticketShowtimeSelect').addEventListener('change', e => {
        const id = e.target.value;
        activeShowtimeId = id ? parseInt(id) : null;
        if (activeShowtimeId) loadTicketView(activeShowtimeId);
        else hideSeatMap();
    });
    document.getElementById('btnFilterAllTickets').addEventListener('click',   () => filterTicketTable('all'));
    document.getElementById('btnFilterSoldTickets').addEventListener('click',  () => filterTicketTable('sold'));
    document.getElementById('btnFilterEmptyTickets').addEventListener('click', () => filterTicketTable('empty'));
}

// --- Nạp danh sách suất chiếu vào dropdown Bán vé ---
async function populateShowtimeDropdown() {
    try {
        const r    = await fetch(API_SHOWTIMES);
        const list = await r.json();

        const sel = document.getElementById('ticketShowtimeSelect');
        const prevId = sel.value;
        sel.innerHTML = '<option value="">-- Chọn suất chiếu --</option>';

        list.forEach(st => {
            const mv   = st.movie || {};
            const opt  = document.createElement('option');
            opt.value  = st.id;
            opt.textContent =
                `${esc(mv.title||'?')} | ${formatDate(st.showDate)} ${formatTime(st.showTime)} | ${esc(st.room||'?')}`;
            sel.appendChild(opt);
        });

        // Khôi phục lựa chọn nếu vẫn còn tồn tại
        if (prevId && [...sel.options].some(o => o.value == prevId)) {
            sel.value = prevId;
        }
    } catch(e) { console.error('Lỗi nạp suất chiếu:', e); }
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

// --- Render sơ đồ ghế ngồi trực quan ---
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
            const typeClass  = isVIP ? 'vip' : 'standard';
            const stateClass = isSold ? 'is-sold' : 'available';
            const priceLabel = formatVND(ticket.price) + 'đ';
            const icon       = isSold ? '🔒' : ticket.seatNumber;

            const btn = document.createElement('button');
            btn.className        = `seat-btn ${typeClass} ${stateClass}`;
            btn.dataset.ticketId = ticket.id;
            btn.dataset.price    = priceLabel;
            btn.dataset.seatNumber = ticket.seatNumber;
            btn.textContent      = icon;
            btn.title            = `Ghế ${ticket.seatNumber} — ${ticket.seatType} — ${priceLabel} — ${ticket.status}`;
            btn.setAttribute('aria-label', `Ghế ${ticket.seatNumber}`);

            if (!isSold) {
                btn.addEventListener('click', () => toggleSeat(ticket.id));
            } else {
                // Ghế đã bán: click để hỏi có muốn hủy vé không
                btn.addEventListener('click', () => confirmCancelSeat(ticket.id, ticket.seatNumber));
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
    const colors = { 'Thường':'#10b981', 'VIP':'#f59e0b' };
    Object.entries(priceSet).forEach(([type, price]) => {
        const div = document.createElement('div');
        div.className = 'price-info-item';
        div.innerHTML = `
            <span class="price-info-dot" style="background:${colors[type]||'#aaa'};"></span>
            Ghế ${type}: <strong>${formatVND(price)}đ</strong>`;
        info.appendChild(div);
    });
}

// --- Toggle đặt vé / hủy đặt ---
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
        seatType = btn.classList.contains('vip') ? 'VIP' : 'Thường';

        // Cập nhật UI ngay lập tức (optimistic update)
        if (isCurrentlySold) {
            btn.classList.remove('is-sold');
            btn.classList.add('available');
            btn.textContent = seatNumber;
            btn.title = `Ghế ${seatNumber} — ${seatType} — ${priceLabel} — Còn trống`;
        } else {
            btn.classList.remove('available');
            btn.classList.add('is-sold');
            btn.textContent = '🔒';
            btn.title = `Ghế ${seatNumber} — ${seatType} — ${priceLabel} — Đã bán`;
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
                btn.textContent = '🔒';
                btn.title = `Ghế ${seatNumber} — ${seatType} — ${priceLabel} — Đã bán`;
            } else {
                btn.classList.remove('is-sold');
                btn.classList.add('available');
                btn.textContent = seatNumber;
                btn.title = `Ghế ${seatNumber} — ${seatType} — ${priceLabel} — Còn trống`;
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

// --- Render bảng vé chi tiết ---
function renderTicketTable(tickets) {
    const tbody = document.getElementById('ticketTableBody');
    tbody.innerHTML = '';

    if (!tickets.length) {
        tbody.innerHTML = `<tr><td colspan="5" class="text-center" style="padding:2rem;color:var(--text-muted);">
            Không có vé nào.
        </td></tr>`;
        return;
    }

    tickets.forEach(t => {
        const isSold    = t.status === 'Đã bán';
        const isVIP     = t.seatType === 'VIP';
        const statusBadge = isSold
            ? `<span class="badge-sold">Đã bán</span>`
            : `<span class="badge-available">Còn trống</span>`;
        const typeBadge = isVIP
            ? `<span class="badge-vip">VIP</span>`
            : `<span class="badge-standard">Thường</span>`;

        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td><strong style="font-size:1rem;">${esc(t.seatNumber)}</strong></td>
            <td>${typeBadge}</td>
            <td><strong>${formatVND(t.price)}</strong></td>
            <td>${statusBadge}</td>
            <td class="text-center">
                <button class="action-btn ${isSold ? 'action-btn-edit' : 'action-btn-delete'}"
                        onclick="${isSold ? `confirmCancelSeat(${t.id},'${t.seatNumber}')` : `toggleSeat(${t.id})`}">
                    ${isSold ? 'Hủy vé' : 'Bán vé'}
                </button>
            </td>`;
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
