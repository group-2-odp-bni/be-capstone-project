package main

import (
	"bytes"
	"context"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"strings"
	"time"

	"github.com/lestrrat-go/jwx/v2/jwk"
	"github.com/lestrrat-go/jwx/v2/jws"
	"github.com/lestrrat-go/jwx/v2/jwt"
)

const clientSecret = "secret-key-for-dev"
const transactionServiceCallbackURL = "http://localhost:8084/api/v1/topup/callback/bni"
const authServiceJwkUrl = "http://localhost:8081/oauth2/jwks"

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

type contextKey string

const tokenContextKey = contextKey("jwt")

var jwksCache *jwk.Cache

func main() {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	jwksCache = jwk.NewCache(ctx)
	jwksCache.Register(authServiceJwkUrl, jwk.WithMinRefreshInterval(15*time.Minute))

	_, err := jwksCache.Refresh(ctx, authServiceJwkUrl)
	if err != nil {
		log.Fatalf("Gagal melakukan fetch JWKS awal dari %s: %v", authServiceJwkUrl, err)
	}
	log.Println("Cache JWKS berhasil diinisialisasi.")

	payHandler := http.HandlerFunc(handlePayment)
	http.Handle("/pay", authMiddleware(payHandler))

	log.Println("Mock Payment Server (Go) berjalan di port :8090...")
	if err := http.ListenAndServe(":8090", nil); err != nil {
		log.Fatalf("Gagal menjalankan server: %v", err)
	}
}

func authMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		authHeader := r.Header.Get("Authorization")
		if authHeader == "" {
			http.Error(w, "Header Authorization tidak ditemukan", http.StatusUnauthorized)
			return
		}

		tokenString := strings.TrimPrefix(authHeader, "Bearer ")
		if tokenString == authHeader {
			http.Error(w, "Format Authorization header tidak valid, harus 'Bearer <token>'", http.StatusUnauthorized)
			return
		}

		keySet, err := jwksCache.Get(r.Context(), authServiceJwkUrl)
		if err != nil {
			log.Printf("Gagal mendapatkan JWK set dari cache: %v", err)
			http.Error(w, "Gagal memverifikasi token: tidak bisa mengambil kunci publik", http.StatusInternalServerError)
			return
		}

		parsedToken, err := jwt.Parse(

			[]byte(tokenString),

			jwt.WithValidate(false),

			jwt.WithVerify(false),
		)

		if err != nil {

			log.Printf("Gagal parse token (format tidak valid): %v", err)

			http.Error(w, "Format token tidak valid", http.StatusUnauthorized)

			return

		}

		_, err = jws.Verify([]byte(tokenString), jws.WithKeySet(keySet, jws.WithRequireKid(false)))

		if err != nil {

			log.Printf("Token tidak valid (signature salah): %v", err)

			http.Error(w, "Token signature tidak valid", http.StatusUnauthorized)

			return

		}

		if err := jwt.Validate(parsedToken); err != nil {

			log.Printf("Klaim token tidak valid (misal: kedaluwarsa): %v", err)

			http.Error(w, "Klaim token tidak valid (misal: kedaluwarsa)", http.StatusUnauthorized)

			return

		}
		ctx := context.WithValue(r.Context(), tokenContextKey, tokenString)
		next.ServeHTTP(w, r.WithContext(ctx))
	})
}

func handlePayment(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Hanya metode POST yang diizinkan", http.StatusMethodNotAllowed)
		return
	}

	jwtToken, ok := r.Context().Value(tokenContextKey).(string)
	if !ok {
		http.Error(w, "Gagal mendapatkan token dari context", http.StatusInternalServerError)
		return
	}

	var req MockPaymentRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Request body tidak valid", http.StatusBadRequest)
		return
	}

	log.Printf("Menerima request pembayaran untuk VA: %s, Jumlah: %d", req.VaNumber, req.Amount)

	callbackReq := TopUpCallbackRequest{
		VaNumber:         req.VaNumber,
		PaidAmount:       req.Amount,
		PaymentTimestamp: time.Now().UTC().Format(time.RFC3339Nano),
		PaymentReference: fmt.Sprintf("mock-ref-%d", time.Now().Unix()),
	}

	payload := fmt.Sprintf("%s%s%s%s",
		callbackReq.VaNumber,
		callbackReq.PaidAmount.String(),
		callbackReq.PaymentTimestamp,
		callbackReq.PaymentReference,
	)

	signature := calculateHmacSha256(payload, clientSecret)
	log.Printf("Signature yang dihitung: %s", signature)

	err := sendCallback(callbackReq, signature, jwtToken)
	if err != nil {
		log.Printf("Gagal mengirim callback ke transaction-service: %v", err)
		http.Error(w, "Gagal memproses callback pembayaran", http.StatusInternalServerError)
		return
	}

	log.Println("Callback berhasil dikirim ke transaction-service")
	w.WriteHeader(http.StatusOK)
	if _, err := fmt.Fprintln(w, "Pembayaran berhasil! Callback telah dikirim ke transaction-service."); err != nil {
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

func sendCallback(callbackData TopUpCallbackRequest, signature string, jwtToken string) error {
	jsonData, err := json.Marshal(callbackData)
	if err != nil {
		return fmt.Errorf("gagal marshal data callback: %w", err)
	}

	req, err := http.NewRequest("POST", transactionServiceCallbackURL, bytes.NewBuffer(jsonData))
	if err != nil {
		return fmt.Errorf("gagal membuat request: %w", err)
	}

	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Signature", signature)
	req.Header.Set("Authorization", "Bearer "+jwtToken)

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
