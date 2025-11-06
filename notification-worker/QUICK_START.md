# 🚀 Quick Start - Gmail SMTP Setup (3 Minutes)

## Prerequisites
- ✅ Gmail account
- ✅ Java 21 installed
- ✅ Kafka running (localhost:9092)

---

## Option 1: Automated Setup (EASIEST) ⭐

### Windows (PowerShell):
```powershell
cd notification-worker
.\setup-gmail-env.ps1
```

### Linux/Mac:
```bash
cd notification-worker
chmod +x setup-gmail-env.sh
./setup-gmail-env.sh
```

Script will:
1. Prompt for Gmail credentials
2. Set environment variables
3. Optionally make them permanent

**Then run:**
```bash
./gradlew bootRun
```

---

## Option 2: Manual Setup (5 Minutes)

### Step 1: Get Gmail App Password

1. **Enable 2FA:** https://myaccount.google.com/security
2. **Generate App Password:** https://myaccount.google.com/apppasswords
3. Select "Mail" → Generate
4. **Copy 16-digit password**

### Step 2: Set Environment Variables

**Windows (PowerShell):**
```powershell
$env:MAIL_USERNAME="your-email@gmail.com"
$env:MAIL_PASSWORD="xxxx xxxx xxxx xxxx"
```

**Linux/Mac:**
```bash
export MAIL_USERNAME="your-email@gmail.com"
export MAIL_PASSWORD="xxxx xxxx xxxx xxxx"
```

### Step 3: Run Application
```bash
./gradlew bootRun
```

---

## Option 3: Using .env File

1. **Copy example file:**
   ```bash
   cp .env.example .env
   ```

2. **Edit .env:**
   ```bash
   MAIL_USERNAME=your-email@gmail.com
   MAIL_PASSWORD=xxxx xxxx xxxx xxxx
   ```

3. **Load and run:**
   ```bash
   # Load .env (Linux/Mac)
   export $(cat .env | xargs)
   ./gradlew bootRun

   # Or use your IDE to load .env automatically
   ```

---

## Verify Setup

### Check Logs:
```
INFO  Started NotificationWorkerApplication in 3.5 seconds
INFO  Kafka consumer started successfully
INFO  Listening on topic: notification.otp.email
```

### Test Email:
Update user profile with new email → Check inbox for OTP email

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Authentication Failed | Use App Password (not regular password) |
| Connection Timeout | Check firewall, try port 465 |
| Email in Spam | Add sender to contacts |

**Full guide:** [GMAIL_SETUP_GUIDE.md](./GMAIL_SETUP_GUIDE.md)

---

## Next Steps

✅ **You're ready!** Email OTP notifications will now be sent via Gmail.

For production deployment:
- Consider switching to SendGrid/Amazon SES (Gmail has 500/day limit)
- See [SMTP_SETUP_GUIDE.md](./SMTP_SETUP_GUIDE.md) for other providers

---

## Files Reference

- `GMAIL_SETUP_GUIDE.md` - Detailed Gmail setup guide
- `SMTP_SETUP_GUIDE.md` - Other SMTP providers guide
- `setup-gmail-env.ps1` - Windows setup script
- `setup-gmail-env.sh` - Linux/Mac setup script
- `.env.example` - Environment variables template

---

## Support

Having issues? Check:
1. 2FA is enabled on Gmail
2. Using App Password (not regular password)
3. Environment variables are set correctly
4. Kafka is running

**Full troubleshooting:** [GMAIL_SETUP_GUIDE.md#troubleshooting](./GMAIL_SETUP_GUIDE.md#-troubleshooting)
