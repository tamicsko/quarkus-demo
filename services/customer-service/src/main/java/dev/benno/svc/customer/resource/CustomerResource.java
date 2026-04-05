package dev.benno.svc.customer.resource;

import dev.benno.svc.customer.api.CustomerApi;
import dev.benno.svc.customer.api.model.*;
import dev.benno.svc.customer.entity.Customer;
import dev.benno.svc.customer.entity.Customer.CustomerStatusEnum;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import jakarta.enterprise.context.ApplicationScoped;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.jboss.logging.Logger;

@ApplicationScoped
public class CustomerResource implements CustomerApi {

    private static final Logger LOG = Logger.getLogger(CustomerResource.class);

    @Override
    public Response listCustomers() {
        LOG.debug("Listing all customers");
        List<CustomerDto> customers = Customer.<Customer>listAll()
                .stream()
                .map(this::toDto)
                .toList();
        LOG.debugf("Found %d customers", customers.size());
        return Response.ok(customers).build();
    }

    @Override
    public Response getCustomer(UUID id) {
        LOG.debugf("Getting customer id=%s", id);
        Customer customer = Customer.findById(id.toString());
        if (customer == null) {
            LOG.warnf("Customer not found: id=%s", id);
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(errorResponse("Customer not found: id=" + id))
                    .build();
        }
        return Response.ok(toDto(customer)).build();
    }

    @Override
    @Transactional
    public Response createCustomer(CreateCustomerRequest request) {
        LOG.infof("Creating customer: taxId=%s, name=%s %s",
                request.getTaxId(), request.getFirstName(), request.getLastName());

        // Ellenőrizzük, hogy nincs-e már ilyen adóazonosítóval ügyfél
        long existing = Customer.count("taxId", request.getTaxId());
        if (existing > 0) {
            LOG.warnf("Duplicate taxId: %s", request.getTaxId());
            return Response.status(Response.Status.CONFLICT)
                    .entity(errorResponse("Customer already exists with taxId: " + request.getTaxId()))
                    .build();
        }

        Customer customer = new Customer();
        customer.taxId = request.getTaxId();
        customer.firstName = request.getFirstName();
        customer.lastName = request.getLastName();
        customer.email = request.getEmail();
        customer.phone = request.getPhone();
        customer.persist();

        LOG.infof("Customer created: id=%s, taxId=%s", customer.id, customer.taxId);

        return Response.created(URI.create("/api/customers/" + customer.id))
                .entity(toDto(customer))
                .build();
    }

    @Override
    @Transactional
    public Response updateCustomer(UUID id, UpdateCustomerRequest request) {
        LOG.infof("Updating customer id=%s", id);
        Customer customer = Customer.findById(id.toString());
        if (customer == null) {
            LOG.warnf("Customer not found for update: id=%s", id);
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(errorResponse("Customer not found: id=" + id))
                    .build();
        }

        if (request.getFirstName() != null) {
            customer.firstName = request.getFirstName();
        }
        if (request.getLastName() != null) {
            customer.lastName = request.getLastName();
        }
        if (request.getEmail() != null) {
            customer.email = request.getEmail();
        }
        if (request.getPhone() != null) {
            customer.phone = request.getPhone();
        }

        LOG.infof("Customer updated: id=%s", customer.id);
        return Response.ok(toDto(customer)).build();
    }

    @Override
    @Transactional
    public Response updateCustomerStatus(UUID id, UpdateStatusRequest request) {
        LOG.infof("Updating customer status: id=%s, newStatus=%s", id, request.getStatus());
        Customer customer = Customer.findById(id.toString());
        if (customer == null) {
            LOG.warnf("Customer not found for status update: id=%s", id);
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(errorResponse("Customer not found: id=" + id))
                    .build();
        }

        customer.status = CustomerStatusEnum.valueOf(request.getStatus().name());

        LOG.infof("Customer status updated: id=%s, status=%s", customer.id, customer.status);
        return Response.ok(toDto(customer)).build();
    }

    // =========================================================================
    // Mapping helpers
    // =========================================================================

    private CustomerDto toDto(Customer entity) {
        CustomerDto dto = new CustomerDto();
        dto.setId(UUID.fromString(entity.id));
        dto.setTaxId(entity.taxId);
        dto.setFirstName(entity.firstName);
        dto.setLastName(entity.lastName);
        dto.setEmail(entity.email);
        dto.setPhone(entity.phone);
        dto.setStatus(CustomerStatus.fromValue(entity.status.name()));
        dto.setCreatedAt(toDate(entity.createdAt));
        return dto;
    }

    private Date toDate(LocalDateTime ldt) {
        if (ldt == null) return null;
        return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
    }

    private ErrorResponse errorResponse(String message) {
        ErrorResponse error = new ErrorResponse();
        error.setMessage(message);
        error.setTimestamp(new Date());
        return error;
    }
}
