package com.amerritt.tradejournal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String broker;

    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Account() {
    }

    public Account(User user, String broker, String baseCurrency) {
        this.user = user;
        this.broker = broker;
        this.baseCurrency = baseCurrency;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getBroker() {
        return broker;
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
