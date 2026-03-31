package dev.benno.backend.resource;

import dev.benno.backend.api.TransferApi;
import dev.benno.backend.api.model.*;
import dev.benno.backend.client.AccountServiceClient;
import dev.benno.backend.client.AccountServiceClient.AccountDto;
import dev.benno.backend.client.TransactionServiceClient;
import dev.benno.backend.client.TransactionServiceClient.TransactionDto;
import dev.benno.backend.client.TransactionServiceClient.TransactionStatusDto;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.Date;
import java.util.UUID;

@ApplicationScoped
public class TransferResource implements TransferApi {

    private static final Logger LOG = Logger.getLogger(TransferResource.class);

    @RestClient
    AccountServiceClient accountService;

    @RestClient
    TransactionServiceClient transactionService;

    // =========================================================================
    // POST /api/transfer — átutalás indítása
    // Orchestráció: Account Service (ellenőrzés) → Transaction Service
    // =========================================================================

    @Override
    public Response initiateTransfer(TransferRequest request) {
        LOG.infof("Initiating transfer: from=%d, to=%d, amount=%s, currency=%s",
                request.getFromAccountId(), request.getToAccountId(),
                request.getAmount(), request.getCurrency());

        // 1. Küldő számla ellenőrzése
        AccountDto fromAccount;
        try {
            fromAccount = accountService.getAccount(request.getFromAccountId());
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == 404) {
                return notFound("Source account not found: id=" + request.getFromAccountId());
            }
            throw e;
        }

        if (!"ACTIVE".equals(fromAccount.status)) {
            LOG.warnf("Transfer rejected: source account id=%d is %s",
                    fromAccount.id, fromAccount.status);
            return badRequest("Source account is not active: status=" + fromAccount.status);
        }

        if (fromAccount.balance < request.getAmount()) {
            LOG.warnf("Transfer rejected: insufficient funds. accountId=%d, balance=%s, requested=%s",
                    fromAccount.id, fromAccount.balance, request.getAmount());
            return badRequest("Insufficient funds: available=" + fromAccount.balance +
                    ", requested=" + request.getAmount());
        }

        // 2. Fogadó számla ellenőrzése
        AccountDto toAccount;
        try {
            toAccount = accountService.getAccount(request.getToAccountId());
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == 404) {
                return notFound("Target account not found: id=" + request.getToAccountId());
            }
            throw e;
        }

        if (!"ACTIVE".equals(toAccount.status)) {
            LOG.warnf("Transfer rejected: target account id=%d is %s",
                    toAccount.id, toAccount.status);
            return badRequest("Target account is not active: status=" + toAccount.status);
        }

        LOG.info("Transfer validation passed. Calling Transaction Service...");

        // 3. Tranzakció indítása a Transaction Service-en
        TransactionDto tx = transactionService.createTransaction(
                new TransactionServiceClient.CreateTransactionRequest(
                        request.getFromAccountId(),
                        request.getToAccountId(),
                        request.getAmount(),
                        request.getCurrency()));

        LOG.infof("Transfer initiated: ref=%s, status=%s", tx.transactionRef, tx.status);

        TransferResultDto result = new TransferResultDto();
        result.setTransactionRef(UUID.fromString(tx.transactionRef));
        result.setStatus(TransferResultDto.StatusEnum.fromValue(tx.status));
        result.setMessage(tx.failureReason != null ? tx.failureReason : "Transfer " + tx.status.toLowerCase());

        return Response.status(Response.Status.CREATED).entity(result).build();
    }

    // =========================================================================
    // GET /api/transfer/{ref}/status — átutalás státusz
    // =========================================================================

    @Override
    public Response getTransferStatus(UUID ref) {
        LOG.debugf("Getting transfer status: ref=%s", ref);

        TransactionStatusDto txStatus;
        try {
            txStatus = transactionService.getTransactionStatusByRef(ref);
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == 404) {
                return notFound("Transaction not found: ref=" + ref);
            }
            throw e;
        }

        TransferStatusDto result = new TransferStatusDto();
        result.setTransactionRef(UUID.fromString(txStatus.transactionRef));
        result.setStatus(TransferStatusDto.StatusEnum.fromValue(txStatus.status));
        result.setFailureReason(txStatus.failureReason);
        return Response.ok(result).build();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

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
