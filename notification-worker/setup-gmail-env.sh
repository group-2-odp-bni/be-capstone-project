#!/bin/bash

# Gmail SMTP Environment Variables Setup Script (Bash)
# Run: chmod +x setup-gmail-env.sh && ./setup-gmail-env.sh

echo "=== Gmail SMTP Configuration Setup ==="
echo ""

# Prompt for Gmail credentials
read -p "Enter your Gmail address (e.g., yourname@gmail.com): " email
echo ""
echo "To get App Password:"
echo "1. Go to https://myaccount.google.com/apppasswords"
echo "2. Select 'Mail' and generate password"
echo "3. Copy the 16-digit password (format: xxxx xxxx xxxx xxxx)"
echo ""
read -sp "Enter your Gmail App Password (16 digits): " appPassword
echo ""
echo ""
read -p "Enter sender name (default: BNI Orange E-Wallet): " fromName
fromName=${fromName:-"BNI Orange E-Wallet"}

echo ""
echo "Setting environment variables..."

# Export environment variables for current session
export MAIL_HOST="smtp.gmail.com"
export MAIL_PORT="587"
export MAIL_USERNAME="$email"
export MAIL_PASSWORD="$appPassword"
export EMAIL_FROM_ADDRESS="$email"
export EMAIL_FROM_NAME="$fromName"

echo ""
echo "✓ Environment variables set successfully!"
echo ""
echo "Current configuration:"
echo "  MAIL_HOST          = $MAIL_HOST"
echo "  MAIL_PORT          = $MAIL_PORT"
echo "  MAIL_USERNAME      = $MAIL_USERNAME"
echo "  MAIL_PASSWORD      = ****"
echo "  EMAIL_FROM_ADDRESS = $EMAIL_FROM_ADDRESS"
echo "  EMAIL_FROM_NAME    = $EMAIL_FROM_NAME"
echo ""

# Ask if user wants to add to shell profile
echo "Do you want to add these variables to your shell profile?"
echo "This will make them permanent (available in all terminal sessions)."
read -p "Add to profile? (y/n): " permanent

if [[ "$permanent" == "y" || "$permanent" == "Y" ]]; then
    # Detect shell
    if [[ -n "$ZSH_VERSION" ]]; then
        profile="$HOME/.zshrc"
    elif [[ -n "$BASH_VERSION" ]]; then
        profile="$HOME/.bashrc"
    else
        profile="$HOME/.profile"
    fi

    echo ""
    echo "Adding to $profile..."

    # Create backup
    cp "$profile" "$profile.backup.$(date +%Y%m%d_%H%M%S)"

    # Add variables
    cat >> "$profile" << EOF

# Gmail SMTP Configuration (Added by setup-gmail-env.sh)
export MAIL_HOST="smtp.gmail.com"
export MAIL_PORT="587"
export MAIL_USERNAME="$email"
export MAIL_PASSWORD="$appPassword"
export EMAIL_FROM_ADDRESS="$email"
export EMAIL_FROM_NAME="$fromName"
EOF

    echo ""
    echo "✓ Variables added to $profile"
    echo "⚠️  Run: source $profile"
    echo "   Or restart your terminal for changes to take effect"
else
    echo ""
    echo "Environment variables set for current session only."
    echo "Run this script again in new terminal sessions."
    echo ""
    echo "Or manually add to ~/.bashrc or ~/.zshrc:"
    echo ""
    echo "export MAIL_HOST=\"smtp.gmail.com\""
    echo "export MAIL_PORT=\"587\""
    echo "export MAIL_USERNAME=\"$email\""
    echo "export MAIL_PASSWORD=\"$appPassword\""
    echo "export EMAIL_FROM_ADDRESS=\"$email\""
    echo "export EMAIL_FROM_NAME=\"$fromName\""
fi

echo ""
echo "=== Setup Complete ==="
echo ""
echo "Next steps:"
echo "1. Start Kafka (if not already running)"
echo "2. Run: ./gradlew bootRun"
echo "3. Test by updating user profile with new email"
echo ""
echo "For troubleshooting, see: GMAIL_SETUP_GUIDE.md"
echo ""
