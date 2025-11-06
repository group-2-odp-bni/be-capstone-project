# 📧 Gmail SMTP Setup Guide

## Step-by-Step Setup (5 Menit)

### Step 1: Enable 2-Factor Authentication

1. **Buka Google Account Settings:**
   - Go to: https://myaccount.google.com
   - Atau klik foto profile Anda → "Manage your Google Account"

2. **Enable 2-Step Verification:**
   - Klik tab **"Security"** di sidebar kiri
   - Scroll ke bawah, cari **"2-Step Verification"**
   - Klik **"2-Step Verification"**
   - Follow the setup wizard (biasanya verifikasi dengan phone)
   - **PENTING:** Ini wajib untuk bisa generate App Password!

---

### Step 2: Generate App Password

1. **Access App Passwords:**
   - Go to: https://myaccount.google.com/apppasswords
   - Atau dari Security page → scroll ke bawah → "App passwords"
   - Login lagi jika diminta

2. **Create New App Password:**
   - Di dropdown "Select app": pilih **"Mail"**
   - Di dropdown "Select device": pilih **"Other (Custom name)"**
   - Type: `BNI Orange Notification Worker`
   - Klik **"Generate"**

3. **Copy Password:**
   - Akan muncul popup dengan **16-digit password** seperti: `xxxx xxxx xxxx xxxx`
   - **PENTING: COPY PASSWORD INI SEKARANG!**
   - Password ini hanya ditampilkan sekali
   - Klik **"Done"** setelah copy

---

### Step 3: Configure Application

#### Option A: Using Environment Variables (RECOMMENDED)

**Windows (PowerShell):**
```powershell
# Set environment variables untuk session saat ini
$env:MAIL_HOST="smtp.gmail.com"
$env:MAIL_PORT="587"
$env:MAIL_USERNAME="your-email@gmail.com"
$env:MAIL_PASSWORD="xxxx xxxx xxxx xxxx"  # 16-digit App Password
$env:EMAIL_FROM_ADDRESS="your-email@gmail.com"
$env:EMAIL_FROM_NAME="BNI Orange E-Wallet"

# Verify
echo $env:MAIL_USERNAME
```

**Permanent (System Environment Variables):**
```powershell
# Run as Administrator
[System.Environment]::SetEnvironmentVariable("MAIL_HOST", "smtp.gmail.com", "User")
[System.Environment]::SetEnvironmentVariable("MAIL_PORT", "587", "User")
[System.Environment]::SetEnvironmentVariable("MAIL_USERNAME", "your-email@gmail.com", "User")
[System.Environment]::SetEnvironmentVariable("MAIL_PASSWORD", "xxxx xxxx xxxx xxxx", "User")
[System.Environment]::SetEnvironmentVariable("EMAIL_FROM_ADDRESS", "your-email@gmail.com", "User")
[System.Environment]::SetEnvironmentVariable("EMAIL_FROM_NAME", "BNI Orange E-Wallet", "User")
```

**Linux/Mac (.bashrc or .zshrc):**
```bash
export MAIL_HOST=smtp.gmail.com
export MAIL_PORT=587
export MAIL_USERNAME=your-email@gmail.com
export MAIL_PASSWORD="xxxx xxxx xxxx xxxx"
export EMAIL_FROM_ADDRESS=your-email@gmail.com
export EMAIL_FROM_NAME="BNI Orange E-Wallet"
```

#### Option B: Using application.yml (NOT RECOMMENDED - untuk testing only)

**JANGAN COMMIT FILE INI KE GIT!**

Edit: `notification-worker/src/main/resources/application.yml`

```yaml
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: your-email@gmail.com
    password: xxxx xxxx xxxx xxxx  # App Password
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
            required: true
          connectiontimeout: 5000
          timeout: 5000
          writetimeout: 5000
    default-encoding: UTF-8

orange:
  email:
    from-address: your-email@gmail.com
    from-name: BNI Orange E-Wallet
```

---

### Step 4: Create .env File (For Docker/Easy Config)

Create file: `notification-worker/.env`

```bash
# Gmail SMTP Configuration
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=xxxx xxxx xxxx xxxx

# Email Settings
EMAIL_FROM_ADDRESS=your-email@gmail.com
EMAIL_FROM_NAME=BNI Orange E-Wallet

# Kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
```

**Add to .gitignore:**
```bash
# Add this to your .gitignore
.env
*.env
application-local.yml
```

---

### Step 5: Run & Test

#### A. Run Notification Worker

**Using Gradle:**
```bash
cd notification-worker

# Windows PowerShell (dengan env vars sudah di-set)
./gradlew bootRun

# Atau dengan inline env
./gradlew bootRun --args='--MAIL_USERNAME=your-email@gmail.com --MAIL_PASSWORD="xxxx xxxx xxxx xxxx"'
```

**Expected Output:**
```
Started NotificationWorkerApplication in 3.5 seconds
Kafka consumer started successfully
Listening on topic: notification.otp.email
```

