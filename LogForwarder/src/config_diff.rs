use crate::config::{Config, InputConfig, OutputConfig};

#[derive(Debug, Clone)]
pub struct ConfigDiff {
    pub added_inputs: Vec<InputConfig>,
    pub removed_inputs: Vec<InputConfig>,
    pub unchanged_inputs: Vec<InputConfig>,
    pub added_outputs: Vec<OutputConfig>,
    pub removed_outputs: Vec<OutputConfig>,
    pub unchanged_outputs: Vec<OutputConfig>,
    pub has_changes: bool,
}

impl ConfigDiff {
    pub fn compute(old: &Config, new: &Config) -> Self {
        let added_inputs = Self::compute_added_items(&old.inputs, &new.inputs);
        let removed_inputs = Self::compute_removed_items(&old.inputs, &new.inputs);
        let unchanged_inputs = Self::compute_unchanged_items(&old.inputs, &new.inputs);

        let added_outputs = Self::compute_added_items(&old.outputs, &new.outputs);
        let removed_outputs = Self::compute_removed_items(&old.outputs, &new.outputs);
        let unchanged_outputs = Self::compute_unchanged_items(&old.outputs, &new.outputs);

        let has_changes = !added_inputs.is_empty()
            || !removed_inputs.is_empty()
            || !added_outputs.is_empty()
            || !removed_outputs.is_empty();

        ConfigDiff {
            added_inputs,
            removed_inputs,
            unchanged_inputs,
            added_outputs,
            removed_outputs,
            unchanged_outputs,
            has_changes,
        }
    }

    fn compute_added_items<T: PartialEq + Clone>(old: &[T], new: &[T]) -> Vec<T> {
        new.iter()
            .filter(|new_item| !old.contains(new_item))
            .cloned()
            .collect()
    }

    fn compute_removed_items<T: PartialEq + Clone>(old: &[T], new: &[T]) -> Vec<T> {
        old.iter()
            .filter(|old_item| !new.contains(old_item))
            .cloned()
            .collect()
    }

    fn compute_unchanged_items<T: PartialEq + Clone>(old: &[T], new: &[T]) -> Vec<T> {
        old.iter()
            .filter(|old_item| new.contains(old_item))
            .cloned()
            .collect()
    }

    pub fn log_summary(&self) {
        tracing::info!(
            "Configuration diff: +{} inputs, -{} inputs, +{} outputs, -{} outputs",
            self.added_inputs.len(),
            self.removed_inputs.len(),
            self.added_outputs.len(),
            self.removed_outputs.len()
        );

        for input in &self.added_inputs {
            let name = match input {
                InputConfig::File(cfg) => &cfg.name,
                InputConfig::Tcp(cfg) => &cfg.name,
                InputConfig::Udp(cfg) => &cfg.name,
                InputConfig::Script(cfg) => &cfg.name,
                InputConfig::WindowsEventLog(cfg) => &cfg.name,
            };
            tracing::info!("Added input: {}", name);
        }

        for input in &self.removed_inputs {
            let name = match input {
                InputConfig::File(cfg) => &cfg.name,
                InputConfig::Tcp(cfg) => &cfg.name,
                InputConfig::Udp(cfg) => &cfg.name,
                InputConfig::Script(cfg) => &cfg.name,
                InputConfig::WindowsEventLog(cfg) => &cfg.name,
            };
            tracing::info!("Removed input: {}", name);
        }

        for output in &self.added_outputs {
            tracing::info!("Added output: {}", output.name);
        }

        for output in &self.removed_outputs {
            tracing::info!("Removed output: {}", output.name);
        }
    }
}
