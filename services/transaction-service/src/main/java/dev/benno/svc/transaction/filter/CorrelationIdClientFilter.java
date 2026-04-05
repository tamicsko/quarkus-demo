package dev.benno.svc.transaction.filter;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory;
import org.jboss.logging.MDC;

/**
 * Kimenő REST Client hívásokhoz: továbbítja a Correlation ID-t.
 * A bejövő kérés CorrelationIdFilter-je az MDC-be tette az ID-t,
 * ez a filter pedig kimásolja a kimenő kérés header-jébe.
 */
@ApplicationScoped
public class CorrelationIdClientFilter implements ClientHeadersFactory {

    @Override
    public MultivaluedMap<String, String> update(
            MultivaluedMap<String, String> incomingHeaders,
            MultivaluedMap<String, String> outgoingHeaders) {

        MultivaluedMap<String, String> result = new MultivaluedHashMap<>(outgoingHeaders);
        Object correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            result.putSingle(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId.toString());
        }
        return result;
    }
}
