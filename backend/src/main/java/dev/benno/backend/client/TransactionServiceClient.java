package dev.benno.backend.client;

import dev.benno.backend.filter.CorrelationIdClientFilter;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@RegisterRestClient(configKey = "transaction-service-api")
@RegisterClientHeaders(CorrelationIdClientFilter.class)
@Path("/api/transactions")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface TransactionServiceClient {

    @GET
    List<TransactionDto> listTransactions(@QueryParam("accountId") Long accountId);

    @POST
    TransactionDto createTransaction(CreateTransactionRequest request);

    @GET
    @Path("/ref/{ref}/status")
    TransactionStatusDto getTransactionStatusByRef(@PathParam("ref") UUID ref);

    class TransactionDto {
        public Long id;
        public String transactionRef;
        public Long fromAccountId;
        public Long toAccountId;
        public Double amount;
        public String currency;
        public String status;
        public String failureReason;
        public Date createdAt;
        public Date completedAt;
    }

    class CreateTransactionRequest {
        public Long fromAccountId;
        public Long toAccountId;
        public Double amount;
        public String currency;

        public CreateTransactionRequest() {}

        public CreateTransactionRequest(Long fromAccountId, Long toAccountId,
                                        Double amount, String currency) {
            this.fromAccountId = fromAccountId;
            this.toAccountId = toAccountId;
            this.amount = amount;
            this.currency = currency;
        }
    }

    class TransactionStatusDto {
        public String transactionRef;
        public String status;
        public String failureReason;
    }
}
