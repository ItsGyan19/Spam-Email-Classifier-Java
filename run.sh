#!/usr/bin/env bash
# Start Spam Email Classifier Spring Boot Web Application
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
cd "$DIR"

echo "Starting Spam Email Classifier on http://localhost:8080 ..."
mvn spring-boot:run
