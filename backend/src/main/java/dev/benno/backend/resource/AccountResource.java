package dev.benno.backend.resource;

import dev.benno.backend.api.AccountApi;
import dev.benno.backend.api.model.AccountInfoDto;
import dev.benno.backend.api.model.DepositRequest;
import dev.benno.backend.api.model.WithdrawRequest;
import dev.benno.backend.client.AccountServiceClient;
import dev.benno.backend.client.AccountServiceClient.AccountDto;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.Date;
import java.util.UUID;

@ApplicationScoped
public class AccountResource implements AccountApi {

    private static final Logger LOG = Logger.getLogger(AccountResource.class);

    @RestClient
    AccountServiceClient accountService;

    @Override
    public Response depositToAccount(UUID id, DepositRequest request) {
        LOG.infof("Deposit to account id=%s, amount=%.2f, reason=%s",
                id, request.getAmount(), request.getReason());

        if (request.getAmount() == null || request.getAmount() <= 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(errorResponse("Amount must be positive"))
                    .build();
        }

        try {
            accountService.creditBalance(id.toString(),
                    new AccountServiceClient.BalanceOperationRequest(
                            request.getAmount(),
                            request.getReason() != null ? request.getReason() : "Befizetés"));
        } catch (WebApplicationException e) {
            return handleAccountError(e, id, "Deposit");
        }

        AccountDto account = accountService.getAccount(id.toString());
        LOG.infof("Deposit successful: accountId=%s, newBalance=%.2f", id, account.balance);
        return Response.ok(toAccountInfoDto(account)).build();
    }

    @Override
    public Response withdrawFromAccount(UUID id, WithdrawRequest request) {
        LOG.infof("Withdraw from account id=%s, amount=%.2f, reason=%s",
                id, request.getAmount(), request.getReason());

        if (request.getAmount() == null || request.getAmount() <= 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(errorResponse("Amount must be positive"))
                    .build();
        }

        try {
            accountService.debitBalance(id.toString(),
                    new AccountServiceClient.BalanceOperationRequest(
                            request.getAmount(),
                            request.getReason() != null ? request.getReason() : "Kifizetés"));
        } catch (WebApplicationException e) {
            return handleAccountError(e, id, "Withdrawal");
        }

        AccountDto account = accountService.getAccount(id.toString());
        LOG.infof("Withdrawal successful: accountId=%s, newBalance=%.2f", id, account.balance);
        return Response.ok(toAccountInfoDto(account)).build();
    }

    private Response handleAccountError(WebApplicationException e, UUID id, String operation) {
        int status = e.getResponse().getStatus();
        if (status == 404) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(errorResponse("Account not found: id=" + id))
                    .build();
        }
        if (status == 400) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(errorResponse(operation + " failed: insufficient funds or account not active"))
                    .build();
        }
        throw e;
    }

    private AccountInfoDto toAccountInfoDto(AccountDto a) {
        AccountInfoDto dto = new AccountInfoDto();
        dto.setId(UUID.fromString(a.id));
        dto.setAccountNumber(a.accountNumber);
        dto.setAccountType(a.accountType != null ? AccountInfoDto.AccountTypeEnum.fromValue(a.accountType) : null);
        dto.setBalance(a.balance);
        dto.setCurrency(a.currency != null ? AccountInfoDto.CurrencyEnum.fromValue(a.currency) : null);
        dto.setStatus(a.status != null ? AccountInfoDto.StatusEnum.fromValue(a.status) : null);
        return dto;
    }

    private dev.benno.backend.api.model.ErrorResponse errorResponse(String message) {
        dev.benno.backend.api.model.ErrorResponse error = new dev.benno.backend.api.model.ErrorResponse();
        error.setMessage(message);
        error.setTimestamp(new Date());
        return error;
    }
}
