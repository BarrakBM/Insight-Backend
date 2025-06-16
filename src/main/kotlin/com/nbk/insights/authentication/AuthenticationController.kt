package com.nbk.insights.authentication

import com.nbk.insights.authentication.jwt.JwtService
import com.nbk.insights.dto.authentication.LoginRequest
import com.nbk.insights.dto.authentication.RegisterRequest
import com.nbk.insights.repository.UserEntity
import com.nbk.insights.repository.UserRepository
import com.nbk.insights.service.UserService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/auth")
class AuthController(
    private val authenticationManager: AuthenticationManager,
    private val userDetailsService: UserDetailsService,
    private val userRepository: UserRepository,
    private val jwtService: JwtService,
    private val usersService: UserService,
    private val passwordEncoder: PasswordEncoder
) {

    @PostMapping("/login")
    fun login(@RequestBody authRequest: LoginRequest): TokenResponse {
        val authToken = UsernamePasswordAuthenticationToken(authRequest.username, authRequest.password)
        val authentication = authenticationManager.authenticate(authToken)

        if (authentication.isAuthenticated) {
            val userDetails = userDetailsService.loadUserByUsername(authRequest.username)
            val token = jwtService.generateToken(userDetails.username)
            return TokenResponse(token)
        } else {
            throw UsernameNotFoundException("Invalid user request!")
        }
    }

    @PostMapping("/register")
    fun register(@RequestBody request: RegisterRequest): ResponseEntity<Any> {
        val user = UserEntity(
            username = request.username,
            password = passwordEncoder.encode(request.password),
            fullName = request.fullName
        )
        usersService.create(user)
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }

    @GetMapping("/users")
    fun listUsers() = mapOf("users" to usersService.getAll())

    @GetMapping("/users/me")
    fun getCurrentUser(): ResponseEntity<UserEntity> {
        val username = SecurityContextHolder.getContext().authentication.name
        val user = userRepository.findByUsername(username)
            ?: throw UsernameNotFoundException("User not found with email: $username")
        return ResponseEntity.ok(user)
    }
}
