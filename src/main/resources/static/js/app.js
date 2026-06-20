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

// ==================== KHỞI TẠO ====================
document.addEventListener('DOMContentLoaded', () => {
    const page = document.body.dataset.page || 'movies';
    initTabs();

    if (page === 'movies') {
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
    if (!url) return null;
    const m = url.match(/(?:youtu\.be\/|v=|embed\/)([^#&?]{11})/);
    return m ? m[1] : null;
}

// ======================================================
//   QUẢN LÝ PHIM
// ======================================================

// --- Đăng ký sự kiện phim ---
function initMovieEvents() {
    document.getElementById('btnOpenAddModal').addEventListener('click',   () => openMovieModal(false));
    document.getElementById('btnCloseModal').addEventListener('click',     closeMovieModal);
    document.getElementById('btnCancelModal').addEventListener('click',    closeMovieModal);
    document.getElementById('btnApplyFilter').addEventListener('click',    applyMovieFilter);
    document.getElementById('btnResetFilter').addEventListener('click',    resetMovieFilter);
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

// --- Tải danh sách phim ---
async function loadMovies(filters) {
    const qs = new URLSearchParams();
    Object.entries(filters).forEach(([k,v]) => { if (v != null && v !== '') qs.append(k, v); });
    try {
        const r = await fetch(`${API_MOVIES}${qs.toString() ? '?'+qs : ''}`);
        if (!r.ok) throw new Error();
        moviesData  = await r.json();
        moviesPage  = 1;
        document.getElementById('resultsCount').textContent = `Tìm thấy ${moviesData.length} kết quả`;
        renderMovieTable();
    } catch(e) {
        document.getElementById('movieTableBody').innerHTML =
            `<tr><td colspan="6" class="text-center" style="padding:3rem;color:var(--stat-red);">
                <i class="fa-solid fa-triangle-exclamation"></i> Không thể tải danh sách phim.
            </td></tr>`;
    }
}

// --- Render bảng phim ---
function renderMovieTable() {
    const tbody = document.getElementById('movieTableBody');
    tbody.innerHTML = '';

    if (!moviesData.length) {
        tbody.innerHTML = `<tr><td colspan="6" class="text-center" style="padding:3rem;color:var(--text-muted);">
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
        const poster = mv.posterUrl
            ? `<img class="movie-poster-thumb" src="${esc(mv.posterUrl)}" alt="Poster"
                    onerror="this.outerHTML='<div class=\\'movie-poster-placeholder\\'><i class=\\'fa-regular fa-image\\'></i></div>'">`
            : `<div class="movie-poster-placeholder"><i class="fa-regular fa-image"></i></div>`;
        const trailer = mv.trailerUrl
            ? `<button class="badge-trailer" onclick="openTrailer('${esc(mv.trailerUrl)}')">
                    <i class="fa-solid fa-play"></i> Trailer</button>` : '';

        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td>
                <div class="movie-info-cell">
                    ${poster}
                    <div class="movie-meta">
                        <div class="movie-title-row">
                            <span class="movie-title-text">${esc(mv.title)}</span>
                            ${trailer}
                        </div>
                        <span class="movie-director-text">Đạo diễn: ${esc(mv.director||'Chưa rõ')}</span>
                    </div>
                </div>
            </td>
            <td>${esc(mv.genre||'—')}</td>
            <td>${mv.duration ? mv.duration+' phút' : '—'}</td>
            <td><span class="status-text" style="color:${colorMap[mv.status]||'inherit'}">${esc(mv.status||'—')}</span></td>
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
    loadMovies({
        title:       document.getElementById('filterTitle').value.trim(),
        genre:       document.getElementById('filterGenre').value.trim(),
        director:    document.getElementById('filterDirector').value.trim(),
        duration:    document.getElementById('filterDuration').value.trim() || null,
        status:      document.getElementById('filterStatus').value,
        releaseDate: document.getElementById('filterReleaseDate').value || null
    });
}
function resetMovieFilter() {
    ['filterTitle','filterGenre','filterDirector','filterDuration','filterReleaseDate']
        .forEach(id => document.getElementById(id).value = '');
    document.getElementById('filterStatus').value = '';
    loadMovies({});
}

// --- Modal phim ---
function openMovieModal(isEdit) {
    document.getElementById('modalTitle').textContent = isEdit ? 'Sửa thông tin phim' : 'Thêm phim mới';
    if (!isEdit) {
        document.getElementById('movieForm').reset();
        document.getElementById('movieParamId').value = '';
        showPosterPreview('');
    }
    document.getElementById('movieModal').classList.add('show');
}
function closeMovieModal() { document.getElementById('movieModal').classList.remove('show'); }

// --- Lưu phim (có upload video nếu cần) ---
async function handleMovieSave(e) {
    e.preventDefault();
    const id        = document.getElementById('movieParamId').value;
    const trailerUrl = document.getElementById('movieTrailerUrl').value; // URL từ upload

    const body = {
        title:       document.getElementById('movieTitle').value.trim(),
        trailerUrl:  trailerUrl || null,           // Dùng URL video đã upload
        summary:     document.getElementById('movieSummary').value.trim()   || null,
        genre:       document.getElementById('movieGenre').value.trim()     || null,
        duration:    document.getElementById('movieDuration').value
                        ? parseInt(document.getElementById('movieDuration').value) : null,
        director:    document.getElementById('movieDirector').value.trim()  || null,
        language:    document.getElementById('movieLanguage').value.trim()  || null,
        actors:      document.getElementById('movieActors').value.trim()    || null,
        posterUrl:   document.getElementById('moviePosterUrl').value.trim() || null,
        releaseDate: document.getElementById('movieReleaseDate').value      || null,
        status:      document.getElementById('movieStatus').value
    };

    if (!body.title || !body.releaseDate || !body.status) {
        alert('Vui lòng điền đầy đủ các trường bắt buộc (*)');
        return;
    }
    try {
        body.posterUrl = await resolvePosterUrl() || null;
        const url    = id ? `${API_MOVIES}/${id}` : API_MOVIES;
        const method = id ? 'PUT' : 'POST';
        const r = await fetch(url, {
            method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });
        if (!r.ok) throw new Error();
        closeMovieModal();
        loadMovieStats();
        applyMovieFilter();
        showAppNotice('success', id ? 'Đã cập nhật phim.' : 'Đã thêm phim mới.');
    } catch(err) { alert(err.message || 'Lỗi khi lưu phim. Vui lòng thử lại.'); }
}

// --- Sửa phim (khôi phục trạng thái upload nếu có video) ---
async function editMovie(id) {
    try {
        const r  = await fetch(`${API_MOVIES}/${id}`);
        const mv = await r.json();
        document.getElementById('movieParamId').value   = mv.id;
        document.getElementById('movieTitle').value     = mv.title       || '';
        document.getElementById('movieSummary').value   = mv.summary     || '';
        document.getElementById('movieGenre').value     = mv.genre       || '';
        document.getElementById('movieDuration').value  = mv.duration    || '';
        document.getElementById('movieDirector').value  = mv.director    || '';
        document.getElementById('movieLanguage').value  = mv.language    || '';
        document.getElementById('movieActors').value    = mv.actors      || '';
        document.getElementById('moviePosterUrl').value = mv.posterUrl   || '';
        showPosterPreview(mv.posterUrl || '');
        document.getElementById('movieReleaseDate').value = mv.releaseDate || '';
        document.getElementById('movieStatus').value    = mv.status      || 'Đang chiếu';

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
    } catch(e) { alert('Không thể tải thông tin phim.'); }
}

function handlePosterFilePreview(e) {
    const file = e.target.files && e.target.files[0];
    if (!file) return;
    if (!file.type.startsWith('image/')) {
        alert('Vui lòng chọn file ảnh hợp lệ.');
        e.target.value = '';
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

// --- Xóa phim ---
async function deleteMovie(id) {
    if (!confirm('Xóa phim này? Tất cả lịch chiếu và vé liên quan cũng sẽ bị xóa.')) return;
    try {
        const r = await fetch(`${API_MOVIES}/${id}`, { method:'DELETE' });
        if (!r.ok) throw new Error();
        loadMovieStats();
        applyMovieFilter();
        showAppNotice('success', 'Đã xóa phim.');
    } catch(e) { alert('Lỗi khi xóa phim.'); }
}

// =====================================================
//   UPLOAD VIDEO
// =====================================================

// Xử lý khi người dùng chọn file (qua hộp thoại hoặc kéo thả)
function handleVideoFileSelected(file) {
    // Kiểm tra định dạng file video hợp lệ
    const allowed = ['video/mp4','video/webm','video/x-matroska','video/avi','video/quicktime'];
    if (!allowed.includes(file.type) && !file.name.match(/\.(mp4|webm|mkv|avi|mov)$/i)) {
        alert('Định dạng không được hỗ trợ.\nVui lòng chọn file MP4, WebM, MKV, AVI hoặc MOV.');
        return;
    }
    // Kiểm tra giới hạn kích thước (500MB)
    if (file.size > 500 * 1024 * 1024) {
        alert('File quá lớn! Kích thước tối đa là 500MB.');
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
            let msg = 'Upload thất bại.';
            try { msg = JSON.parse(xhr.responseText).error || msg; } catch(_) {}
            alert(msg);
            resetUploadZone();
        }
    };

    xhr.onerror = () => {
        alert('Lỗi kết nối khi upload. Vui lòng thử lại.');
        resetUploadZone();
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

// Xóa video đã upload và khôi phục upload zone
function removeUploadedVideo() {
    if (!confirm('Bỏ video trailer này?')) return;
    resetUploadZone();
}

// --- Mở trình phát trailer (tự phát hiện YouTube hoặc video cục bộ) ---
function openTrailer(url) {
    if (!url) { alert('Phim này chưa có video trailer.'); return; }

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
        const source = document.getElementById('localVideoSource');
        const ext    = url.split('.').pop().toLowerCase();
        const mimeMap = { mp4:'video/mp4', webm:'video/webm', mkv:'video/x-matroska',
                          avi:'video/avi', mov:'video/quicktime' };
        source.src  = url;
        source.type = mimeMap[ext] || 'video/mp4';
        const player = document.getElementById('localVideoPlayer');
        player.load();
        player.play().catch(() => {}); // Tự động phát, bỏ qua lỗi autoplay
        ytCont.style.display = 'none';
        lcCont.style.display = '';
    }

    modal.classList.add('show');
}

// --- Đóng trình phát trailer ---
function closeTrailer() {
    document.getElementById('trailerModal').classList.remove('show');
    // Dừng YouTube
    document.getElementById('trailerIframe').src = '';
    // Dừng video HTML5
    const player = document.getElementById('localVideoPlayer');
    player.pause();
    player.src = '';
    document.getElementById('localVideoSource').src = '';
}

// ======================================================
//   QUẢN LÝ LỊCH CHIẾU
// ======================================================

function initShowtimeEvents() {
    document.getElementById('btnOpenAddShowtimeModal').addEventListener('click', () => openShowtimeModal(false));
    document.getElementById('btnCloseShowtimeModal').addEventListener('click',   closeShowtimeModal);
    document.getElementById('btnCancelShowtimeModal').addEventListener('click',  closeShowtimeModal);
    document.getElementById('btnApplyShowtimeFilter').addEventListener('click',  applyShowtimeFilter);
    document.getElementById('btnResetShowtimeFilter').addEventListener('click',  resetShowtimeFilter);
    document.getElementById('showtimeForm').addEventListener('submit',           handleShowtimeSave);
    document.querySelectorAll('[data-showtime-view]').forEach(btn => {
        btn.addEventListener('click', () => setShowtimeView(btn.dataset.showtimeView));
    });
    renderShowtimeQuickDates();
    // Tự phát hiện loại ngày khi chọn ngày chiếu
    document.getElementById('showtimeDateInput').addEventListener('change', e => {
        document.getElementById('showtimeDayTypeDisplay').value = detectDayType(e.target.value) || 'Chưa xác định';
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

// --- Nạp phim vào các Select dropdown ---
async function populateMovieDropdowns() {
    try {
        const r    = await fetch(API_MOVIES);
        const list = await r.json();

        const filterSel = document.getElementById('filterShowtimeMovie');
        const formSel   = document.getElementById('showtimeMovieSelect');
        filterSel.innerHTML = '<option value="">-- Tất cả phim --</option>';
        formSel.innerHTML   = '<option value="">-- Chọn bộ phim --</option>';

        list.forEach(mv => {
            [filterSel, formSel].forEach(sel => {
                const opt = document.createElement('option');
                opt.value       = mv.id;
                opt.textContent = mv.title;
                sel.appendChild(opt);
            });
        });
    } catch(e) { console.error('Lỗi nạp dropdown phim:', e); }
}

// --- Nạp phòng chiếu từ danh mục phòng vào select lịch chiếu ---
async function populateRoomDropdown(selectedRoomName = '') {
    const roomSel = document.getElementById('showtimeRoomInput');
    if (!roomSel) return;

    roomSel.innerHTML = '<option value="">-- Chọn phòng từ danh mục phòng --</option>';
    try {
        const r = await fetch(API_ROOMS);
        if (!r.ok) throw new Error();
        const rooms = await r.json();

        rooms.forEach(room => {
            const opt = document.createElement('option');
            opt.value = room.roomName || '';
            const details = [room.roomType, room.audioTech, room.totalSeats ? `${room.totalSeats} ghế` : '']
                .filter(Boolean)
                .join(' · ');
            opt.textContent = details ? `${room.roomName} (${details})` : room.roomName;
            roomSel.appendChild(opt);
        });

        if (selectedRoomName && [...roomSel.options].some(option => option.value === selectedRoomName)) {
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

// --- Modal lịch chiếu ---
function openShowtimeModal(isEdit) {
    document.getElementById('showtimeModalTitle').textContent =
        isEdit ? 'Sửa lịch chiếu phim' : 'Thêm lịch chiếu mới';
    if (!isEdit) {
        document.getElementById('showtimeForm').reset();
        document.getElementById('showtimeParamId').value      = '';
        document.getElementById('showtimeDayTypeDisplay').value = 'Chưa xác định ngày';
        populateMovieDropdowns();
        populateRoomDropdown();
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

    if (!movieId) { alert('Vui lòng chọn bộ phim.'); return; }

    const body = {
        movie:    { id: parseInt(movieId) },
        showDate: document.getElementById('showtimeDateInput').value,
        showTime: timeVal ? timeVal + ':00' : null,
        room:     document.getElementById('showtimeRoomInput').value
    };

    if (!body.showDate || !body.showTime || !body.room) {
        alert('Vui lòng điền đầy đủ các trường bắt buộc (*)');
        return;
    }

    try {
        const url    = id ? `${API_SHOWTIMES}/${id}` : API_SHOWTIMES;
        const method = id ? 'PUT' : 'POST';
        const r = await fetch(url, { method, headers:{'Content-Type':'application/json'}, body: JSON.stringify(body) });
        if (!r.ok) throw new Error(await readShowtimeApiError(r));
        closeShowtimeModal();
        applyShowtimeFilter();
        // Cập nhật lại dropdown suất chiếu cho tab Vé
        populateShowtimeDropdown();
        showAppNotice('success', id ? 'Đã cập nhật lịch chiếu.' : 'Đã thêm lịch chiếu mới.');
    } catch(err) {
        showAppNotice('error', err.message || 'Lỗi khi lưu lịch chiếu. Vui lòng thử lại.');
    }
}

// --- Sửa lịch chiếu ---
async function editShowtime(id) {
    try {
        const r  = await fetch(`${API_SHOWTIMES}/${id}`);
        const st = await r.json();

        await populateMovieDropdowns();
        await populateRoomDropdown(st.room || '');

        document.getElementById('showtimeParamId').value         = st.id;
        document.getElementById('showtimeMovieSelect').value     = st.movie?.id || '';
        document.getElementById('showtimeDateInput').value       = st.showDate  || '';
        document.getElementById('showtimeTimeInput').value       = st.showTime
            ? st.showTime.substring(0,5) : '';
        document.getElementById('showtimeRoomInput').value       = st.room      || '';
        document.getElementById('showtimeDayTypeDisplay').value  = st.dayType   || '—';

        openShowtimeModal(true);
    } catch(e) { alert('Không thể tải thông tin lịch chiếu.'); }
}

// --- Xóa lịch chiếu ---
async function deleteShowtime(id) {
    if (!confirm('Xóa lịch chiếu này? Tất cả vé liên quan cũng sẽ bị xóa.')) return;
    try {
        const r = await fetch(`${API_SHOWTIMES}/${id}`, { method:'DELETE' });
        if (!r.ok) throw new Error();
        applyShowtimeFilter();
        populateShowtimeDropdown();
        showAppNotice('success', 'Đã xóa lịch chiếu.');
    } catch(e) { alert('Lỗi khi xóa lịch chiếu.'); }
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
    try {
        const r = await fetch(`${API_TICKETS}/${ticketId}/status`, { method:'PUT' });
        if (!r.ok) throw new Error();
        // Tải lại toàn bộ để cập nhật sơ đồ + thống kê + bảng
        await loadTicketView(activeShowtimeId);
        showAppNotice('success', 'Đã cập nhật trạng thái ghế.');
    } catch(e) { alert('Không thể cập nhật trạng thái ghế.'); }
}

// --- Hỏi hủy vé đã bán ---
async function confirmCancelSeat(ticketId, seatNum) {
    if (!confirm(`Ghế ${seatNum} đang ở trạng thái "Đã bán".\nBạn có muốn hủy vé này (đặt lại thành "Còn trống") không?`)) return;
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
