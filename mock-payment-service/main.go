package main

import (
	"bytes"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"time"

	"github.com/joho/godotenv"
)

// Configuration from environment variables
var (
	bniAPIKey          string
	webhookSecret      string
	transactionBaseURL string
	serverPort         string
)

type MockPaymentRequest struct {
	VaNumber string      `json:"va_number"`
	Amount   json.Number `json:"amount"`
}

type TopUpCallbackRequest struct {
	VaNumber         string      `json:"vaNumber"`
	PaidAmount       json.Number `json:"paidAmount"`
	PaymentTimestamp string      `json:"paymentTimestamp"`
	PaymentReference string      `json:"paymentReference"`
}

type TransactionServiceResponse struct {
	Data struct {
		ID             string      `json:"id"`
		VaNumber       string      `json:"vaNumber"`
		AccountName    string      `json:"accountName"`
		TransactionID  string      `json:"transactionId"`
		TransactionRef string      `json:"transactionRef"`
		Provider       string      `json:"provider"`
		Status         string      `json:"status"`
		Amount         json.Number `json:"amount"`
		PaidAmount     interface{} `json:"paidAmount"`
		ExpiresAt      string      `json:"expiresAt"`
		PaidAt         interface{} `json:"paidAt"`
		CreatedAt      string      `json:"createdAt"`
	} `json:"data"`
	Timestamp string `json:"timestamp"`
}

// Response to frontend
type VirtualAccountResponse struct {
	Success bool `json:"success"`
	Data    struct {
		ID             string      `json:"id"`
		VaNumber       string      `json:"vaNumber"`
		AccountName    string      `json:"accountName"`
		Amount         json.Number `json:"amount"`
		Status         string      `json:"status"`
		Provider       string      `json:"provider"`
		ExpiresAt      string      `json:"expiresAt"`
		TransactionID  string      `json:"transactionId"`
		TransactionRef string      `json:"transactionRef"`
		PaidAmount     interface{} `json:"paidAmount"`
		PaidAt         interface{} `json:"paidAt"`
		CreatedAt      string      `json:"createdAt"`
	} `json:"data"`
}

func main() {
	if err := godotenv.Load(); err != nil {
		log.Println("Warning: .env file not found, using environment variables")
	}

	bniAPIKey = getEnv("BNI_API_KEY", "bni-api-key-dev")
	webhookSecret = getEnv("WEBHOOK_SECRET", "secret-key-for-dev")
	transactionBaseURL = getEnv("TRANSACTION_SERVICE_BASE_URL", "http://localhost:8080")
	serverPort = getEnv("PORT", "8090")

	log.Printf("Mock Payment Server configuration:")
	log.Printf("  - Transaction Service URL: %s", transactionBaseURL)
	log.Printf("  - Server Port: %s", serverPort)
	log.Printf("  - API Key configured: %v", bniAPIKey != "")

	http.HandleFunc("/inquiry", corsMiddleware(handleInquiry))
	http.HandleFunc("/pay", corsMiddleware(handlePayment))

	log.Printf("Mock Payment Server (Go) berjalan di port :%s...", serverPort)
	if err := http.ListenAndServe(":"+serverPort, nil); err != nil {
		log.Fatalf("Gagal menjalankan server: %v", err)
	}
}

