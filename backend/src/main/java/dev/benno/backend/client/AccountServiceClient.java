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
    List<AccountDto> listAccounts(@QueryParam("customerId") Long customerId);

    @GET
    @Path("/{id}")
    AccountDto getAccount(@PathParam("id") Long id);

    @POST
    AccountDto createAccount(CreateAccountRequest request);

    @GET
    @Path("/{id}/balance")
    BalanceDto getBalance(@PathParam("id") Long id);

    class AccountDto {
        public Long id;
        public String accountNumber;
        public Long customerId;
        public String accountType;
        public Double balance;
        public String currency;
        public String status;
        public Date createdAt;
    }

    class CreateAccountRequest {
        public String accountNumber;
        public Long customerId;
        public String accountType;
        public String currency;
        public Double initialBalance;

        public CreateAccountRequest() {}

        public CreateAccountRequest(String accountNumber, Long customerId,
                                    String accountType, String currency, Double initialBalance) {
            this.accountNumber = accountNumber;
            this.customerId = customerId;
            this.accountType = accountType;
            this.currency = currency;
            this.initialBalance = initialBalance;
        }
    }

    class BalanceDto {
        public Long accountId;
        public Double balance;
        public String currency;
    }
}
