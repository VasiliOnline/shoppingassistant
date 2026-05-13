// Last synced: 2025-11-23 18:38
package com.example.shoppingassistant.server.auth

import com.example.shoppingassistant.domain.auth.LoginUserUseCase
import com.example.shoppingassistant.domain.auth.LogoutUseCase
import com.example.shoppingassistant.domain.auth.RegisterUserUseCase
import com.example.shoppingassistant.domain.auth.AuthRepository
import com.example.shoppingassistant.domain.model.AuthError
import com.example.shoppingassistant.domain.model.AuthResult
import com.example.shoppingassistant.server.db.AuthUsersTable
import com.example.shoppingassistant.server.auth.AuthErrorResponse
import com.example.shoppingassistant.server.auth.AuthUserResponse
import com.example.shoppingassistant.server.auth.PasswordResetCompleteRequest
import com.example.shoppingassistant.server.auth.PasswordResetCompleteResponse
import com.example.shoppingassistant.server.auth.PasswordResetService
import com.example.shoppingassistant.server.auth.PasswordResetStartRequest
import com.example.shoppingassistant.server.auth.PasswordResetStartByPhoneRequest
import com.example.shoppingassistant.server.auth.PasswordResetStartResponse
import com.example.shoppingassistant.server.auth.PasswordResetTokenManager
import com.example.shoppingassistant.server.auth.RateLimiter
import com.example.shoppingassistant.server.auth.SessionManager
import com.example.shoppingassistant.server.auth.SessionStore
import com.example.shoppingassistant.server.auth.ChangePasswordRequest
import com.example.shoppingassistant.server.auth.PasswordHasher
import com.example.shoppingassistant.server.auth.toResponse
import com.example.shoppingassistant.server.auth.EmailVerificationService
import com.example.shoppingassistant.server.auth.EmailVerificationConfirmRequest
import com.example.shoppingassistant.server.auth.EmailVerificationStartResponse
import com.example.shoppingassistant.server.auth.AuthAuditService
import com.example.shoppingassistant.server.auth.ChangeEmailConfirmRequest
import com.example.shoppingassistant.server.auth.ChangeEmailService
import com.example.shoppingassistant.server.auth.ChangeEmailStartRequest
import com.example.shoppingassistant.server.auth.ChangePhoneConfirmRequest
import com.example.shoppingassistant.server.auth.ChangePhoneStartRequest
import com.example.shoppingassistant.server.auth.DeleteAccountResponse
import com.example.shoppingassistant.server.auth.DeleteAccountRequest
import com.example.shoppingassistant.server.auth.PhoneVerificationService
import com.example.shoppingassistant.server.auth.PhoneVerificationStartResult
import com.example.shoppingassistant.server.auth.PhoneVerificationConfirmRequest
import com.example.shoppingassistant.server.auth.AccountDeletionService
import com.example.shoppingassistant.server.auth.RestoreAccountRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.request.host
import org.koin.java.KoinJavaComponent
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import org.jetbrains.exposed.sql.and

/**
 * Корневой routing для эндпойнтов авторизации (/api/auth).
 *
 * Сейчас реализовано:
 * - POST /api/auth/register — регистрация;
 * - POST /api/auth/login — логин;
 * - POST /api/auth/logout — выход (инвалидация сессии по Bearer-токену);
 * - POST /api/auth/forgot-password — старт восстановления пароля;
 * - POST /api/auth/reset-password — завершение восстановления пароля.
 * - POST /api/auth/change-password — смена пароля по старому (для авторизованного пользователя).
 */
