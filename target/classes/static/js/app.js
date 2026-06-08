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

// ==================== TRẠNG THÁI TOÀN CỤC ====================
let moviesData      = [];   // Toàn bộ phim sau khi lọc
let showtimesData   = [];   // Toàn bộ lịch chiếu sau khi lọc
let ticketsData     = [];   // Vé của suất chiếu đang xem
let ticketsFiltered = [];   // Vé sau khi lọc theo trạng thái

let moviesPage    = 1;
let showtimesPage = 1;
const PAGE_SIZE   = 6;      // Số dòng mỗi trang

let activeShowtimeId = null; // ID suất chiếu đang mở sơ đồ ghế

// ==================== KHỞI TẠO ====================
document.addEventListener('DOMContentLoaded', () => {
    initTabs();
    initMovieEvents();
    initShowtimeEvents();
    initTicketEvents();

    // Tải dữ liệu mặc định cho tab Phim
    loadMovieStats();
    loadMovies({});
});

// ==================== ĐIỀU KHIỂN TABS ====================
function initTabs() {
    document.getElementById('tabMoviesBtn').addEventListener('click', () => switchTab('movies'));
    document.getElementById('tabShowtimesBtn').addEventListener('click', () => switchTab('showtimes'));
    document.getElementById('tabTicketsBtn').addEventListener('click', () => switchTab('tickets'));
}

