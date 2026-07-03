package com.group3.cinema.controller;

/*
 * Created on 2026-06-25: Admin contact inbox for customer requests from public pages.
 * Updated on 2026-06-25: Added email reply and AI-assisted reply actions.
 * Created by: NinhDD - HE186113
 */

import com.group3.cinema.entity.CustomerContact;
import com.group3.cinema.service.CustomerContactService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/contacts")
public class AdminContactController {

    private final CustomerContactService customerContactService;

    public AdminContactController(CustomerContactService customerContactService) {
        this.customerContactService = customerContactService;
    }

    @GetMapping
    public String listContacts(@RequestParam(value = "status", required = false) CustomerContact.ContactStatus status,
                               Model model) {
        List<CustomerContact> contacts = customerContactService.getContacts(status);
        model.addAttribute("contacts", contacts);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("statuses", List.of(CustomerContact.ContactStatus.IN_PROGRESS, CustomerContact.ContactStatus.RESOLVED));
        model.addAttribute("totalContacts", contacts.size());
        model.addAttribute("processingContacts", customerContactService.countByStatus(CustomerContact.ContactStatus.IN_PROGRESS));
        model.addAttribute("resolvedContacts", customerContactService.countByStatus(CustomerContact.ContactStatus.RESOLVED));
        addReplyDrafts(contacts, model);
        return "admin-contact-list";
    }

    @PostMapping("/{id}/reply/auto")
    public String sendAiReply(@PathVariable("id") Long id,
                              RedirectAttributes redirectAttributes) {
        try {
            customerContactService.sendAiReply(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã gửi phản hồi AI qua email khách hàng.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/contacts";
    }

    @PostMapping("/{id}/reply")
    public String sendManualReply(@PathVariable("id") Long id,
                                  @RequestParam("replySubject") String replySubject,
                                  @RequestParam("replyMessage") String replyMessage,
                                  RedirectAttributes redirectAttributes) {
        try {
            customerContactService.sendReply(id, replySubject, replyMessage);
            redirectAttributes.addFlashAttribute("successMessage", "Đã gửi email phản hồi cho khách hàng.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/contacts";
    }

    @PostMapping("/{id}/delete")
    public String deleteContact(@PathVariable("id") Long id,
                                RedirectAttributes redirectAttributes) {
        try {
            customerContactService.deleteContact(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa liên hệ khách hàng.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/contacts";
    }

    private void addReplyDrafts(List<CustomerContact> contacts, Model model) {
        Map<Long, String> suggestedSubjects = new LinkedHashMap<>();
        Map<Long, String> suggestedReplies = new LinkedHashMap<>();
        for (CustomerContact contact : contacts) {
            CustomerContactService.ReplyDraft draft = customerContactService.generateReplyDraft(contact);
            suggestedSubjects.put(contact.getId(), draft.getSubject());
            suggestedReplies.put(contact.getId(), draft.getBody());
        }
        model.addAttribute("suggestedSubjects", suggestedSubjects);
        model.addAttribute("suggestedReplies", suggestedReplies);
    }
}
