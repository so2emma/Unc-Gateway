# Public Key Infrastructure (PKI) - Local Development & Production Guide

This directory manages TLS certificates and PKI configuration for Unc Gateway.

## Directory Layout

```
pki/
├── ca/
│   ├── ca-key.pem        # Self-signed root CA private key (dev only)
│   ├── ca-cert.pem       # Root CA public certificate
│   └── truststore.p12    # PKCS12 truststore containing the root CA
├── certs/
│   ├── admin-api/        # Keystore & cert for admin-api
│   ├── analytics-api/    # Keystore & cert for analytics-api
│   ├── gateway-core/     # Keystore & cert for gateway-core (server & mTLS client)
│   └── mock-upstream/    # Keystore & cert for mock-upstream (upstream server)
├── generate-dev-certs.sh # Generator script
└── README.md
```

## Local Development PKI Generation

To regenerate local self-signed certificates:

```bash
chmod +x pki/generate-dev-certs.sh
./pki/generate-dev-certs.sh
```

Default keystore/truststore password: `changeit`

Each service directory in `pki/certs/<service>` contains:
- `key.pem`: Private key (RSA 2048)
- `cert.pem`: X.509 certificate signed by Unc Local Root CA with Subject Alternative Names (SANs) for `localhost`, `127.0.0.1`, and Docker Compose service hostnames.
- `keystore.p12`: PKCS12 archive containing the private key and certificate chain.
- `truststore.p12`: PKCS12 truststore containing the Root CA certificate.
- `ca-cert.pem`: PEM-encoded root CA certificate.

## Production Path: cert-manager & Let's Encrypt

In production (Kubernetes deployment in Phase 27):
1. **cert-manager** is deployed into the cluster.
2. A `ClusterIssuer` (e.g. Let's Encrypt ACME HTTP01/DNS01 or internal HashiCorp Vault PKI) issues public certificates for ingress endpoints.
3. For mTLS service-to-service communication, cert-manager issues short-lived leaf certificates signed by an internal intermediate CA.
4. Kubernetes automatically projects certificates as `Secret` volumes into pods (`/etc/tls/keystore.p12`, `/etc/tls/truststore.p12`).
5. Spring Boot SSL bundles or Netty `SslContextBuilder` dynamically reload certificates on rotation without downtime.
