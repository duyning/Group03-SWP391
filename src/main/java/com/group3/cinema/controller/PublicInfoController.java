package com.group3.cinema.controller;

/*
 * Created on 2026-06-25: Public cinema information pages and customer contact form.
 * Created by: NinhDD - HE186113
 */

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.CustomerContact;
import com.group3.cinema.service.CustomerContactService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class PublicInfoController {

    private static final List<InfoPage> INFO_PAGES = buildInfoPages();
    private static final Map<String, InfoPage> INFO_PAGE_BY_KEY = buildInfoPageMap();

    private final CustomerContactService customerContactService;

    public PublicInfoController(CustomerContactService customerContactService) {
        this.customerContactService = customerContactService;
    }

    @GetMapping({"/about", "/gioi-thieu"})
    public String showAbout(HttpSession session, Model model) {
        return showInfoPage("about", session, model);
    }

    @GetMapping({"/contact", "/lien-he"})
    public String showContact(HttpSession session, Model model) {
        addLoggedInUser(session, model);
        addNavigation(model, "contact");
        if (!model.containsAttribute("contact")) {
            model.addAttribute("contact", new CustomerContact());
        }
        return "public-contact";
    }

    @PostMapping({"/contact", "/lien-he"})
    public String submitContact(@ModelAttribute("contact") CustomerContact contact,
                                RedirectAttributes redirectAttributes) {
        try {
            customerContactService.createContact(contact);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Cảm ơn bạn đã liên hệ. Bộ phận chăm sóc khách hàng sẽ phản hồi trong thời gian sớm nhất.");
            return "redirect:/contact";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            redirectAttributes.addFlashAttribute("contact", contact);
            return "redirect:/contact";
        }
    }

    @GetMapping({"/general-terms", "/dieu-khoan-chung"})
    public String showGeneralTerms(HttpSession session, Model model) {
        return showInfoPage("general-terms", session, model);
    }

    @GetMapping({"/transaction-terms", "/dieu-khoan-giao-dich"})
    public String showTransactionTerms(HttpSession session, Model model) {
        return showInfoPage("transaction-terms", session, model);
    }

    @GetMapping({"/payment-policy", "/chinh-sach-thanh-toan"})
    public String showPaymentPolicy(HttpSession session, Model model) {
        return showInfoPage("payment-policy", session, model);
    }

    @GetMapping({"/privacy-policy", "/chinh-sach-bao-mat"})
    public String showPrivacyPolicy(HttpSession session, Model model) {
        return showInfoPage("privacy-policy", session, model);
    }

    @GetMapping({"/faq", "/cau-hoi-thuong-gap"})
    public String showFaq(HttpSession session, Model model) {
        return showInfoPage("faq", session, model);
    }

    @GetMapping({"/partners", "/danh-cho-doi-tac"})
    public String showPartners(HttpSession session, Model model) {
        return showInfoPage("partners", session, model);
    }

    @GetMapping({"/cinema-rules", "/quy-dinh-tai-rap"})
    public String showCinemaRules(HttpSession session, Model model) {
        return showInfoPage("cinema-rules", session, model);
    }

    private String showInfoPage(String activeKey, HttpSession session, Model model) {
        addLoggedInUser(session, model);
        addNavigation(model, activeKey);
        model.addAttribute("page", INFO_PAGE_BY_KEY.get(activeKey));
        return "public-info-page";
    }

    private void addNavigation(Model model, String activeKey) {
        model.addAttribute("infoPages", INFO_PAGES);
        model.addAttribute("activeInfoKey", activeKey);
        model.addAttribute("contactPage", INFO_PAGE_BY_KEY.get("contact"));
    }

    private void addLoggedInUser(HttpSession session, Model model) {
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        if (loggedInUser != null) {
            model.addAttribute("user", loggedInUser);
        }
    }

    private static List<InfoPage> buildInfoPages() {
        List<InfoPage> pages = new ArrayList<>();
        pages.add(new InfoPage("about", "/about", "GIỚI THIỆU", "Giới thiệu Beta Cinemas",
                "Không gian điện ảnh hiện đại dành cho khách hàng trẻ, gia đình và cộng đồng yêu phim.",
                List.of(
                        new InfoSection("Về Beta Cinemas",
                                List.of("Beta Cinemas được xây dựng theo định hướng rạp chiếu phim hiện đại, thân thiện và dễ tiếp cận. Hệ thống tập trung vào trải nghiệm đặt vé nhanh, phòng chiếu sạch sẽ, âm thanh ổn định và dịch vụ khách hàng rõ ràng.",
                                        "Rạp phục vụ các dòng phim đang chiếu, phim sắp chiếu, suất chiếu đặc biệt và các chương trình khuyến mãi theo từng nhóm khách hàng."),
                                List.of()),
                        new InfoSection("Trải nghiệm tại rạp",
                                List.of("Khách hàng có thể theo dõi phim, lịch chiếu, ưu đãi, tin tức và thông tin rạp trực tiếp trên website. Các phòng chiếu được quản lý theo sơ đồ ghế, loại ghế và trạng thái vận hành để hỗ trợ đặt vé chính xác."),
                                List.of("Phòng chiếu 2D, 3D và các định dạng mở rộng theo cấu hình rạp.",
                                        "Ghế thường, ghế VIP, ghế đôi và khu vực trống được quản lý riêng.",
                                        "Combo bắp nước và chiến dịch khuyến mãi được cập nhật theo từng giai đoạn.")),
                        new InfoSection("Cam kết dịch vụ",
                                List.of("Beta Cinemas đặt mục tiêu cung cấp dịch vụ minh bạch, thuận tiện và đáng tin cậy từ lúc khách hàng chọn phim đến khi hoàn tất giao dịch."),
                                List.of("Thông tin phim và suất chiếu được cập nhật thường xuyên.",
                                        "Giá vé, combo và ưu đãi hiển thị rõ trước khi thanh toán.",
                                        "Mọi phản hồi của khách hàng được tiếp nhận qua trang liên hệ."))
                )));
        pages.add(new InfoPage("contact", "/contact", "LIÊN HỆ", "Liên hệ Beta Cinemas",
                "Địa chỉ rạp, bản đồ và biểu mẫu tiếp nhận thông tin khách hàng.", List.of()));
        pages.add(new InfoPage("general-terms", "/general-terms", "ĐIỀU KHOẢN CHUNG", "Điều khoản chung",
                "Các nguyên tắc sử dụng website, dịch vụ rạp và thông tin hiển thị trên hệ thống.",
                List.of(
                        new InfoSection("Phạm vi áp dụng",
                                List.of("Điều khoản chung áp dụng cho khách hàng truy cập website, sử dụng thông tin phim, tin tức, khuyến mãi, đặt vé hoặc gửi yêu cầu liên hệ tới Beta Cinemas."),
                                List.of()),
                        new InfoSection("Trách nhiệm của khách hàng",
                                List.of("Khách hàng cần cung cấp thông tin chính xác khi đăng ký tài khoản, đặt vé hoặc gửi liên hệ. Mọi hành vi sử dụng sai mục đích, can thiệp hệ thống hoặc gây ảnh hưởng tới người dùng khác đều không được chấp nhận."),
                                List.of("Không sử dụng thông tin giả mạo khi giao dịch.",
                                        "Không sao chép nội dung website cho mục đích thương mại khi chưa được cho phép.",
                                        "Tuân thủ quy định tại rạp và hướng dẫn của nhân viên vận hành.")),
                        new InfoSection("Thay đổi điều khoản",
                                List.of("Beta Cinemas có thể cập nhật điều khoản để phù hợp với chính sách vận hành, quy định pháp luật hoặc thay đổi nghiệp vụ. Nội dung mới có hiệu lực khi được công bố trên website."),
                                List.of())
                )));
        pages.add(new InfoPage("transaction-terms", "/transaction-terms", "ĐIỀU KHOẢN GIAO DỊCH", "Điều khoản giao dịch",
                "Quy định liên quan đến đặt vé, xác nhận giao dịch, đổi trả và trách nhiệm khi mua vé.",
                List.of(
                        new InfoSection("Xác nhận giao dịch",
                                List.of("Giao dịch được xem là hợp lệ khi hệ thống ghi nhận đầy đủ thông tin phim, rạp, phòng, suất chiếu, ghế, giá vé và trạng thái thanh toán thành công."),
                                List.of()),
                        new InfoSection("Đổi hoặc hủy vé",
                                List.of("Vé đã thanh toán thường không được hoàn, hủy hoặc đổi sang suất chiếu khác trừ trường hợp lỗi phát sinh từ hệ thống hoặc rạp có thông báo riêng."),
                                List.of("Khách hàng cần kiểm tra kỹ phim, ngày chiếu, giờ chiếu và vị trí ghế trước khi thanh toán.",
                                        "Nếu suất chiếu bị thay đổi bởi rạp, khách hàng sẽ được hỗ trợ theo chính sách tại thời điểm phát sinh.")),
                        new InfoSection("Sai lệch thông tin",
                                List.of("Nếu thông tin giao dịch bị sai lệch do khách hàng nhập nhầm hoặc chọn nhầm, Beta Cinemas sẽ hỗ trợ kiểm tra nhưng không cam kết xử lý đổi trả trong mọi trường hợp."),
                                List.of())
                )));
        pages.add(new InfoPage("payment-policy", "/payment-policy", "CHÍNH SÁCH THANH TOÁN", "Chính sách thanh toán",
                "Quy định thanh toán, hình thức thanh toán và danh sách thẻ được chấp nhận khi giao dịch trực tuyến.",
                List.of(
                        new InfoSection("Chính sách thanh toán",
                                List.of("Khách hàng cần hoàn tất thanh toán trong thời gian hệ thống cho phép giữ ghế. Sau thời gian này, ghế có thể được mở lại cho khách hàng khác."),
                                List.of("Giá trị thanh toán bao gồm vé, combo và các khoản phí hiển thị trước khi xác nhận.",
                                        "Mã giảm giá hoặc ưu đãi chỉ áp dụng khi đáp ứng đủ điều kiện của từng chương trình.",
                                        "Hóa đơn hoặc thông tin giao dịch được ghi nhận theo dữ liệu khách hàng cung cấp.")),
                        new InfoSection("Chi tiết các hình thức thanh toán",
                                List.of("Website có thể hỗ trợ nhiều phương thức thanh toán tùy theo cấu hình hệ thống và đối tác thanh toán tại từng thời điểm."),
                                List.of("Thẻ ATM nội địa có Internet Banking.",
                                        "Thẻ quốc tế Visa, MasterCard, JCB nếu cổng thanh toán hỗ trợ.",
                                        "Ví điện tử hoặc mã QR theo đối tác được tích hợp.",
                                        "Thanh toán tại quầy đối với các giao dịch chưa thanh toán online.")),
                        new InfoSection("Danh sách thẻ được chấp nhận thanh toán trực tuyến",
                                List.of("Danh sách thẻ được chấp nhận phụ thuộc vào cổng thanh toán. Khách hàng nên đảm bảo thẻ còn hiệu lực, đủ hạn mức và đã bật tính năng thanh toán trực tuyến."),
                                List.of("Thẻ ATM nội địa từ các ngân hàng có liên kết Napas.",
                                        "Visa, MasterCard, JCB.",
                                        "Các loại thẻ hoặc ví khác theo thông báo của đối tác thanh toán."))
                )));
        pages.add(new InfoPage("privacy-policy", "/privacy-policy", "CHÍNH SÁCH BẢO MẬT", "Chính sách bảo mật thông tin cá nhân khách hàng",
                "Cách Beta Cinemas thu thập, sử dụng, lưu trữ và bảo vệ thông tin cá nhân của khách hàng.",
                List.of(
                        new InfoSection("Thông tin được thu thập",
                                List.of("Beta Cinemas chỉ thu thập các thông tin cần thiết để phục vụ đăng ký tài khoản, đặt vé, chăm sóc khách hàng, gửi liên hệ và xử lý giao dịch."),
                                List.of("Họ tên, email, số điện thoại và thông tin tài khoản.",
                                        "Thông tin giao dịch như phim, suất chiếu, ghế, combo và trạng thái thanh toán.",
                                        "Nội dung khách hàng gửi qua biểu mẫu liên hệ.")),
                        new InfoSection("Mục đích sử dụng",
                                List.of("Thông tin cá nhân được sử dụng để xác nhận giao dịch, hỗ trợ khách hàng, cải thiện dịch vụ và gửi thông báo liên quan đến quyền lợi của khách hàng."),
                                List.of()),
                        new InfoSection("Bảo vệ dữ liệu",
                                List.of("Beta Cinemas áp dụng các biện pháp quản trị phù hợp để hạn chế truy cập trái phép, thất lạc hoặc sử dụng sai mục đích dữ liệu khách hàng."),
                                List.of("Không bán thông tin cá nhân cho bên thứ ba.",
                                        "Chỉ chia sẻ dữ liệu khi cần thiết cho vận hành dịch vụ hoặc theo yêu cầu pháp luật.",
                                        "Khách hàng có thể liên hệ để yêu cầu kiểm tra hoặc cập nhật thông tin cá nhân."))
                )));
        pages.add(new InfoPage("faq", "/faq", "CÂU HỎI THƯỜNG GẶP", "Câu hỏi thường gặp",
                "Các thắc mắc phổ biến khi xem lịch chiếu, đặt vé, sử dụng combo và tham gia ưu đãi.",
                List.of(
                        new InfoSection("Tôi có cần tài khoản để đặt vé không?",
                                List.of("Khách hàng nên đăng nhập tài khoản để hệ thống lưu lịch sử giao dịch, hỗ trợ tra cứu vé và áp dụng quyền lợi thành viên nếu có."),
                                List.of()),
                        new InfoSection("Tôi có thể đổi ghế sau khi thanh toán không?",
                                List.of("Thông thường vé đã thanh toán không thể đổi ghế. Khách hàng cần kiểm tra kỹ sơ đồ ghế trước khi xác nhận giao dịch."),
                                List.of()),
                        new InfoSection("Combo có được đổi món không?",
                                List.of("Combo được bán theo cấu hình món ăn đã công bố. Việc đổi món chỉ áp dụng khi rạp có chính sách riêng hoặc món trong combo tạm hết."),
                                List.of()),
                        new InfoSection("Tại sao không áp dụng được khuyến mãi?",
                                List.of("Một số khuyến mãi có điều kiện về ngày chiếu, nhóm khách hàng, hạng thành viên, phương thức thanh toán hoặc không áp dụng đồng thời với ưu đãi khác."),
                                List.of()),
                        new InfoSection("Tôi cần hỗ trợ giao dịch thì liên hệ ở đâu?",
                                List.of("Khách hàng có thể gửi thông tin tại trang Liên hệ. Bộ phận quản lý sẽ tiếp nhận và xử lý trên hệ thống quản trị."),
                                List.of())
                )));
        pages.add(new InfoPage("partners", "/partners", "DÀNH CHO ĐỐI TÁC", "Dành cho đối tác",
                "Thông tin hợp tác truyền thông, quảng cáo, sự kiện và phát hành phim cùng Beta Cinemas.",
                List.of(
                        new InfoSection("Cơ hội hợp tác",
                                List.of("Beta Cinemas chào đón các đối tác trong lĩnh vực phát hành phim, truyền thông, nhãn hàng, trường học, doanh nghiệp và tổ chức sự kiện."),
                                List.of("Tổ chức suất chiếu doanh nghiệp hoặc suất chiếu học đường.",
                                        "Hợp tác truyền thông cho phim, thương hiệu hoặc sự kiện.",
                                        "Đặt quảng cáo tại rạp, trên website hoặc các kênh truyền thông liên quan.")),
                        new InfoSection("Thông tin cần cung cấp",
                                List.of("Để việc phản hồi nhanh chóng, đối tác nên gửi rõ tên đơn vị, người phụ trách, số điện thoại, email, mục tiêu hợp tác và thời gian dự kiến triển khai."),
                                List.of()),
                        new InfoSection("Kênh tiếp nhận",
                                List.of("Đối tác có thể gửi yêu cầu qua trang Liên hệ hoặc liên hệ trực tiếp hotline của rạp để được chuyển đến bộ phận phụ trách."),
                                List.of())
                )));
        pages.add(new InfoPage("cinema-rules", "/cinema-rules", "QUY ĐỊNH TẠI RẠP PHIM", "Những quy định tại rạp phim",
                "Quy định giúp đảm bảo an toàn, trật tự và trải nghiệm xem phim tốt cho mọi khách hàng.",
                List.of(
                        new InfoSection("Trước khi vào phòng chiếu",
                                List.of("Khách hàng cần kiểm tra vé, đúng rạp, phòng chiếu, suất chiếu và vị trí ghế. Nhân viên có quyền từ chối phục vụ nếu thông tin vé không hợp lệ."),
                                List.of("Có mặt trước giờ chiếu để ổn định chỗ ngồi.",
                                        "Xuất trình giấy tờ tùy thân khi phim có giới hạn độ tuổi.",
                                        "Không mang đồ ăn, thức uống bên ngoài vào rạp nếu rạp có quy định hạn chế.")),
                        new InfoSection("Trong phòng chiếu",
                                List.of("Khách hàng cần giữ trật tự, không ghi hình, không hút thuốc và hạn chế sử dụng thiết bị phát sáng gây ảnh hưởng đến người khác."),
                                List.of("Tắt chuông điện thoại hoặc chuyển sang chế độ im lặng.",
                                        "Ngồi đúng ghế đã chọn.",
                                        "Giữ vệ sinh chung và không làm hư hỏng tài sản rạp.")),
                        new InfoSection("An toàn và xử lý vi phạm",
                                List.of("Rạp có quyền mời khách hàng rời khỏi phòng chiếu nếu có hành vi gây rối, vi phạm bản quyền, không tuân thủ hướng dẫn an toàn hoặc ảnh hưởng đến trải nghiệm chung."),
                                List.of())
                )));
        return pages;
    }

    private static Map<String, InfoPage> buildInfoPageMap() {
        Map<String, InfoPage> pages = new LinkedHashMap<>();
        for (InfoPage page : INFO_PAGES) {
            pages.put(page.getKey(), page);
        }
        return pages;
    }

    public static class InfoPage {
        private final String key;
        private final String url;
        private final String menuTitle;
        private final String title;
        private final String summary;
        private final List<InfoSection> sections;

        public InfoPage(String key, String url, String menuTitle, String title, String summary, List<InfoSection> sections) {
            this.key = key;
            this.url = url;
            this.menuTitle = menuTitle;
            this.title = title;
            this.summary = summary;
            this.sections = sections;
        }

        public String getKey() {
            return key;
        }

        public String getUrl() {
            return url;
        }

        public String getMenuTitle() {
            return menuTitle;
        }

        public String getTitle() {
            return title;
        }

        public String getSummary() {
            return summary;
        }

        public List<InfoSection> getSections() {
            return sections;
        }
    }

    public static class InfoSection {
        private final String heading;
        private final List<String> paragraphs;
        private final List<String> bullets;

        public InfoSection(String heading, List<String> paragraphs, List<String> bullets) {
            this.heading = heading;
            this.paragraphs = paragraphs;
            this.bullets = bullets;
        }

        public String getHeading() {
            return heading;
        }

        public List<String> getParagraphs() {
            return paragraphs;
        }

        public List<String> getBullets() {
            return bullets;
        }
    }
}
