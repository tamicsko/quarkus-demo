package dev.benno.svc.account.resource;

import dev.benno.svc.account.api.AccountApi;
import dev.benno.svc.account.api.model.*;
import dev.benno.svc.account.entity.Account;
import dev.benno.svc.account.entity.Account.AccountStatusEnum;
import dev.benno.svc.account.entity.Account.AccountTypeEnum;
import dev.benno.svc.account.entity.Account.CurrencyEnum;
import dev.benno.svc.account.entity.BalanceHistory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.jboss.logging.Logger;

@ApplicationScoped
public class AccountResource implements AccountApi {

    private static final Logger LOG = Logger.getLogger(AccountResource.class);

    // =========================================================================
    // CRUD
    // =========================================================================

    @Override
    public Response listAccounts(UUID customerId) {
        List<Account> accounts;
        if (customerId != null) {
            LOG.debugf("Listing accounts for customerId=%s", customerId);
            accounts = Account.list("customerId", customerId.toString());
        } else {
            LOG.debug("Listing all accounts");
            accounts = Account.listAll();
        }
        List<AccountDto> dtos = accounts.stream().map(this::toDto).toList();
        LOG.debugf("Found %d accounts", dtos.size());
        return Response.ok(dtos).build();
    }

    @Override
    public Response getAccount(UUID id) {
        LOG.debugf("Getting account id=%s", id);
        Account account = Account.findById(id.toString());
        if (account == null) {
            return notFound("Account not found: id=" + id);
        }
        return Response.ok(toDto(account)).build();
    }

    @Override
    @Transactional
    public Response createAccount(CreateAccountRequest request) {
        LOG.infof("Creating account: number=%s, customerId=%s, type=%s, currency=%s",
                request.getAccountNumber(), request.getCustomerId(),
                request.getAccountType(), request.getCurrency());

        long existing = Account.count("accountNumber", request.getAccountNumber());
        if (existing > 0) {
            LOG.warnf("Duplicate accountNumber: %s", request.getAccountNumber());
            return Response.status(Response.Status.CONFLICT)
                    .entity(errorResponse("Account already exists with number: " + request.getAccountNumber()))
                    .build();
        }

        Account account = new Account();
        account.accountNumber = request.getAccountNumber();
        account.customerId = request.getCustomerId().toString();
        account.accountType = AccountTypeEnum.valueOf(request.getAccountType().name());
        account.currency = CurrencyEnum.valueOf(request.getCurrency().name());
        account.balance = request.getInitialBalance() != null
                ? BigDecimal.valueOf(request.getInitialBalance())
                : BigDecimal.ZERO;
        account.persist();

        if (account.balance.compareTo(BigDecimal.ZERO) > 0) {
            recordBalanceChange(account, BigDecimal.ZERO, account.balance, account.balance, "Initial deposit");
        }

        LOG.infof("Account created: id=%s, number=%s", account.id, account.accountNumber);
        return Response.created(URI.create("/api/accounts/" + account.id))
                .entity(toDto(account))
                .build();
    }

    @Override
    @Transactional
    public Response updateAccountStatus(UUID id, UpdateAccountStatusRequest request) {
        LOG.infof("Updating account status: id=%s, newStatus=%s", id, request.getStatus());
        Account account = Account.findById(id.toString());
        if (account == null) {
            return notFound("Account not found: id=" + id);
        }
        account.status = AccountStatusEnum.valueOf(request.getStatus().name());
        LOG.infof("Account status updated: id=%s, status=%s", account.id, account.status);
        return Response.ok(toDto(account)).build();
    }

    // =========================================================================
    // BALANCE — lekérés, credit, debit
    // =========================================================================

    @Override
    public Response getBalance(UUID id) {
        LOG.debugf("Getting balance for account id=%s", id);
        Account account = Account.findById(id.toString());
        if (account == null) {
            return notFound("Account not found: id=" + id);
        }
        return Response.ok(toBalanceDto(account)).build();
    }

    @Override
    @Transactional
    public Response creditAccount(UUID id, BalanceOperationRequest request) {
        LOG.infof("Credit account: id=%s, amount=%s, reason=%s",
                id, request.getAmount(), request.getReason());

        Account account = Account.findById(id.toString());
        if (account == null) {
            return notFound("Account not found: id=" + id);
        }

        if (account.status != AccountStatusEnum.ACTIVE) {
            LOG.warnf("Credit rejected: account id=%s is %s", id, account.status);
            return badRequest("Account is not active: status=" + account.status);
        }

        BigDecimal amount = BigDecimal.valueOf(request.getAmount());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return badRequest("Amount must be positive: " + amount);
        }

