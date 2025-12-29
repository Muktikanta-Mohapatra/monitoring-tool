# Environment Variable Support for LogForwarder

## Overview

This guide shows how to configure LogForwarder to read API keys and other sensitive data from environment variables, similar to running a Java JAR with environment variables.

---

## ✅ Files Created

1. **`.env.example`** - Template for environment variables
2. **`src/env_loader.rs`** - Module for loading and applying environment variables
3. **`.gitignore.append`** - Lines to add to .gitignore

---

## 📝 Required Code Changes

### 1. Add Dependency to `Cargo.toml`

Add after line 10 (after `serde_json = "1.0"`):

```toml
dotenvy = "0.15"
```

**Full dependencies section should look like:**
```toml
[dependencies]
tokio = { version = "1.35", features = ["full"] }
serde = { version = "1.0", features = ["derive"] }
serde_yaml = "0.9"
serde_json = "1.0"
dotenvy = "0.15"  # <-- ADD THIS LINE
arc-swap = "1.6"
# ... rest of dependencies
```

---

### 2. Register Module in `src/lib.rs`

Find the module declarations section and add:

```rust
pub mod env_loader;
```

**Example location (add near other module declarations):**
```rust
pub mod config;
pub mod queue;
pub mod event;
pub mod env_loader;  // <-- ADD THIS LINE
// ... other modules
```

---

### 3. Update `src/main.rs` to Load Environment Variables

At the **very beginning** of the `main()` function, add:

```rust
#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    // Load environment variables from .env file
    if let Err(e) = high_perf_forwarder::env_loader::load_env_config() {
        eprintln!("Warning: Failed to load environment config: {}", e);
    }

    // ... rest of your main function
```

---

### 4. Update Config Loading in `src/config/mod.rs`

**Find the `from_yaml` function** (around line 521) and modify it:

```rust
pub fn from_yaml<P: AsRef<std::path::Path>>(path: P) -> Result<Self, ConfigError> {
    let content = std::fs::read_to_string(path)?;
    let mut config = serde_yaml::from_str::<Config>(&content)?;

    // Apply environment variable overrides
    config.outputs = crate::env_loader::apply_env_overrides(config.outputs);

    config.validate_all()?;
    Ok(config)
}
```

---

## 🚀 Usage Instructions

### Step 1: Create `.env` File

```bash
cd LogForwarder
cp .env.example .env
```

### Step 2: Edit `.env` with Your API Key

```bash
# Edit .env file
nano .env  # or use your favorite editor
```

**Add your plain-text API key:**
```bash
FORWARDER_API_KEY=your-actual-api-key-here-plain-text
FORWARDER_ID=forwarder-001
MIDDLEWARE_URL=http://localhost:8080/api/v1/events/batch
```

### Step 3: Update `.gitignore`

```bash
# Append to .gitignore
cat .gitignore.append >> .gitignore
```

### Step 4: Build and Run

```bash
# Development
cargo run -- --config config/inputs.yaml

# Production
cargo build --release
./target/release/high-perf-forwarder --config config/inputs.yaml
```

---

## 🔐 Generating API Key

### Option 1: Use Existing Hash (Reverse Not Possible)

You have the BCrypt hash: `$2a$12$T.M8zxSTMZxoN/flHgXGjeHXM40itF7WBhqd3QAwC.EALGIrgsGFi`

**Problem:** BCrypt is one-way encryption - you cannot get the plain-text from the hash.

**Solution:** Generate a new API key pair.

### Option 2: Generate New API Key

**Using Node.js:**
```bash
node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
```

**Example Output:**
```
a1b2c3d4e5f6789012345678901234567890abcdef1234567890abcdef123456
```

**Using OpenSSL:**
```bash
openssl rand -hex 32
```

**Using Online Tool:**
- Go to: https://www.uuidgenerator.net/api-token
- Or: https://randomkeygen.com/

### Option 3: Hash Your New API Key

**Java code (add to middleware):**
```java
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class ApiKeyGenerator {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
        String plainKey = "a1b2c3d4e5f6789012345678901234567890abcdef1234567890abcdef123456";
        String hash = encoder.encode(plainKey);
        System.out.println("Plain Key: " + plainKey);
        System.out.println("BCrypt Hash: " + hash);
    }
}
```

**Online BCrypt Tool:**
- Go to: https://bcrypt-generator.com/
- Enter your new plain-text API key
- Use rounds: 12
- Copy the hash

---

## 📄 Update Database with New Hash

```sql
-- Update the API key hash
UPDATE forwarder_api_keys
SET api_key_hash = '$2a$12$NEW_HASH_HERE',
    last_used_at = NOW()
WHERE forwarder_id = 'forwarder-001';
```

---

