set -a
source .env
set +a

docker-compose up -d

cd backend/text-book-illustration
./mvnw test
