package dev.benno.svc.transaction.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transaction", schema = "tx_svc")
public class Transaction extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "transaction_ref", nullable = false, unique = true, length = 36)
    public String transactionRef;

    @Column(name = "from_account_id", nullable = false)
    public Long fromAccountId;

    @Column(name = "to_account_id", nullable = false)
    public Long toAccountId;

    @Column(nullable = false, precision = 19, scale = 2)
    public BigDecimal amount;

    @Column(nullable = false, length = 3)
    public String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public TransactionStatusEnum status;

    @Column(name = "failure_reason", length = 500)
    public String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    public LocalDateTime createdAt;

    @Column(name = "completed_at")
    public LocalDateTime completedAt;

    @PrePersist
    void onPersist() {
        this.createdAt = LocalDateTime.now();
    }

    public enum TransactionStatusEnum {
        PENDING, COMPLETED, FAILED, REVERSED
    }
}
