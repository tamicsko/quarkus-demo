package dev.benno.backend.client;

import dev.benno.backend.filter.CorrelationIdClientFilter;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.Date;
import java.util.List;

@RegisterRestClient(configKey = "account-service-api")
@RegisterClientHeaders(CorrelationIdClientFilter.class)
@Path("/api/accounts")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface AccountServiceClient {

    @GET
    List<AccountDto> listAccounts(@QueryParam("customerId") String customerId);

    @GET
    @Path("/{id}")
    AccountDto getAccount(@PathParam("id") String id);

    @POST
    AccountDto createAccount(CreateAccountRequest request);

    @GET
    @Path("/{id}/balance")
    BalanceDto getBalance(@PathParam("id") String id);

    @POST
    @Path("/{id}/balance/credit")
    BalanceDto creditBalance(@PathParam("id") String id, BalanceOperationRequest request);

    @POST
    @Path("/{id}/balance/debit")
    BalanceDto debitBalance(@PathParam("id") String id, BalanceOperationRequest request);

    @GET
    @Path("/{id}/balance-history")
    List<BalanceHistoryDto> getBalanceHistory(@PathParam("id") String id);

    class BalanceOperationRequest {
        public Double amount;
        public String reason;

        public BalanceOperationRequest() {}

        public BalanceOperationRequest(Double amount, String reason) {
            this.amount = amount;
            this.reason = reason;
        }
    }

    class AccountDto {
        public String id;
        public String accountNumber;
        public String customerId;
        public String accountType;
        public Double balance;
        public String currency;
        public String status;
        public Date createdAt;
    }

    class CreateAccountRequest {
        public String accountNumber;
        public String customerId;
        public String accountType;
        public String currency;
        public Double initialBalance;

        public CreateAccountRequest() {}

        public CreateAccountRequest(String accountNumber, String customerId,
                                    String accountType, String currency, Double initialBalance) {
            this.accountNumber = accountNumber;
            this.customerId = customerId;
            this.accountType = accountType;
            this.currency = currency;
            this.initialBalance = initialBalance;
        }
    }

    class BalanceDto {
        public String accountId;
        public Double balance;
        public String currency;
    }

    class BalanceHistoryDto {
        public String id;
        public String accountId;
        public Double oldBalance;
        public Double newBalance;
        public Double changeAmount;
        public String reason;
        public Date createdAt;
    }
}
