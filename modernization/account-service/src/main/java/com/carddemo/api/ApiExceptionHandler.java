package com.carddemo.api;

import com.carddemo.api.dto.ErrorResponse;
import com.carddemo.domain.ScreenMessages;
import com.carddemo.exception.AdminOnlyOptionException;
import com.carddemo.exception.RecordNotFoundException;
import com.carddemo.exception.ScreenValidationException;
import com.carddemo.exception.StorageAccessException;
import com.carddemo.exception.UpdateFailedException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps the outcomes the legacy screens reported in WS-RETURN-MSG onto HTTP status codes.
 *
 * <p>The message text is the source text: the screens showed one line and highlighted the offending
 * fields, so the response carries the same line plus the field flags. The status code is the only
 * addition, because REST clients cannot infer an outcome from a redisplayed map.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Field edits of 1200-EDIT-MAP-INPUTS and 2210-EDIT-ACCOUNT. */
    @ExceptionHandler(ScreenValidationException.class)
    public ResponseEntity<ErrorResponse> validation(ScreenValidationException failure) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(failure.getStatus(), failure.getMessage(),
                        failure.getFieldFlags()));
    }

    /** The '10' file status paths: no cross reference, account or customer record. */
    @ExceptionHandler(RecordNotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(RecordNotFoundException failure) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NOT_FOUND", failure.getMessage(), failure.getFieldFlags()));
    }

    /**
     * A request body the mapper cannot read has no counterpart in the source, where the terminal
     * delivered a fixed length map. It is reported as an input error rather than a failure.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> unreadable(HttpMessageNotReadableException failure) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("MALFORMED_REQUEST", ScreenMessages.NO_SEARCH_CRITERIA_RECEIVED,
                        Map.of()));
    }

    /** COMEN01C refused the option for this user type. */
    @ExceptionHandler(AdminOnlyOptionException.class)
    public ResponseEntity<ErrorResponse> forbidden(AdminOnlyOptionException failure) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("NO_ACCESS", failure.getMessage(), Map.of()));
    }

    /** A rewrite failed after both records were locked; the unit of work was rolled back. */
    @ExceptionHandler(UpdateFailedException.class)
    public ResponseEntity<ErrorResponse> updateFailed(UpdateFailedException failure) {
        LOG.error("Rewrite failed after locking, unit of work rolled back", failure);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("UPDATE_FAILED", failure.getMessage(), Map.of()));
    }

    /**
     * The abend paths: any file status other than '00' or '10'. The legacy programs abend after
     * writing the file error message, which locally becomes a 500 with the same message.
     */
    @ExceptionHandler(StorageAccessException.class)
    public ResponseEntity<ErrorResponse> storage(StorageAccessException failure) {
        LOG.error("Storage access failure", failure);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("STORAGE_ERROR", failure.getMessage(), Map.of()));
    }
}