#### B. Test Email Sending

**Method 1: Via User Service (Full Flow)**
```bash
# 1. Update profile dengan email baru (will trigger OTP)
curl -X PUT http://localhost:8082/api/v1/users/profile \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "newemail@example.com"
  }'

# 2. Check email inbox
# Should receive OTP email
```

**Method 2: Publish Test Message to Kafka**
```bash
# Using kafka-console-producer
kafka-console-producer.sh --broker-list localhost:9092 \
  --topic notification.otp.email \
  --property "parse.key=true" \
  --property "key.separator=:"

# Then type (as binary, this is just example structure):
user123:{"email":"test@example.com","otp_code":"123456","user_id":"user123"}
```

#### C. Check Logs

**Success Logs:**
```
INFO  c.b.o.n.c.EmailOtpKafkaConsumer - Received email OTP notification - User ID: xxx
INFO  c.b.o.n.s.EmailService - Preparing to send OTP email to user xxx at te***@example.com
INFO  c.b.o.n.s.EmailService - OTP email successfully sent to user xxx
INFO  c.b.o.n.c.EmailOtpKafkaConsumer - Successfully sent OTP email and acknowledged
```

**Error Logs:**
```
ERROR c.b.o.n.s.EmailService - Failed to send OTP email: Authentication failed
```

---

## 🔍 Troubleshooting

### Issue 1: "Authentication Failed"
```
Error: 535-5.7.8 Username and Password not accepted
```

**Solutions:**
1. ✅ Make sure 2FA is enabled
2. ✅ Use App Password, NOT your regular Gmail password
3. ✅ Check username is full email (yourname@gmail.com)
4. ✅ Remove spaces from App Password in config
5. ✅ Regenerate new App Password

### Issue 2: "Connection Timeout"
```
Error: Connection timed out
```

**Solutions:**
1. ✅ Check firewall blocking port 587
2. ✅ Try port 465 with SSL:
   ```yaml
   spring:
     mail:
       host: smtp.gmail.com
       port: 465
       properties:
         mail:
           smtp:
             auth: true
             ssl:
               enable: true
   ```
3. ✅ Check internet connection

### Issue 3: "Less Secure App Access"
```
Error: Please log in via your web browser
```

**Solution:**
- This error shouldn't happen with App Password
- If happens: regenerate App Password
- Make sure you're using App Password, not regular password

### Issue 4: Email Goes to Spam
**Solutions:**
1. ✅ Add sender to contacts
2. ✅ Mark as "Not Spam"
3. ✅ For production, use proper SMTP service (SendGrid/SES)

### Issue 5: "Daily Limit Exceeded"
```
Error: 550 5.4.5 Daily sending quota exceeded
```

**Solution:**
- Gmail limit: 500 emails/day
- Wait 24 hours or upgrade to Google Workspace
- Consider switching to SendGrid/SES for production

---

## ⚠️ Important Notes

### Gmail Limitations:
- ❌ Max 500 emails per day
- ❌ Not recommended for production
- ❌ May have deliverability issues
- ✅ OK for development/testing
- ✅ Free and easy to setup

### Security Best Practices:
1. **NEVER commit credentials to Git**
2. Use environment variables
3. Add `.env` to `.gitignore`
4. Rotate App Password periodically
5. Use different App Password for each application

### When to Switch to Production SMTP:
- ✅ When you need > 500 emails/day
- ✅ When going to production
- ✅ When you need better deliverability
- ✅ When you need analytics/tracking

---

## 🎯 Quick Checklist

Before running, verify:

- [ ] 2FA enabled on Gmail account
- [ ] App Password generated (16 digits)
- [ ] Environment variables set
- [ ] `MAIL_USERNAME` is full email address
- [ ] `MAIL_PASSWORD` is App Password (not regular password)
- [ ] Port 587 is not blocked by firewall
- [ ] Kafka is running (localhost:9092)
- [ ] User-service is running (for testing)

---

## 📊 Monitoring

### Check Gmail Activity
1. Go to: https://myaccount.google.com/device-activity
2. Should see "BNI Orange Notification Worker" accessing your account

### Check Sent Emails
1. Open Gmail
2. Check "Sent" folder
3. Should see OTP emails sent

### Application Logs
```bash
# Follow logs
tail -f logs/notification-worker.log

# Or in Windows
Get-Content logs/notification-worker.log -Wait
```

---

## 🚀 Next Steps

After Gmail is working:

1. **Test the full flow** (user profile update → email sent)
2. **Check email formatting** in real inbox
3. **Monitor logs** for any errors
4. **Plan migration** to production SMTP (SendGrid/SES) when ready

---

## 📞 Support

If still having issues:
1. Check application logs
2. Verify all environment variables
3. Try regenerating App Password
4. Test with simple email first (not OTP)

Gmail SMTP should work immediately after following these steps! 🎉
