use crate::parser::{ParserRegistry, ParseError};
use crate::enrichment::{Enricher, EnrichmentPipeline, EnrichmentError};
use crate::masking::{MaskingEngine, MaskingError};
use crate::routing::{ConditionalRouter, RoutingRule};
use serde_json::Value;
use std::collections::HashMap;
use std::sync::Arc;
use thiserror::Error;

#[derive(Error, Debug)]
pub enum PipelineError {
    #[error("Parse error: {0}")]
    ParseError(#[from] ParseError),
    #[error("Enrichment error: {0}")]
    EnrichmentError(#[from] EnrichmentError),
    #[error("Masking error: {0}")]
    MaskingError(#[from] MaskingError),
    #[error("Pipeline error: {0}")]
    PipelineError(String),
}

pub type PipelineResult<T> = Result<T, PipelineError>;

pub struct PipelineConfig {
    pub parse_errors_allowed: bool,
    pub stop_on_enrichment_error: bool,
    pub apply_masking: bool,
    pub apply_routing: bool,
}

impl Default for PipelineConfig {
    fn default() -> Self {
        PipelineConfig {
            parse_errors_allowed: true,
            stop_on_enrichment_error: false,
            apply_masking: true,
            apply_routing: true,
        }
    }
}

pub struct ProcessingPipeline {
    parser_registry: ParserRegistry,
    enrichment_pipeline: EnrichmentPipeline,
    masking_engine: MaskingEngine,
    router: ConditionalRouter,
    config: PipelineConfig,
}

impl ProcessingPipeline {
    pub fn new(
        parser_registry: ParserRegistry,
        masking_engine: MaskingEngine,
        router: ConditionalRouter,
    ) -> Self {
        ProcessingPipeline {
            parser_registry,
            enrichment_pipeline: EnrichmentPipeline::new(),
            masking_engine,
            router,
            config: PipelineConfig::default(),
        }
    }

    pub fn add_enricher(&mut self, enricher: Arc<dyn Enricher>) {
        self.enrichment_pipeline.add_enricher(enricher);
    }

    pub fn set_config(&mut self, config: PipelineConfig) {
        self.config = config;
    }

    pub async fn process_event(&self, line: &str, parser_id: Option<&str>) -> PipelineResult<ProcessedEvent> {
        let parser = if let Some(id) = parser_id {
            self.parser_registry.get_parser(id)
                .ok_or_else(|| PipelineError::PipelineError(format!("Parser {} not found", id)))?
        } else {
            self.parser_registry.detect_parser(line)
                .ok_or_else(|| PipelineError::PipelineError("No parser found for input".to_string()))?
        };

        let parsed = parser.parse(line).await?;

        let mut fields = parsed.fields.clone();

        if self.config.apply_masking {
            fields = self.masking_engine.apply_masking(fields)?;
            fields = self.masking_engine.detect_and_mask_pii(fields);
        }

        let fields = if !self.config.stop_on_enrichment_error {
            self.enrichment_pipeline.enrich(fields.clone()).await.unwrap_or(fields)
        } else {
            self.enrichment_pipeline.enrich(fields).await?
        };

        let routing_rule = if self.config.apply_routing {
            self.router.route(&fields)
                .unwrap_or_else(|_| RoutingRule::new("default".to_string(), "default".to_string()))
        } else {
            RoutingRule::new("none".to_string(), "default".to_string())
        };

        Ok(ProcessedEvent {
            timestamp: parsed.timestamp,
            fields,
            raw_message: parsed.raw_message,
            parser_id: parsed.parser_id,
            parse_duration_us: parsed.parse_duration_us,
            routing_rule,
        })
    }
}

pub struct ProcessedEvent {
    pub timestamp: chrono::DateTime<chrono::Utc>,
    pub fields: HashMap<String, Value>,
    pub raw_message: String,
    pub parser_id: String,
    pub parse_duration_us: u64,
    pub routing_rule: RoutingRule,
}

impl ProcessedEvent {
    pub fn to_json(&self) -> serde_json::Value {
        let mut obj = serde_json::json!({
            "timestamp": self.timestamp.to_rfc3339(),
            "parser_id": self.parser_id,
            "parse_duration_us": self.parse_duration_us,
            "raw_message": self.raw_message,
            "output_index": self.routing_rule.output_index,
        });

        if let serde_json::Value::Object(ref mut map) = obj {
            for (k, v) in &self.fields {
                map.insert(k.clone(), v.clone());
            }
        }

        obj
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::parser::JsonParser;

    #[test]
    fn test_pipeline_config_default() {
        let config = PipelineConfig::default();
        assert!(config.parse_errors_allowed);
        assert!(!config.stop_on_enrichment_error);
        assert!(config.apply_masking);
        assert!(config.apply_routing);
    }

    #[tokio::test]
    async fn test_processing_pipeline() {
        let registry = ParserRegistry::new();
        let json_parser = Arc::new(JsonParser::new("test_json".to_string(), None));
        registry.register("json".to_string(), json_parser);

        let masking_engine = MaskingEngine::new();
        let router = ConditionalRouter::new("default".to_string());

        let pipeline = ProcessingPipeline::new(registry, masking_engine, router);

        let json_line = r#"{"level":"INFO","message":"test message","timestamp":"2025-01-01T00:00:00Z"}"#;
        let result = pipeline.process_event(json_line, None).await;

        assert!(result.is_ok());
        let event = result.unwrap();
        assert_eq!(event.parser_id, "test_json");
    }
}
