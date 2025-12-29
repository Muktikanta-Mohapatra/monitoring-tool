use crate::parser::{FormatDetector, ParsedEvent, ParseError};
use crate::enrichment::EnrichmentPipeline;
use crate::masking::MaskingEngine;
use crate::routing::ConditionalRouter;
use std::sync::Arc;
use thiserror::Error;
use serde_json::Value;
use std::collections::HashMap;

#[derive(Error, Debug)]
pub enum PipelineError {
    #[error("Parse error: {0}")]
    ParseError(#[from] ParseError),
    #[error("No parser found for format")]
    NoParserFound,
    #[error("Format detection failed")]
    FormatDetectionFailed,
}

pub type PipelineResult<T> = Result<T, PipelineError>;

#[derive(Debug)]
pub struct EnrichedEvent {
    pub parsed: ParsedEvent,
    pub fields: HashMap<String, Value>,
}

pub struct LogPipeline {
    detector: Arc<FormatDetector>,
    enricher: Arc<EnrichmentPipeline>,
    masker: Arc<MaskingEngine>,
    _router: Arc<ConditionalRouter>,
}

impl LogPipeline {
    pub fn new(
        detector: Arc<FormatDetector>,
        enricher: Arc<EnrichmentPipeline>,
        masker: Arc<MaskingEngine>,
        router: Arc<ConditionalRouter>,
    ) -> Self {
        LogPipeline {
            detector,
            enricher,
            masker,
            _router: router,
        }
    }

    pub async fn process(&self, line: &str) -> PipelineResult<EnrichedEvent> {
        let parser_idx = self.detector.detect(line)
            .ok_or(PipelineError::NoParserFound)?;

        let parser = self.detector.get_parser(parser_idx)
            .ok_or(PipelineError::FormatDetectionFailed)?;

        let mut parsed = parser.parse(line).await?;

        let mut fields = parsed.fields.clone();

        let masked = self.masker.apply_masking(fields)
            .unwrap_or_else(|e| {
                tracing::warn!("Masking error: {}, using original fields", e);
                parsed.fields.clone()
            });
        fields = masked;

        fields = self.masker.detect_and_mask_pii(fields);

        let enriched = self.enricher.enrich(fields).await
            .unwrap_or_else(|e| {
                tracing::warn!("Enrichment error: {}, using current fields", e);
                parsed.fields.clone()
            });
        fields = enriched;

        parsed.fields = fields.clone();

        Ok(EnrichedEvent {
            parsed,
            fields,
        })
    }

    pub async fn process_batch(&self, lines: Vec<String>) -> Vec<Result<EnrichedEvent, PipelineError>> {
        let mut results = Vec::new();
        for line in lines {
            results.push(self.process(&line).await);
        }
        results
    }

    pub fn detector(&self) -> &Arc<FormatDetector> {
        &self.detector
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::parser::JsonParser;
    use crate::routing::ConditionalRouter;

    #[tokio::test]
    async fn test_pipeline_json_processing() {
        let json_parser = Arc::new(JsonParser::new("json".to_string(), None));
        let detector = Arc::new(FormatDetector::new(vec![json_parser], 100, 0.8, 10000));
        let enricher = Arc::new(EnrichmentPipeline::new());
        let masker = Arc::new(MaskingEngine::new());
        let router = Arc::new(ConditionalRouter::new("default".to_string()));

        let pipeline = LogPipeline::new(detector, enricher, masker, router);

        let json_line = r#"{"level":"INFO","message":"test message","timestamp":"2025-01-01T00:00:00Z"}"#;
        let result = pipeline.process(json_line).await;

        assert!(result.is_ok());
        let event = result.unwrap();
        assert_eq!(event.parsed.parser_id, "json");
    }

    #[tokio::test]
    async fn test_pipeline_batch_processing() {
        let json_parser = Arc::new(JsonParser::new("json".to_string(), None));
        let detector = Arc::new(FormatDetector::new(vec![json_parser], 100, 0.8, 10000));
        let enricher = Arc::new(EnrichmentPipeline::new());
        let masker = Arc::new(MaskingEngine::new());
        let router = Arc::new(ConditionalRouter::new("default".to_string()));

        let pipeline = LogPipeline::new(detector, enricher, masker, router);

        let lines = vec![
            r#"{"level":"INFO","message":"msg1","timestamp":"2025-01-01T00:00:00Z"}"#.to_string(),
            r#"{"level":"ERROR","message":"msg2","timestamp":"2025-01-01T00:00:01Z"}"#.to_string(),
        ];

        let results = pipeline.process_batch(lines).await;
        assert_eq!(results.len(), 2);
        assert!(results[0].is_ok());
        assert!(results[1].is_ok());
    }
}
