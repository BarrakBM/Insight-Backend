    package com.nbk.insights.exception

    import jakarta.persistence.EntityNotFoundException
    import org.springframework.http.HttpStatus
    import org.springframework.http.ResponseEntity
    import org.springframework.security.core.userdetails.UsernameNotFoundException
    import org.springframework.web.bind.annotation.ControllerAdvice
    import org.springframework.web.bind.annotation.ExceptionHandler
    import java.time.LocalDateTime

    data class ErrorResponse(
        val timestamp: LocalDateTime = LocalDateTime.now(),
        val status: Int,
//        val error: String,
        val message: String?,
    )

    @ControllerAdvice
    class GlobalExceptionHandler {

        @ExceptionHandler(IllegalArgumentException::class)
        fun handleIllegalArgument(e: IllegalArgumentException): ResponseEntity<ErrorResponse> {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, e.message)
        }

        @ExceptionHandler(IllegalStateException::class)
        fun handleIllegalState(e: IllegalStateException): ResponseEntity<ErrorResponse> {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, e.message)
        }

        @ExceptionHandler(IllegalAccessException::class)
        fun handleIllegalAccess(e: IllegalAccessException): ResponseEntity<ErrorResponse> {
            return buildErrorResponse(HttpStatus.FORBIDDEN, e.message)
        }

        @ExceptionHandler(NoSuchElementException::class)
        fun handleNoSuchElement(e: NoSuchElementException): ResponseEntity<ErrorResponse> {
            return buildErrorResponse(HttpStatus.NOT_FOUND, e.message)
        }

        @ExceptionHandler(Exception::class)
        fun handleGeneralException(e: Exception): ResponseEntity<ErrorResponse> {
            return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, e.message)
        }

        @ExceptionHandler(EntityNotFoundException::class)
        fun handleEntityNotFound(e: EntityNotFoundException): ResponseEntity<ErrorResponse> {
            return buildErrorResponse(HttpStatus.NOT_FOUND, e.message)
        }

        @ExceptionHandler(UsernameNotFoundException::class)
        fun handleUsernameNotFound(e: UsernameNotFoundException): ResponseEntity<ErrorResponse> {
            return buildErrorResponse(HttpStatus.NOT_FOUND, e.message)
        }

        private fun buildErrorResponse(status: HttpStatus, message: String?): ResponseEntity<ErrorResponse> {
            val response = ErrorResponse(
                status = status.value(),
//                error = status.reasonPhrase,
                message = message
            )
            return ResponseEntity(response, status)
        }
    }
