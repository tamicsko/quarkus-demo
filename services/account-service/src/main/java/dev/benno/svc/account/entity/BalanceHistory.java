package dev.benno.svc.account.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "balance_history", schema = "account_svc")
public class BalanceHistory extends PanacheEntityBase {

    @Id
    public String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_balance_history_account"))
    public Account account;

    @Column(name = "old_balance", nullable = false, precision = 19, scale = 2)
    public BigDecimal oldBalance;

    @Column(name = "new_balance", nullable = false, precision = 19, scale = 2)
    public BigDecimal newBalance;

    @Column(name = "change_amount", nullable = false, precision = 19, scale = 2)
    public BigDecimal changeAmount;

    @Column(nullable = false)
    public String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    public LocalDateTime createdAt;

    @PrePersist
    void onPersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        this.createdAt = LocalDateTime.now();
    }
}
