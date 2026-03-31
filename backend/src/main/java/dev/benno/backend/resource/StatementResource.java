package dev.benno.backend.resource;

import dev.benno.backend.api.StatementApi;
import dev.benno.backend.api.model.*;
import dev.benno.backend.client.AccountServiceClient;
import dev.benno.backend.client.AccountServiceClient.AccountDto;
import dev.benno.backend.client.TransactionServiceClient;
import dev.benno.backend.client.TransactionServiceClient.TransactionDto;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class StatementResource implements StatementApi {

    private static final Logger LOG = Logger.getLogger(StatementResource.class);

    @RestClient
    AccountServiceClient accountService;

    @RestClient
    TransactionServiceClient transactionService;

    // =========================================================================
    // GET /api/accounts/{id}/statement — számla kivonat
    // Orchestráció: Account Service (számla adatok) + Transaction Service (tranzakciók)
    // =========================================================================

    @Override
    public Response getAccountStatement(Long id) {
        LOG.infof("Getting account statement: accountId=%d", id);

        // 1. Számla adatok lekérése
        AccountDto account;
        try {
            account = accountService.getAccount(id);
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == 404) {
                return notFound("Account not found: id=" + id);
            }
            throw e;
        }

        // 2. Tranzakciók lekérése
        List<TransactionDto> transactions = transactionService.listTransactions(id);

        LOG.infof("Account statement: accountId=%d, transactions=%d", id, transactions.size());

        // 3. Összeállítás
        AccountStatementDto statement = new AccountStatementDto();
        statement.setAccountId(account.id);
        statement.setAccountNumber(account.accountNumber);
        statement.setBalance(account.balance);
        statement.setCurrency(account.currency);
        statement.setStatus(account.status);

        List<StatementTransactionDto> txList = transactions.stream()
                .map(tx -> toStatementTx(tx, id))
                .toList();
        statement.setTransactions(txList);

        return Response.ok(statement).build();
    }

    // =========================================================================
    // Mapping
    // =========================================================================

    private StatementTransactionDto toStatementTx(TransactionDto tx, Long accountId) {
        StatementTransactionDto dto = new StatementTransactionDto();
        dto.setTransactionRef(UUID.fromString(tx.transactionRef));

        // Az adott számla szemszögéből: bejövő vagy kimenő?
        boolean isOutgoing = tx.fromAccountId != null && tx.fromAccountId.equals(accountId);
        dto.setDirection(isOutgoing
                ? StatementTransactionDto.DirectionEnum.OUTGOING
                : StatementTransactionDto.DirectionEnum.INCOMING);
        dto.setCounterpartyAccountId(isOutgoing ? tx.toAccountId : tx.fromAccountId);

        dto.setAmount(tx.amount);
        dto.setCurrency(tx.currency);
        dto.setStatus(tx.status != null
                ? StatementTransactionDto.StatusEnum.fromValue(tx.status) : null);
        dto.setCreatedAt(tx.createdAt);
        return dto;
    }

    private Response notFound(String message) {
        LOG.warn(message);
        return Response.status(Response.Status.NOT_FOUND)
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
