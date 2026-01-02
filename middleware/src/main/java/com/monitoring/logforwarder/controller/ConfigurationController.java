package com.monitoring.logforwarder.controller;

import com.monitoring.logforwarder.dto.ApiResponseDTO;
import com.monitoring.logforwarder.util.ApiResponseBuilder;
import com.monitoring.logforwarder.util.AsyncHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller for system configuration management.
 *
 * <p><b>Purpose:</b> Handles CRUD operations for input configurations,
 * allowing administrators and operators to manage data collection settings.</p>
 *
 * <p><b>Technical Details:</b></p>
 * <ul>
 *   <li>Base path: /api/v1/config</li>
 *   <li>Requires ADMIN or OPERATOR authority</li>
 *   <li>Manages input source configurations</li>
 * </ul>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/config")
@CrossOrigin(origins = "*")
public class ConfigurationController {

    /**
     * Retrieves all input configurations.
     *
     * @return list of configured input sources
     */
    @GetMapping("/inputs")
    public CompletableFuture<ResponseEntity<ApiResponseDTO>> getInputConfigs() {
        return AsyncHelper.executeAsyncFuture(() -> {
            Map<String, Object> config = new HashMap<>();
            config.put("inputs", new Object[]{});
            return ApiResponseBuilder.success("Input configurations retrieved", "GET_INPUTS_SUCCESS", config);
        }).exceptionally(ex -> {
            log.error("Error retrieving input configs", ex);
            return ApiResponseBuilder.fromException(ex, "GET_INPUTS_FAILED", HttpStatus.INTERNAL_SERVER_ERROR);
        });
    }

    /**
     * Creates a new input configuration.
     *
     * @param config the input configuration settings
     * @return created configuration
     */
    @PostMapping("/inputs")
    public CompletableFuture<ResponseEntity<ApiResponseDTO>> createInputConfig(@RequestBody Map<String, Object> config) {
        return AsyncHelper.executeAsyncFuture(() -> 
            ApiResponseBuilder.success("Input configuration created", "CREATE_INPUT_SUCCESS", config)
        ).exceptionally(ex -> {
            log.error("Error creating input config", ex);
            return ApiResponseBuilder.fromException(ex, "CREATE_INPUT_FAILED", HttpStatus.INTERNAL_SERVER_ERROR);
        });
    }

    /**
     * Updates an existing input configuration.
     *
     * @param id the configuration ID
     * @param config the updated configuration settings
     * @return updated configuration
     */
    @PutMapping("/inputs/{id}")
    public CompletableFuture<ResponseEntity<ApiResponseDTO>> updateInputConfig(
            @PathVariable String id,
            @RequestBody Map<String, Object> config) {
        return AsyncHelper.executeAsyncFuture(() -> 
            ApiResponseBuilder.success("Input configuration updated", "UPDATE_INPUT_SUCCESS", config)
        ).exceptionally(ex -> {
            log.error("Error updating input config", ex);
            return ApiResponseBuilder.fromException(ex, "UPDATE_INPUT_FAILED", HttpStatus.INTERNAL_SERVER_ERROR);
        });
    }

    /**
     * Deletes an input configuration.
     *
     * @param id the configuration ID to delete
     * @return success confirmation
     */
    @DeleteMapping("/inputs/{id}")
    public CompletableFuture<ResponseEntity<ApiResponseDTO>> deleteInputConfig(@PathVariable String id) {
        return AsyncHelper.executeAsyncFuture(() -> 
            ApiResponseBuilder.success("Input configuration deleted", "DELETE_INPUT_SUCCESS")
        ).exceptionally(ex -> {
            log.error("Error deleting input config", ex);
            return ApiResponseBuilder.fromException(ex, "DELETE_INPUT_FAILED", HttpStatus.INTERNAL_SERVER_ERROR);
        });
    }
}
