use crate::network::NetworkError;
use rustls::pki_types::CertificateDer;
use std::fs;
use std::sync::Arc;
use tracing::debug;

#[derive(Clone, Debug)]
pub struct TlsConfig {
    pub client_config: Arc<rustls::ClientConfig>,
    pub enabled: bool,
}

pub struct TlsConfigBuilder {
    cert_path: Option<String>,
    key_path: Option<String>,
    ca_path: Option<String>,
    enable_0rtt: bool,
    verify: bool,
    alpn_protocols: Vec<Vec<u8>>,
}

impl TlsConfigBuilder {
    pub fn new() -> Self {
        TlsConfigBuilder {
            cert_path: None,
            key_path: None,
            ca_path: None,
            enable_0rtt: true,
            verify: true,
            alpn_protocols: vec![b"h2".to_vec()],
        }
    }

    pub fn with_cert_path(mut self, path: impl Into<String>) -> Self {
        self.cert_path = Some(path.into());
        self
    }

    pub fn with_key_path(mut self, path: impl Into<String>) -> Self {
        self.key_path = Some(path.into());
        self
    }

    pub fn with_ca_path(mut self, path: impl Into<String>) -> Self {
        self.ca_path = Some(path.into());
        self
    }

    pub fn with_0rtt(mut self, enable: bool) -> Self {
        self.enable_0rtt = enable;
        self
    }

    pub fn with_verify(mut self, verify: bool) -> Self {
        self.verify = verify;
        self
    }

    pub fn build(self) -> Result<TlsConfig, NetworkError> {
        debug!("Building TLS config with 0-RTT: {}", self.enable_0rtt);

        let mut root_store = rustls::RootCertStore::empty();

        if let Some(ca_path) = &self.ca_path {
            debug!("Loading CA certificates from {}", ca_path);
            let ca_certs = Self::load_certificates(ca_path)?;
            for cert in ca_certs {
                root_store.add(cert).map_err(|e| {
                    NetworkError::TlsError(format!("Failed to add certificate: {}", e))
                })?;
            }
        } else {
            debug!("Loading native CA certificates");
            let native_certs = rustls_native_certs::load_native_certs().map_err(|e| {
                NetworkError::TlsError(format!("Failed to load native certs: {}", e))
            })?;
            for cert in native_certs {
                root_store.add(cert).map_err(|e| {
                    NetworkError::TlsError(format!("Failed to add native certificate: {}", e))
                })?;
            }
        }

        let mut config = rustls::ClientConfig::builder()
            .with_root_certificates(root_store)
            .with_no_client_auth();

        config.alpn_protocols = self.alpn_protocols;

        if !self.verify {
            debug!("WARNING: TLS verification disabled (development only)");
            config = rustls::ClientConfig::builder()
                .dangerous()
                .with_custom_certificate_verifier(Arc::new(NoVerifier))
                .with_no_client_auth();
        }

        Ok(TlsConfig {
            client_config: Arc::new(config),
            enabled: true,
        })
    }

    fn load_certificates(path: &str) -> Result<Vec<CertificateDer<'static>>, NetworkError> {
        let pem = fs::read_to_string(path)
            .map_err(|e| NetworkError::TlsError(format!("Failed to read cert file: {}", e)))?;

        let certs = rustls_pemfile::certs(&mut pem.as_bytes())
            .collect::<Result<Vec<_>, _>>()
            .map_err(|e| NetworkError::TlsError(format!("Failed to parse certificates: {}", e)))?;

        if certs.is_empty() {
            return Err(NetworkError::TlsError(
                "No certificates found in file".to_string(),
            ));
        }

        Ok(certs)
    }
}

impl Default for TlsConfigBuilder {
    fn default() -> Self {
        Self::new()
    }
}

pub fn create_insecure_config() -> TlsConfig {
    debug!("Creating insecure TLS config for development");
    TlsConfig {
        client_config: Arc::new(
            rustls::ClientConfig::builder()
                .dangerous()
                .with_custom_certificate_verifier(Arc::new(NoVerifier))
                .with_no_client_auth(),
        ),
        enabled: true,
    }
}

#[derive(Debug)]
struct NoVerifier;

impl rustls::client::danger::ServerCertVerifier for NoVerifier {
    fn verify_server_cert(
        &self,
        _end_entity: &CertificateDer<'_>,
        _intermediates: &[CertificateDer<'_>],
        _server_name: &rustls::pki_types::ServerName<'_>,
        _ocsp_response: &[u8],
        _now: rustls::pki_types::UnixTime,
    ) -> Result<rustls::client::danger::ServerCertVerified, rustls::Error> {
        Ok(rustls::client::danger::ServerCertVerified::assertion())
    }

    fn verify_tls12_signature(
        &self,
        _message: &[u8],
        _cert: &CertificateDer<'_>,
        _dss: &rustls::DigitallySignedStruct,
    ) -> Result<rustls::client::danger::HandshakeSignatureValid, rustls::Error> {
        Ok(rustls::client::danger::HandshakeSignatureValid::assertion())
    }

    fn verify_tls13_signature(
        &self,
        _message: &[u8],
        _cert: &CertificateDer<'_>,
        _dss: &rustls::DigitallySignedStruct,
    ) -> Result<rustls::client::danger::HandshakeSignatureValid, rustls::Error> {
        Ok(rustls::client::danger::HandshakeSignatureValid::assertion())
    }

    fn supported_verify_schemes(&self) -> Vec<rustls::SignatureScheme> {
        vec![
            rustls::SignatureScheme::RSA_PKCS1_SHA256,
            rustls::SignatureScheme::ECDSA_NISTP256_SHA256,
            rustls::SignatureScheme::ED25519,
        ]
    }
}
