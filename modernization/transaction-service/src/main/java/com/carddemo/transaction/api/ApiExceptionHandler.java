package com.carddemo.transaction.api;

import com.carddemo.exception.RecordNotFoundException;
import com.carddemo.exception.ScreenValidationException;
import com.carddemo.exception.StorageAccessException;
import com.carddemo.exception.UpdateFailedException;
import com.carddemo.transaction.api.dto.ApiError;
import com.carddemo.transaction.client.AccountServiceException;
import com.carddemo.transaction.domain.TransactionMessages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns the screen outcomes into responses that carry the source's own message line.
 *
 * <p>The screens had one message field and no status codes, so the message text is what the React
 * screens display; the status code only tells a client which kind of outcome it was.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ScreenValidationException.class)
    public ResponseEntity<ApiError> onValidation(ScreenValidationException e) {
        return ResponseEntity.badRequest().body(new ApiError(e.getStatus(), e.getMessage()));
    }

    @ExceptionHandler(RecordNotFoundException.class)
    public ResponseEntity<ApiError> onNotFound(RecordNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(UpdateFailedException.class)
    public ResponseEntity<ApiError> onUpdateFailed(UpdateFailedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("UPDATE_FAILED", e.getMessage()));
    }

    @ExceptionHandler(StorageAccessException.class)
    public ResponseEntity<ApiError> onStorage(StorageAccessException e) {
        log.error("Storage access failed", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("STORAGE_ERROR", e.getMessage()));
    }

    /**
     * The account service could not be reached. The source read the account locally, so there is
     * no message for this case other than the lookup failure the screen would have shown.
     */
    @ExceptionHandler(AccountServiceException.class)
    public ResponseEntity<ApiError> onAccountService(AccountServiceException e) {
        log.error("The account service could not be reached", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("ACCOUNT_SERVICE_UNAVAILABLE",
                        TransactionMessages.UNABLE_TO_LOOKUP_ACCOUNT));
    }
}
