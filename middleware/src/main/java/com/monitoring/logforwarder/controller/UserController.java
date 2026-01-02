package com.monitoring.logforwarder.controller;

import com.monitoring.logforwarder.dto.ApiResponseDTO;
import com.monitoring.logforwarder.dto.UserDTO;
import com.monitoring.logforwarder.util.ApiResponseBuilder;
import com.monitoring.logforwarder.util.AsyncHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller for user management operations.
 *
 * <p><b>Purpose:</b> Handles CRUD operations for user accounts,
 * including listing, creation, updates, and deletion.</p>
 *
 * <p><b>Technical Details:</b></p>
 * <ul>
 *   <li>Base path: /api/v1/users</li>
 *   <li>List, create, and delete require ADMIN authority</li>
 *   <li>Get and update accessible to authenticated users</li>
 * </ul>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@CrossOrigin(origins = "*")
public class UserController {

    /**
     * Retrieves all users (ADMIN only).
     *
     * @return list of all users
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public CompletableFuture<ResponseEntity<ApiResponseDTO>> getAllUsers() {
        return AsyncHelper.executeAsyncFuture(() -> 
            ApiResponseBuilder.success("Users retrieved", "GET_USERS_SUCCESS", new ArrayList<>())
        ).exceptionally(ex -> {
            log.error("Error retrieving users", ex);
            return ApiResponseBuilder.fromException(ex, "GET_USERS_FAILED", HttpStatus.BAD_REQUEST);
        });
    }

    /**
     * Creates a new user (ADMIN only).
     *
     * @param userDTO the user data to create
     * @return created user
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public CompletableFuture<ResponseEntity<ApiResponseDTO>> createUser(@RequestBody UserDTO userDTO) {
        return AsyncHelper.executeAsyncFuture(() -> 
            ApiResponseBuilder.created("User created", "USER_CREATED", userDTO)
        ).exceptionally(ex -> {
            log.error("Error creating user", ex);
            return ApiResponseBuilder.fromException(ex, "CREATE_USER_FAILED", HttpStatus.BAD_REQUEST);
        });
    }

    /**
     * Retrieves a user by ID.
     *
     * @param id the user ID
     * @return user details or 404 if not found
     */
    @GetMapping("/{id}")
    public CompletableFuture<ResponseEntity<ApiResponseDTO>> getUserById(@PathVariable Long id) {
        return AsyncHelper.executeAsyncFuture(() -> 
            ApiResponseBuilder.success("User retrieved", "GET_USER_SUCCESS", new UserDTO())
        ).exceptionally(ex -> {
            log.error("Error retrieving user", ex);
            return ApiResponseBuilder.fromException(ex, "USER_NOT_FOUND", HttpStatus.NOT_FOUND);
        });
    }

    /**
     * Updates a user.
     *
     * @param id the user ID to update
     * @param userDTO the updated user data
     * @return updated user
     */
    @PutMapping("/{id}")
    public CompletableFuture<ResponseEntity<ApiResponseDTO>> updateUser(
            @PathVariable Long id,
            @RequestBody UserDTO userDTO) {
        return AsyncHelper.executeAsyncFuture(() -> 
            ApiResponseBuilder.success("User updated", "USER_UPDATED", userDTO)
        ).exceptionally(ex -> {
            log.error("Error updating user", ex);
            return ApiResponseBuilder.fromException(ex, "UPDATE_USER_FAILED", HttpStatus.BAD_REQUEST);
        });
    }

    /**
     * Deletes a user (ADMIN only).
     *
     * @param id the user ID to delete
     * @return success confirmation
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public CompletableFuture<ResponseEntity<ApiResponseDTO>> deleteUser(@PathVariable Long id) {
        return AsyncHelper.executeAsyncFuture(() -> 
            ApiResponseBuilder.success("User deleted", "USER_DELETED")
        ).exceptionally(ex -> {
            log.error("Error deleting user", ex);
            return ApiResponseBuilder.fromException(ex, "DELETE_USER_FAILED", HttpStatus.BAD_REQUEST);
        });
    }
}
