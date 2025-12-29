#!/bin/bash
# ================================================================
# Automatic Setup Script for Environment Variable Support
# ================================================================
# This script applies all necessary changes for env variable support
# Run with: bash apply_env_support.sh
# ================================================================

set -e  # Exit on error

echo "==================================================================="
echo "  LogForwarder - Environment Variable Support Setup"
echo "==================================================================="
echo ""

# Step 1: Check if we're in the right directory
if [ ! -f "Cargo.toml" ]; then
    echo "❌ Error: Cargo.toml not found. Please run from LogForwarder directory."
    exit 1
fi

echo "✅ Found Cargo.toml"
echo ""

# Step 2: Add dotenvy dependency to Cargo.toml
echo "📦 Adding dotenvy dependency to Cargo.toml..."
if grep -q "dotenvy" Cargo.toml; then
    echo "⏭️  dotenvy already in Cargo.toml, skipping"
else
    # Insert after serde_json line
    sed -i '/^serde_json = /a dotenvy = "0.15"' Cargo.toml
    echo "✅ Added dotenvy dependency"
fi
echo ""

# Step 3: Check if env_loader.rs exists
if [ -f "src/env_loader.rs" ]; then
    echo "✅ env_loader.rs already exists"
else
    echo "❌ env_loader.rs not found. Please copy the file from the setup guide."
    exit 1
fi
echo ""

# Step 4: Add module to lib.rs
echo "📝 Updating src/lib.rs..."
if [ -f "src/lib.rs" ]; then
    if grep -q "pub mod env_loader" src/lib.rs; then
        echo "⏭️  env_loader module already registered in lib.rs"
    else
        # Add after first pub mod line
        sed -i '0,/pub mod/a pub mod env_loader;' src/lib.rs
        echo "✅ Added env_loader module to lib.rs"
    fi
else
    echo "❌ src/lib.rs not found"
    exit 1
fi
echo ""

# Step 5: Update .gitignore
echo "📝 Updating .gitignore..."
if [ -f ".gitignore" ]; then
    if grep -q "^\.env$" .gitignore; then
        echo "⏭️  .env already in .gitignore"
    else
        cat .gitignore.append >> .gitignore
        echo "✅ Updated .gitignore"
    fi
else
    echo "⚠️  .gitignore not found, creating new one"
    cat .gitignore.append > .gitignore
fi
echo ""

# Step 6: Create .env from example if not exists
echo "🔐 Setting up .env file..."
if [ -f ".env" ]; then
    echo "⏭️  .env already exists, skipping"
else
    cp .env.example .env
    echo "✅ Created .env from .env.example"
    echo ""
    echo "⚠️  IMPORTANT: Edit .env and add your API key!"
    echo "    nano .env"
fi
echo ""

# Step 7: Show next steps
echo "==================================================================="
echo "  ✅ Setup Complete!"
echo "==================================================================="
echo ""
echo "📋 Manual steps required:"
echo ""
echo "1. Update src/main.rs:"
echo "   Add this at the start of main() function:"
echo ""
echo "   if let Err(e) = high_perf_forwarder::env_loader::load_env_config() {"
echo "       eprintln!(\"Warning: Failed to load environment config: {}\", e);"
echo "   }"
echo ""
echo "2. Update src/config/mod.rs in from_yaml() function:"
echo "   Add after line: let mut config = serde_yaml::from_str::<Config>(&content)?;"
echo ""
echo "   config.outputs = crate::env_loader::apply_env_overrides(config.outputs);"
echo ""
echo "3. Edit .env file with your API key:"
echo "   nano .env"
echo ""
echo "4. Build and test:"
echo "   cargo build --release"
echo "   cargo run -- --config config/inputs.yaml"
echo ""
echo "📖 For detailed instructions, see: ENV_VARIABLE_SETUP.md"
echo ""
