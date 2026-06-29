package com.group3.cinema.entity;

/*
 * Created on 2026-06-25: Customer contact request entity for cinema information pages.
 * Updated on 2026-06-25: Added email reply tracking fields for admin support workflow.
 * Created by: NinhDD - HE186113
 */

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "customer_contacts")
public class CustomerContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "NVARCHAR(120)")
    private String name;

    @Column(nullable = false, columnDefinition = "NVARCHAR(160)")
    private String email;

    @Column(columnDefinition = "NVARCHAR(30)")
    private String phone;

    @Column(nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "NVARCHAR(30)")
    private ContactStatus status = ContactStatus.IN_PROGRESS;

    @Column(columnDefinition = "NVARCHAR(180)")
    private String replySubject;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String replyMessage;

    private LocalDateTime repliedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        normalizeDefaults();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
        normalizeDefaults();
    }

    private void normalizeDefaults() {
        if (status == null) {
            status = ContactStatus.IN_PROGRESS;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public ContactStatus getStatus() {
        return status;
    }

    public void setStatus(ContactStatus status) {
        this.status = status;
    }

    public String getReplySubject() {
        return replySubject;
    }

    public void setReplySubject(String replySubject) {
        this.replySubject = replySubject;
    }

    public String getReplyMessage() {
        return replyMessage;
    }

    public void setReplyMessage(String replyMessage) {
        this.replyMessage = replyMessage;
    }

    public LocalDateTime getRepliedAt() {
        return repliedAt;
    }

    public void setRepliedAt(LocalDateTime repliedAt) {
        this.repliedAt = repliedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public enum ContactStatus {
        NEW("Đang xử lý", "bg-warning-subtle text-warning border border-warning-subtle"),
        IN_PROGRESS("Đang xử lý", "bg-warning-subtle text-warning border border-warning-subtle"),
        RESOLVED("Đã phản hồi", "bg-success-subtle text-success border border-success-subtle");

        private final String displayName;
        private final String cssClass;

        ContactStatus(String displayName, String cssClass) {
            this.displayName = displayName;
            this.cssClass = cssClass;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getCssClass() {
            return cssClass;
        }
    }
}