func handleInquiry(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "Hanya metode GET yang diizinkan", http.StatusMethodNotAllowed)
		return
	}

	vaNumber := r.URL.Query().Get("va_number")
	if vaNumber == "" {
		http.Error(w, "Parameter va_number wajib diisi", http.StatusBadRequest)
		return
	}

	log.Printf("Menerima request inquiry untuk VA: %s", vaNumber)

	inquiryURL := fmt.Sprintf("%s/api/v1/topup/inquiry/%s", transactionBaseURL, vaNumber)
	req, err := http.NewRequest("GET", inquiryURL, nil)
	if err != nil {
		log.Printf("Gagal membuat request inquiry: %v", err)
		http.Error(w, "Gagal membuat request", http.StatusInternalServerError)
		return
	}

	req.Header.Set("X-API-Key", bniAPIKey)

	client := &http.Client{Timeout: 10 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		log.Printf("Gagal mengirim request inquiry: %v", err)
		http.Error(w, "Gagal menghubungi transaction service", http.StatusInternalServerError)
		return
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		log.Printf("Transaction service merespon status non-200: %s, body: %s", resp.Status, string(body))
		http.Error(w, fmt.Sprintf("Transaction service error: %s", resp.Status), resp.StatusCode)
		return
	}

	// Decode response from transaction service
	var txResponse TransactionServiceResponse
	if err := json.NewDecoder(resp.Body).Decode(&txResponse); err != nil {
		log.Printf("Gagal decode response: %v", err)
		http.Error(w, "Gagal memproses response", http.StatusInternalServerError)
		return
	}

	log.Printf("Inquiry berhasil untuk VA: %s, Amount: %s, Status: %s",
		vaNumber, txResponse.Data.Amount.String(), txResponse.Data.Status)

	// Construct response for frontend
	response := VirtualAccountResponse{
		Success: true,
	}
	response.Data.ID = txResponse.Data.ID
	response.Data.VaNumber = txResponse.Data.VaNumber
	response.Data.AccountName = txResponse.Data.AccountName
	response.Data.Amount = txResponse.Data.Amount
	response.Data.Status = txResponse.Data.Status
	response.Data.Provider = txResponse.Data.Provider
	response.Data.ExpiresAt = txResponse.Data.ExpiresAt
	response.Data.TransactionID = txResponse.Data.TransactionID
	response.Data.TransactionRef = txResponse.Data.TransactionRef
	response.Data.PaidAmount = txResponse.Data.PaidAmount
	response.Data.PaidAt = txResponse.Data.PaidAt
	response.Data.CreatedAt = txResponse.Data.CreatedAt

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

func handlePayment(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Hanya metode POST yang diizinkan", http.StatusMethodNotAllowed)
		return
	}

	var req MockPaymentRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Request body tidak valid", http.StatusBadRequest)
		return
	}

	log.Printf("Menerima request pembayaran untuk VA: %s, Jumlah: %s", req.VaNumber, req.Amount.String())

	// Step 1: Call inquiry to validate VA
	inquiryURL := fmt.Sprintf("%s/api/v1/topup/inquiry/%s", transactionBaseURL, req.VaNumber)
	inquiryReq, err := http.NewRequest("GET", inquiryURL, nil)
	if err != nil {
		log.Printf("Gagal membuat request inquiry: %v", err)
		http.Error(w, "Gagal validasi VA", http.StatusInternalServerError)
		return
	}

	inquiryReq.Header.Set("X-API-Key", bniAPIKey)

	client := &http.Client{Timeout: 10 * time.Second}
	inquiryResp, err := client.Do(inquiryReq)
	if err != nil {
		log.Printf("Gagal mengirim request inquiry: %v", err)
		http.Error(w, "Gagal validasi VA", http.StatusInternalServerError)
		return
	}
	defer inquiryResp.Body.Close()

	if inquiryResp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(inquiryResp.Body)
		log.Printf("VA tidak valid atau tidak ditemukan: %s", string(body))
		http.Error(w, "VA tidak valid atau tidak ditemukan", http.StatusBadRequest)
		return
	}

	var txResponse TransactionServiceResponse
	if err := json.NewDecoder(inquiryResp.Body).Decode(&txResponse); err != nil {
		log.Printf("Gagal decode inquiry response: %v", err)
		http.Error(w, "Gagal memproses inquiry response", http.StatusInternalServerError)
		return
	}

	// Step 2: Validate VA status and amount
	if txResponse.Data.Status != "ACTIVE" {
		log.Printf("VA status bukan ACTIVE: %s", txResponse.Data.Status)
		http.Error(w, fmt.Sprintf("VA tidak dapat digunakan, status: %s", txResponse.Data.Status), http.StatusBadRequest)
		return
	}

	// Compare amounts (numeric comparison to handle decimal differences)
	expectedAmountFloat, err := txResponse.Data.Amount.Float64()
	if err != nil {
		log.Printf("Failed to parse expected amount: %v", err)
		http.Error(w, "Invalid expected amount format", http.StatusInternalServerError)
		return
	}

	paidAmountFloat, err := req.Amount.Float64()
	if err != nil {
		log.Printf("Failed to parse paid amount: %v", err)
		http.Error(w, "Invalid paid amount format", http.StatusBadRequest)
		return
	}

	if paidAmountFloat != expectedAmountFloat {
		log.Printf("Amount mismatch: expected %.2f, got %.2f", expectedAmountFloat, paidAmountFloat)
		http.Error(w, fmt.Sprintf("Jumlah pembayaran tidak sesuai. Expected: %.2f, Got: %.2f", expectedAmountFloat, paidAmountFloat), http.StatusBadRequest)
		return
	}

	log.Printf("VA validated successfully, proceeding with callback")

	// Step 3: Send callback to transaction-service
	callbackReq := TopUpCallbackRequest{
		VaNumber:         req.VaNumber,
		PaidAmount:       req.Amount,
		PaymentTimestamp: time.Now().UTC().Format(time.RFC3339Nano),
		PaymentReference: fmt.Sprintf("mock-ref-%d", time.Now().Unix()),
	}

	// Format amount as plain string without decimals (to match Java's toPlainString())
	amountForSignature := fmt.Sprintf("%.0f", paidAmountFloat)

	payload := fmt.Sprintf("%s%s%s%s",
		callbackReq.VaNumber,
		amountForSignature,
		callbackReq.PaymentTimestamp,
		callbackReq.PaymentReference,
	)

	signature := calculateHmacSha256(payload, webhookSecret)
	log.Printf("Signature calculation - Payload: %s", payload)
	log.Printf("Signature calculation - Secret: %s", webhookSecret)
	log.Printf("Signature yang dihitung: %s", signature)

	err = sendCallback(callbackReq, signature)
	if err != nil {
		log.Printf("Gagal mengirim callback ke transaction-service: %v", err)
		http.Error(w, "Gagal memproses callback pembayaran", http.StatusInternalServerError)
		return
	}

	log.Println("Callback berhasil dikirim ke transaction-service")

	// Prepare response
	type ResponseData struct {
		ReferenceID string      `json:"reference_id"`
		VaNumber    string      `json:"va_number"`
		AccountName string      `json:"account_name"`
		Amount      json.Number `json:"amount"`
		Timestamp   string      `json:"timestamp"`
	}
	type SuccessResponse struct {
		Success bool         `json:"success"`
		Data    ResponseData `json:"data"`
	}

	loc, _ := time.LoadLocation("Asia/Jakarta")
	response := SuccessResponse{
		Success: true,
		Data: ResponseData{
			ReferenceID: callbackReq.PaymentReference,
			VaNumber:    req.VaNumber,
			AccountName: txResponse.Data.AccountName,
			Amount:      req.Amount,
			Timestamp:   time.Now().In(loc).Format("2006-01-02T15:04:05-07:00"),
		},
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	if err := json.NewEncoder(w).Encode(response); err != nil {
		log.Printf("warning: gagal menulis response ke client: %v", err)
	}
}

