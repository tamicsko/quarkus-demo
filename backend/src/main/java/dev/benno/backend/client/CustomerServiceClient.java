package dev.benno.backend.client;

import dev.benno.backend.filter.CorrelationIdClientFilter;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.Date;
import java.util.List;

@RegisterRestClient(configKey = "customer-service-api")
@RegisterClientHeaders(CorrelationIdClientFilter.class)
@Path("/api/customers")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface CustomerServiceClient {

    @GET
    List<CustomerDto> listCustomers();

    @GET
    @Path("/{id}")
    CustomerDto getCustomer(@PathParam("id") String id);

    @POST
    CustomerDto createCustomer(CreateCustomerRequest request);

    class CustomerDto {
        public String id;
        public String taxId;
        public String firstName;
        public String lastName;
        public String email;
        public String phone;
        public String status;
        public Date createdAt;
    }

    class CreateCustomerRequest {
        public String taxId;
        public String firstName;
        public String lastName;
        public String email;
        public String phone;

        public CreateCustomerRequest() {}

        public CreateCustomerRequest(String taxId, String firstName, String lastName,
                                     String email, String phone) {
            this.taxId = taxId;
            this.firstName = firstName;
            this.lastName = lastName;
            this.email = email;
            this.phone = phone;
        }
    }
}
