# PKI Authentication for Emissary

This document describes how to configure and use PKI (Public Key Infrastructure) certificate-based authentication for Emissary Jetty endpoints.

## Overview

PKI authentication replaces traditional username/password authentication with X.509 client certificates. This provides stronger security through:

- **Cryptographic authentication**: Based on public/private key pairs rather than passwords
- **Mutual TLS (mTLS)**: Both server and client prove their identities
- **Non-repudiation**: Certificate-based authentication provides stronger audit trails
- **Automated authentication**: No need for users to enter credentials

## Architecture

The PKI authentication implementation consists of:

1. **PkiLoginService**: Manages certificate-to-user-role mappings
2. **PkiAuthenticator**: Jetty authenticator that validates client certificates
3. **PkiAuthenticationConfig**: Configuration loader for PKI settings
4. **EmissaryServer**: Updated to support PKI authentication

## Configuration

### 1. Enable PKI Authentication

Edit `src/main/resources/emissary/client/HTTPConnectionFactory.cfg`:

```properties
# Enable PKI authentication
PKI_AUTHENTICATION_ENABLED = "true"

# Require client certificates (true) or make them optional (false)
PKI_CLIENT_CERT_REQUIRED = "true"

# Allow any valid certificate (true) or only explicitly mapped ones (false)
PKI_ALLOW_ANY_VALID_CERT = "false"

# Default role for authenticated users
PKI_DEFAULT_ROLE = "emissary"
```

### 2. Configure SSL/TLS with Keystores

In the same file, configure your SSL keystores:

```properties
# Server keystore (contains server certificate and private key)
javax.net.ssl.keyStore = "/path/to/server-keystore.jks"
javax.net.ssl.keyStorePassword = "changeit"
javax.net.ssl.keyStoreType = "JKS"

# Truststore (contains trusted CA certificates for validating client certificates)
javax.net.ssl.trustStore = "/path/to/truststore.jks"
javax.net.ssl.trustStorePassword = "changeit"
javax.net.ssl.trustStoreType = "JKS"
```

**Password Options:**
- Direct password: `"mypassword"`
- From file: `"file:///path/to/password.txt"`
- From environment: `"${KEYSTORE_PASSWORD}"`

### 3. Map Certificates to Roles

Create a PKI users file (e.g., `/path/to/pki-users.properties`):

```properties
# Format: certificate_dn = role1,role2,role3
CN=John Doe,OU=Engineering,O=Emissary,C=US = emissary,admin
CN=Jane Smith,OU=Operations,O=Emissary,C=US = emissary,support
CN=Bob Manager,OU=Management,O=Emissary,C=US = manager,admin
```

Then reference it in `HTTPConnectionFactory.cfg`:

```properties
PKI_USERS_FILE = "/path/to/pki-users.properties"
```

### 4. Use DN Patterns (Alternative to Explicit Mappings)

Instead of mapping every certificate, you can use regex patterns:

```properties
# Accept any Engineering department member
PKI_TRUSTED_DN_PATTERN = "^CN=.*,OU=Engineering,O=Emissary,C=US$"

# Accept any Operations department member
PKI_TRUSTED_DN_PATTERN = "^CN=.*,OU=Operations,O=Emissary,C=US$"
```

## Authentication Modes

### Mode 1: Strict Certificate Mapping
```properties
PKI_AUTHENTICATION_ENABLED = "true"
PKI_CLIENT_CERT_REQUIRED = "true"
PKI_ALLOW_ANY_VALID_CERT = "false"
PKI_USERS_FILE = "/path/to/pki-users.properties"
```
- Client MUST present a certificate
- Certificate MUST be explicitly mapped in the users file
- Most secure option

### Mode 2: Trusted CA with Pattern Matching
```properties
PKI_AUTHENTICATION_ENABLED = "true"
PKI_CLIENT_CERT_REQUIRED = "true"
PKI_ALLOW_ANY_VALID_CERT = "false"
PKI_TRUSTED_DN_PATTERN = "^CN=.*,OU=Engineering,O=Emissary,C=US$"
```
- Client MUST present a certificate
- Certificate must match a trusted DN pattern
- Good for organizations with many users

### Mode 3: Any Trusted Certificate
```properties
PKI_AUTHENTICATION_ENABLED = "true"
PKI_CLIENT_CERT_REQUIRED = "true"
PKI_ALLOW_ANY_VALID_CERT = "true"
PKI_DEFAULT_ROLE = "emissary"
```
- Client MUST present a certificate
- Any certificate signed by a trusted CA is accepted
- Simpler configuration, relies on CA trust

### Mode 4: Optional PKI (Fallback to Username/Password)
```properties
PKI_AUTHENTICATION_ENABLED = "true"
PKI_CLIENT_CERT_REQUIRED = "false"
PKI_ALLOW_ANY_VALID_CERT = "false"
PKI_USERS_FILE = "/path/to/pki-users.properties"
```
- Client MAY present a certificate
- Falls back to traditional authentication if no certificate
- Good for migration scenarios

## Creating Certificates

### 1. Create a CA (Certificate Authority)

```bash
# Generate CA private key
openssl genrsa -out ca-key.pem 4096

# Generate CA certificate
openssl req -new -x509 -days 3650 -key ca-key.pem -out ca-cert.pem \
  -subj "/C=US/O=Emissary/OU=CA/CN=Emissary CA"
```

### 2. Create Server Certificate

```bash
# Generate server private key
openssl genrsa -out server-key.pem 2048

# Generate server CSR
openssl req -new -key server-key.pem -out server.csr \
  -subj "/C=US/O=Emissary/OU=Server/CN=localhost"

# Sign server certificate with CA
openssl x509 -req -days 365 -in server.csr \
  -CA ca-cert.pem -CAkey ca-key.pem -CAcreateserial \
  -out server-cert.pem
```

