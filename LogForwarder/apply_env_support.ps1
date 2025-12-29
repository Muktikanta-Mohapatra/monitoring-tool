# ================================================================
# Automatic Setup Script for Environment Variable Support (Windows)
# ================================================================
# This script applies all necessary changes for env variable support
# Run with: .\apply_env_support.ps1
# ================================================================

Write-Host "===================================================================" -ForegroundColor Cyan
Write-Host "  LogForwarder - Environment Variable Support Setup" -ForegroundColor Cyan
Write-Host "===================================================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: Check if we're in the right directory
if (-not (Test-Path "Cargo.toml")) {
    Write-Host "❌ Error: Cargo.toml not found. Please run from LogForwarder directory." -ForegroundColor Red
    exit 1
}

Write-Host "✅ Found Cargo.toml" -ForegroundColor Green
Write-Host ""

# Step 2: Add dotenvy dependency to Cargo.toml
Write-Host "📦 Adding dotenvy dependency to Cargo.toml..." -ForegroundColor Yellow
$cargoContent = Get-Content "Cargo.toml" -Raw
if ($cargoContent -match "dotenvy") {
    Write-Host "⏭️  dotenvy already in Cargo.toml, skipping" -ForegroundColor Gray
} else {
    $cargoLines = Get-Content "Cargo.toml"
    $newLines = @()
    foreach ($line in $cargoLines) {
        $newLines += $line
        if ($line -match '^serde_json\s*=') {
            $newLines += 'dotenvy = "0.15"'
        }
    }
    $newLines | Set-Content "Cargo.toml"
    Write-Host "✅ Added dotenvy dependency" -ForegroundColor Green
}
Write-Host ""

# Step 3: Check if env_loader.rs exists
if (Test-Path "src/env_loader.rs") {
    Write-Host "✅ env_loader.rs already exists" -ForegroundColor Green
} else {
    Write-Host "❌ env_loader.rs not found. File should have been created." -ForegroundColor Red
    Write-Host "   Please check that ENV_VARIABLE_SETUP.md was followed correctly." -ForegroundColor Yellow
    exit 1
}
Write-Host ""

# Step 4: Add module to lib.rs
Write-Host "📝 Updating src/lib.rs..." -ForegroundColor Yellow
if (Test-Path "src/lib.rs") {
    $libContent = Get-Content "src/lib.rs" -Raw
    if ($libContent -match "pub mod env_loader") {
        Write-Host "⏭️  env_loader module already registered in lib.rs" -ForegroundColor Gray
    } else {
        $libLines = Get-Content "src/lib.rs"
        $newLines = @()
        $added = $false
        foreach ($line in $libLines) {
            $newLines += $line
            if (-not $added -and $line -match '^pub mod\s+\w+') {
                $newLines += "pub mod env_loader;"
                $added = $true
            }
        }
        $newLines | Set-Content "src/lib.rs"
        Write-Host "✅ Added env_loader module to lib.rs" -ForegroundColor Green
    }
} else {
    Write-Host "❌ src/lib.rs not found" -ForegroundColor Red
    exit 1
}
Write-Host ""

# Step 5: Update .gitignore
Write-Host "📝 Updating .gitignore..." -ForegroundColor Yellow
if (Test-Path ".gitignore") {
    $gitignoreContent = Get-Content ".gitignore" -Raw
    if ($gitignoreContent -match "^\.env$") {
        Write-Host "⏭️  .env already in .gitignore" -ForegroundColor Gray
    } else {
        Get-Content ".gitignore.append" | Add-Content ".gitignore"
        Write-Host "✅ Updated .gitignore" -ForegroundColor Green
    }
} else {
    Write-Host "⚠️  .gitignore not found, creating new one" -ForegroundColor Yellow
    Copy-Item ".gitignore.append" ".gitignore"
}
Write-Host ""

# Step 6: Create .env from example if not exists
Write-Host "🔐 Setting up .env file..." -ForegroundColor Yellow
if (Test-Path ".env") {
    Write-Host "⏭️  .env already exists, skipping" -ForegroundColor Gray
} else {
    Copy-Item ".env.example" ".env"
    Write-Host "✅ Created .env from .env.example" -ForegroundColor Green
    Write-Host ""
    Write-Host "⚠️  IMPORTANT: Edit .env and add your API key!" -ForegroundColor Yellow
    Write-Host "    notepad .env" -ForegroundColor White
}
Write-Host ""

# Step 7: Show next steps
Write-Host "===================================================================" -ForegroundColor Cyan
Write-Host "  ✅ Setup Complete!" -ForegroundColor Green
Write-Host "===================================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "📋 Manual steps required:" -ForegroundColor Yellow
Write-Host ""
Write-Host "1. Update src/main.rs:" -ForegroundColor White
Write-Host "   Add this at the start of main() function:" -ForegroundColor Gray
Write-Host ""
Write-Host "   if let Err(e) = high_perf_forwarder::env_loader::load_env_config() {" -ForegroundColor Cyan
Write-Host "       eprintln!(""Warning: Failed to load environment config: {}"", e);" -ForegroundColor Cyan
Write-Host "   }" -ForegroundColor Cyan
Write-Host ""
Write-Host "2. Update src/config/mod.rs in from_yaml() function:" -ForegroundColor White
Write-Host "   Add after: let mut config = serde_yaml::from_str::<Config>(&content)?;" -ForegroundColor Gray
Write-Host ""
Write-Host "   config.outputs = crate::env_loader::apply_env_overrides(config.outputs);" -ForegroundColor Cyan
Write-Host ""
Write-Host "3. Edit .env file with your API key:" -ForegroundColor White
Write-Host "   notepad .env" -ForegroundColor Cyan
Write-Host ""
Write-Host "4. Build and test:" -ForegroundColor White
Write-Host "   cargo build --release" -ForegroundColor Cyan
Write-Host "   cargo run -- --config config/inputs.yaml" -ForegroundColor Cyan
Write-Host ""
Write-Host "📖 For detailed instructions, see: ENV_VARIABLE_SETUP.md" -ForegroundColor Yellow
Write-Host ""
