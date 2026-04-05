package dev.benno.backend.resource;

import dev.benno.backend.api.StatementApi;
import dev.benno.backend.api.model.*;
import dev.benno.backend.client.AccountServiceClient;
import dev.benno.backend.client.AccountServiceClient.AccountDto;
import dev.benno.backend.client.AccountServiceClient.BalanceHistoryDto;
import dev.benno.backend.client.TransactionServiceClient;
import dev.benno.backend.client.TransactionServiceClient.TransactionDto;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.*;
import java.util.stream.Stream;

@ApplicationScoped
public class StatementResource implements StatementApi {

    private static final Logger LOG = Logger.getLogger(StatementResource.class);

    @RestClient
    AccountServiceClient accountService;

    @RestClient
    TransactionServiceClient transactionService;

    @Override
    public Response getAccountStatement(UUID id) {
        LOG.infof("Getting account statement: accountId=%s", id);

        // 1. Számla adatok
        AccountDto account;
        try {
            account = accountService.getAccount(id.toString());
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == 404) {
                return notFound("Account not found: id=" + id);
            }
            throw e;
        }

        // 2. Átutalások (Transaction Service)
        List<TransactionDto> transactions = transactionService.listTransactions(id.toString());

        // 3. Egyenleg történet (Account Service) — befizetések/kifizetések
        List<BalanceHistoryDto> balanceHistory;
        try {
            balanceHistory = accountService.getBalanceHistory(id.toString());
        } catch (Exception e) {
            LOG.warnf("Failed to get balance history for account %s: %s", id, e.getMessage());
            balanceHistory = List.of();
        }

        // 4. Összeállítás — átutalások + befizetések/kifizetések együtt, idő szerint rendezve
        final String accountCurrency = account.currency;
        AccountStatementDto statement = new AccountStatementDto();
        statement.setAccountId(UUID.fromString(account.id));
        statement.setAccountNumber(account.accountNumber);
        statement.setBalance(account.balance);
        statement.setCurrency(account.currency);
        statement.setStatus(account.status);

        Stream<StatementTransactionDto> transferStream = transactions.stream()
                .map(tx -> toTransferTx(tx, id));

        // Balance history-ból csak a NEM transfer-ek (befizetés, kifizetés, stb.)
        // A transfer reason-ök "Transfer {uuid}" formátumúak
        Stream<StatementTransactionDto> balanceStream = balanceHistory.stream()
                .filter(bh -> bh.reason == null || !bh.reason.startsWith("Transfer "))
                .map(bh -> toBalanceHistoryTx(bh, accountCurrency));

        List<StatementTransactionDto> allTx = Stream.concat(transferStream, balanceStream)
                .sorted(Comparator.comparing(
                        (StatementTransactionDto t) -> t.getCreatedAt() != null ? t.getCreatedAt() : new Date(0))
                        .reversed())
                .toList();

        statement.setTransactions(allTx);

        LOG.infof("Account statement: accountId=%s, total entries=%d", id, allTx.size());
        return Response.ok(statement).build();
    }

    private StatementTransactionDto toTransferTx(TransactionDto tx, UUID accountId) {
        StatementTransactionDto dto = new StatementTransactionDto();
        dto.setTransactionRef(UUID.fromString(tx.transactionRef));

        String accountIdStr = accountId.toString();
        boolean isOutgoing = tx.fromAccountId != null && tx.fromAccountId.equals(accountIdStr);
        dto.setDirection(isOutgoing
                ? StatementTransactionDto.DirectionEnum.OUTGOING
                : StatementTransactionDto.DirectionEnum.INCOMING);
        String counterpartyId = isOutgoing ? tx.toAccountId : tx.fromAccountId;
        dto.setCounterpartyAccountId(counterpartyId != null ? UUID.fromString(counterpartyId) : null);

        dto.setAmount(tx.amount);
        dto.setCurrency(tx.currency);
        dto.setStatus(tx.status != null
                ? StatementTransactionDto.StatusEnum.fromValue(tx.status) : null);
        dto.setCreatedAt(tx.createdAt);
        return dto;
    }

    private StatementTransactionDto toBalanceHistoryTx(BalanceHistoryDto bh, String currency) {
        StatementTransactionDto dto = new StatementTransactionDto();
        dto.setTransactionRef(null);

        boolean isDeposit = bh.changeAmount != null && bh.changeAmount > 0;
        dto.setDirection(isDeposit
                ? StatementTransactionDto.DirectionEnum.DEPOSIT
                : StatementTransactionDto.DirectionEnum.WITHDRAWAL);
        dto.setCounterpartyAccountId(null);

        dto.setAmount(Math.abs(bh.changeAmount != null ? bh.changeAmount : 0));
        dto.setCurrency(currency);
        dto.setReason(bh.reason);
        dto.setStatus(StatementTransactionDto.StatusEnum.COMPLETED);
        dto.setCreatedAt(bh.createdAt);
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
