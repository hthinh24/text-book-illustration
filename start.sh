#!/bin/bash
set -a
source .env
set +a

docker-compose up -d

cleanup() {
  echo "Stopping..."
  kill "$BACKEND_PID" "$FRONTEND_PID" 2>/dev/null
  exit
}
trap cleanup SIGINT SIGTERM

cd backend/text-book-illustration
./mvnw spring-boot:run &
BACKEND_PID=$!

cd ../../frontend/text-book-illustration
npm install
npm run dev &
FRONTEND_PID=$!

wait "$BACKEND_PID" "$FRONTEND_PID"