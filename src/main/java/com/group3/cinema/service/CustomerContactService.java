package com.group3.cinema.service;

/*
 * Created on 2026-06-25: Business validation and workflow for customer contacts.
 * Updated on 2026-06-25: Added email reply workflow and AI-style reply draft generation.
 * Created by: NinhDD - HE186113
 */

import com.group3.cinema.entity.CustomerContact;
import com.group3.cinema.repository.CustomerContactRepository;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class CustomerContactService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile("^(\\+84|0)\\d{9,10}$");
    private static final Pattern NAME_PATTERN = Pattern.compile(
            "^[\\p{L}\\p{M}][\\p{L}\\p{M}\\s'.-]{1,79}$",
            Pattern.UNICODE_CHARACTER_CLASS
    );

    private final CustomerContactRepository customerContactRepository;
    private final JavaMailSender mailSender;

    public CustomerContactService(CustomerContactRepository customerContactRepository,
                                  JavaMailSender mailSender) {
        this.customerContactRepository = customerContactRepository;
        this.mailSender = mailSender;
    }

    public CustomerContact createContact(CustomerContact contact) {
        CustomerContact normalized = normalizeAndValidate(contact);
        normalized.setStatus(CustomerContact.ContactStatus.IN_PROGRESS);
        CustomerContact savedContact = customerContactRepository.save(normalized);
        sendReceivedNotification(savedContact);
        return savedContact;
    }

    public List<CustomerContact> getContacts(CustomerContact.ContactStatus status) {
        List<CustomerContact> contacts = customerContactRepository.findAllByOrderByCreatedAtDesc();
        if (status == null) {
            return contacts;
        }
        if (status == CustomerContact.ContactStatus.IN_PROGRESS || status == CustomerContact.ContactStatus.NEW) {
            return contacts.stream()
                    .filter(contact -> isProcessingStatus(contact.getStatus()))
                    .toList();
        }
        return contacts.stream()
                .filter(contact -> contact.getStatus() == status)
                .toList();
    }

    public CustomerContact updateStatus(Long id, CustomerContact.ContactStatus status) {
        CustomerContact contact = getContact(id);
        if (status != null
                && status != CustomerContact.ContactStatus.IN_PROGRESS
                && status != CustomerContact.ContactStatus.RESOLVED) {
            throw new IllegalArgumentException("Trạng thái liên hệ không hợp lệ.");
        }
        contact.setStatus(status == null ? CustomerContact.ContactStatus.IN_PROGRESS : status);
        return customerContactRepository.save(contact);
    }

    public CustomerContact sendAiReply(Long id) {
        CustomerContact contact = getContact(id);
        ReplyDraft draft = generateReplyDraft(contact);
        return sendReply(id, draft.getSubject(), draft.getBody());
    }

    public CustomerContact sendReply(Long id, String subject, String replyMessage) {
        CustomerContact contact = getContact(id);
        String normalizedSubject = trim(subject);
        String normalizedReply = trim(replyMessage);

        if (!EMAIL_PATTERN.matcher(trim(contact.getEmail())).matches()) {
            throw new IllegalArgumentException("Email khách hàng không hợp lệ, không thể gửi phản hồi.");
        }
        if (normalizedSubject.contains("\n") || normalizedSubject.contains("\r")) {
            throw new IllegalArgumentException("Tiêu đề email phản hồi không được xuống dòng.");
        }
        if (normalizedSubject.length() < 8 || normalizedSubject.length() > 160) {
            throw new IllegalArgumentException("Tiêu đề email phản hồi phải từ 8 đến 160 ký tự.");
        }
        if (normalizedReply.length() < 20 || normalizedReply.length() > 2000) {
            throw new IllegalArgumentException("Nội dung phản hồi phải từ 20 đến 2000 ký tự.");
        }
        if (containsUnsafeMarkup(normalizedReply)) {
            throw new IllegalArgumentException("Nội dung phản hồi không được chứa mã HTML/script.");
        }

        sendReplyEmail(contact, normalizedSubject, normalizedReply);
        contact.setReplySubject(normalizedSubject);
        contact.setReplyMessage(normalizedReply);
        contact.setRepliedAt(LocalDateTime.now());
        contact.setStatus(CustomerContact.ContactStatus.RESOLVED);
        return customerContactRepository.save(contact);
    }

    public ReplyDraft generateReplyDraft(CustomerContact contact) {
        String customerMessage = trim(contact.getMessage());
        String content = customerMessage.toLowerCase(Locale.ROOT);
        String topic = detectPrimaryTopic(content);
        List<String> answers = buildContextualAnswers(content);
        List<String> requiredDetails = buildRequiredDetails(content);

        if (answers.isEmpty()) {
            answers.add("Mình đã ghi nhận nội dung bạn gửi. Với thông tin hiện tại, yêu cầu này cần được kiểm tra theo dữ liệu vận hành thực tế của rạp trước khi kết luận. Bạn có thể phản hồi thêm chi tiết cụ thể hơn để Beta Cinemas hỗ trợ chính xác ngay trên email này.");
        }

        String detailInstruction = requiredDetails.isEmpty()
                ? "Nếu cần kiểm tra sâu hơn, bạn chỉ cần phản hồi email này với thông tin bổ sung mà bạn đang có."
                : "Để xử lý nhanh hơn, bạn vui lòng phản hồi email này kèm: " + String.join("; ", requiredDetails) + ".";

        String body = """
                Chào %s,

                Mình là trợ lý hỗ trợ khách hàng của Beta Cinemas. Mình đã đọc nội dung bạn gửi: "%s"

                %s

                %s

                Nếu câu trả lời này chưa đúng trọng tâm bạn cần, bạn chỉ cần phản hồi lại email này bằng một câu hỏi cụ thể hơn. Bộ phận chăm sóc khách hàng sẽ tiếp tục hỗ trợ trên cùng luồng email.

                Trân trọng,
                Bộ phận Chăm sóc khách hàng Beta Cinemas
                Hotline: 1900 636807
                """.formatted(
                contact.getName(),
                shorten(customerMessage, 220),
                String.join("\n\n", answers),
                detailInstruction
        );

        return new ReplyDraft(buildReplySubject(topic), body);
    }

    public void deleteContact(Long id) {
        customerContactRepository.delete(getContact(id));
    }

    public CustomerContact getContact(Long id) {
        if (id == null || id < 1) {
            throw new IllegalArgumentException("Mã liên hệ khách hàng không hợp lệ.");
        }
        return customerContactRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy liên hệ khách hàng."));
    }

    public long countByStatus(CustomerContact.ContactStatus status) {
        if (status == CustomerContact.ContactStatus.IN_PROGRESS || status == CustomerContact.ContactStatus.NEW) {
            return customerContactRepository.countByStatus(CustomerContact.ContactStatus.IN_PROGRESS)
                    + customerContactRepository.countByStatus(CustomerContact.ContactStatus.NEW);
        }
        return customerContactRepository.countByStatus(status);
    }

    private void sendReceivedNotification(CustomerContact contact) {
        String subject = "Beta Cinemas đã nhận thông tin liên hệ của bạn";
        String body = """
                Chào %s,

                Beta Cinemas đã nhận được thông tin liên hệ của bạn. Bộ phận chăm sóc khách hàng sẽ kiểm tra và phản hồi qua email này trong thời gian sớm nhất.

                Nội dung bạn đã gửi:
                %s

                Trân trọng,
                Bộ phận Chăm sóc khách hàng Beta Cinemas
                Hotline: 1900 636807
                """.formatted(contact.getName(), contact.getMessage());
        try {
            sendEmail(contact.getEmail(), subject, body);
        } catch (Exception e) {
            System.err.println("Không gửi được email xác nhận liên hệ: " + e.getMessage());
        }
    }

    private void sendReplyEmail(CustomerContact contact, String subject, String replyMessage) {
        String body = """
                %s

                ---
                Thông tin yêu cầu ban đầu:
                Khách hàng: %s
                Email: %s
                Điện thoại: %s

                Nội dung liên hệ:
                %s
                """.formatted(
                replyMessage,
                contact.getName(),
                contact.getEmail(),
                contact.getPhone() == null ? "Chưa cung cấp" : contact.getPhone(),
                contact.getMessage()
        );
        try {
            sendEmail(contact.getEmail(), subject, body);
        } catch (Exception e) {
            throw new IllegalArgumentException("Không gửi được email phản hồi: " + e.getMessage());
        }
    }

    private void sendEmail(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }

    private String detectPrimaryTopic(String content) {
        if (containsAny(content, "thanh toán", "trừ tiền", "chuyển khoản", "ngân hàng", "bank", "ví", "payos", "thẻ")) {
            return "payment";
        }
        if (containsAny(content, "giá vé", "bao nhiêu tiền", "vé bao nhiêu", "bảng giá")) {
            return "price";
        }
        if (containsAny(content, "đặt vé", "mua vé", "book vé", "booking", "cách mua")) {
            return "booking";
        }
        if (containsAny(content, "đổi vé", "hủy vé", "hoàn vé", "refund", "mã vé", "vé")) {
            return "ticket";
        }
        if (containsAny(content, "lịch chiếu", "suất chiếu", "phòng", "ghế", "giờ chiếu")) {
            return "showtime";
        }
        if (containsAny(content, "khuyến mãi", "ưu đãi", "voucher", "mã giảm", "giảm giá")) {
            return "promotion";
        }
        if (containsAny(content, "combo", "bắp", "nước", "đồ ăn", "món")) {
            return "combo";
        }
        if (containsAny(content, "đối tác", "hợp tác", "quảng cáo", "sự kiện", "doanh nghiệp")) {
            return "partner";
        }
        if (containsAny(content, "phàn nàn", "khiếu nại", "không hài lòng", "tệ", "lỗi", "chậm", "bực")) {
            return "complaint";
        }
        if (containsAny(content, "đăng nhập", "đăng ký", "mật khẩu", "tài khoản", "otp")) {
            return "account";
        }
        if (containsAny(content, "địa chỉ", "ở đâu", "bản đồ", "google map", "hotline", "liên hệ")) {
            return "location";
        }
        return "general";
    }

    private String buildReplySubject(String topic) {
        return switch (topic) {
            case "payment" -> "Beta Cinemas phản hồi về thanh toán";
            case "price" -> "Beta Cinemas phản hồi về giá vé";
            case "booking" -> "Beta Cinemas hướng dẫn đặt vé";
            case "ticket" -> "Beta Cinemas phản hồi về vé";
            case "showtime" -> "Beta Cinemas phản hồi về lịch chiếu";
            case "promotion" -> "Beta Cinemas phản hồi về ưu đãi";
            case "combo" -> "Beta Cinemas phản hồi về combo";
            case "partner" -> "Beta Cinemas phản hồi yêu cầu hợp tác";
            case "complaint" -> "Beta Cinemas ghi nhận phản hồi dịch vụ";
            case "account" -> "Beta Cinemas hỗ trợ tài khoản";
            case "location" -> "Beta Cinemas gửi thông tin liên hệ rạp";
            default -> "Beta Cinemas phản hồi yêu cầu hỗ trợ";
        };
    }

    private List<String> buildContextualAnswers(String content) {
        List<String> answers = new ArrayList<>();

        if (containsAny(content, "thanh toán", "trừ tiền", "chuyển khoản", "ngân hàng", "bank", "ví", "payos", "thẻ")) {
            answers.add("Về thanh toán: nếu tài khoản của bạn đã bị trừ tiền nhưng website chưa hiển thị vé, giao dịch cần được đối soát với cổng thanh toán. Trong thời gian đối soát, bạn không nên thanh toán lại cùng một đơn nếu hệ thống vẫn còn ghi nhận giao dịch đang xử lý.");
        }
        if (containsAny(content, "giá vé", "bao nhiêu tiền", "vé bao nhiêu", "bảng giá")) {
            answers.add("Về giá vé: giá có thể thay đổi theo phim, định dạng phòng chiếu, loại ghế, khung giờ, ngày chiếu và chương trình ưu đãi. Bạn nên xem giá cuối cùng ở bước chọn suất chiếu và ghế, vì đó là giá được hệ thống dùng để thanh toán.");
        }
        if (containsAny(content, "đặt vé", "mua vé", "book vé", "booking", "cách mua")) {
            answers.add("Về đặt vé: bạn chọn phim, chọn suất chiếu, chọn ghế còn trống, chọn combo nếu cần, sau đó kiểm tra lại thông tin trước khi thanh toán. Khi giao dịch thành công, thông tin vé sẽ được ghi nhận theo tài khoản hoặc thông tin liên hệ bạn cung cấp.");
        }
        if (containsAny(content, "đổi vé", "hủy vé", "hoàn vé", "refund", "mã vé", "vé")) {
            answers.add("Về đổi, hủy hoặc hoàn vé: vé đã thanh toán thường không được đổi/hủy nếu thông tin phim, giờ chiếu và ghế đã được khách xác nhận. Trường hợp lỗi phát sinh từ hệ thống hoặc rạp thay đổi suất chiếu, Beta Cinemas sẽ kiểm tra và hỗ trợ theo điều khoản giao dịch.");
        }
        if (containsAny(content, "lịch chiếu", "suất chiếu", "phòng", "ghế", "giờ chiếu", "2d", "3d")) {
            answers.add("Về lịch chiếu và ghế: lịch chiếu được sắp theo phim, phòng chiếu và định dạng chiếu. Nếu bạn không thấy phòng hoặc ghế mong muốn, có thể suất chiếu chưa mở bán, phòng đang bảo trì hoặc ghế đã được giữ bởi giao dịch khác.");
        }
        if (containsAny(content, "khuyến mãi", "ưu đãi", "voucher", "mã giảm", "giảm giá")) {
            answers.add("Về ưu đãi: mỗi chiến dịch có điều kiện riêng về thời gian áp dụng, nhóm khách hàng, hạng thành viên, số lượng vé, phương thức thanh toán và khả năng áp dụng đồng thời. Nếu mã ưu đãi không dùng được, nguyên nhân thường nằm ở một trong các điều kiện này.");
        }
        if (containsAny(content, "combo", "bắp", "nước", "đồ ăn", "món")) {
            answers.add("Về combo và món ăn: combo được tạo từ danh mục món đang bán tại rạp. Nếu một món tạm hết hoặc không còn bán, rạp có thể thay thế theo chính sách tại quầy hoặc cập nhật lại combo trên hệ thống.");
        }
        if (containsAny(content, "đăng nhập", "đăng ký", "mật khẩu", "tài khoản", "otp")) {
            answers.add("Về tài khoản: nếu bạn không đăng nhập được, hãy kiểm tra đúng email, mật khẩu và trạng thái tài khoản. Với quên mật khẩu hoặc OTP, bạn nên yêu cầu gửi lại mã và kiểm tra cả hộp thư rác/quảng cáo.");
        }
        if (containsAny(content, "bảo mật", "dữ liệu", "thông tin cá nhân", "riêng tư")) {
            answers.add("Về bảo mật thông tin: Beta Cinemas chỉ sử dụng thông tin cá nhân để phục vụ tài khoản, giao dịch, chăm sóc khách hàng và các nghiệp vụ cần thiết. Thông tin không được bán cho bên thứ ba.");
        }
        if (containsAny(content, "quy định", "độ tuổi", "cmnd", "cccd", "đồ ăn ngoài", "ghi hình", "hút thuốc")) {
            answers.add("Về quy định tại rạp: khách hàng cần xem đúng ghế, đúng suất, tuân thủ phân loại độ tuổi của phim, không ghi hình trong phòng chiếu và thực hiện theo hướng dẫn của nhân viên rạp.");
        }
        if (containsAny(content, "đối tác", "hợp tác", "quảng cáo", "sự kiện", "doanh nghiệp")) {
            answers.add("Về hợp tác: Beta Cinemas tiếp nhận đề xuất quảng cáo, suất chiếu doanh nghiệp, sự kiện, truyền thông và hợp tác phát hành phim. Nội dung phù hợp sẽ được chuyển đến bộ phận phụ trách đối tác để trao đổi chi tiết.");
        }
        if (containsAny(content, "địa chỉ", "ở đâu", "bản đồ", "google map", "hotline", "liên hệ")) {
            answers.add("Về thông tin rạp: bạn có thể xem địa chỉ và Google Map trực tiếp tại trang Liên hệ trên website. Hotline hỗ trợ hiện tại là 1900 636807.");
        }
        if (containsAny(content, "mất đồ", "thất lạc", "quên đồ", "nhặt được")) {
            answers.add("Về đồ thất lạc: bạn vui lòng cung cấp mô tả món đồ, suất chiếu, phòng chiếu, vị trí ghế và thời gian phát hiện. Rạp sẽ kiểm tra với bộ phận vận hành và khu vực phòng chiếu.");
        }
        if (containsAny(content, "lỗi", "không vào được", "không hiện", "treo", "lag", "chậm", "không nhận")) {
            answers.add("Về lỗi kỹ thuật: bạn nên thử tải lại trang, kiểm tra kết nối mạng và đăng nhập lại. Nếu lỗi vẫn còn, Beta Cinemas cần ảnh chụp màn hình, thời điểm lỗi và thao tác trước khi lỗi xảy ra để kiểm tra.");
        }
        if (containsAny(content, "phàn nàn", "khiếu nại", "không hài lòng", "tệ", "bực", "thái độ")) {
            answers.add("Về phản hồi dịch vụ: Beta Cinemas xin lỗi nếu trải nghiệm của bạn chưa tốt. Nội dung này sẽ được chuyển cho bộ phận vận hành để kiểm tra nhân sự, quy trình phục vụ hoặc tình trạng cơ sở vật chất liên quan.");
        }

        return answers;
    }

    private List<String> buildRequiredDetails(String content) {
        List<String> details = new ArrayList<>();
        if (containsAny(content, "thanh toán", "trừ tiền", "ngân hàng", "payos", "thẻ")) {
            details.add("mã giao dịch hoặc ảnh chụp giao dịch thanh toán");
        }
        if (containsAny(content, "vé", "đổi vé", "hủy vé", "hoàn vé", "mã vé")) {
            details.add("mã vé, tên phim, ngày giờ chiếu và số điện thoại đặt vé");
        }
        if (containsAny(content, "lịch chiếu", "suất chiếu", "phòng", "ghế")) {
            details.add("tên phim, rạp/phòng, ngày chiếu, giờ chiếu và vị trí ghế nếu có");
        }
        if (containsAny(content, "combo", "bắp", "nước", "món")) {
            details.add("tên combo hoặc món ăn, thời điểm mua và rạp phát sinh");
        }
        if (containsAny(content, "khuyến mãi", "ưu đãi", "voucher", "mã giảm")) {
            details.add("tên chương trình ưu đãi hoặc mã khuyến mãi bạn đã nhập");
        }
        if (containsAny(content, "đăng nhập", "đăng ký", "mật khẩu", "otp", "tài khoản")) {
            details.add("email tài khoản và ảnh chụp lỗi nếu có");
        }
        if (containsAny(content, "mất đồ", "thất lạc", "quên đồ")) {
            details.add("mô tả đồ thất lạc, phòng chiếu, ghế và thời gian xem phim");
        }
        if (containsAny(content, "lỗi", "không vào được", "không hiện", "treo", "lag")) {
            details.add("ảnh chụp màn hình lỗi, thiết bị sử dụng và thời điểm phát sinh");
        }
        if (details.isEmpty()) {
            details.add("thời điểm phát sinh và thông tin liên quan để rạp kiểm tra chính xác");
        }
        return details;
    }

    private boolean containsAny(String content, String... keywords) {
        for (String keyword : keywords) {
            if (content.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean isProcessingStatus(CustomerContact.ContactStatus status) {
        return status == CustomerContact.ContactStatus.IN_PROGRESS || status == CustomerContact.ContactStatus.NEW;
    }

    private String shorten(String value, int maxLength) {
        String normalized = trim(value).replaceAll("\\s+", " ");
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength - 3) + "...";
    }

    private CustomerContact normalizeAndValidate(CustomerContact contact) {
        if (contact == null) {
            throw new IllegalArgumentException("Thông tin liên hệ không hợp lệ.");
        }
        String name = trim(contact.getName()).replaceAll("\\s+", " ");
        String email = trim(contact.getEmail()).toLowerCase(Locale.ROOT);
        String phone = normalizePhone(contact.getPhone());
        String message = trim(contact.getMessage());

        if (name.length() < 2 || name.length() > 80) {
            throw new IllegalArgumentException("Tên khách hàng phải từ 2 đến 80 ký tự.");
        }
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("Tên khách hàng chỉ nên chứa chữ cái, khoảng trắng và dấu nối hợp lệ.");
        }
        if (email.length() > 160) {
            throw new IllegalArgumentException("Email liên hệ không được vượt quá 160 ký tự.");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Email liên hệ không đúng định dạng.");
        }
        if (!phone.isEmpty() && !PHONE_PATTERN.matcher(phone).matches()) {
            throw new IllegalArgumentException("Số điện thoại phải bắt đầu bằng 0 hoặc +84 và có độ dài hợp lệ.");
        }
        if (message.length() < 10 || message.length() > 1200) {
            throw new IllegalArgumentException("Nội dung liên hệ phải từ 10 đến 1200 ký tự.");
        }
        if (message.replaceAll("\\s+", "").length() < 8) {
            throw new IllegalArgumentException("Nội dung liên hệ cần mô tả rõ hơn vấn đề khách hàng gặp phải.");
        }
        if (containsUnsafeMarkup(message)) {
            throw new IllegalArgumentException("Nội dung liên hệ không được chứa mã HTML/script.");
        }

        contact.setName(name);
        contact.setEmail(email);
        contact.setPhone(phone.isEmpty() ? null : phone);
        contact.setMessage(message);
        return contact;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizePhone(String phone) {
        return trim(phone)
                .replace(" ", "")
                .replace("-", "")
                .replace(".", "")
                .replace("(", "")
                .replace(")", "");
    }

    private boolean containsUnsafeMarkup(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("<script")
                || lower.contains("</script")
                || lower.contains("javascript:")
                || lower.contains("onerror=")
                || lower.contains("onload=");
    }

    public static class ReplyDraft {
        private final String subject;
        private final String body;

        public ReplyDraft(String subject, String body) {
            this.subject = subject;
            this.body = body;
        }

        public String getSubject() {
            return subject;
        }

        public String getBody() {
            return body;
        }
    }
}
