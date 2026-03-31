package dev.benno.svc.customer.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer", schema = "customer_svc")
public class Customer extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "tax_id", nullable = false, unique = true, length = 20)
    public String taxId;

    @Column(name = "first_name", nullable = false)
    public String firstName;

    @Column(name = "last_name", nullable = false)
    public String lastName;

    @Column(nullable = false)
    public String email;

    @Column(length = 20)
    public String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public CustomerStatusEnum status;

    @Column(name = "created_at", nullable = false, updatable = false)
    public LocalDateTime createdAt;

    @PrePersist
    void onPersist() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = CustomerStatusEnum.ACTIVE;
        }
    }

    public enum CustomerStatusEnum {
        ACTIVE, SUSPENDED, CLOSED
    }
}
