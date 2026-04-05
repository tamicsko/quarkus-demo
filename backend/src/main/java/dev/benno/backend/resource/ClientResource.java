package dev.benno.backend.resource;

import dev.benno.backend.api.ClientApi;
import dev.benno.backend.api.model.*;
import dev.benno.backend.client.AccountServiceClient;
import dev.benno.backend.client.AccountServiceClient.AccountDto;
import dev.benno.backend.client.CustomerServiceClient;
import dev.benno.backend.client.CustomerServiceClient.CustomerDto;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ClientResource implements ClientApi {

    private static final Logger LOG = Logger.getLogger(ClientResource.class);

    @RestClient
    CustomerServiceClient customerService;

    @RestClient
    AccountServiceClient accountService;

    // =========================================================================
    // GET /api/clients — ügyfelek listázása
    // =========================================================================

    @Override
    public Response listClients() {
        LOG.debug("Listing all clients");

        List<CustomerDto> customers = customerService.listCustomers();
        List<AccountDto> allAccounts = accountService.listAccounts(null);

        List<ClientSummaryDto> result = customers.stream().map(c -> {
            long accountCount = allAccounts.stream()
                    .filter(a -> a.customerId != null && a.customerId.equals(c.id))
                    .count();
            return toSummaryDto(c, (int) accountCount);
        }).toList();

        LOG.debugf("Returning %d clients", result.size());
        return Response.ok(result).build();
    }

    // =========================================================================
    // GET /api/clients/{id} — ügyfél összesítő (adatok + számlák)
    // Orchestráció: Customer Service + Account Service
    // =========================================================================

    @Override
    public Response getClientDetail(UUID id) {
        LOG.infof("Getting client detail: customerId=%s", id);

        CustomerDto customer;
        try {
            customer = customerService.getCustomer(id.toString());
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == 404) {
                return notFound("Client not found: id=" + id);
            }
            throw e;
        }

        List<AccountDto> accounts = accountService.listAccounts(id.toString());

        LOG.infof("Client detail: customerId=%s, accounts=%d", id, accounts.size());
        return Response.ok(toDetailDto(customer, accounts)).build();
    }

    // =========================================================================
    // GET /api/clients/{id}/accounts — ügyfél számlái
    // =========================================================================

    @Override
    public Response getClientAccounts(UUID id) {
        LOG.debugf("Getting accounts for customerId=%s", id);
        List<AccountDto> accounts = accountService.listAccounts(id.toString());
        List<AccountInfoDto> result = accounts.stream().map(this::toAccountInfoDto).toList();
        return Response.ok(result).build();
    }

    // =========================================================================
    // POST /api/clients — új ügyfél regisztrálása + számlanyitás
    // Orchestráció: Customer Service → Account Service
    // =========================================================================

    @Override
    public Response registerClient(RegisterClientRequest request) {
        LOG.infof("Registering new client: taxId=%s, name=%s %s",
                request.getTaxId(), request.getFirstName(), request.getLastName());

        // 1. Ügyfél létrehozása
        CustomerDto customer;
        try {
            customer = customerService.createCustomer(
                    new CustomerServiceClient.CreateCustomerRequest(
                            request.getTaxId(),
                            request.getFirstName(),
                            request.getLastName(),
                            request.getEmail(),
                            request.getPhone()));
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == 409) {
                LOG.warnf("Duplicate taxId: %s", request.getTaxId());
                return Response.status(Response.Status.CONFLICT)
                        .entity(errorResponse("Customer already exists with taxId: " + request.getTaxId()))
                        .build();
            }
            throw e;
        }

        LOG.infof("Customer created: id=%s. Opening account...", customer.id);

        // 2. Számla nyitása
        AccountDto account;
        try {
            account = accountService.createAccount(
                    new AccountServiceClient.CreateAccountRequest(
                            request.getAccountNumber(),
                            customer.id,
                            request.getAccountType().value(),
                            request.getCurrency().value(),
                            0.0));
        } catch (WebApplicationException e) {
            LOG.errorf("Account creation failed for new customer id=%s: %s",
                    customer.id, e.getMessage());
            // Megjegyzés: itt lehetne kompenzáció (ügyfél törlése), de a terv szerint
            // csak logolunk + hibát jelzünk
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(errorResponse("Customer created (id=" + customer.id +
                            ") but account opening failed: " + e.getMessage()))
                    .build();
        }

        LOG.infof("Client registered: customerId=%s, accountId=%s", customer.id, account.id);
        return Response.status(Response.Status.CREATED)
                .entity(toDetailDto(customer, List.of(account)))
                .build();
    }

    // =========================================================================
    // POST /api/clients/{id}/accounts — új számla nyitás ügyfélnek
    // Orchestráció: Customer Service (ellenőrzés) → Account Service
    // =========================================================================

    @Override
    public Response openAccount(UUID id, OpenAccountRequest request) {
        LOG.infof("Opening account for customerId=%s", id);

        // 1. Ügyfél ellenőrzése
        CustomerDto customer;
        try {
            customer = customerService.getCustomer(id.toString());
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == 404) {
                return notFound("Client not found: id=" + id);
            }
            throw e;
        }

        if (!"ACTIVE".equals(customer.status)) {
            LOG.warnf("Cannot open account: customer id=%s is %s", id, customer.status);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(errorResponse("Customer is not active: status=" + customer.status))
                    .build();
        }

        // 2. Számla nyitása
        AccountDto account = accountService.createAccount(
                new AccountServiceClient.CreateAccountRequest(
                        request.getAccountNumber(),
                        id.toString(),
                        request.getAccountType().value(),
                        request.getCurrency().value(),
                        request.getInitialBalance() != null ? request.getInitialBalance() : 0.0));

        LOG.infof("Account opened: accountId=%s for customerId=%s", account.id, id);
        return Response.status(Response.Status.CREATED)
                .entity(toAccountInfoDto(account))
                .build();
    }

    // =========================================================================
    // Mapping
    // =========================================================================

    private ClientSummaryDto toSummaryDto(CustomerDto c, int accountCount) {
        ClientSummaryDto dto = new ClientSummaryDto();
        dto.setId(UUID.fromString(c.id));
        dto.setTaxId(c.taxId);
        dto.setFirstName(c.firstName);
        dto.setLastName(c.lastName);
        dto.setEmail(c.email);
        dto.setStatus(c.status != null ? ClientSummaryDto.StatusEnum.fromValue(c.status) : null);
        dto.setAccountCount(accountCount);
        return dto;
    }

    private ClientDetailDto toDetailDto(CustomerDto c, List<AccountDto> accounts) {
        ClientDetailDto dto = new ClientDetailDto();
        dto.setId(UUID.fromString(c.id));
        dto.setTaxId(c.taxId);
        dto.setFirstName(c.firstName);
        dto.setLastName(c.lastName);
        dto.setEmail(c.email);
        dto.setPhone(c.phone);
        dto.setStatus(c.status != null ? ClientDetailDto.StatusEnum.fromValue(c.status) : null);
        dto.setCreatedAt(c.createdAt);
        dto.setAccounts(accounts.stream().map(this::toAccountInfoDto).toList());
        return dto;
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