## 🎯 Complete Workflow

### 1. Generate New API Key Pair

```bash
# Generate plain-text key
node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
# Output: a1b2c3d4e5f6...
```

### 2. Hash It

```bash
# Use Java code or online tool to get BCrypt hash
# Output: $2a$12$xyz123...
```

### 3. Update Database

```sql
UPDATE forwarder_api_keys
SET api_key_hash = '$2a$12$xyz123...'
WHERE forwarder_id = 'forwarder-001';
```

### 4. Create `.env` File

```bash
cat > .env << EOF
FORWARDER_API_KEY=a1b2c3d4e5f6...
FORWARDER_ID=forwarder-001
MIDDLEWARE_URL=http://localhost:8080/api/v1/events/batch
EOF
```

### 5. Run LogForwarder

```bash
cargo run --release -- --config config/inputs.yaml
```

---

## ✅ Verification

### Check Environment Loading

LogForwarder logs should show:
```
[INFO] Loaded environment variables from: ".env"
[INFO] API key loaded from environment variable
[INFO] Using forwarder ID from environment: forwarder-001
```

### Check API Key in Requests

Enable debug logging to see outgoing requests:
```bash
RUST_LOG=debug cargo run -- --config config/inputs.yaml
```

Look for:
```
[DEBUG] Sending request to http://localhost:8080/api/v1/events/batch
[DEBUG] Headers: X-API-Key: a1b2c3d4...
```

### Check Middleware Logs

Middleware should show:
```
[INFO] ForwarderAuthFilter: Forwarder authenticated: forwarder-001
[INFO] EventController: Received batch of 100 events
```

---

## 🐛 Troubleshooting

### Issue: "API key not loaded from environment"

**Check:**
```bash
# Verify .env file exists
ls -la .env

# Check file content
cat .env

# Check for Windows line endings (should be LF, not CRLF)
file .env  # or cat -A .env
```

**Fix:**
```bash
# Convert line endings if needed
dos2unix .env
```

### Issue: "Authentication failed" in middleware

**Cause:** Plain-text key in `.env` doesn't match BCrypt hash in database

**Solution:**
1. Verify plain-text key: `echo $FORWARDER_API_KEY`
2. Verify database hash: `SELECT api_key_hash FROM forwarder_api_keys WHERE forwarder_id='forwarder-001';`
3. Re-generate key pair if they don't match

### Issue: ".env file not found"

**Solution:**
```bash
# Check current directory
pwd

# .env must be in the same directory as you run cargo from
cd LogForwarder
cargo run -- --config config/inputs.yaml
```

### Issue: "Token field is empty in config"

This is normal! The environment variable will override it.

**Check config:**
```yaml
outputs:
  - name: http_output
    url: "http://localhost:8080/api/v1/events/batch"
    token: ""  # <-- Empty is OK, will be filled from .env
```

---

## 🔒 Security Best Practices

1. **Never commit `.env` to git**
   ```bash
   # Verify it's ignored
   git status | grep .env  # Should show nothing
   ```

2. **Restrict file permissions**
   ```bash
   chmod 600 .env  # Only owner can read/write
   ```

3. **Use different keys per environment**
   - Development: `FORWARDER_API_KEY_DEV`
   - Staging: `FORWARDER_API_KEY_STAGING`
   - Production: `FORWARDER_API_KEY_PROD`

4. **Rotate keys regularly**
   - Generate new key every 90 days
   - Update database hash
   - Update `.env` file
   - Restart forwarder

5. **Use CI/CD secrets for production**
   ```bash
   # GitHub Actions
   echo "${{ secrets.FORWARDER_API_KEY }}" > .env

   # GitLab CI
   echo "FORWARDER_API_KEY=$FORWARDER_API_KEY" > .env

   # Docker
   docker run -e FORWARDER_API_KEY=$API_KEY ...
   ```

---

## 📊 Environment Variables Reference

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `FORWARDER_API_KEY` | **Yes** | None | Plain-text API key for X-API-Key header |
| `FORWARDER_ID` | No | UUID | Unique identifier for this forwarder |
| `MIDDLEWARE_URL` | No | From config | Override middleware endpoint URL |
| `RUST_LOG` | No | `info` | Log level (trace, debug, info, warn, error) |

---

## 🎉 Success Criteria

Your setup is complete when:

- [ ] `.env` file created with API key
- [ ] Code changes applied (Cargo.toml, lib.rs, main.rs, config/mod.rs)
- [ ] LogForwarder compiles without errors
- [ ] Logs show "API key loaded from environment variable"
- [ ] Middleware authenticates forwarder successfully
- [ ] No authentication errors in middleware logs

---

**Last Updated:** $(date)
**Status:** ✅ Ready for implementation
