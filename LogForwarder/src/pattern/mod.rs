use regex::Regex;
use std::path::Path;
use thiserror::Error;

#[derive(Error, Debug)]
pub enum PatternError {
    #[error("Invalid regex pattern: {0}")]
    InvalidPattern(String),
    #[error("Glob pattern error: {0}")]
    GlobError(String),
}

pub struct GlobPattern {
    includes: Vec<Regex>,
    excludes: Vec<Regex>,
}

impl GlobPattern {
    pub fn new(
        include_patterns: Vec<&str>,
        exclude_patterns: Vec<&str>,
    ) -> Result<Self, PatternError> {
        let mut includes = Vec::new();
        let mut excludes = Vec::new();

        for pattern in include_patterns {
            let regex = glob_to_regex(pattern)?;
            includes.push(regex);
        }

        for pattern in exclude_patterns {
            let regex = glob_to_regex(pattern)?;
            excludes.push(regex);
        }

        Ok(GlobPattern { includes, excludes })
    }

    pub fn matches(&self, path: &Path) -> bool {
        let mut path_str = path.to_string_lossy().to_string();
        path_str = path_str.replace('\\', "/");

        let included = self.includes.iter().any(|re| re.is_match(&path_str));
        if !included {
            return false;
        }

        let excluded = self.excludes.iter().any(|re| re.is_match(&path_str));
        !excluded
    }

    pub fn filter_paths<'a>(&self, paths: Vec<&'a Path>) -> Vec<&'a Path> {
        paths.into_iter().filter(|p| self.matches(p)).collect()
    }
}

fn glob_to_regex(pattern: &str) -> Result<Regex, PatternError> {
    let mut regex_pattern = String::new();
    let mut chars = pattern.chars().peekable();

    while let Some(ch) = chars.next() {
        match ch {
            '*' => {
                if chars.peek() == Some(&'*') {
                    chars.next();
                    if chars.peek() == Some(&'/') {
                        chars.next();
                        regex_pattern.push_str("(?:.*/)?");
                    } else {
                        regex_pattern.push_str(".*");
                    }
                } else {
                    regex_pattern.push_str("[^/]*");
                }
            }
            '?' => {
                regex_pattern.push_str("[^/]");
            }
            '[' => {
                regex_pattern.push('[');
                for ch in chars.by_ref() {
                    if ch == ']' {
                        regex_pattern.push(']');
                        break;
                    } else if ch == '!' && regex_pattern.ends_with('[') {
                        regex_pattern.push('^');
                    } else {
                        regex_pattern.push(ch);
                    }
                }
            }
            '.' | '+' | '^' | '$' | '|' | '(' | ')' | '{' | '}' => {
                regex_pattern.push('\\');
                regex_pattern.push(ch);
            }
            ch => {
                regex_pattern.push(ch);
            }
        }
    }

    let regex_pattern = format!("^{}$", regex_pattern);
    Regex::new(&regex_pattern).map_err(|e| PatternError::InvalidPattern(e.to_string()))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_glob_simple() -> Result<(), PatternError> {
        let pattern = GlobPattern::new(vec!["*.log"], vec![])?;

        assert!(pattern.matches(Path::new("test.log")));
        assert!(!pattern.matches(Path::new("test.txt")));

        Ok(())
    }

    #[test]
    fn test_glob_recursive() -> Result<(), PatternError> {
        let pattern = GlobPattern::new(vec!["/var/log/**/*.log"], vec![])?;

        assert!(pattern.matches(Path::new("/var/log/test.log")));
        assert!(pattern.matches(Path::new("/var/log/app/debug.log")));
        assert!(!pattern.matches(Path::new("/var/log/test.txt")));

        Ok(())
    }

    #[test]
    fn test_glob_with_exclusions() -> Result<(), PatternError> {
        let pattern =
            GlobPattern::new(vec!["/var/log/**/*.log"], vec!["**/*.gz", "**/archive/**"])?;

        assert!(pattern.matches(Path::new("/var/log/test.log")));
        assert!(!pattern.matches(Path::new("/var/log/test.log.gz")));
        assert!(!pattern.matches(Path::new("/var/log/archive/old.log")));

        Ok(())
    }

    #[test]
    fn test_glob_question_mark() -> Result<(), PatternError> {
        let pattern = GlobPattern::new(vec!["test?.log"], vec![])?;

        assert!(pattern.matches(Path::new("test1.log")));
        assert!(pattern.matches(Path::new("testA.log")));
        assert!(!pattern.matches(Path::new("test.log")));
        assert!(!pattern.matches(Path::new("test12.log")));

        Ok(())
    }

    #[test]
    fn test_filter_paths() -> Result<(), PatternError> {
        let pattern = GlobPattern::new(vec!["*.log"], vec![])?;

        let paths = vec![
            Path::new("test.log"),
            Path::new("test.txt"),
            Path::new("debug.log"),
        ];

        let filtered = pattern.filter_paths(paths);
        assert_eq!(filtered.len(), 2);

        Ok(())
    }
}
