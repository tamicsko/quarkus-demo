package dev.benno.svc.transaction.resource;

import dev.benno.svc.transaction.api.TransactionApi;
import dev.benno.svc.transaction.api.model.*;
import dev.benno.svc.transaction.client.AccountServiceClient;
import dev.benno.svc.transaction.client.AccountServiceClient.BalanceOperationRequest;
import dev.benno.svc.transaction.entity.Transaction;
import dev.benno.svc.transaction.entity.Transaction.TransactionStatusEnum;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class TransactionResource implements TransactionApi {

    private static final Logger LOG = Logger.getLogger(TransactionResource.class);

    /**
     * OpenAPI kliens bekötés — ez a MicroProfile REST Client minta:
     * 1. A @RestClient annotáció injektálja a generált/kézi REST Client interface-t
     * 2. Az URL-t az application.properties-ből olvassa (configKey = "account-service-api")
     * 3. A hívás transzparens HTTP kérésként fut a háttérben
     */
    @RestClient
    AccountServiceClient accountService;

    // =========================================================================
    // QUERY végpontok
    // =========================================================================

    @Override
    public Response listTransactions(Long accountId) {
        List<Transaction> transactions;
        if (accountId != null) {
            LOG.debugf("Listing transactions for accountId=%d", accountId);
            transactions = Transaction.list(
                    "fromAccountId = ?1 or toAccountId = ?1 order by createdAt desc", accountId);
        } else {
            LOG.debug("Listing all transactions");
            transactions = Transaction.list("order by createdAt desc");
        }
        List<TransactionDto> dtos = transactions.stream().map(this::toDto).toList();
        return Response.ok(dtos).build();
    }

    @Override
    public Response getTransaction(Long id) {
        LOG.debugf("Getting transaction id=%d", id);
        Transaction tx = Transaction.findById(id);
        if (tx == null) {
            return notFound("Transaction not found: id=" + id);
        }
        return Response.ok(toDto(tx)).build();
    }

    @Override
    public Response getTransactionStatusByRef(UUID ref) {
        LOG.debugf("Getting transaction status for ref=%s", ref);
        Transaction tx = Transaction.find("transactionRef", ref.toString()).firstResult();
        if (tx == null) {
            return notFound("Transaction not found: ref=" + ref);
        }
        TransactionStatusDto statusDto = new TransactionStatusDto();
        statusDto.setTransactionRef(ref);
        statusDto.setStatus(TransactionStatus.fromValue(tx.status.name()));
        statusDto.setFailureReason(tx.failureReason);
        return Response.ok(statusDto).build();
    }

    // =========================================================================
    // ÁTUTALÁS — ez demonstrálja az OpenAPI kliens bekötést
    // =========================================================================

    @Override
    @Transactional
    public Response createTransaction(CreateTransactionRequest request) {
        LOG.infof("Creating transaction: from=%d, to=%d, amount=%s, currency=%s",
                request.getFromAccountId(), request.getToAccountId(),
                request.getAmount(), request.getCurrency());

        // Validáció
        if (request.getFromAccountId().equals(request.getToAccountId())) {
            return badRequest("Source and target account cannot be the same");
        }
        if (request.getAmount() <= 0) {
            return badRequest("Amount must be positive: " + request.getAmount());
        }

        // 1. Tranzakció létrehozása PENDING státusszal
        Transaction tx = new Transaction();
        tx.transactionRef = UUID.randomUUID().toString();
        tx.fromAccountId = request.getFromAccountId();
        tx.toAccountId = request.getToAccountId();
        tx.amount = BigDecimal.valueOf(request.getAmount());
        tx.currency = request.getCurrency();
        tx.status = TransactionStatusEnum.PENDING;
        tx.persist();

        LOG.infof("Transaction created: ref=%s, status=PENDING", tx.transactionRef);

        String reason = "Transfer " + tx.transactionRef;

        // 2. Debit a küldő számláról (Account Service hívás)
        try {
            LOG.infof("Calling Account Service: debit accountId=%d, amount=%s",
                    tx.fromAccountId, tx.amount);
            accountService.debitAccount(tx.fromAccountId,
                    new BalanceOperationRequest(request.getAmount(), reason));
            LOG.info("Debit successful");
        } catch (WebApplicationException e) {
            String errorMsg = "Debit failed: " + extractErrorMessage(e);
            LOG.errorf("Debit failed for transaction ref=%s: %s", tx.transactionRef, errorMsg);
            tx.status = TransactionStatusEnum.FAILED;
            tx.failureReason = errorMsg;
            tx.completedAt = LocalDateTime.now();
            return Response.created(URI.create("/api/transactions/" + tx.id))
                    .entity(toDto(tx))
                    .build();
        }

        // 3. Credit a fogadó számlára (Account Service hívás)
        try {
            LOG.infof("Calling Account Service: credit accountId=%d, amount=%s",
                    tx.toAccountId, tx.amount);
            accountService.creditAccount(tx.toAccountId,
                    new BalanceOperationRequest(request.getAmount(), reason));
            LOG.info("Credit successful");
        } catch (WebApplicationException e) {
            // KOMPENZÁCIÓ: a debit már megtörtént, vissza kell írni!
            String errorMsg = "Credit failed: " + extractErrorMessage(e);
            LOG.errorf("Credit failed for transaction ref=%s: %s. Initiating compensation.",
                    tx.transactionRef, errorMsg);

            try {
                LOG.infof("Compensation: crediting back accountId=%d, amount=%s",
                        tx.fromAccountId, tx.amount);
                accountService.creditAccount(tx.fromAccountId,
                        new BalanceOperationRequest(request.getAmount(),
                                "Compensation for failed transfer " + tx.transactionRef));
                LOG.info("Compensation successful");
            } catch (Exception compEx) {
                LOG.errorf(compEx, "CRITICAL: Compensation failed for transaction ref=%s!", tx.transactionRef);
                errorMsg += " | Compensation also failed: " + compEx.getMessage();
            }

            tx.status = TransactionStatusEnum.FAILED;
            tx.failureReason = errorMsg;
            tx.completedAt = LocalDateTime.now();
            return Response.created(URI.create("/api/transactions/" + tx.id))
                    .entity(toDto(tx))
                    .build();
        }

        // 4. Mindkettő sikeres → COMPLETED
        tx.status = TransactionStatusEnum.COMPLETED;
        tx.completedAt = LocalDateTime.now();
        LOG.infof("Transaction completed: ref=%s", tx.transactionRef);

        return Response.created(URI.create("/api/transactions/" + tx.id))
                .entity(toDto(tx))
                .build();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String extractErrorMessage(WebApplicationException e) {
        try {
            if (e.getResponse() != null && e.getResponse().hasEntity()) {
                return e.getResponse().readEntity(String.class);
            }
        } catch (Exception ignored) {}
        return e.getMessage();
    }

    private TransactionDto toDto(Transaction entity) {
        TransactionDto dto = new TransactionDto();
        dto.setId(entity.id);
        dto.setTransactionRef(UUID.fromString(entity.transactionRef));
        dto.setFromAccountId(entity.fromAccountId);
        dto.setToAccountId(entity.toAccountId);
        dto.setAmount(entity.amount.doubleValue());
        dto.setCurrency(entity.currency);
        dto.setStatus(TransactionStatus.fromValue(entity.status.name()));
        dto.setFailureReason(entity.failureReason);
        dto.setCreatedAt(toDate(entity.createdAt));
        dto.setCompletedAt(toDate(entity.completedAt));
        return dto;
    }

    private Date toDate(LocalDateTime ldt) {
        if (ldt == null) return null;
        return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
    }

    private Response notFound(String message) {
        LOG.warn(message);
        return Response.status(Response.Status.NOT_FOUND)
                .entity(errorResponse(message))
                .build();
    }

    private Response badRequest(String message) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(errorResponse(message))
                .build();
    }

    private ErrorResponse errorResponse(String message) {
        ErrorResponse error = new ErrorResponse();
        error.setMessage(message);
        error.setTimestamp(new Date());
        return error;
    }
}