function switchTab(tab) {
    // Ẩn tất cả section, bỏ active tất cả tabs
    ['moviesSection', 'showtimesSection', 'ticketsSection'].forEach(id => {
        document.getElementById(id).classList.remove('active');
    });
    ['tabMoviesBtn', 'tabShowtimesBtn', 'tabTicketsBtn'].forEach(id => {
        document.getElementById(id).classList.remove('active');
    });

    if (tab === 'movies') {
        document.getElementById('moviesSection').classList.add('active');
        document.getElementById('tabMoviesBtn').classList.add('active');
        loadMovieStats();
        loadMovies({});
    } else if (tab === 'showtimes') {
        document.getElementById('showtimesSection').classList.add('active');
        document.getElementById('tabShowtimesBtn').classList.add('active');
        loadShowtimeStats();
        populateMovieDropdowns();
        loadShowtimes({});
    } else if (tab === 'tickets') {
        document.getElementById('ticketsSection').classList.add('active');
        document.getElementById('tabTicketsBtn').classList.add('active');
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
    } catch(err) { alert('Lỗi khi lưu phim. Vui lòng thử lại.'); }
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

// --- Xóa phim ---
async function deleteMovie(id) {
    if (!confirm('Xóa phim này? Tất cả lịch chiếu và vé liên quan cũng sẽ bị xóa.')) return;
    try {
        await fetch(`${API_MOVIES}/${id}`, { method:'DELETE' });
        loadMovieStats();
        applyMovieFilter();
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
    // Tự phát hiện loại ngày khi chọn ngày chiếu
    document.getElementById('showtimeDateInput').addEventListener('change', e => {
        document.getElementById('showtimeDayTypeDisplay').value = detectDayType(e.target.value) || 'Chưa xác định';
    });
}

// --- Thống kê lịch chiếu ---
async function loadShowtimeStats() {
    try {
        const r = await fetch(`${API_SHOWTIMES}/stats`);
        if (!r.ok) return;
        const s = await r.json();
        document.getElementById('statShowtimeTotal').textContent   = s.total   ?? 0;
        document.getElementById('statShowtimeWeekday').textContent = s.weekday ?? 0;
        document.getElementById('statShowtimeWeekend').textContent = s.weekend ?? 0;
        document.getElementById('statShowtimeHoliday').textContent = s.holiday ?? 0;
    } catch(e) { console.error('Lỗi thống kê lịch chiếu:', e); }
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

// --- Render bảng lịch chiếu ---
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

    const start = (showtimesPage - 1) * PAGE_SIZE;
    const slice = showtimesData.slice(start, start + PAGE_SIZE);
    const badgeMap = { 'Trong tuần':'badge-weekday', 'Cuối tuần':'badge-weekend', 'Ngày lễ':'badge-holiday' };

    // Lấy thống kê ghế cho từng lịch chiếu trong trang hiện tại
    const statsPromises = slice.map(st =>
        fetch(`${API_TICKETS}/stats/${st.id}`).then(r => r.ok ? r.json() : null).catch(() => null)
    );
    const statsResults = await Promise.all(statsPromises);

    slice.forEach((st, idx) => {
        const mv  = st.movie || {};
        const poster = mv.posterUrl
            ? `<img class="movie-poster-thumb" src="${esc(mv.posterUrl)}" alt="Poster"
                    onerror="this.outerHTML='<div class=\\'movie-poster-placeholder\\'><i class=\\'fa-regular fa-image\\'></i></div>'">`
            : `<div class="movie-poster-placeholder"><i class="fa-regular fa-image"></i></div>`;

        const stats    = statsResults[idx];
        const seatInfo = stats
            ? `<span style="font-weight:600;">${stats.emptyCount}</span> / ${stats.totalCount}
               <span style="font-size:.75rem;color:var(--text-muted);">(trống/tổng)</span>`
            : '—';

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
            <td><strong>${formatDate(st.showDate)}</strong></td>
            <td><strong style="color:var(--primary-color);font-size:1rem;">${formatTime(st.showTime)}</strong></td>
            <td>${esc(st.room||'—')}</td>
            <td><span class="badge-daytype ${badgeMap[st.dayType]||''}">${esc(st.dayType||'—')}</span></td>
            <td>${seatInfo}</td>
            <td>
                <div class="action-cell">
                    <button class="action-btn action-btn-edit"   onclick="editShowtime(${st.id})">Sửa</button>
                    <button class="action-btn action-btn-delete" onclick="deleteShowtime(${st.id})">Xóa</button>
                </div>
            </td>`;
        tbody.appendChild(tr);
    });

    buildPagination('showtimePaginationControls','showtimePaginationInfo',
        showtimesData.length, showtimesPage, p => { showtimesPage = p; renderShowtimeTable(); });
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
    ['filterShowtimeMovie','filterShowtimeDayType','filterShowtimeDate']
        .forEach(id => document.getElementById(id).value = '');
    document.getElementById('filterShowtimeViewMode').value = 'all';
    loadShowtimes({});
}

// --- Modal lịch chiếu ---
function openShowtimeModal(isEdit) {
    document.getElementById('showtimeModalTitle').textContent =
        isEdit ? 'Sửa lịch chiếu phim' : 'Thêm lịch chiếu mới';
    if (!isEdit) {
        document.getElementById('showtimeForm').reset();
        document.getElementById('showtimeParamId').value      = '';
        document.getElementById('showtimeDayTypeDisplay').value = 'Chưa xác định ngày';
    }
    populateMovieDropdowns();
    document.getElementById('showtimeModal').classList.add('show');
}
function closeShowtimeModal() { document.getElementById('showtimeModal').classList.remove('show'); }

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
        if (!r.ok) throw new Error();
        closeShowtimeModal();
        loadShowtimeStats();
        applyShowtimeFilter();
        // Cập nhật lại dropdown suất chiếu cho tab Vé
        populateShowtimeDropdown();
    } catch(err) { alert('Lỗi khi lưu lịch chiếu. Vui lòng thử lại.'); }
}

// --- Sửa lịch chiếu ---
async function editShowtime(id) {
    try {
        const r  = await fetch(`${API_SHOWTIMES}/${id}`);
        const st = await r.json();

        await populateMovieDropdowns();

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
        await fetch(`${API_SHOWTIMES}/${id}`, { method:'DELETE' });
        loadShowtimeStats();
        applyShowtimeFilter();
        populateShowtimeDropdown();
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
