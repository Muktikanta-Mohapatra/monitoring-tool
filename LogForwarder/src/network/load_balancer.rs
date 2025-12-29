use crate::network::{IndexerConfig, NetworkError};
use dashmap::DashMap;
use parking_lot::RwLock;
use rand::Rng;
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::Arc;
use tracing::debug;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum LoadBalancingStrategy {
    RoundRobin,
    LeastConnections,
    Random,
    Weighted,
}

pub struct LoadBalancer {
    indexers: Vec<IndexerConfig>,
    strategy: LoadBalancingStrategy,
    round_robin_counter: AtomicUsize,
    active_connections: Arc<DashMap<String, usize>>,
    weights: Arc<RwLock<Vec<u32>>>,
}

impl LoadBalancer {
    pub fn new(indexers: Vec<IndexerConfig>, strategy: LoadBalancingStrategy) -> Self {
        let weights = indexers.iter().map(|i| i.weight).collect::<Vec<_>>();

        LoadBalancer {
            indexers,
            strategy,
            round_robin_counter: AtomicUsize::new(0),
            active_connections: Arc::new(DashMap::new()),
            weights: Arc::new(RwLock::new(weights)),
        }
    }

    pub fn select_indexer(&self) -> Result<IndexerConfig, NetworkError> {
        if self.indexers.is_empty() {
            return Err(NetworkError::AllIndexersUnavailable);
        }

        let selected = match self.strategy {
            LoadBalancingStrategy::RoundRobin => self.select_round_robin(),
            LoadBalancingStrategy::LeastConnections => self.select_least_connections(),
            LoadBalancingStrategy::Random => self.select_random(),
            LoadBalancingStrategy::Weighted => self.select_weighted(),
        };

        debug!("Selected indexer: {}", selected.id);
        Ok(selected)
    }

    fn select_round_robin(&self) -> IndexerConfig {
        let idx = self.round_robin_counter.fetch_add(1, Ordering::Relaxed) % self.indexers.len();
        self.indexers[idx].clone()
    }

    fn select_least_connections(&self) -> IndexerConfig {
        self.indexers
            .iter()
            .min_by_key(|idx| {
                self.active_connections
                    .get(&idx.id)
                    .map(|v| *v)
                    .unwrap_or(0)
            })
            .cloned()
            .unwrap_or_else(|| self.indexers[0].clone())
    }

    fn select_random(&self) -> IndexerConfig {
        let mut rng = rand::thread_rng();
        let idx = rng.gen_range(0..self.indexers.len());
        self.indexers[idx].clone()
    }

    fn select_weighted(&self) -> IndexerConfig {
        let weights = self.weights.read();
        let total_weight: u32 = weights.iter().sum();

        if total_weight == 0 {
            return self.indexers[0].clone();
        }

        let mut rng = rand::thread_rng();
        let mut random_value = rng.gen_range(0..total_weight);

        for (i, &weight) in weights.iter().enumerate() {
            if random_value < weight {
                return self.indexers[i].clone();
            }
            random_value -= weight;
        }

        self.indexers[self.indexers.len() - 1].clone()
    }

    pub fn record_connection_open(&self, indexer_id: &str) {
        self.active_connections
            .entry(indexer_id.to_string())
            .and_modify(|count| *count += 1)
            .or_insert(1);
    }

    pub fn record_connection_closed(&self, indexer_id: &str) {
        if let Some(mut entry) = self.active_connections.get_mut(indexer_id) {
            if *entry > 0 {
                *entry -= 1;
            }
        }
    }

    pub fn get_active_connections(&self, indexer_id: &str) -> usize {
        self.active_connections
            .get(indexer_id)
            .map(|v| *v)
            .unwrap_or(0)
    }

    pub fn get_indexer_by_id(&self, id: &str) -> Option<IndexerConfig> {
        self.indexers.iter().find(|idx| idx.id == id).cloned()
    }

    pub fn update_weights(&self, weights: Vec<u32>) -> Result<(), NetworkError> {
        if weights.len() != self.indexers.len() {
            return Err(NetworkError::InvalidConfig(
                "Weight count mismatch".to_string(),
            ));
        }
        *self.weights.write() = weights;
        Ok(())
    }

    pub fn get_stats(&self) -> LoadBalancerStats {
        let indexer_stats = self
            .indexers
            .iter()
            .map(|idx| {
                let connections = self.get_active_connections(&idx.id);
                (idx.id.clone(), connections)
            })
            .collect();

        LoadBalancerStats {
            strategy: self.strategy,
            indexer_connections: indexer_stats,
            total_indexers: self.indexers.len(),
        }
    }
}

#[derive(Debug, Clone)]
pub struct LoadBalancerStats {
    pub strategy: LoadBalancingStrategy,
    pub indexer_connections: Vec<(String, usize)>,
    pub total_indexers: usize,
}

#[cfg(test)]
mod tests {
    use super::*;

    fn create_test_indexers() -> Vec<IndexerConfig> {
        vec![
            IndexerConfig {
                id: "indexer1".to_string(),
                host: "localhost".to_string(),
                port: 50051,
                weight: 100,
            },
            IndexerConfig {
                id: "indexer2".to_string(),
                host: "localhost".to_string(),
                port: 50052,
                weight: 100,
            },
            IndexerConfig {
                id: "indexer3".to_string(),
                host: "localhost".to_string(),
                port: 50053,
                weight: 100,
            },
        ]
    }

    #[test]
    fn test_round_robin() {
        let lb = LoadBalancer::new(create_test_indexers(), LoadBalancingStrategy::RoundRobin);

        let first = lb.select_indexer().unwrap();
        let second = lb.select_indexer().unwrap();
        let third = lb.select_indexer().unwrap();
        let fourth = lb.select_indexer().unwrap();

        assert_eq!(first.id, "indexer1");
        assert_eq!(second.id, "indexer2");
        assert_eq!(third.id, "indexer3");
        assert_eq!(fourth.id, "indexer1");
    }

    #[test]
    fn test_least_connections() {
        let lb = LoadBalancer::new(
            create_test_indexers(),
            LoadBalancingStrategy::LeastConnections,
        );

        lb.record_connection_open("indexer1");
        lb.record_connection_open("indexer1");
        lb.record_connection_open("indexer2");

        let selected = lb.select_indexer().unwrap();
        assert_eq!(selected.id, "indexer3");
    }

    #[test]
    fn test_weighted_selection() {
        let mut indexers = create_test_indexers();
        indexers[0].weight = 50;
        indexers[1].weight = 30;
        indexers[2].weight = 20;

        let lb = LoadBalancer::new(indexers, LoadBalancingStrategy::Weighted);

        let mut counts = std::collections::HashMap::new();
        for _ in 0..1000 {
            let selected = lb.select_indexer().unwrap();
            *counts.entry(selected.id).or_insert(0) += 1;
        }

        let count1 = counts.get("indexer1").unwrap_or(&0);
        let count2 = counts.get("indexer2").unwrap_or(&0);
        let count3 = counts.get("indexer3").unwrap_or(&0);

        assert!((count1 > count2) && (count2 > count3));
    }
}