        BigDecimal oldBalance = account.balance;
        account.balance = account.balance.add(amount);

        recordBalanceChange(account, oldBalance, account.balance, amount, request.getReason());

        LOG.infof("Credit successful: account id=%s, oldBalance=%s, newBalance=%s",
                account.id, oldBalance, account.balance);
        return Response.ok(toBalanceDto(account)).build();
    }

    @Override
    @Transactional
    public Response debitAccount(UUID id, BalanceOperationRequest request) {
        LOG.infof("Debit account: id=%s, amount=%s, reason=%s",
                id, request.getAmount(), request.getReason());

        Account account = Account.findById(id.toString());
        if (account == null) {
            return notFound("Account not found: id=" + id);
        }

        if (account.status != AccountStatusEnum.ACTIVE) {
            LOG.warnf("Debit rejected: account id=%s is %s", id, account.status);
            return badRequest("Account is not active: status=" + account.status);
        }

        BigDecimal amount = BigDecimal.valueOf(request.getAmount());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return badRequest("Amount must be positive: " + amount);
        }

        if (account.balance.compareTo(amount) < 0) {
            LOG.warnf("Insufficient funds: account id=%s, requested=%s, available=%s",
                    account.id, amount, account.balance);
            return badRequest("Insufficient funds: requested=" + amount + ", available=" + account.balance);
        }

        BigDecimal oldBalance = account.balance;
        account.balance = account.balance.subtract(amount);

        recordBalanceChange(account, oldBalance, account.balance, amount.negate(), request.getReason());

        LOG.infof("Debit successful: account id=%s, oldBalance=%s, newBalance=%s",
                account.id, oldBalance, account.balance);
        return Response.ok(toBalanceDto(account)).build();
    }

    // =========================================================================
    // BALANCE HISTORY
    // =========================================================================

    @Override
    public Response getBalanceHistory(UUID id) {
        LOG.debugf("Getting balance history for account id=%s", id);
        Account account = Account.findById(id.toString());
        if (account == null) {
            return notFound("Account not found: id=" + id);
        }

        List<BalanceHistoryDto> history = BalanceHistory
                .list("account.id = ?1 order by createdAt desc", id.toString())
                .stream()
                .map(e -> toBalanceHistoryDto((BalanceHistory) e))
                .toList();

        return Response.ok(history).build();
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private void recordBalanceChange(Account account, BigDecimal oldBalance,
                                     BigDecimal newBalance, BigDecimal changeAmount, String reason) {
        BalanceHistory entry = new BalanceHistory();
        entry.account = account;
        entry.oldBalance = oldBalance;
        entry.newBalance = newBalance;
        entry.changeAmount = changeAmount;
        entry.reason = reason;
        entry.persist();
    }

    // =========================================================================
    // Mapping
    // =========================================================================

    private AccountDto toDto(Account entity) {
        AccountDto dto = new AccountDto();
        dto.setId(UUID.fromString(entity.id));
        dto.setAccountNumber(entity.accountNumber);
        dto.setCustomerId(UUID.fromString(entity.customerId));
        dto.setAccountType(AccountType.fromValue(entity.accountType.name()));
        dto.setBalance(entity.balance.doubleValue());
        dto.setCurrency(Currency.fromValue(entity.currency.name()));
        dto.setStatus(AccountStatus.fromValue(entity.status.name()));
        dto.setCreatedAt(toDate(entity.createdAt));
        return dto;
    }

    private BalanceDto toBalanceDto(Account entity) {
        BalanceDto dto = new BalanceDto();
        dto.setAccountId(UUID.fromString(entity.id));
        dto.setBalance(entity.balance.doubleValue());
        dto.setCurrency(Currency.fromValue(entity.currency.name()));
        return dto;
    }

    private BalanceHistoryDto toBalanceHistoryDto(BalanceHistory entity) {
        BalanceHistoryDto dto = new BalanceHistoryDto();
        dto.setId(Long.parseLong(entity.id));
        dto.setAccountId(UUID.fromString(entity.account.id));
        dto.setOldBalance(entity.oldBalance.doubleValue());
        dto.setNewBalance(entity.newBalance.doubleValue());
        dto.setChangeAmount(entity.changeAmount.doubleValue());
        dto.setReason(entity.reason);
        dto.setCreatedAt(toDate(entity.createdAt));
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