### 3. Create Client Certificate

```bash
# Generate client private key
openssl genrsa -out client-key.pem 2048

# Generate client CSR
openssl req -new -key client-key.pem -out client.csr \
  -subj "/C=US/O=Emissary/OU=Engineering/CN=John Doe"

# Sign client certificate with CA
openssl x509 -req -days 365 -in client.csr \
  -CA ca-cert.pem -CAkey ca-key.pem -CAcreateserial \
  -out client-cert.pem
```

### 4. Create Keystores

```bash
# Create server keystore
openssl pkcs12 -export -in server-cert.pem -inkey server-key.pem \
  -out server.p12 -name server -passout pass:changeit

keytool -importkeystore -srckeystore server.p12 -srcstoretype PKCS12 \
  -destkeystore server-keystore.jks -deststoretype JKS \
  -srcstorepass changeit -deststorepass changeit

# Create truststore with CA certificate
keytool -import -trustcacerts -alias ca -file ca-cert.pem \
  -keystore truststore.jks -storepass changeit -noprompt

# Create client keystore (for testing)
openssl pkcs12 -export -in client-cert.pem -inkey client-key.pem \
  -out client.p12 -name client -passout pass:changeit
```

### 5. Extract Certificate DN

To get the exact DN for your PKI users file:

```bash
openssl x509 -in client-cert.pem -noout -subject -nameopt RFC2253
```

Example output: `CN=John Doe,OU=Engineering,O=Emissary,C=US`

## Testing PKI Authentication

### Test with curl

```bash
# Test with client certificate
curl --cert client-cert.pem --key client-key.pem \
  --cacert ca-cert.pem https://localhost:8443/api/health

# Test with PKCS12 file
curl --cert-type P12 --cert client.p12:changeit \
  --cacert ca-cert.pem https://localhost:8443/api/health
```

### Test with Emissary Client

The `EmissaryClient` automatically uses certificates configured in `HTTPConnectionFactory.cfg`:

```java
EmissaryClient client = new EmissaryClient();
EmissaryResponse response = client.send(new HttpGet("https://localhost:8443/api/places"));
```

## Troubleshooting

### Issue: "No client certificate provided"

**Cause**: Client didn't send a certificate or SSL handshake failed

**Solutions:**
- Verify `PKI_CLIENT_CERT_REQUIRED` setting
- Check that client has a valid certificate and private key
- Ensure client trusts the server certificate
- Check firewall/proxy settings

### Issue: "Certificate validation failed - certificate not authorized"

**Cause**: Certificate is valid but not mapped to any roles

**Solutions:**
- Add certificate DN to `pki-users.properties`
- Add a matching `PKI_TRUSTED_DN_PATTERN`
- Set `PKI_ALLOW_ANY_VALID_CERT = "true"` (if appropriate)

### Issue: "javax.net.ssl.SSLHandshakeException"

**Cause**: SSL/TLS configuration error

**Solutions:**
- Verify keystore and truststore paths
- Check keystore passwords
- Ensure certificates are not expired
- Verify CA certificate is in truststore

### Enable Debug Logging

Add to your logging configuration:

```properties
log4j.logger.emissary.server.auth=DEBUG
log4j.logger.org.eclipse.jetty.security=DEBUG
```

## Security Considerations

1. **Protect Private Keys**: Store private keys securely with restricted file permissions
2. **Certificate Expiration**: Monitor and renew certificates before expiration
3. **Revocation**: Implement CRL or OCSP checking for revoked certificates
4. **Strong Algorithms**: Use modern algorithms (RSA 2048+, or ECDSA)
5. **Least Privilege**: Assign minimum necessary roles to certificates
6. **Audit Logging**: Log all authentication attempts and certificate usage

## Migration from Username/Password

1. **Phase 1 - Optional PKI**: Set `PKI_CLIENT_CERT_REQUIRED = "false"`
2. **Phase 2 - Issue Certificates**: Distribute certificates to all users
3. **Phase 3 - Enforce PKI**: Set `PKI_CLIENT_CERT_REQUIRED = "true"`
4. **Phase 4 - Remove Password Auth**: Optional - modify code to fully remove password auth

## API Reference

### Configuration Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `PKI_AUTHENTICATION_ENABLED` | boolean | false | Enable PKI authentication |
| `PKI_CLIENT_CERT_REQUIRED` | boolean | false | Require client certificates |
| `PKI_ALLOW_ANY_VALID_CERT` | boolean | false | Accept any trusted certificate |
| `PKI_DEFAULT_ROLE` | string | emissary | Default role for authenticated users |
| `PKI_USERS_FILE` | string | null | Path to certificate-to-role mappings |
| `PKI_TRUSTED_DN_PATTERN` | string | - | Regex pattern for trusted DNs (repeatable) |

### Available Roles

- `emissary`: Basic Emissary user
- `admin`: Administrative privileges
- `support`: Support operations
- `manager`: Management operations
- `everyone`: All authenticated users

## Example Configurations

See `src/main/config/pki-users.properties.example` and
`src/main/resources/emissary/client/HTTPConnectionFactory.cfg` for complete examples.

## Further Reading

- [RFC 5280 - X.509 Certificate Profile](https://tools.ietf.org/html/rfc5280)
- [Jetty SSL Configuration](https://www.eclipse.org/jetty/documentation/)
- [Mutual TLS Best Practices](https://owasp.org/www-community/Mutual_Authentication)