func calculateHmacSha256(data, secret string) string {
	h := hmac.New(sha256.New, []byte(secret))
	if _, err := h.Write([]byte(data)); err != nil {
		log.Printf("warning: gagal menulis ke HMAC hasher: %v", err)
	}
	return hex.EncodeToString(h.Sum(nil))
}

func sendCallback(callbackData TopUpCallbackRequest, signature string) error {
	jsonData, err := json.Marshal(callbackData)
	if err != nil {
		return fmt.Errorf("gagal marshal data callback: %w", err)
	}

	callbackURL := fmt.Sprintf("%s/api/v1/topup/callback/bni", transactionBaseURL)
	req, err := http.NewRequest("POST", callbackURL, bytes.NewBuffer(jsonData))
	if err != nil {
		return fmt.Errorf("gagal membuat request: %w", err)
	}

	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Signature", signature)
	req.Header.Set("X-API-Key", bniAPIKey)

	client := &http.Client{Timeout: 10 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return fmt.Errorf("gagal mengirim request: %w", err)
	}
	defer func() {
		if cerr := resp.Body.Close(); cerr != nil {
			log.Printf("warning: gagal menutup response body: %v", cerr)
		}
	}()

	if resp.StatusCode != http.StatusOK {
		body, errRead := io.ReadAll(resp.Body)
		if errRead != nil {
			log.Printf("warning: gagal membaca body response callback: %v", errRead)
		}
		return fmt.Errorf("transaction-service merespon status non-200: %s, body: %s", resp.Status, string(body))
	}

	return nil
}

func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}

// CORS middleware to allow requests from frontend
func corsMiddleware(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		// Allow requests from frontend (localhost:3000)
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type")

		// Handle preflight requests
		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusOK)
			return
		}

		next(w, r)
	}
}