fun Route.authRoutes() {
    val auditLogger = LoggerFactory.getLogger("AuthAudit")
    val auditService: AuthAuditService =
        KoinJavaComponent.get(AuthAuditService::class.java)
    route("/api/auth") {

        // --- REGISTER ---

        post("/register") {
            val limiter: RateLimiter =
                KoinJavaComponent.get(RateLimiter::class.java)
            if (!limiter.allow(rateKey(call.request.host(), "/register"))) {
                call.respond(
                    status = HttpStatusCode.TooManyRequests,
                    message = AuthErrorResponse(
                        error = "RATE_LIMITED",
                        message = "Слишком много попыток регистрации. Попробуйте позже.",
                    ),
                )
                return@post
            }

            val request = runCatching {
                call.receive<AuthRegisterRequest>()
            }.getOrElse {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = AuthErrorResponse(
                        error = "BAD_REQUEST",
                        message = "Некорректное тело запроса регистрации",
                    ),
                )
                return@post
            }

            val normalizedEmail = request.email.trim().lowercase()
            if (!isAuthEmailValid(normalizedEmail)) {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = AuthErrorResponse(
                        error = "INVALID_EMAIL",
                        message = "Введите корректный email",
                        field = "email",
                    ),
                )
                return@post
            }
            if (!isAuthPasswordValid(request.password)) {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = AuthErrorResponse(
                        error = "INVALID_PASSWORD",
                        message = "Пароль должен содержать минимум 8 символов",
                        field = "password",
                    ),
                )
                return@post
            }

            val useCase: RegisterUserUseCase =
                KoinJavaComponent.get(RegisterUserUseCase::class.java)
            val sessionManager: SessionManager =
                KoinJavaComponent.get(SessionManager::class.java)

            val result = runCatching {
                useCase(
                    email = request.email,
                    password = request.password,
                    displayName = request.displayName,
                )
            }.getOrElse { throwable ->
                call.respond(
                    status = HttpStatusCode.InternalServerError,
                    message = AuthErrorResponse(
                        error = "REGISTER_FAILED",
                        message = throwable.message ?: "Не удалось выполнить регистрацию",
                    ),
                )
                return@post
            }

            when (result) {
                is AuthResult.Success -> {
                    val token = sessionManager.createSession(result.user.id)
                    auditLogger.info("register success userId=${result.user.id} email=${result.user.email}")
                    auditService.log(
                        event = "register_success",
                        userId = result.user.id,
                        email = result.user.email,
                        ip = call.request.headers["X-Forwarded-For"] ?: call.request.host(),
                        userAgent = call.request.headers["User-Agent"],
                    )
                    call.respond(
                        status = HttpStatusCode.Created,
                        message = result.user.toResponse(token),
                    )
                }

                is AuthResult.Error -> {
                    when (result.error) {
                        AuthError.EMAIL_ALREADY_EXISTS -> {
                            auditLogger.info("register conflict email=${request.email}")
                            call.respond(
                                status = HttpStatusCode.Conflict,
                                message = AuthErrorResponse(
                                    error = "EMAIL_ALREADY_EXISTS",
                                    message = "Пользователь с таким email уже существует",
                                    field = "email",
                                ),
                            )
                        }

                        AuthError.INVALID_CREDENTIALS -> {
                            auditLogger.info("register invalid_credentials email=${request.email}")
                            call.respond(
                                status = HttpStatusCode.BadRequest,
                                message = AuthErrorResponse(
                                    error = "INVALID_CREDENTIALS",
                                    message = "Некорректные учётные данные",
                                ),
                            )
                        }

                        AuthError.UNKNOWN -> {
                            call.respond(
                                status = HttpStatusCode.InternalServerError,
                                message = AuthErrorResponse(
                                    error = "REGISTER_UNKNOWN_ERROR",
                                    message = "Неизвестная ошибка регистрации",
                                ),
                            )
                        }
                    }
                }
        }
    }

    // --- CHANGE EMAIL (start) ---

    post("/change-email/start") {
        val limiter: RateLimiter = KoinJavaComponent.get(RateLimiter::class.java)
        if (!limiter.allow(rateKey(call.request.host(), "/change-email/start"))) {
            call.respond(
                status = HttpStatusCode.TooManyRequests,
                message = AuthErrorResponse(
                    error = "RATE_LIMITED",
                    message = "Слишком много попыток смены email. Попробуйте позже.",
                ),
            )
            return@post
        }

        val token = extractBearerToken(call.request.headers["Authorization"])
        if (token == null) {
            call.respond(HttpStatusCode.Unauthorized)
            return@post
        }

        val sessionManager: SessionManager =
            KoinJavaComponent.get(SessionManager::class.java)
        val userId = sessionManager.getUserId(token)
        if (userId == null) {
            call.respond(HttpStatusCode.Unauthorized)
            return@post
        }

        val request = runCatching {
            call.receive<ChangeEmailStartRequest>()
        }.getOrElse {
            call.respond(
                status = HttpStatusCode.BadRequest,
                message = AuthErrorResponse(
                    error = "BAD_REQUEST",
                    message = "Некорректное тело запроса смены email",
                ),
            )
            return@post
        }

        val normalizedEmail = request.newEmail.trim().lowercase()
        if (request.currentPassword.isBlank()) {
            call.respond(
                status = HttpStatusCode.BadRequest,
                message = AuthErrorResponse(
                    error = "BAD_REQUEST",
                    message = "Введите текущий пароль",
                    field = "currentPassword",
                ),
            )
            return@post
        }
        val passwordHasher: PasswordHasher = KoinJavaComponent.get(PasswordHasher::class.java)
        if (!verifyCurrentPassword(userId = userId, currentPassword = request.currentPassword, passwordHasher = passwordHasher)) {
            call.respond(
                status = HttpStatusCode.BadRequest,
                message = AuthErrorResponse(
                    error = "INVALID_CREDENTIALS",
                    message = "Текущий пароль неверен",
                    field = "currentPassword",
                ),
            )
            return@post
        }
        if (!isAuthEmailValid(normalizedEmail)) {
            call.respond(
                status = HttpStatusCode.BadRequest,
                message = AuthErrorResponse(
                    error = "INVALID_EMAIL",
                    message = "Введите корректный email",
                    field = "email",
                ),
            )
            return@post
        }
        val emailExists = transaction {
            AuthUsersTable
                .selectAll()
                .where {
                    (AuthUsersTable.email eq normalizedEmail) and (AuthUsersTable.isDeleted eq false)
                }
                .limit(1)
                .empty().not()
        }
        if (emailExists) {
            call.respond(
                status = HttpStatusCode.Conflict,
                message = AuthErrorResponse(
                    error = "EMAIL_ALREADY_EXISTS",
                    message = "Пользователь с таким email уже существует",
                    field = "email",
                ),
            )
            return@post
        }

        val service: ChangeEmailService = KoinJavaComponent.get(ChangeEmailService::class.java)
        runCatching { service.start(userId = userId, newEmail = normalizedEmail) }
            .onFailure {
                call.respond(
                    status = HttpStatusCode.InternalServerError,
                    message = AuthErrorResponse(
                        error = "CHANGE_EMAIL_FAILED",
                        message = it.message ?: "Не удалось инициировать смену email",
                    ),
                )
                return@post
            }

        auditService.log(
            event = "change_email_start",
            userId = userId,
            email = normalizedEmail,
            ip = call.request.headers["X-Forwarded-For"] ?: call.request.host(),
            userAgent = call.request.headers["User-Agent"],
        )

        call.respond(mapOf("status" to "OK"))
    }

    // --- CHANGE PHONE (start) ---

    post("/change-phone/start") {
        val limiter: RateLimiter = KoinJavaComponent.get(RateLimiter::class.java)
        if (!limiter.allow(rateKey(call.request.host(), "/change-phone/start"))) {
            call.respond(
                status = HttpStatusCode.TooManyRequests,
                message = AuthErrorResponse(
                    error = "RATE_LIMITED",
                    message = "Слишком много попыток смены телефона. Попробуйте позже.",
                ),
            )
            return@post
        }

        val token = extractBearerToken(call.request.headers["Authorization"])
        if (token == null) {
            call.respond(HttpStatusCode.Unauthorized)
            return@post
        }

        val sessionManager: SessionManager = KoinJavaComponent.get(SessionManager::class.java)
        val userId = sessionManager.getUserId(token)
        if (userId == null) {
            call.respond(HttpStatusCode.Unauthorized)
            return@post
        }

        val request = runCatching { call.receive<ChangePhoneStartRequest>() }
            .getOrElse {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = AuthErrorResponse(
                        error = "BAD_REQUEST",
                        message = "Некорректное тело запроса смены телефона",
                    ),
                )
                return@post
            }

        if (request.currentPassword.isBlank()) {
            call.respond(
                status = HttpStatusCode.BadRequest,
                message = AuthErrorResponse(
                    error = "BAD_REQUEST",
                    message = "Введите текущий пароль",
                    field = "currentPassword",
                ),
            )
            return@post
        }

        val normalizedPhone = normalizePhone(request.newPhone)
        if (!isPhoneValid(normalizedPhone)) {
            call.respond(
                status = HttpStatusCode.BadRequest,
                message = AuthErrorResponse(
                    error = "INVALID_PHONE",
                    message = "Введите корректный номер телефона",
                    field = "newPhone",
                ),
            )
            return@post
        }

        val passwordHasher: PasswordHasher = KoinJavaComponent.get(PasswordHasher::class.java)
        if (!verifyCurrentPassword(userId = userId, currentPassword = request.currentPassword, passwordHasher = passwordHasher)) {
            call.respond(
                status = HttpStatusCode.BadRequest,
                message = AuthErrorResponse(
                    error = "INVALID_CREDENTIALS",
                    message = "Текущий пароль неверен",
                    field = "currentPassword",
                ),
            )
            return@post
        }

        val row = transaction {
            AuthUsersTable
                .selectAll()
                .where { (AuthUsersTable.id eq userId) and (AuthUsersTable.isDeleted eq false) }
                .singleOrNull()
        }
        if (row == null) {
            call.respond(HttpStatusCode.Unauthorized)
            return@post
        }

        val currentPhone = row[AuthUsersTable.phone]
        if (currentPhone != null && normalizePhone(currentPhone) == normalizedPhone) {
            call.respond(
                status = HttpStatusCode.Conflict,
                message = AuthErrorResponse(
                    error = "PHONE_ALREADY_CURRENT",
                    message = if (row[AuthUsersTable.phoneVerifiedAt] != null) {
                        "Этот номер уже используется как основной."
                    } else {
                        "Этот номер уже указан в аккаунте. Подтвердите его как текущий телефон."
                    },
                    field = "newPhone",
                ),
            )
            return@post
        }

        transaction {
            AuthUsersTable.update({ AuthUsersTable.id eq userId }) {
                it[pendingPhone] = normalizedPhone
                it[pendingPhoneRequestedAt] = System.currentTimeMillis()
            }
        }

        val service: PhoneVerificationService = KoinJavaComponent.get(PhoneVerificationService::class.java)
        when (service.startPendingChange(userId)) {
            PhoneVerificationStartResult.Sent -> {
                auditService.log(
                    event = "change_phone_start",
                    userId = userId,
                    email = null,
                    ip = call.request.headers["X-Forwarded-For"] ?: call.request.host(),
                    userAgent = call.request.headers["User-Agent"],
                )
                call.respond(
                    status = HttpStatusCode.OK,
                    message = EmailVerificationStartResponse(status = "OK"),
                )
            }

            PhoneVerificationStartResult.RateLimited -> {
                call.respond(
                    status = HttpStatusCode.TooManyRequests,
                    message = AuthErrorResponse(
                        error = "RATE_LIMITED",
                        message = "Слишком много запросов. Попробуйте позже.",
                    ),
                )
            }

            PhoneVerificationStartResult.MissingPhone,
            PhoneVerificationStartResult.AlreadyVerified -> {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = AuthErrorResponse(
                        error = "PHONE_CHANGE_NOT_AVAILABLE",
                        message = "Не удалось подготовить смену телефона. Попробуйте снова.",
                    ),
                )
            }
        }
    }

    // --- CHANGE PHONE (confirm) ---

    post("/change-phone/confirm") {
        val request = runCatching { call.receive<ChangePhoneConfirmRequest>() }
            .getOrElse {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = AuthErrorResponse(
                        error = "BAD_REQUEST",
                        message = "Некорректное тело запроса подтверждения телефона",
                    ),
                )
                return@post
            }
        if (request.token.isBlank()) {
            call.respond(
                status = HttpStatusCode.BadRequest,
                message = AuthErrorResponse(
                    error = "BAD_REQUEST",
                    message = "Введите код подтверждения",
                    field = "token",
                ),
            )
            return@post
        }

        val service: PhoneVerificationService = KoinJavaComponent.get(PhoneVerificationService::class.java)
        val payload = runCatching { service.confirm(request.token) }.getOrNull()
        if (payload == null) {
            call.respond(
                status = HttpStatusCode.BadRequest,
                message = AuthErrorResponse(
                    error = "INVALID_VERIFY_TOKEN",
                    message = "Код подтверждения недействителен или уже истёк",
                ),
            )
            return@post
        }

        auditService.log(
            event = "change_phone_confirm",
            userId = payload.userId,
            email = null,
            ip = call.request.headers["X-Forwarded-For"] ?: call.request.host(),
            userAgent = call.request.headers["User-Agent"],
        )

        call.respond(
            status = HttpStatusCode.OK,
            message = mapOf("status" to "OK"),
        )
    }

    // --- CHANGE EMAIL (confirm) ---

    post("/change-email/confirm") {
        val request = runCatching {
            call.receive<ChangeEmailConfirmRequest>()
        }.getOrElse {
            call.respond(
                status = HttpStatusCode.BadRequest,
                message = AuthErrorResponse(
                    error = "BAD_REQUEST",
                    message = "Некорректное тело запроса подтверждения email",
                ),
            )
            return@post
        }
        if (request.token.isBlank()) {
            call.respond(
                status = HttpStatusCode.BadRequest,
                message = AuthErrorResponse(
                    error = "BAD_REQUEST",
                    message = "Введите код или токен подтверждения",
                    field = "token",
                ),
            )
            return@post
        }

        val service: ChangeEmailService = KoinJavaComponent.get(ChangeEmailService::class.java)
        val payload = service.confirm(request.token)
        if (payload == null) {
            call.respond(
                status = HttpStatusCode.BadRequest,
                message = AuthErrorResponse(
                    error = "INVALID_CHANGE_EMAIL_TOKEN",
                    message = "Некорректный или просроченный токен смены email",
                ),
            )
            return@post
        }

        val updateResult = runCatching {
            transaction {
                // Проверяем, что новый email ещё свободен и пользователь не удалён
                val exists = AuthUsersTable
                    .selectAll()
                    .where {
                        (AuthUsersTable.email eq payload.newEmail) and (AuthUsersTable.isDeleted eq false)
                    }
                    .empty().not()

                if (exists) return@transaction false

                val updatedRows = AuthUsersTable.update(
                    where = { AuthUsersTable.id eq payload.userId },
                ) {
                    it[email] = payload.newEmail
                    it[emailVerified] = false
                    it[emailVerifiedAt] = null
                }
                updatedRows > 0
            }
        }.getOrElse { false }

        if (!updateResult) {
            call.respond(
                status = HttpStatusCode.Conflict,
                message = AuthErrorResponse(
                    error = "EMAIL_ALREADY_EXISTS",
                    message = "Пользователь с таким email уже существует",
                ),
            )
            return@post
        }

        val sessionManager: SessionManager =
            KoinJavaComponent.get(SessionManager::class.java)
        val newToken = sessionManager.createSession(payload.userId)

        val authRepository: AuthRepository =
            KoinJavaComponent.get(AuthRepository::class.java)
        val user = authRepository.getUserById(payload.userId)
        if (user == null) {
            call.respond(HttpStatusCode.InternalServerError)
            return@post
        }

        auditService.log(
            event = "change_email_confirm",
            userId = payload.userId,
            email = payload.newEmail,
            ip = call.request.headers["X-Forwarded-For"] ?: call.request.host(),
            userAgent = call.request.headers["User-Agent"],
        )

        call.respond(
            status = HttpStatusCode.OK,
            message = user.toResponse(newToken),
        )
    }

    // --- DELETE ACCOUNT (soft delete) ---

    post("/delete-account") {
        val token = extractBearerToken(call.request.headers["Authorization"])
        if (token == null) {
            call.respond(HttpStatusCode.Unauthorized)
            return@post
        }

        val sessionManager: SessionManager =
            KoinJavaComponent.get(SessionManager::class.java)
        val userId = sessionManager.getUserId(token)
        if (userId == null) {
            call.respond(HttpStatusCode.Unauthorized)
            return@post
        }

        val request = runCatching { call.receive<DeleteAccountRequest>() }
            .getOrElse {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = AuthErrorResponse(
                        error = "BAD_REQUEST",
                        message = "Некорректное тело запроса удаления аккаунта",
                    ),
                )
                return@post
            }

        if (request.currentPassword.isBlank()) {
            call.respond(
                status = HttpStatusCode.BadRequest,
                message = AuthErrorResponse(
                    error = "BAD_REQUEST",
                    message = "Введите текущий пароль",
                    field = "currentPassword",
                ),
            )
            return@post
        }

        val passwordHasher: PasswordHasher = KoinJavaComponent.get(PasswordHasher::class.java)
        if (!verifyCurrentPassword(userId = userId, currentPassword = request.currentPassword, passwordHasher = passwordHasher)) {
            call.respond(
                status = HttpStatusCode.BadRequest,
                message = AuthErrorResponse(
                    error = "INVALID_CREDENTIALS",
                    message = "Текущий пароль неверен",
                    field = "currentPassword",
                ),
            )
            return@post
        }

        val deletionService: AccountDeletionService =
            KoinJavaComponent.get(AccountDeletionService::class.java)
        runCatching { deletionService.purgeExpired() }
        val schedule = runCatching { deletionService.schedule(userId) }.getOrNull()

        if (schedule == null) {
            call.respond(
                status = HttpStatusCode.InternalServerError,
                message = AuthErrorResponse(
                    error = "DELETE_FAILED",
                    message = "Не удалось удалить аккаунт",
                ),
            )
            return@post
        }

        sessionManager.invalidateAllForUser(userId)

        auditService.log(
            event = "delete_account",
            userId = userId,
            email = null,
            ip = call.request.headers["X-Forwarded-For"] ?: call.request.host(),
            userAgent = call.request.headers["User-Agent"],
        )

        call.respond(
            status = HttpStatusCode.OK,
            message = DeleteAccountResponse(
                status = "SCHEDULED",
                deleteAfter = schedule.deleteAfter,
                restoreToken = schedule.restoreToken,
            ),
        )
    }

        // --- RESTORE ACCOUNT ---

        post("/delete-account/restore") {
            val request = runCatching {
                call.receive<RestoreAccountRequest>()
            }.getOrElse {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = AuthErrorResponse(
                        error = "BAD_REQUEST",
                        message = "Некорректное тело запроса восстановления",
                    ),
                )
                return@post
            }

            val deletionService: AccountDeletionService =
                KoinJavaComponent.get(AccountDeletionService::class.java)
            val userId = runCatching { deletionService.restore(request.token) }.getOrNull()
            if (userId == null) {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = AuthErrorResponse(
                        error = "RESTORE_FAILED",
                        message = "Токен восстановления недействителен или протух",
                    ),
                )
                return@post
            }

            val authRepository: AuthRepository =
                KoinJavaComponent.get(AuthRepository::class.java)
            val user = authRepository.getUserById(userId)
            if (user == null) {
                call.respond(HttpStatusCode.Gone)
                return@post
            }

            val sessionManager: SessionManager =
                KoinJavaComponent.get(SessionManager::class.java)
            val newToken = sessionManager.createSession(userId)

            auditService.log(
                event = "restore_account",
                userId = userId,
                email = user.email,
                ip = call.request.headers["X-Forwarded-For"] ?: call.request.host(),
                userAgent = call.request.headers["User-Agent"],
            )

            call.respond(
                status = HttpStatusCode.OK,
                message = user.toResponse(newToken),
            )
        }

        // --- LOGIN ---

        post("/login") {
            val limiter: RateLimiter =
                KoinJavaComponent.get(RateLimiter::class.java)
            if (!limiter.allow(rateKey(call.request.host(), "/login"))) {
                call.respond(
                    status = HttpStatusCode.TooManyRequests,
                    message = AuthErrorResponse(
                        error = "RATE_LIMITED",
                        message = "Слишком много попыток входа. Попробуйте позже.",
                    ),
                )
                return@post
            }

            val request = runCatching {
                call.receive<AuthLoginRequest>()
            }.getOrElse {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = AuthErrorResponse(
                        error = "BAD_REQUEST",
                        message = "Некорректное тело запроса авторизации",
                    ),
                )
                return@post
            }

            val useCase: LoginUserUseCase =
                KoinJavaComponent.get(LoginUserUseCase::class.java)
            val sessionManager: SessionManager =
                KoinJavaComponent.get(SessionManager::class.java)

            val result = runCatching {
                useCase(
                    email = request.email,
                    password = request.password,
                )
            }.getOrElse { throwable ->
                call.respond(
                    status = HttpStatusCode.InternalServerError,
                    message = AuthErrorResponse(
                        error = "LOGIN_FAILED",
                        message = throwable.message ?: "Не удалось выполнить вход",
                    ),
                )
                return@post
            }

            when (result) {
                is AuthResult.Success -> {
                    val token = sessionManager.createSession(result.user.id)
                    auditLogger.info("login success userId=${result.user.id} email=${result.user.email}")
                    auditService.log(
                        event = "login_success",
                        userId = result.user.id,
                        email = result.user.email,
                        ip = call.request.headers["X-Forwarded-For"] ?: call.request.host(),
                        userAgent = call.request.headers["User-Agent"],
                    )
                    call.respond(
                        status = HttpStatusCode.OK,
                        message = result.user.toResponse(token),
                    )
                }

                is AuthResult.Error -> {
                    when (result.error) {
                        AuthError.INVALID_CREDENTIALS -> {
                            auditLogger.info("login invalid_credentials email=${request.email}")
                            call.respond(
                                status = HttpStatusCode.Unauthorized,
                                message = AuthErrorResponse(
                                    error = "INVALID_CREDENTIALS",
                                    message = "Неверный email или пароль",
                                ),
                            )
                        }

                        AuthError.EMAIL_ALREADY_EXISTS -> {
                            auditLogger.info("login email_already_exists email=${request.email}")
                            call.respond(
                                status = HttpStatusCode.BadRequest,
                                message = AuthErrorResponse(
                                    error = "EMAIL_ALREADY_EXISTS",
                                    message = "Email уже используется",
                                    field = "email",
                                ),
                            )
                        }

                        AuthError.UNKNOWN -> {
                            call.respond(
                                status = HttpStatusCode.InternalServerError,
                                message = AuthErrorResponse(
                                    error = "LOGIN_UNKNOWN_ERROR",
                                    message = "Неизвестная ошибка авторизации",
                                ),
                            )
                        }
                    }
                }
            }
        }

        // --- LOGOUT ---

        post("/logout") {
            val useCase: LogoutUseCase =
                KoinJavaComponent.get(LogoutUseCase::class.java)
            val sessionManager: SessionManager =
                KoinJavaComponent.get(SessionManager::class.java)

            val token = extractBearerToken(call.request.headers["Authorization"])
            if (token != null) {
                sessionManager.invalidate(token)
                auditLogger.info("logout token=$token")
                auditService.log(
                    event = "logout",
                    userId = sessionManager.getUserId(token),
                    ip = call.request.headers["X-Forwarded-For"] ?: call.request.host(),
                    userAgent = call.request.headers["User-Agent"],
                )
            }

            val error = runCatching { useCase() }.exceptionOrNull()

            if (error != null) {
                call.respond(
                    status = HttpStatusCode.InternalServerError,
                    message = AuthErrorResponse(
                        error = "LOGOUT_FAILED",
                        message = error.message ?: "Не удалось выполнить выход",
                    ),
                )
            } else {
                call.respond(
                    mapOf(
                        "status" to "OK",
                        "message" to "Выход выполнен",
                    ),
                )
            }
        }

        // --- FORGOT PASSWORD (start reset) ---

        post("/forgot-password") {
            val limiter: RateLimiter =
                KoinJavaComponent.get(RateLimiter::class.java)
            if (!limiter.allow(rateKey(call.request.host(), "/forgot-password"))) {
                call.respond(
                    status = HttpStatusCode.TooManyRequests,
                    message = AuthErrorResponse(
                        error = "RATE_LIMITED",
                        message = "Слишком много попыток восстановления. Попробуйте позже.",
                    ),
                )
                return@post
            }

            val request = runCatching {
                call.receive<PasswordResetStartRequest>()
            }.getOrElse {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = AuthErrorResponse(
                        error = "BAD_REQUEST",
                        message = "Некорректное тело запроса восстановления пароля",
                    ),
                )
                return@post
            }
            if (!isAuthEmailValid(request.email)) {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = AuthErrorResponse(
                        error = "INVALID_EMAIL",
                        message = "Введите корректный email",
                        field = "email",
                    ),
                )
                return@post
            }

            val service: PasswordResetService =
                KoinJavaComponent.get(PasswordResetService::class.java)

            val error = runCatching {
                service.startPasswordReset(request.email)
            }.exceptionOrNull()

            if (error != null) {
                auditLogger.warn("forgot_password error email=${request.email} err=${error.message}")
                call.respond(
                    status = HttpStatusCode.InternalServerError,
                    message = AuthErrorResponse(
                        error = "RESET_START_FAILED",
                        message = error.message
                            ?: "Не удалось инициировать восстановление пароля",
                    ),
                )
                return@post
            }

            // Даже если пользователя нет, отвечаем 200 OK без раскрытия деталей.
            auditLogger.info("forgot_password requested email=${request.email}")
            auditService.log(
                event = "forgot_password",
                email = request.email,
                ip = call.request.headers["X-Forwarded-For"] ?: call.request.host(),
                userAgent = call.request.headers["User-Agent"],
            )
            call.respond(
                status = HttpStatusCode.OK,
                message = PasswordResetStartResponse(status = "OK"),
            )
        }

        // --- FORGOT PASSWORD BY PHONE (start reset) ---

        post("/forgot-password/phone") {
            val limiter: RateLimiter =
                KoinJavaComponent.get(RateLimiter::class.java)
            if (!limiter.allow(rateKey(call.request.host(), "/forgot-password/phone"))) {
                call.respond(
                    status = HttpStatusCode.TooManyRequests,
                    message = AuthErrorResponse(
                        error = "RATE_LIMITED",
                        message = "Слишком много попыток восстановления. Попробуйте позже.",
                    ),
                )
                return@post
            }

            val request = runCatching {
                call.receive<PasswordResetStartByPhoneRequest>()
            }.getOrElse {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = AuthErrorResponse(
                        error = "BAD_REQUEST",
                        message = "Некорректное тело запроса восстановления по телефону",
                    ),
                )
                return@post
            }

            val normalizedPhone = normalizePhone(request.phone)
            if (!isPhoneValid(normalizedPhone)) {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = AuthErrorResponse(
                        error = "INVALID_PHONE",
                        message = "Некорректный номер телефона",
                        field = "phone",
                    ),
                )
                return@post
            }

            if (!limiter.allow("phone-reset:$normalizedPhone")) {
                call.respond(
                    status = HttpStatusCode.TooManyRequests,
                    message = AuthErrorResponse(
                        error = "RATE_LIMITED",
                        message = "Слишком много попыток. Попробуйте позже.",
                    ),
                )
                return@post
            }
            val service: PasswordResetService =
                KoinJavaComponent.get(PasswordResetService::class.java)

            val error = runCatching {
                service.startPasswordResetByPhone(request.phone)
            }.exceptionOrNull()

            if (error != null) {
                auditLogger.warn("forgot_password_phone error phone=${maskPhone(request.phone)} err=${error.message}")
                call.respond(
                    status = HttpStatusCode.InternalServerError,
                    message = AuthErrorResponse(
                        error = "RESET_START_FAILED",
                        message = error.message ?: "Не удалось инициировать восстановление пароля",
                    ),
                )
                return@post
            }

            auditLogger.info("forgot_password_phone requested phone=${maskPhone(request.phone)}")
            auditService.log(
                event = "forgot_password_phone",
                email = null,
                ip = call.request.headers["X-Forwarded-For"] ?: call.request.host(),
                userAgent = call.request.headers["User-Agent"],
            )
            call.respond(
                status = HttpStatusCode.OK,
                message = PasswordResetStartResponse(status = "OK"),
            )
        }

        // --- RESET PASSWORD (complete reset) ---

        post("/reset-password") {
            val limiter: RateLimiter =
                KoinJavaComponent.get(RateLimiter::class.java)
            if (!limiter.allow(rateKey(call.request.host(), "/reset-password"))) {
                call.respond(
                    status = HttpStatusCode.TooManyRequests,
                    message = AuthErrorResponse(
                        error = "RATE_LIMITED",
                        message = "Слишком много попыток смены пароля. Попробуйте позже.",
                    ),
                )
                return@post
            }

            val request = runCatching {
                call.receive<PasswordResetCompleteRequest>()
            }.getOrElse {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = AuthErrorResponse(
                        error = "BAD_REQUEST",
                        message = "Некорректное тело запроса смены пароля",
                    ),
                )
                return@post
            }
            if (request.token.isBlank()) {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = AuthErrorResponse(
                        error = "BAD_REQUEST",
                        message = "Введите код или токен восстановления",
                        field = "token",
                    ),
                )
                return@post
            }
            if (!isAuthPasswordValid(request.newPassword)) {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = AuthErrorResponse(
                        error = "INVALID_PASSWORD",
                        message = "Пароль должен содержать минимум 8 символов",
                        field = "newPassword",
                    ),
                )
                return@post
            }
            val service: PasswordResetService =
                KoinJavaComponent.get(PasswordResetService::class.java)

            val success = runCatching {
                service.resetPassword(
                    resetToken = request.token,
                    newPassword = request.newPassword,
                )
            }.getOrElse { throwable ->
                auditLogger.warn("reset_password error token=${request.token} err=${throwable.message}")
                call.respond(
                    status = HttpStatusCode.InternalServerError,
                    message = AuthErrorResponse(
                        error = "RESET_FAILED",
                        message = throwable.message
                            ?: "Не удалось завершить восстановление пароля",
                    ),
                )
                return@post
            }

            if (!success) {
                auditLogger.info("reset_password invalid token=${maskToken(request.token)}")
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = AuthErrorResponse(
                        error = "INVALID_RESET_TOKEN",
                        message = "Некорректный или просроченный токен восстановления",
                    ),
                )
            } else {
                auditLogger.info("reset_password success token=${request.token}")
                auditService.log(
                    event = "reset_password",
                    ip = call.request.headers["X-Forwarded-For"] ?: call.request.host(),
                    userAgent = call.request.headers["User-Agent"],
                )
                call.respond(
                    status = HttpStatusCode.OK,
                    message = PasswordResetCompleteResponse(
                        status = "OK",
                    ),
                )
            }
        }

        // --- CHANGE PASSWORD (authorized, old -> new) ---

        post("/change-password") {
            val limiter: RateLimiter =
                KoinJavaComponent.get(RateLimiter::class.java)
            if (!limiter.allow(rateKey(call.request.host(), "/change-password"))) {
                auditLogger.info("change_password rate_limited userId=unknown")
                call.respond(
                    status = HttpStatusCode.TooManyRequests,
                    message = AuthErrorResponse(
                        error = "RATE_LIMITED",
                        message = "Слишком много попыток смены пароля. Попробуйте позже.",
                    ),
                )
                return@post
            }

            val token = extractBearerToken(call.request.headers["Authorization"])
            if (token == null) {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            val sessionManager: SessionManager =
                KoinJavaComponent.get(SessionManager::class.java)
            val userId = sessionManager.getUserId(token)
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            val request = runCatching {
                call.receive<ChangePasswordRequest>()
            }.getOrElse {
                auditLogger.info("change_password bad_request userId=$userId")
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = AuthErrorResponse(
                        error = "BAD_REQUEST",
                        message = "Некорректное тело запроса смены пароля",
                    ),
                )
                return@post
            }
            if (request.oldPassword.isBlank()) {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = AuthErrorResponse(
                        error = "BAD_REQUEST",
                        message = "Введите текущий пароль",
                        field = "oldPassword",
                    ),
                )
                return@post
            }
            if (!isAuthPasswordValid(request.newPassword)) {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = AuthErrorResponse(
                        error = "INVALID_PASSWORD",
                        message = "Пароль должен содержать минимум 8 символов",
                        field = "newPassword",
                    ),
                )
                return@post
            }
            if (request.oldPassword == request.newPassword) {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = AuthErrorResponse(
                        error = "PASSWORD_REUSE",
                        message = "Новый пароль должен отличаться от текущего",
                        field = "newPassword",
                    ),
                )
                return@post
            }

            val passwordHasher: PasswordHasher =
                KoinJavaComponent.get(PasswordHasher::class.java)

            val success = runCatching {
                transaction {
                    val row = AuthUsersTable
                        .selectAll()
                        .where { AuthUsersTable.id eq userId }
                        .singleOrNull()
                        ?: return@transaction false

                    val stored = row[AuthUsersTable.password]
                    if (!passwordHasher.verify(request.oldPassword, stored)) {
                        return@transaction false
                    }

                    val newHash = passwordHasher.hash(request.newPassword)
                    AuthUsersTable.update({ AuthUsersTable.id eq userId }) {
                        it[password] = newHash
                    }
                    true
                }
            }.getOrElse { false }

            if (!success) {
                auditLogger.info("change_password invalid_old_password userId=$userId")
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = AuthErrorResponse(
                        error = "INVALID_CREDENTIALS",
                        message = "Неверный текущий пароль",
                    ),
                )
                return@post
            }

            // Инвалидируем старый токен и выдаём новый
            sessionManager.invalidateAllForUser(userId)
            val newToken = sessionManager.createSession(userId)

            val authRepository: AuthRepository =
                KoinJavaComponent.get(AuthRepository::class.java)
            val user = authRepository.getUserById(userId)
            if (user == null) {
                call.respond(HttpStatusCode.InternalServerError)
                return@post
            }

            call.respond(
                status = HttpStatusCode.OK,
                message = user.toResponse(newToken),
            )
            auditLogger.info("change_password success userId=$userId")
        }

        // --- EMAIL VERIFICATION START (authorized) ---
        post("/verify-email/start") {
            val limiter: RateLimiter = KoinJavaComponent.get(RateLimiter::class.java)
            val token = extractBearerToken(call.request.headers["Authorization"])
            if (token == null) {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            val sessionManager: SessionManager =
                KoinJavaComponent.get(SessionManager::class.java)
            val userId = sessionManager.getUserId(token)
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            val authRepository: AuthRepository =
                KoinJavaComponent.get(AuthRepository::class.java)
            val user = authRepository.getUserById(userId)
            if (user == null) {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            val verificationService: EmailVerificationService =
                KoinJavaComponent.get(EmailVerificationService::class.java)

            if (user.emailVerified) {
                call.respond(
                    status = HttpStatusCode.OK,
                    message = EmailVerificationStartResponse(status = "ALREADY_VERIFIED"),
                )
                return@post
            }

            if (!limiter.allow("verify-email:$userId")) {
                call.respond(
                    status = HttpStatusCode.TooManyRequests,
                    message = AuthErrorResponse(
                        error = "RATE_LIMITED",
                        message = "Слишком много запросов верификации. Попробуйте позже.",
                    ),
                )
                return@post
            }

            val sent = verificationService.startVerification(userId, user.email)
            if (!sent) {
                call.respond(
                    status = HttpStatusCode.TooManyRequests,
                    message = AuthErrorResponse(
                        error = "RATE_LIMITED",
                        message = "Слишком много запросов верификации. Попробуйте позже.",
                    ),
                )
                return@post
            }

            auditLogger.info("verify_email_start userId=$userId email=${maskEmail(user.email)}")
            auditService.log(
                event = "verify_email_start",
                userId = userId,
                email = user.email,
                ip = call.request.headers["X-Forwarded-For"] ?: call.request.host(),
                userAgent = call.request.headers["User-Agent"],
            )

            call.respond(
                status = HttpStatusCode.OK,
                message = EmailVerificationStartResponse(status = "OK"),
            )
        }

        // --- EMAIL VERIFICATION CONFIRM ---
        post("/verify-email/confirm") {
            val request = runCatching {
                call.receive<EmailVerificationConfirmRequest>()
            }.getOrElse {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = AuthErrorResponse(
                        error = "BAD_REQUEST",
                        message = "Некорректное тело запроса подтверждения email",
                    ),
                )
                return@post
            }
            if (request.token.isBlank()) {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = AuthErrorResponse(
                        error = "BAD_REQUEST",
                        message = "Введите код или токен подтверждения",
                        field = "token",
                    ),
                )
                return@post
            }

            val verificationService: EmailVerificationService =
                KoinJavaComponent.get(EmailVerificationService::class.java)

            val success = runCatching {
                verificationService.confirm(request.token)
            }.getOrElse {
                false
            }

            if (!success) {
                auditLogger.info("verify_email_confirm invalid token=${maskToken(request.token)}")
                auditService.log(
                    event = "verify_email_confirm_invalid",
                    email = null,
                    ip = call.request.headers["X-Forwarded-For"] ?: call.request.host(),
                    userAgent = call.request.headers["User-Agent"],
                )
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = AuthErrorResponse(
                        error = "INVALID_VERIFY_TOKEN",
                        message = "Некорректный или просроченный токен подтверждения",
                    ),
                )
            } else {
                auditLogger.info("verify_email_confirm success")
                auditService.log(
                    event = "verify_email_confirm_success",
                    email = null,
                    ip = call.request.headers["X-Forwarded-For"] ?: call.request.host(),
                    userAgent = call.request.headers["User-Agent"],
                )
                call.respond(
                    status = HttpStatusCode.OK,
                    message = mapOf("status" to "OK"),
                )
            }
        }

        // --- PHONE VERIFICATION START ---
        post("/verify-phone/start") {
            val limiter: RateLimiter =
                KoinJavaComponent.get(RateLimiter::class.java)
            if (!limiter.allow(rateKey(call.request.host(), "/verify-phone/start"))) {
                call.respond(
                    status = HttpStatusCode.TooManyRequests,
                    message = AuthErrorResponse(
                        error = "RATE_LIMITED",
                        message = "Слишком много запросов. Попробуйте позже.",
                    ),
                )
                return@post
            }

            val token = extractBearerToken(call.request.headers["Authorization"])
            if (token == null) {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            val sessionManager: SessionManager =
                KoinJavaComponent.get(SessionManager::class.java)
            val userId = sessionManager.getUserId(token)
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            val service: PhoneVerificationService =
                KoinJavaComponent.get(PhoneVerificationService::class.java)
            when (service.start(userId)) {
                PhoneVerificationStartResult.Sent -> {
                    auditLogger.info("verify_phone_start userId=$userId")
                    auditService.log(
                        event = "verify_phone_start",
                        userId = userId,
                        email = null,
                        ip = call.request.headers["X-Forwarded-For"] ?: call.request.host(),
                        userAgent = call.request.headers["User-Agent"],
                    )

                    call.respond(
                        status = HttpStatusCode.OK,
                        message = EmailVerificationStartResponse(status = "OK"),
                    )
                }

                PhoneVerificationStartResult.RateLimited -> {
                    call.respond(
                        status = HttpStatusCode.TooManyRequests,
                        message = AuthErrorResponse(
                            error = "RATE_LIMITED",
                            message = "Слишком много запросов. Попробуйте позже.",
                        ),
                    )
                }

                PhoneVerificationStartResult.MissingPhone -> {
                    call.respond(
                        status = HttpStatusCode.BadRequest,
                        message = AuthErrorResponse(
                            error = "PHONE_NOT_SET",
                            message = "Сначала добавьте телефон в профиль",
                            field = "phone",
                        ),
                    )
                }

                PhoneVerificationStartResult.AlreadyVerified -> {
                    call.respond(
                        status = HttpStatusCode.OK,
                        message = EmailVerificationStartResponse(status = "ALREADY_VERIFIED"),
                    )
                }
            }
        }

        // --- PHONE VERIFICATION CONFIRM ---
        post("/verify-phone/confirm") {
            val request = runCatching {
                call.receive<PhoneVerificationConfirmRequest>()
            }.getOrElse {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = AuthErrorResponse(
                        error = "BAD_REQUEST",
                        message = "Некорректное тело запроса подтверждения телефона",
                    ),
                )
                return@post
            }
            if (request.token.isBlank()) {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = AuthErrorResponse(
                        error = "BAD_REQUEST",
                        message = "Введите код подтверждения",
                        field = "token",
                    ),
                )
                return@post
            }

            val service: PhoneVerificationService =
                KoinJavaComponent.get(PhoneVerificationService::class.java)
            val payload = runCatching { service.confirm(request.token) }
                .getOrNull()

            if (payload == null) {
                auditLogger.info("verify_phone_confirm invalid token=${maskToken(request.token)}")
                auditService.log(
                    event = "verify_phone_confirm_invalid",
                    email = null,
                    ip = call.request.headers["X-Forwarded-For"] ?: call.request.host(),
                    userAgent = call.request.headers["User-Agent"],
                )
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = AuthErrorResponse(
                        error = "INVALID_VERIFY_TOKEN",
                        message = "Некорректный или просроченный токен подтверждения",
                    ),
                )
            } else {
                auditLogger.info("verify_phone_confirm success")
                auditService.log(
                    event = "verify_phone_confirm_success",
                    userId = payload.userId,
                    email = null,
                    ip = call.request.headers["X-Forwarded-For"] ?: call.request.host(),
                    userAgent = call.request.headers["User-Agent"],
                )
                call.respond(
                    status = HttpStatusCode.OK,
                    message = mapOf("status" to "OK"),
                )
            }
        }
    }
}

private fun verifyCurrentPassword(
    userId: Long,
    currentPassword: String,
    passwordHasher: PasswordHasher,
): Boolean = transaction {
    val row = AuthUsersTable
        .selectAll()
        .where { (AuthUsersTable.id eq userId) and (AuthUsersTable.isDeleted eq false) }
        .singleOrNull()
        ?: return@transaction false

    passwordHasher.verify(currentPassword, row[AuthUsersTable.password])
}

private fun extractBearerToken(header: String?): String? {
    if (header == null) return null
    if (!header.startsWith("Bearer ")) return null
    val token = header.removePrefix("Bearer ").trim()
    return token.takeIf { it.isNotEmpty() }
}

private fun rateKey(host: String?, path: String): String =
    "${host ?: "unknown"}:$path"

private fun isAuthEmailValid(email: String): Boolean =
    AUTH_EMAIL_REGEX.matches(email.trim())

private fun isAuthPasswordValid(password: String): Boolean =
    password.length >= MIN_AUTH_PASSWORD_LENGTH

private const val MIN_AUTH_PASSWORD_LENGTH = 8
private val AUTH_EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$".toRegex()
