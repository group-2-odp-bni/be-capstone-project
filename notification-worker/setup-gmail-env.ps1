# Gmail SMTP Environment Variables Setup Script (PowerShell)
# Run this script to set environment variables for current session

Write-Host "=== Gmail SMTP Configuration Setup ===" -ForegroundColor Cyan
Write-Host ""

# Prompt for Gmail credentials
$email = Read-Host "Enter your Gmail address (e.g., yourname@gmail.com)"
Write-Host ""
Write-Host "To get App Password:" -ForegroundColor Yellow
Write-Host "1. Go to https://myaccount.google.com/apppasswords" -ForegroundColor Yellow
Write-Host "2. Select 'Mail' and generate password" -ForegroundColor Yellow
Write-Host "3. Copy the 16-digit password (format: xxxx xxxx xxxx xxxx)" -ForegroundColor Yellow
Write-Host ""
$appPassword = Read-Host "Enter your Gmail App Password (16 digits)" -AsSecureString
$appPasswordPlain = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($appPassword)
)

Write-Host ""
$fromName = Read-Host "Enter sender name (default: BNI Orange E-Wallet)"
if ([string]::IsNullOrWhiteSpace($fromName)) {
    $fromName = "BNI Orange E-Wallet"
}

Write-Host ""
Write-Host "Setting environment variables..." -ForegroundColor Green

# Set environment variables for current session
$env:MAIL_HOST = "smtp.gmail.com"
$env:MAIL_PORT = "587"
$env:MAIL_USERNAME = $email
$env:MAIL_PASSWORD = $appPasswordPlain
$env:EMAIL_FROM_ADDRESS = $email
$env:EMAIL_FROM_NAME = $fromName

Write-Host ""
Write-Host "✓ Environment variables set successfully!" -ForegroundColor Green
Write-Host ""
Write-Host "Current configuration:" -ForegroundColor Cyan
Write-Host "  MAIL_HOST          = $env:MAIL_HOST"
Write-Host "  MAIL_PORT          = $env:MAIL_PORT"
Write-Host "  MAIL_USERNAME      = $env:MAIL_USERNAME"
Write-Host "  MAIL_PASSWORD      = ****"
Write-Host "  EMAIL_FROM_ADDRESS = $env:EMAIL_FROM_ADDRESS"
Write-Host "  EMAIL_FROM_NAME    = $env:EMAIL_FROM_NAME"
Write-Host ""

# Ask if user wants to set permanently
Write-Host "Do you want to set these variables permanently for your user account?" -ForegroundColor Yellow
$permanent = Read-Host "(y/n)"

if ($permanent -eq "y" -or $permanent -eq "Y") {
    Write-Host ""
    Write-Host "Setting permanent environment variables..." -ForegroundColor Green

    [System.Environment]::SetEnvironmentVariable("MAIL_HOST", "smtp.gmail.com", "User")
    [System.Environment]::SetEnvironmentVariable("MAIL_PORT", "587", "User")
    [System.Environment]::SetEnvironmentVariable("MAIL_USERNAME", $email, "User")
    [System.Environment]::SetEnvironmentVariable("MAIL_PASSWORD", $appPasswordPlain, "User")
    [System.Environment]::SetEnvironmentVariable("EMAIL_FROM_ADDRESS", $email, "User")
    [System.Environment]::SetEnvironmentVariable("EMAIL_FROM_NAME", $fromName, "User")

    Write-Host ""
    Write-Host "✓ Permanent environment variables set!" -ForegroundColor Green
    Write-Host "⚠️  You may need to restart your terminal/IDE for changes to take effect" -ForegroundColor Yellow
} else {
    Write-Host ""
    Write-Host "Environment variables set for current session only." -ForegroundColor Yellow
    Write-Host "Run this script again in new terminal sessions." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=== Setup Complete ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Green
Write-Host "1. Start Kafka (if not already running)"
Write-Host "2. Run: ./gradlew bootRun"
Write-Host "3. Test by updating user profile with new email"
Write-Host ""
Write-Host "For troubleshooting, see: GMAIL_SETUP_GUIDE.md" -ForegroundColor Cyan
Write-Host ""
