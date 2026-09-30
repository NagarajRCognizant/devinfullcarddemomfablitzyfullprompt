package com.carddemo.authorization.api;

import com.carddemo.authorization.api.dto.ApiError;
import com.carddemo.authorization.client.CardholderLookupException;
import com.carddemo.exception.RecordNotFoundException;
import com.carddemo.exception.ScreenValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps the outcomes the authorization screens reported in WS-MESSAGE onto HTTP status codes, using
 * the same convention as the account service so the frontend renders one error shape.
 *
 * <p>The message text is the source text; the status code is the only addition, because a REST
 * client cannot infer the outcome from a redisplayed map.
 */
@RestControllerAdvice
public class AuthorizationExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(AuthorizationExceptionHandler.class);

    /** The field edits of PROCESS-ENTER-KEY in COPAUS0C. */
    @ExceptionHandler(ScreenValidationException.class)
    public ResponseEntity<ApiError> validation(ScreenValidationException failure) {
        return ResponseEntity.badRequest()
                .body(new ApiError(failure.getStatus(), failure.getMessage()));
    }

    /** The NOTFND and GE paths: no cross reference, account, customer or authorization. */
    @ExceptionHandler(RecordNotFoundException.class)
    public ResponseEntity<ApiError> notFound(RecordNotFoundException failure) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("NOT_FOUND", failure.getMessage()));
    }

    /**
     * A failure of the remote read that replaced the VSAM reads. The source could not fail this
     * way, so it is reported as a dependency failure rather than decided on absent data.
     */
    @ExceptionHandler(CardholderLookupException.class)
    public ResponseEntity<ApiError> lookupFailed(CardholderLookupException failure) {
        LOG.error("Cardholder lookup failed", failure);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiError("CARDHOLDER_LOOKUP_FAILED", failure.getMessage()));
    }
}
