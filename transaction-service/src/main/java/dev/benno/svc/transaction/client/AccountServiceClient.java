package dev.benno.svc.transaction.client;

import dev.benno.svc.transaction.filter.CorrelationIdClientFilter;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * MicroProfile REST Client interface az Account Service hívásához.
 *
 * Az URL-t az application.properties-ből olvassa:
 *   quarkus.rest-client.account-service-api.url=http://localhost:8082
 *
 * Használat:
 *   @RestClient AccountServiceClient accountService;
 */
@RegisterRestClient(configKey = "account-service-api")
@RegisterClientHeaders(CorrelationIdClientFilter.class)
@Path("/api/accounts")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface AccountServiceClient {

    @POST
    @Path("/{id}/balance/credit")
    BalanceDto creditAccount(@PathParam("id") Long id, BalanceOperationRequest request);

    @POST
    @Path("/{id}/balance/debit")
    BalanceDto debitAccount(@PathParam("id") Long id, BalanceOperationRequest request);

    /**
     * Egyenleg válasz DTO — csak a kliens oldali deserializáláshoz szükséges mezők.
     */
    class BalanceDto {
        public Long accountId;
        public Double balance;
        public String currency;
    }

    /**
     * Egyenleg művelet kérés DTO.
     */
    class BalanceOperationRequest {
        public Double amount;
        public String reason;

        public BalanceOperationRequest() {}

        public BalanceOperationRequest(Double amount, String reason) {
            this.amount = amount;
            this.reason = reason;
        }
    }
}
