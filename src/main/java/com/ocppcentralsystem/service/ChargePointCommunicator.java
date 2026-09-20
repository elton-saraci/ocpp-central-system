package com.ocppcentralsystem.service;

import com.ocppcentralsystem.exception.ChargePointCommunicationException;
import eu.chargetime.ocpp.IServerAPI;
import eu.chargetime.ocpp.NotConnectedException;
import eu.chargetime.ocpp.OccurenceConstraintException;
import eu.chargetime.ocpp.UnsupportedFeatureException;
import eu.chargetime.ocpp.model.Confirmation;
import eu.chargetime.ocpp.model.Request;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ExecutionException;

/**
 * The single place where a request is pushed to a connected charge point and its answer awaited.
 *
 * <p>The OCPP transport reports failures with checked exceptions and a future that can complete
 * exceptionally, which previously had to be caught - and was silently swallowed - at every call
 * site. They are all translated into {@link ChargePointCommunicationException} here, so callers
 * either get a confirmation or a failure the API can report.</p>
 */
@Component
@Slf4j
public class ChargePointCommunicator {

    private final IServerAPI jsonServer;

    /**
     * The transport is resolved on the first request instead of at startup. It is assembled from
     * the handler that answers requests - transport, core profile, handler, services, this class -
     * so asking for it here would close a construction cycle. Nothing is sent while the context is
     * still starting, which is why deferring it costs nothing.
     */
    public ChargePointCommunicator(@Lazy IServerAPI jsonServer) {
        this.jsonServer = jsonServer;
    }

    public <T extends Confirmation> T send(UUID websocketId, Request request, Class<T> expectedConfirmation) {
        String requestName = request.getClass().getSimpleName();
        try {
            log.info("Sending {} to sessionIndex {}", requestName, websocketId);
            Confirmation confirmation = jsonServer.send(websocketId, request).toCompletableFuture().get();

            if (!expectedConfirmation.isInstance(confirmation)) {
                throw new ChargePointCommunicationException("Charge point answered " + requestName + " with "
                        + (confirmation == null ? "no confirmation" : confirmation.getClass().getSimpleName()));
            }
            return expectedConfirmation.cast(confirmation);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ChargePointCommunicationException("Interrupted while waiting for the " + requestName + " confirmation", ex);
        } catch (ExecutionException ex) {
            throw new ChargePointCommunicationException(
                    "Charge point did not confirm " + requestName + ": " + rootCauseMessage(ex), ex);
        } catch (OccurenceConstraintException | UnsupportedFeatureException | NotConnectedException ex) {
            throw new ChargePointCommunicationException(
                    "Charge point is not reachable for " + requestName + ": " + ex.getMessage(), ex);
        }
    }

    private String rootCauseMessage(ExecutionException ex) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }
}
