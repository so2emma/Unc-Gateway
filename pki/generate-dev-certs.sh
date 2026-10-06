#!/bin/sh
set -e

# ==============================================================================
# Local Development PKI Generator
# ==============================================================================
# Generates a self-signed root CA and leaf certificates for:
#   - gateway-core (Server & Client certificate)
#   - admin-api (Server certificate)
#   - analytics-api (Server certificate)
#   - mock-upstream (Server certificate)
#
# Leaf certificates include Subject Alternative Names (SANs) for both localhost
# and the Docker Compose service network hostnames.
#
# Production Replacement Path:
# In production (Kubernetes), replace this local PKI generation with cert-manager
# and Let's Encrypt / Vault ClusterIssuer (Phase 27). Secrets containing TLS
# certificates and keys will be provisioned directly into Kubernetes Secret
# resources and mounted to the pods.
# ==============================================================================

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PKI_DIR="$SCRIPT_DIR"
CA_DIR="$PKI_DIR/ca"
CERTS_DIR="$PKI_DIR/certs"
PASSWORD="changeit"

mkdir -p "$CA_DIR"
mkdir -p "$CERTS_DIR"

echo "==> Generating Self-Signed Root Certificate Authority (CA)..."
CA_KEY="$CA_DIR/ca-key.pem"
CA_CERT="$CA_DIR/ca-cert.pem"

if [ ! -f "$CA_KEY" ] || [ ! -f "$CA_CERT" ]; then
    openssl genrsa -out "$CA_KEY" 4096
    openssl req -x509 -new -nodes -key "$CA_KEY" -sha256 -days 3650 \
        -subj "/C=US/ST=Dev/L=Local/O=Unc Gateway/CN=Unc Local Root CA" \
        -out "$CA_CERT"
    echo "Created Root CA: $CA_CERT"
else
    echo "Root CA already exists: $CA_CERT"
fi

# Create truststore with CA cert
CA_TRUSTSTORE="$CA_DIR/truststore.p12"
rm -f "$CA_TRUSTSTORE"
keytool -importcert -noprompt -alias ca -file "$CA_CERT" -keystore "$CA_TRUSTSTORE" -storepass "$PASSWORD" -storetype PKCS12

generate_service_cert() {
    SERVICE_NAME="$1"
    DNS_NAMES="$2"
    TARGET_DIR="$CERTS_DIR/$SERVICE_NAME"

    echo "==> Generating Certificate for $SERVICE_NAME..."
    mkdir -p "$TARGET_DIR"

    KEY_FILE="$TARGET_DIR/key.pem"
    CSR_FILE="$TARGET_DIR/cert.csr"
    CERT_FILE="$TARGET_DIR/cert.pem"
    P12_FILE="$TARGET_DIR/keystore.p12"
    EXT_FILE="$TARGET_DIR/openssl.cnf"

    openssl genrsa -out "$KEY_FILE" 2048

    cat > "$EXT_FILE" <<EOF
[req]
default_bits = 2048
prompt = no
default_md = sha256
distinguished_name = dn
req_extensions = req_ext

[dn]
C = US
ST = Dev
L = Local
O = Unc Gateway
CN = $SERVICE_NAME

[req_ext]
subjectAltName = $DNS_NAMES

[v3_ca]
subjectAltName = $DNS_NAMES
basicConstraints = CA:FALSE
keyUsage = digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth, clientAuth
EOF

    openssl req -new -key "$KEY_FILE" -out "$CSR_FILE" -config "$EXT_FILE"

    openssl x509 -req -in "$CSR_FILE" -CA "$CA_CERT" -CAkey "$CA_KEY" -CAcreateserial \
        -out "$CERT_FILE" -days 3650 -sha256 \
        -extfile "$EXT_FILE" -extensions v3_ca

    rm -f "$CSR_FILE" "$EXT_FILE"

    # Export to PKCS12 format for Spring Boot / Netty
    rm -f "$P12_FILE"
    openssl pkcs12 -export -in "$CERT_FILE" -inkey "$KEY_FILE" -certfile "$CA_CERT" \
        -out "$P12_FILE" -name "$SERVICE_NAME" -password "pass:$PASSWORD"

    # Also copy truststore to service dir
    cp "$CA_TRUSTSTORE" "$TARGET_DIR/truststore.p12"
    cp "$CA_CERT" "$TARGET_DIR/ca-cert.pem"

    echo "Completed $SERVICE_NAME certificate & PKCS12 keystore."
}

generate_service_cert "gateway-core" "DNS:localhost,DNS:gateway-core,IP:127.0.0.1"
generate_service_cert "admin-api" "DNS:localhost,DNS:admin-api,IP:127.0.0.1"
generate_service_cert "analytics-api" "DNS:localhost,DNS:analytics-api,IP:127.0.0.1"
generate_service_cert "mock-upstream" "DNS:localhost,DNS:mock-upstream,IP:127.0.0.1"

echo "==> All development certificates generated successfully!"
