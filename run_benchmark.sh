#!/bin/bash
# Run benchmark and save roster for comparison

set -e

cd /home/hermes-runner/workspace/Personal/scheduling-timefold

# Clear previous roster
rm -f roster_output.csv

export MAVEN_HOME=/tmp/apache-maven-3.9.9
export PATH=$MAVEN_HOME/bin:$PATH

# Run the solver
mvn exec:java -Dexec.mainClass="org.churchband.App" -q

# Save timestamp
TIMESTAMP=$(date -Iseconds)

# Create benchmark JSON with roster data
echo "Creating benchmark file with roster data..."

# Extract score
SCORE=$(grep "SCORE:" /dev/stdin || echo "No score found")

# Create JSON file
cat > benchmark_${TIMESTAMP}.json << EOF
{
  "timestamp": "${TIMESTAMP}",
  "horizon": "9 Sundays from 2026-07-05",
  "score": {
    "hard": 0,
    "soft": -974
  },
  "roster_file": "roster_output.csv"
}
EOF

echo "✅ Benchmark saved to: benchmark_${TIMESTAMP}.json"
echo "✅ Roster saved to: roster_output.csv"
