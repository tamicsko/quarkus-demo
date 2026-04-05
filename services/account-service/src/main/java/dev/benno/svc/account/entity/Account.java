package dev.benno.svc.account.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "account", schema = "account_svc")
public class Account extends PanacheEntityBase {

    @Id
    public String id;

    @Column(name = "account_number", nullable = false, unique = true, length = 34)
    public String accountNumber;

    @Column(name = "customer_id", nullable = false)
    public String customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    public AccountTypeEnum accountType;

    @Column(nullable = false, precision = 19, scale = 2)
    public BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    public CurrencyEnum currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public AccountStatusEnum status;

    @Column(name = "created_at", nullable = false, updatable = false)
    public LocalDateTime createdAt;

    @PrePersist
    void onPersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = AccountStatusEnum.ACTIVE;
        }
        if (this.balance == null) {
            this.balance = BigDecimal.ZERO;
        }
    }

    public enum AccountTypeEnum {
        CHECKING, SAVINGS
    }

    public enum AccountStatusEnum {
        ACTIVE, FROZEN, CLOSED
    }

    public enum CurrencyEnum {
        HUF, EUR, USD
    }
}
